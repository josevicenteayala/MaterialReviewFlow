package com.proco.simulator;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties="server.port=18080")
class WorkflowIntegrationTest {
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://localhost:5432/proco_test"));}
 @Autowired JdbcTemplate db;
 final HttpClient client=HttpClient.newHttpClient();
 record Reply(int status,String body){}
 Reply request(String user,String path,String body,UUID key)throws Exception{
  var builder=HttpRequest.newBuilder(URI.create("http://localhost:18080/api"+path));
  if(user!=null)builder.header("Authorization","Basic "+Base64.getEncoder().encodeToString((user+":Simulacion123!").getBytes(StandardCharsets.UTF_8)));
  if(body!=null)builder.header("Content-Type","application/json").header("Idempotency-Key",key.toString()).POST(HttpRequest.BodyPublishers.ofString(body));
  var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());return new Reply(response.statusCode(),response.body());
 }
 String command(long version,String fields){return "{\"version\":"+version+",\"reason\":\"Prueba automática\",\"evidence\":\"EV-TEST\""+fields+"}";}
 String createBody(){return command(0,",\"orderCode\":\"OC-H-001\",\"supplierLot\":\""+UUID.randomUUID()+"\",\"productionDate\":\"2026-01-01\",\"quantity\":1,\"location\":\"Q-1\"");}
 UUID create()throws Exception{var response=request("almacen","/receipts",createBody(),UUID.randomUUID());assertEquals(200,response.status,response.body);return UUID.fromString(response.body.split("\"id\":\"")[1].split("\"")[0]);}
 Reply action(UUID id,String user,String action,long version,String fields)throws Exception{return request(user,"/lots/"+id+"/"+action,command(version,fields),UUID.randomUUID());}
 void inspect(UUID id)throws Exception{assertEquals(200,action(id,"calidad","inspect",0,",\"conforms\":true").status);}
 void sample(UUID id)throws Exception{assertEquals(200,action(id,"almacen","sample",1,",\"sampleCode\":\"M-TEST\"").status);}
 @Test void happyPathAndAudit()throws Exception{
  UUID id=create();inspect(id);sample(id);
  assertEquals(200,action(id,"tecnico","test",2,",\"resultValue\":15").status);
  var released=action(id,"aprobador","release",3,"");assertEquals(200,released.status);assertTrue(released.body.contains("QUARANTINE"));
  var available=action(id,"almacen","available",4,"");assertEquals(200,available.status);assertTrue(available.body.contains("AVAILABLE"));assertTrue(available.body.contains("RELEASED"));
  assertEquals(6,db.queryForObject("select count(*) from audit_event where lot_id=?",Integer.class,id));
  assertEquals(4,db.queryForObject("select count(distinct actor) from audit_event where lot_id=?",Integer.class,id));
  var timeline=request("calidad","/lots/"+id+"/timeline",null,null);assertEquals(200,timeline.status);assertTrue(timeline.body.contains("EV-TEST"));
 }
 @Test void elastanoUsesItsOwnCriticalRange()throws Exception{
  var response=request("almacen","/receipts",createBody().replace("OC-H-001","OC-E-001"),UUID.randomUUID());assertEquals(200,response.status);assertTrue(response.body.contains("Elongación"));
  UUID id=UUID.fromString(response.body.split("\"id\":\"")[1].split("\"")[0]);inspect(id);sample(id);
  assertEquals(200,action(id,"tecnico","test",2,",\"resultValue\":500").status);
  assertEquals(200,action(id,"aprobador","release",3,"").status);
  assertEquals(200,action(id,"almacen","available",4,"").status);
 }
 @Test void deniedAttemptsPersistAndDoNotAdvance()throws Exception{
  UUID id=create();assertEquals(403,action(id,"almacen","release",0,"").status);
  assertEquals(422,action(id,"almacen","available",0,"").status);
  inspect(id);assertEquals(422,action(id,"aprobador","release",1,"").status);
  assertEquals(409,action(id,"calidad","inspect",0,",\"conforms\":true").status);
  assertEquals(4,db.queryForObject("select count(*) from audit_event where lot_id=? and event_type='COMMAND_DENIED'",Integer.class,id));
  assertEquals(1L,db.queryForObject("select version from lot where id=?",Long.class,id));
 }
 @Test void retentionAndRetestPreserveHistory()throws Exception{
  UUID id=create();assertEquals(200,action(id,"calidad","inspect",0,",\"conforms\":false").status);
  assertEquals(422,action(id,"almacen","available",1,"").status);
  assertEquals(200,action(id,"calidad","inspect",1,",\"conforms\":true").status);
  assertEquals(200,action(id,"calidad","sample",2,",\"sampleCode\":\"M-2\"").status);
  var fail=action(id,"tecnico","test",3,",\"resultValue\":5");assertEquals(200,fail.status);assertTrue(fail.body.contains("HELD"));assertTrue(fail.body.contains("BLOCKED"));
  assertEquals(409,action(id,"aprobador","release",4,"").status);
  assertEquals(200,action(id,"calidad","inspect",4,",\"conforms\":true").status);
  assertEquals(422,action(id,"aprobador","release",5,"").status);
  assertEquals(200,action(id,"almacen","sample",5,",\"sampleCode\":\"M-3\"").status);
  assertEquals(200,action(id,"tecnico","test",6,",\"resultValue\":15").status);
  assertEquals(422,action(id,"aprobador","release",7,"").status);
  assertEquals("BLOCKED",db.queryForObject("select inventory_state from lot where id=?",String.class,id));
  UUID nc=openNc(id,"almacen","calidad","LABORATORY");
  opinion(id,nc,"CONFORMING");
  assertEquals(200,ncAction(id,nc,"calidad","resolve",version(id),",\"disposition\":\"LIFT_HOLD\"").status);
  assertEquals(200,action(id,"aprobador","release",version(id),"").status);
  assertEquals(2,db.queryForObject("select count(*) from audit_event where lot_id=? and event_type='TEST'",Integer.class,id));
 }
 @Test void idempotencyAndConcurrentVersion()throws Exception{
  var key=UUID.randomUUID();String body=createBody();var first=request("almacen","/receipts",body,key);assertEquals(200,first.status);assertEquals(first.body,request("almacen","/receipts",body,key).body);
  assertEquals(409,request("almacen","/receipts",createBody(),key).status);
  UUID id=create();var pool=Executors.newFixedThreadPool(2);try{
   Callable<Integer> task=()->action(id,"calidad","inspect",0,",\"conforms\":true").status;
   var results=pool.invokeAll(List.of(task,task));var statuses=new ArrayList<Integer>();for(var r:results)statuses.add(r.get());Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
  }finally{pool.shutdownNow();}
 }
 @Test void authenticationValidationAndDatabaseAuditProtection()throws Exception{
  assertEquals(401,request(null,"/lots",null,null).status);
  assertEquals(400,request("almacen","/receipts","{}",UUID.randomUUID()).status);
  assertEquals(422,request("almacen","/receipts",createBody().replace("\"quantity\":1","\"quantity\":10001"),UUID.randomUUID()).status);
  UUID id=create();assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("update audit_event set reason='tamper' where lot_id=?",id));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("delete from audit_event where lot_id=?",id));
 }

 long version(UUID id){return db.queryForObject("select version from lot where id=?",Long.class,id);}
 String inventory(UUID id){return db.queryForObject("select inventory_state from lot where id=?",String.class,id);}
 int count(String table,UUID id){return db.queryForObject("select count(*) from "+table+" where lot_id=?",Integer.class,id);}
 Reply ncAction(UUID lot,UUID nc,String user,String action,long version,String fields)throws Exception{
  return request(user,"/lots/"+lot+"/nonconformities"+(nc==null?"":"/"+nc+"/"+action),command(version,fields),UUID.randomUUID());
 }
 UUID openNc(UUID lot,String reporter,String responsible,String origin)throws Exception{
  var r=ncAction(lot,null,reporter,"open",version(lot),",\"origin\":\""+origin+"\",\"responsible\":\""+responsible+"\"");
  assertEquals(200,r.status,r.body);
  return db.queryForObject("select id from non_conformity where lot_id=? order by opened_at desc limit 1",UUID.class,lot);
 }
 void opinion(UUID lot,UUID nc,String outcome)throws Exception{
  var r=ncAction(lot,nc,"aprobador","opinion",version(lot),",\"outcome\":\""+outcome+"\"");assertEquals(200,r.status,r.body);
 }
 UUID released(boolean available)throws Exception{
  UUID id=create();inspect(id);sample(id);
  assertEquals(200,action(id,"tecnico","test",2,",\"resultValue\":15").status);
  assertEquals(200,action(id,"aprobador","release",3,"").status);
  if(available)assertEquals(200,action(id,"almacen","available",4,"").status);
  return id;
 }
 @Test void releasedAvailableCanBeBlockedThenExplicitlyRestored()throws Exception{
  UUID id=released(true);UUID nc=openNc(id,"almacen","calidad","POST_RELEASE");
  assertEquals("RELEASED",db.queryForObject("select quality_state from lot where id=?",String.class,id));
  assertEquals("BLOCKED",inventory(id));
  assertEquals(422,action(id,"almacen","available",version(id),"").status);
  assertEquals(409,action(id,"calidad","inspect",version(id),",\"conforms\":true").status);
  assertEquals(422,ncAction(id,nc,"calidad","resolve",version(id),",\"disposition\":\"LIFT_HOLD\"").status);
  opinion(id,nc,"CONFORMING");
  assertEquals("BLOCKED",inventory(id));
  assertEquals(200,ncAction(id,nc,"calidad","resolve",version(id),",\"disposition\":\"LIFT_HOLD\"").status);
  assertEquals("QUARANTINE",inventory(id));
  assertEquals(200,action(id,"almacen","available",version(id),"").status);
  assertEquals(1,db.queryForObject("select count(*) from audit_event where lot_id=? and event_type='RELEASE'",Integer.class,id));
  assertEquals("LIFTED",db.queryForObject("select status from inventory_hold where nc_id=?",String.class,nc));
  var response=request("calidad","/lots/"+id+"/nonconformities",null,null);
  assertEquals(200,response.status);assertTrue(response.body.contains("decisionEvidence"));assertTrue(response.body.contains("CONFORMING"));
 }
 @Test void definitiveRejectionHasDispositionAndCannotBeUndone()throws Exception{
  UUID id=released(true);UUID nc=openNc(id,"tecnico","calidad","POST_RELEASE");
  opinion(id,nc,"NONCONFORMING");
  assertEquals(422,ncAction(id,nc,"calidad","reject",version(id),",\"disposition\":\"LIFT_HOLD\"").status);
  assertEquals(200,ncAction(id,nc,"calidad","reject",version(id),",\"disposition\":\"RETURN_TO_SUPPLIER\"").status);
  long v=version(id);
  for(String a:List.of("inspect","sample","test","release","available")){
   String user=switch(a){case "inspect"->"calidad";case "sample","available"->"almacen";case "test"->"tecnico";default->"aprobador";};
   assertEquals(409,action(id,user,a,v,"").status,a);
  }
  assertEquals(409,ncAction(id,nc,"calidad","resolve",v,",\"disposition\":\"LIFT_HOLD\"").status);
  assertEquals(409,ncAction(id,nc,"aprobador","opinion",v,",\"outcome\":\"CONFORMING\"").status);
  assertEquals(409,ncAction(id,null,"almacen","open",v,",\"origin\":\"OTHER\",\"responsible\":\"calidad\"").status);
  assertEquals(v,version(id));assertEquals("BLOCKED",inventory(id));
  assertEquals("REJECTED",db.queryForObject("select quality_state from lot where id=?",String.class,id));
  assertEquals("RETURN_TO_SUPPLIER",db.queryForObject("select disposition from non_conformity where id=?",String.class,nc));
  assertEquals(1,count("inventory_hold",id));
  assertEquals(1,db.queryForObject("select count(*) from audit_event where lot_id=? and event_type='RELEASE'",Integer.class,id));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("update lot set inventory_state='AVAILABLE' where id=?",id));
 }
 @Test void ncRolesOwnerAndSegregationAreEnforced()throws Exception{
  UUID id=released(false);
  assertEquals(422,ncAction(id,null,"almacen","open",version(id),",\"origin\":\"OTHER\",\"responsible\":\"tecnico\"").status);
  assertEquals(422,ncAction(id,null,"calidad","open",version(id),",\"origin\":\"OTHER\",\"responsible\":\"calidad\"").status);
  UUID nc=openNc(id,"calidad","calidad2","OTHER");
  assertEquals(403,ncAction(id,nc,"calidad","opinion",version(id),",\"outcome\":\"CONFORMING\"").status);
  opinion(id,nc,"CONFORMING");
  assertEquals(403,ncAction(id,nc,"calidad","resolve",version(id),",\"disposition\":\"LIFT_HOLD\"").status);
  assertEquals(200,ncAction(id,nc,"calidad2","resolve",version(id),",\"disposition\":\"LIFT_HOLD\"").status);
  assertEquals(403,ncAction(id,nc,"tecnico","reject",version(id),",\"disposition\":\"SCRAP\"").status);
  UUID other=released(false),independent=openNc(other,"calidad","calidad2","OTHER");opinion(other,independent,"CONFORMING");
  assertEquals(200,ncAction(other,independent,"calidad2","resolve",version(other),",\"disposition\":\"LIFT_HOLD\"").status);
  assertEquals(409,ncAction(other,independent,"calidad2","resolve",version(other),",\"disposition\":\"LIFT_HOLD\"").status);
  assertEquals(404,ncAction(id,independent,"aprobador","opinion",version(id),",\"outcome\":\"CONFORMING\"").status);
 }
 @Test void multipleNcRequireIndependentClosuresAndFreshOpinions()throws Exception{
  UUID id=released(true),first=openNc(id,"almacen","calidad","OTHER");opinion(id,first,"CONFORMING");
  UUID second=openNc(id,"tecnico","calidad","OTHER");
  assertEquals(409,ncAction(id,first,"calidad","resolve",version(id),",\"disposition\":\"LIFT_HOLD\"").status);
  opinion(id,first,"CONFORMING");
  assertEquals(200,ncAction(id,first,"calidad","resolve",version(id),",\"disposition\":\"LIFT_HOLD\"").status);
  assertEquals("BLOCKED",inventory(id));assertEquals(422,action(id,"almacen","available",version(id),"").status);
  opinion(id,second,"CONFORMING");
  assertEquals(200,ncAction(id,second,"calidad","resolve",version(id),",\"disposition\":\"LIFT_HOLD\"").status);
  assertEquals("QUARANTINE",inventory(id));
  assertEquals(3,db.queryForObject("select count(*) from nc_opinion where nc_id in (?,?)",Integer.class,first,second));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("update nc_opinion set reason='tamper' where nc_id=?",first));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("delete from nc_opinion where nc_id=?",first));
 }
 @Test void legacyBlockedLotStillNeedsFormalExplicitDecision()throws Exception{
  UUID id=released(false);
  // This new fixture models a V2 BLOCKED lot without hold rows; no real legacy data is modified.
  db.update("update lot set inventory_state='BLOCKED' where id=?",id);
  assertEquals(0,count("inventory_hold",id));
  assertEquals(422,action(id,"almacen","available",version(id),"").status);
  UUID nc=openNc(id,"almacen","calidad","OTHER");opinion(id,nc,"CONFORMING");
  assertEquals(200,ncAction(id,nc,"calidad","resolve",version(id),",\"disposition\":\"LIFT_HOLD\"").status);
  assertEquals("QUARANTINE",inventory(id));
 }
 @Test void ncIdempotencyAndCompetingTerminalDecisions()throws Exception{
  UUID id=released(true);long v=version(id);UUID key=UUID.randomUUID();
  String path="/lots/"+id+"/nonconformities",body=command(v,",\"origin\":\"POST_RELEASE\",\"responsible\":\"calidad\"");
  var pool=Executors.newFixedThreadPool(2);
  try{
   Callable<Integer> open=()->request("almacen",path,body,key).status;
   for(var reply:pool.invokeAll(List.of(open,open)))assertEquals(200,reply.get());
   assertEquals(1,count("non_conformity",id));assertEquals(1,count("inventory_hold",id));assertEquals(v+1,version(id));
   assertEquals(409,request("almacen",path,body.replace("POST_RELEASE","OTHER"),key).status);
   UUID nc=db.queryForObject("select id from non_conformity where lot_id=?",UUID.class,id);
   opinion(id,nc,"NONCONFORMING");long before=version(id);
   Callable<Integer> reject=()->ncAction(id,nc,"calidad","reject",before,",\"disposition\":\"SCRAP\"").status;
   var results=new ArrayList<Integer>();for(var r:pool.invokeAll(List.of(reject,reject)))results.add(r.get());Collections.sort(results);
   assertEquals(List.of(200,409),results);assertEquals(before+1,version(id));
   assertEquals(1,db.queryForObject("select count(*) from audit_event where lot_id=? and event_type='NC_REJECT'",Integer.class,id));
  }finally{pool.shutdownNow();}
 }
 @Test void preventiveBlockRacesAvailabilityWithoutLosingBlock()throws Exception{
  UUID id=released(false);long before=version(id);var pool=Executors.newFixedThreadPool(2);
  try{
   var work=List.<Callable<Integer>>of(
    ()->action(id,"almacen","available",before,"").status,
    ()->ncAction(id,null,"tecnico","open",before,",\"origin\":\"POST_RELEASE\",\"responsible\":\"calidad\"").status);
   var futures=pool.invokeAll(work);int available=futures.get(0).get(),block=futures.get(1).get();
   assertTrue((available==200&&block==409)||(available==409&&block==200));
   if(block==409)openNc(id,"tecnico","calidad","POST_RELEASE");
   assertEquals("BLOCKED",inventory(id));assertEquals(1,count("inventory_hold",id));
  }finally{pool.shutdownNow();}
 }
 @Test void ncFailureRollsBackCaseHoldVersionAndCommand()throws Exception{
  UUID id=create();String suffix=UUID.randomUUID().toString().replace("-","");String marker="rollback-"+suffix;
  db.execute("create function fail_"+suffix+"() returns trigger language plpgsql as $$ begin if NEW.reason='"+marker+"' then raise exception 'forced test failure' using errcode='23514'; end if; return NEW; end $$");
  db.execute("create trigger fail_"+suffix+" before insert on audit_event for each row execute function fail_"+suffix+"()");
  UUID key=UUID.randomUUID();try{
   var r=request("almacen","/lots/"+id+"/nonconformities",command(0,",\"origin\":\"OTHER\",\"responsible\":\"calidad\"").replace("Prueba automática",marker),key);
   assertEquals(409,r.status);assertEquals(0,count("non_conformity",id));assertEquals(0,count("inventory_hold",id));assertEquals(0,version(id));assertEquals("QUARANTINE",inventory(id));
   assertEquals(0,db.queryForObject("select count(*) from command_result where command_key=?",Integer.class,key));
  }finally{db.execute("drop trigger fail_"+suffix+" on audit_event");db.execute("drop function fail_"+suffix+"()");}
 }
 @Test void databaseGuardRejectsInventoryWithActiveHolds()throws Exception{
  UUID id=released(true);openNc(id,"almacen","calidad","POST_RELEASE");
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("update lot set inventory_state='AVAILABLE' where id=?",id));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("update lot set inventory_state='QUARANTINE' where id=?",id));
  assertEquals("BLOCKED",inventory(id));
 }
}
