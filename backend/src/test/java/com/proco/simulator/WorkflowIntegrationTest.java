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
  assertEquals(200,action(id,"aprobador","release",7,"").status);
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
}
