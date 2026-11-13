package com.proco.simulator;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes={SimulatorApplication.class,ExecutionIntegrationTest.TimeConfig.class},webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties="server.port=18280")
class ExecutionIntegrationTest {
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://localhost:5432/proco_test"));}
 @TestConfiguration static class TimeConfig { @Bean @Primary AdjustableClock testClock(){return new AdjustableClock();} }
 static class AdjustableClock extends Clock {
  final AtomicReference<Instant> value=new AtomicReference<>(Instant.now());
  public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);} public Instant instant(){return value.get();}
 }
 @Autowired JdbcTemplate db;
 @Autowired AdjustableClock clock;
 @BeforeEach void resetClock(){clock.value.set(Instant.now());}
 final JsonMapper json=new JsonMapper();final HttpClient client=HttpClient.newHttpClient();
 record Reply(int status,JsonNode data,String raw){}
 Reply call(String user,String path,Map<String,Object> body,UUID key)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://localhost:18280/api"+path)).timeout(Duration.ofSeconds(20));
  if(user!=null)b.header("Authorization","Basic "+Base64.getEncoder().encodeToString((user+":Simulacion123!").getBytes(StandardCharsets.UTF_8)));
  if(body!=null)b.header("Content-Type","application/json").header("Idempotency-Key",key.toString()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  var r=client.send(b.build(),HttpResponse.BodyHandlers.ofString());return new Reply(r.statusCode(),r.body().isBlank()?json.createObjectNode():json.readTree(r.body()),r.body());
 }
 JsonNode good(String user,String path,Map<String,Object> body)throws Exception{var r=call(user,path,body,UUID.randomUUID());assertEquals(200,r.status,r.raw);return r.data;}
 Map<String,Object> data(long version,Map<String,Object> extra){var m=new LinkedHashMap<String,Object>();m.put("version",version);m.put("reason","Prueba Sprint 4");m.put("evidence","EV-S3");m.putAll(extra);return m;}
 UUID lot(String order)throws Exception{return lot(order,"2026-01-01");}
 UUID lot(String order,String productionDate)throws Exception{
  var result=good("almacen","/receipts",data(0,Map.of("orderCode",order,"supplierLot","S4-"+UUID.randomUUID(),"productionDate",productionDate,"quantity",1,"location","Q-S3")));
  UUID id=UUID.fromString(result.get("id").asText());
  String[] users={"calidad","almacen","tecnico","aprobador","almacen"},actions={"inspect","sample","test","release","available"};
  List<Map<String,Object>> extra=List.of(Map.of("conforms",true),Map.of("sampleCode","M-S3"),Map.of("resultValue",order.equals("OC-H-001")?15:500),Map.of(),Map.of());
  for(int i=0;i<actions.length;i++)good(users[i],"/lots/"+id+"/"+actions[i],data(i,extra.get(i)));
  return id;
 }
 Map<String,Object> requestBody(UUID a,UUID b){return data(0,Map.of("montageCode","M-"+UUID.randomUUID(),"validUntil",clock.instant().plusSeconds(86400).toString(),"items",List.of(Map.of("lotId",a,"quantity","0.4"),Map.of("lotId",b,"quantity","0.5"))));}
 UUID mix(UUID a,UUID b)throws Exception{return UUID.fromString(good("tejeduria","/mix-requests",requestBody(a,b)).get("id").asText());}
 JsonNode get(UUID id)throws Exception{return good("almacen","/mix-requests/"+id,null);}
 long version(UUID id)throws Exception{return get(id).get("version").asLong();}
 JsonNode assess(UUID id,String user,String outcome)throws Exception{return good(user,"/mix-requests/"+id+"/assessments",data(version(id),Map.of("outcome",outcome)));}
 void favorable(UUID id)throws Exception{for(String u:List.of("aprobador","geo","calidad"))assess(id,u,"FAVORABLE");}
 JsonNode authorize(UUID id)throws Exception{return good("produccion","/mix-requests/"+id+"/decision",data(version(id),Map.of("decision","AUTHORIZE")));}
 int count(String table,UUID id){return db.queryForObject("select count(*) from "+table+" where "+(table.equals("mix_event")?"mix_id":"request_id")+"=?",Integer.class,id);}


 UUID approved(UUID a,UUID b)throws Exception{UUID m=mix(a,b);favorable(m);if(get(m).get("dyeingRequired").asBoolean())assess(m,"tintoreria","FAVORABLE");authorize(m);return m;}
 JsonNode start(UUID m)throws Exception{return good("tejeduria","/mix-requests/"+m+"/executions",data(version(m),Map.of("montageCode",get(m).get("montageCode").asText())));}
 JsonNode execution(UUID id)throws Exception{return good("geo","/executions/"+id,null);}
 UUID id(JsonNode n){return UUID.fromString(n.get("id").asText());}
 long lv(UUID l){return db.queryForObject("select version from lot where id=?",Long.class,l);}
 UUID nc(UUID lot)throws Exception{return nc(lot,UUID.randomUUID());}
 UUID nc(UUID lot,UUID key)throws Exception{
  var r=call("tintoreria","/lots/"+lot+"/nonconformities",data(lv(lot),Map.of("origin","POST_RELEASE","responsible","calidad")),key);assertEquals(200,r.status,r.raw);
  return db.queryForObject("select id from non_conformity where lot_id=? order by opened_at desc limit 1",UUID.class,lot);
 }
 String user(String role){return switch(role){case "WAREHOUSE"->"almacen";case "QUALITY"->"calidad";case "PRODUCTION_REQUESTER"->"tejeduria";case "GEO"->"geo";case "DYEING"->"tintoreria";case "PROCUREMENT"->"abastecimiento";default->throw new IllegalArgumentException(role);};}
 void closeTasks(UUID e)throws Exception{
  for(var task:execution(e).get("tasks")){String actor=user(task.get("target_role").asText());String path="/tasks/"+task.get("id").asText();
   var ack=good(actor,path+"/acknowledge",data(0,Map.of()));assertEquals("ACKNOWLEDGED",ack.get("status").asText());
   assertEquals("CLOSED",good(actor,path+"/complete",data(1,Map.of())).get("status").asText());}
 }
 @Test void startRevalidatesRoleVersionMontageAndAuthorization()throws Exception{
  UUID a=lot("OC-H-001"),m=mix(a,lot("OC-H-001"));
  assertEquals(422,call("tejeduria","/mix-requests/"+m+"/executions",data(0,Map.of("montageCode",get(m).get("montageCode").asText())),UUID.randomUUID()).status);
  favorable(m);authorize(m);var body=data(version(m),Map.of("montageCode",get(m).get("montageCode").asText()));
  assertEquals(403,call("produccion","/mix-requests/"+m+"/executions",body,UUID.randomUUID()).status);
  assertEquals(422,call("tejeduria","/mix-requests/"+m+"/executions",data(version(m),Map.of("montageCode","WRONG")),UUID.randomUUID()).status);
  assertEquals(409,call("tejeduria","/mix-requests/"+m+"/executions",data(0,Map.of("montageCode",get(m).get("montageCode").asText())),UUID.randomUUID()).status);
  var e=start(m);assertEquals("IN_PROGRESS",e.get("status").asText());assertTrue(e.get("tasks").isEmpty());assertEquals("AVAILABLE",db.queryForObject("select inventory_state from lot where id=?",String.class,a));
  assertEquals(409,call("tejeduria","/mix-requests/"+m+"/executions",body,UUID.randomUUID()).status);
  assertEquals(404,call("tejeduria","/mix-requests/"+UUID.randomUUID()+"/executions",body,UUID.randomUUID()).status);
 }
 @Test void s09NcAfterStartSuspendsCreatesCorrelatedTasksAndNeverResumes()throws Exception{
  UUID a=lot("OC-H-001"),m=approved(a,lot("OC-E-001")),e=id(start(m));UUID caseId=nc(a);
  var result=execution(e);assertEquals("SUSPENDED",result.get("status").asText());assertEquals(5,result.get("tasks").size());assertEquals("REVOKED",get(m).get("status").asText());
  for(var t:result.get("tasks")){assertEquals(caseId.toString(),t.get("nc_id").asText());assertEquals(a.toString(),t.get("lot_id").asText());assertEquals(m.toString(),t.get("mix_id").asText());assertEquals("OPEN",t.get("status").asText());}
  assertEquals(422,call("produccion","/executions/"+e+"/complete",data(1,Map.of()),UUID.randomUUID()).status);
  good("aprobador","/lots/"+a+"/nonconformities/"+caseId+"/opinion",data(lv(a),Map.of("outcome","CONFORMING")));
  good("calidad","/lots/"+a+"/nonconformities/"+caseId+"/resolve",data(lv(a),Map.of("disposition","LIFT_HOLD")));
  good("almacen","/lots/"+a+"/available",data(lv(a),Map.of()));
  assertEquals("SUSPENDED",execution(e).get("status").asText());assertEquals("REVOKED",get(m).get("status").asText());
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("update montage_execution set status='IN_PROGRESS',version=version+1 where id=?",e));
  closeTasks(e);assertEquals("COMPLETED",good("produccion","/executions/"+e+"/complete",data(1,Map.of())).get("status").asText());
  assertEquals(409,call("produccion","/executions/"+e+"/cancel",data(2,Map.of()),UUID.randomUUID()).status);
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("delete from execution_event where execution_id=?",e));
 }
 @Test void taskAuthorizationAcuseEvidenceVersionAndReplay()throws Exception{
  UUID a=lot("OC-H-001"),e=id(start(approved(a,lot("OC-H-001"))));nc(a);
  JsonNode t=execution(e).get("tasks").get(0);String actor=user(t.get("target_role").asText()),path="/tasks/"+t.get("id").asText();
  assertEquals(403,call("aprobador",path+"/acknowledge",data(0,Map.of()),UUID.randomUUID()).status);
  assertEquals(409,call(actor,path+"/complete",data(0,Map.of()),UUID.randomUUID()).status);
  assertEquals(400,call(actor,path+"/acknowledge",data(0,Map.of("evidence","")),UUID.randomUUID()).status);
  UUID key=UUID.randomUUID();var body=data(0,Map.of());assertEquals(200,call(actor,path+"/acknowledge",body,key).status);
  assertEquals(200,call(actor,path+"/acknowledge",body,key).status);
  assertEquals(409,call(actor,path+"/complete",body,UUID.randomUUID()).status);
  assertEquals(409,call(actor,path+"/complete",data(1,Map.of()),key).status);
  assertEquals(200,call(actor,path+"/complete",data(1,Map.of()),UUID.randomUUID()).status);
  var list=good(actor,"/tasks",null);for(var task:list)assertEquals(t.get("target_role"),task.get("target_role"));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("delete from containment_task where id=?",id(t)));
 }
 @Test void completedExecutionGetsRetrospectiveAlertAndUnrelatedLotDoesNotSuspend()throws Exception{
  UUID a=lot("OC-H-001"),m=approved(a,lot("OC-H-001")),e=id(start(m));
  nc(lot("OC-H-001"));assertEquals("IN_PROGRESS",execution(e).get("status").asText());
  good("produccion","/executions/"+e+"/complete",data(0,Map.of()));nc(a);
  assertEquals("COMPLETED",execution(e).get("status").asText());assertEquals(4,execution(e).get("tasks").size());
  assertEquals(1,db.queryForObject("select count(*) from execution_event where execution_id=? and event_type='RETROSPECTIVE_ALERT'",Integer.class,e));
  for(var t:execution(e).get("tasks"))assertEquals("RETROSPECTIVE_REVIEW",t.get("task_type").asText());
 }
 @Test void rejectAddsProcurementTaskEvenWhenAlreadyRevoked()throws Exception{
  UUID a=lot("OC-H-001"),m=approved(a,lot("OC-H-001")),e=id(start(m)),n=nc(a);
  good("aprobador","/lots/"+a+"/nonconformities/"+n+"/opinion",data(lv(a),Map.of("outcome","NONCONFORMING")));
  good("calidad","/lots/"+a+"/nonconformities/"+n+"/reject",data(lv(a),Map.of("disposition","RETURN_TO_SUPPLIER")));
  assertEquals(5,execution(e).get("tasks").size());assertEquals(1,db.queryForObject("select count(*) from containment_task where execution_id=? and target_role='PROCUREMENT' and nc_id=?",Integer.class,e,n));
  closeTasks(e);assertEquals("CANCELLED",good("produccion","/executions/"+e+"/cancel",data(1,Map.of())).get("status").asText());
 }
 @Test void expirationBeforeStartAndDuringExecutionBlocksAdvanceAndRecordsReview()throws Exception{
  UUID m=approved(lot("OC-H-001"),lot("OC-H-001")),e=id(start(m));clock.value.set(clock.instant().plusSeconds(90000));
  assertEquals(422,call("produccion","/executions/"+e+"/complete",data(0,Map.of()),UUID.randomUUID()).status);
  assertEquals("IN_PROGRESS",execution(e).get("status").asText());assertEquals(1,db.queryForObject("select count(*) from execution_event where execution_id=? and event_type='REVIEW_REQUIRED'",Integer.class,e));
  assertEquals("CANCELLED",good("produccion","/executions/"+e+"/cancel",data(0,Map.of())).get("status").asText());
  assertEquals(422,call("tejeduria","/mix-requests/"+m+"/executions",data(version(m),Map.of("montageCode",get(m).get("montageCode").asText())),UUID.randomUUID()).status);
 }
 @Test void concurrentStartAndNcNeverLeaveRunningRevokedExecution()throws Exception{
  try(var pool=Executors.newFixedThreadPool(2)){
   for(int i=0;i<4;i++){UUID a=lot("OC-H-001"),m=approved(a,lot("OC-H-001"));var body=data(version(m),Map.of("montageCode",get(m).get("montageCode").asText()));long v=lv(a);var latch=new CountDownLatch(1);
    var start=pool.submit(()->{latch.await();return call("tejeduria","/mix-requests/"+m+"/executions",body,UUID.randomUUID());});
    var nc=pool.submit(()->{latch.await();return call("geo","/lots/"+a+"/nonconformities",data(v,Map.of("origin","POST_RELEASE","responsible","calidad")),UUID.randomUUID());});latch.countDown();
    var sr=start.get(15,TimeUnit.SECONDS);assertTrue(Set.of(200,409,422).contains(sr.status),sr.raw);assertEquals(200,nc.get(15,TimeUnit.SECONDS).status);
    assertEquals(0,db.queryForObject("select count(*) from montage_execution where mix_request_id=? and status='IN_PROGRESS'",Integer.class,m));
    if(sr.status==200)assertEquals(4,execution(id(sr.data)).get("tasks").size());
   }
  }
 }
 @Test void concurrentStartsSameMontageAcrossDifferentAuthorizations()throws Exception{
  UUID a=lot("OC-H-001"),b=lot("OC-H-001");var body=requestBody(a,b);UUID one=UUID.fromString(good("tejeduria","/mix-requests",body).get("id").asText()),two=UUID.fromString(good("tejeduria","/mix-requests",data(0,Map.of("montageCode",body.get("montageCode"),"validUntil",body.get("validUntil"),"items",List.of(Map.of("lotId",lot("OC-H-001"),"quantity","0.4"),Map.of("lotId",lot("OC-H-001"),"quantity","0.5"))))).get("id").asText());
  favorable(one);authorize(one);favorable(two);authorize(two);
  try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);List<Future<Reply>> futures=new ArrayList<>();for(UUID m:List.of(one,two))futures.add(pool.submit(()->{latch.await();return call("tejeduria","/mix-requests/"+m+"/executions",data(version(m),Map.of("montageCode",body.get("montageCode"))),UUID.randomUUID());}));latch.countDown();var statuses=new ArrayList<Integer>();for(var f:futures)statuses.add(f.get(15,TimeUnit.SECONDS).status);Collections.sort(statuses);assertEquals(List.of(200,409),statuses);}
 }
 @Test void concurrentNcOnSharedExecutionAndStartReplayDoNotDuplicateTasks()throws Exception{
  UUID a=lot("OC-H-001"),b=lot("OC-H-001"),m=approved(a,b);UUID key=UUID.randomUUID();var body=data(version(m),Map.of("montageCode",get(m).get("montageCode").asText()));var first=call("tejeduria","/mix-requests/"+m+"/executions",body,key);assertEquals(200,first.status);UUID e=id(first.data);
  try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);var x=pool.submit(()->{latch.await();return nc(a);});var y=pool.submit(()->{latch.await();return nc(b);});latch.countDown();assertNotNull(x.get(15,TimeUnit.SECONDS));assertNotNull(y.get(15,TimeUnit.SECONDS));}
  assertEquals(8,execution(e).get("tasks").size());assertEquals("SUSPENDED",execution(e).get("status").asText());assertEquals(e,id(call("tejeduria","/mix-requests/"+m+"/executions",body,key).data));assertEquals(8,execution(e).get("tasks").size());
 }
 @Test void taskOrAuditFailureRollsBackEntireMaterialCommand()throws Exception{
  for(String target:List.of("containment_task","execution_event")){
   UUID a=lot("OC-H-001"),m=approved(a,lot("OC-H-001")),e=id(start(m)),key=UUID.randomUUID();long v=lv(a);String suffix=UUID.randomUUID().toString().replace("-","");
   db.execute("create function fail_s4_"+suffix+"() returns trigger language plpgsql as $$ begin if NEW.execution_id='"+e+"'::uuid then raise exception 'forced S4 failure' using errcode='23514'; end if; return NEW; end $$");
   db.execute("create trigger fail_s4_"+suffix+" before insert on "+target+" for each row execute function fail_s4_"+suffix+"()");
   try{var result=call("geo","/lots/"+a+"/nonconformities",data(v,Map.of("origin","POST_RELEASE","responsible","calidad")),key);assertEquals(409,result.status,result.raw);
    assertEquals(v,lv(a));assertEquals("AVAILABLE",db.queryForObject("select inventory_state from lot where id=?",String.class,a));assertEquals("AUTHORIZED",get(m).get("status").asText());assertEquals("IN_PROGRESS",execution(e).get("status").asText());assertTrue(execution(e).get("tasks").isEmpty());
    assertEquals(0,db.queryForObject("select count(*) from non_conformity where lot_id=?",Integer.class,a));assertEquals(0,db.queryForObject("select count(*) from inventory_hold where lot_id=?",Integer.class,a));assertEquals(0,db.queryForObject("select count(*) from command_result where command_key=?",Integer.class,key));
   }finally{db.execute("drop trigger fail_s4_"+suffix+" on "+target);db.execute("drop function fail_s4_"+suffix+"()");}
   nc(a,key);assertEquals("SUSPENDED",execution(e).get("status").asText());
  }
 }
 @Test void taskCompletionAndNewNcSerializeBeforeExecutionDisposition()throws Exception{
  UUID a=lot("OC-H-001"),b=lot("OC-H-001"),e=id(start(approved(a,b)));nc(a);closeTasks(e);
  try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);
   var end=pool.submit(()->{latch.await();return call("produccion","/executions/"+e+"/complete",data(1,Map.of()),UUID.randomUUID());});var incident=pool.submit(()->{latch.await();return nc(b);});latch.countDown();
   var result=end.get(15,TimeUnit.SECONDS);assertTrue(Set.of(200,422).contains(result.status),result.raw);assertNotNull(incident.get(15,TimeUnit.SECONDS));
   assertEquals(8,execution(e).get("tasks").size());assertEquals(4,db.queryForObject("select count(*) from containment_task where execution_id=? and status='OPEN'",Integer.class,e));
   if(result.status==200)assertEquals(4,db.queryForObject("select count(*) from containment_task where execution_id=? and task_type='RETROSPECTIVE_REVIEW'",Integer.class,e));else assertEquals("SUSPENDED",execution(e).get("status").asText());
  }
 }

 @Test void concurrentStartsSameAuthorizationCreateOneExecution()throws Exception{
  UUID m=approved(lot("OC-H-001"),lot("OC-H-001"));var body=data(version(m),Map.of("montageCode",get(m).get("montageCode").asText()));
  try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);List<Future<Reply>> futures=new ArrayList<>();for(int i=0;i<2;i++)futures.add(pool.submit(()->{latch.await();return call("tejeduria","/mix-requests/"+m+"/executions",body,UUID.randomUUID());}));latch.countDown();var statuses=new ArrayList<Integer>();for(var f:futures)statuses.add(f.get(15,TimeUnit.SECONDS).status);Collections.sort(statuses);assertEquals(List.of(200,409),statuses);}
  assertEquals(1,db.queryForObject("select count(*) from montage_execution where mix_request_id=?",Integer.class,m));
 }
 @Test void concurrentAcuseAssignsSingleIdentityAndOnlyAssigneeCompletes()throws Exception{
  UUID a=lot("OC-H-001"),e=id(start(approved(a,lot("OC-H-001"))));nc(a);
  UUID t=db.queryForObject("select id from containment_task where execution_id=? and target_role='QUALITY'",UUID.class,e);
  String path="/tasks/"+t;var statuses=new ArrayList<Integer>();
  try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);List<Future<Reply>> fs=new ArrayList<>();for(String u:List.of("calidad","calidad2"))fs.add(pool.submit(()->{latch.await();return call(u,path+"/acknowledge",data(0,Map.of()),UUID.randomUUID());}));latch.countDown();for(var f:fs)statuses.add(f.get(15,TimeUnit.SECONDS).status);}
  Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
  String assignee=db.queryForObject("select acknowledged_by from containment_task where id=?",String.class,t),other=assignee.equals("calidad")?"calidad2":"calidad";
  assertEquals(403,call(other,path+"/complete",data(1,Map.of()),UUID.randomUUID()).status);
  assertEquals(200,call(assignee,path+"/complete",data(1,Map.of()),UUID.randomUUID()).status);
 }
 @Autowired com.proco.simulator.inventory.HoldStore holdStore;
 @Autowired org.springframework.transaction.PlatformTransactionManager tm;
 @Test void directHoldAndItsLaterNcFormalizationBothCoordinateWithoutDuplicateReplay()throws Exception{
  UUID a=lot("OC-H-001"),m=approved(a,lot("OC-H-001")),e=id(start(m));
  new org.springframework.transaction.support.TransactionTemplate(tm).executeWithoutResult(tx->{db.queryForList("select id from lot where id=? for update",a);holdStore.place(a,null,"calidad","Retención preventiva ficticia","EV-HOLD-S4",UUID.randomUUID());db.update("update lot set version=version+1 where id=?",a);});
  assertEquals("SUSPENDED",execution(e).get("status").asText());assertEquals(4,execution(e).get("tasks").size());for(var t:execution(e).get("tasks"))assertTrue(t.get("nc_id").isNull());
  UUID key=UUID.randomUUID();var body=data(lv(a),Map.of("origin","POST_RELEASE","responsible","calidad"));
  assertEquals(200,call("geo","/lots/"+a+"/nonconformities",body,key).status);assertEquals(200,call("geo","/lots/"+a+"/nonconformities",body,key).status);
  assertEquals(8,execution(e).get("tasks").size());assertEquals(1,db.queryForObject("select count(*) from inventory_hold where lot_id=?",Integer.class,a));
 }
}
