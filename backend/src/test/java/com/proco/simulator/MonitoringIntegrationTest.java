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

@SpringBootTest(classes={SimulatorApplication.class,MonitoringIntegrationTest.TimeConfig.class},webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties="server.port=18380")
class MonitoringIntegrationTest {
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
  var b=HttpRequest.newBuilder(URI.create("http://localhost:18380/api"+path)).timeout(Duration.ofSeconds(20));
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

 long ruleVersion(String type,String role){return db.queryForObject("select coalesce(max(version),0) from ola_rule where task_type=? and target_role=?",Long.class,type,role);}
 Map<String,Object> ruleBody(String type,String role,Integer ack,Integer close){var c=data(ruleVersion(type,role),Map.of("taskType",type,"targetRole",role,"dueSoonMinutes",5));c.put("ackMinutes",ack);c.put("closeMinutes",close);return c;}
 JsonNode rule(String type,String role,Integer ack,Integer close)throws Exception{return good("geo","/monitoring/ola-rules",ruleBody(type,role,ack,close));}
 String params(UUID mix)throws Exception{return "?montageCode="+get(mix).get("montageCode").asText()+"&from="+clock.instant().minusSeconds(86400)+"&to="+clock.instant().plusSeconds(86400);}
 JsonNode taskFor(UUID e,String role)throws Exception{for(var t:execution(e).get("tasks"))if(t.get("target_role").asText().equals(role))return t;throw new AssertionError("Missing task");}
 @Test void snapshotSurvivesNewRuleAndReadsDoNotMutateProcess()throws Exception{
  rule("CONTAINMENT","WAREHOUSE",30,60);UUID a=lot("OC-H-001"),m=approved(a,lot("OC-H-001")),e=id(start(m));nc(a);
  var original=taskFor(e,"WAREHOUSE");var due=original.get("ack_due_at");var snapshot=original.get("ola_rule_id");
  rule("CONTAINMENT","WAREHOUSE",120,240);
  clock.value.set(clock.instant().plusSeconds(31*60));var current=taskFor(e,"WAREHOUSE");assertEquals(due,current.get("ack_due_at"));assertEquals(snapshot,current.get("ola_rule_id"));assertEquals("OVERDUE",current.get("ack_ola_status").asText());assertEquals(1,current.get("alerts").size());
  long events=db.queryForObject("select count(*) from execution_event where execution_id=?",Long.class,e);
  good("geo","/monitoring/tasks"+params(m),null);good("geo","/monitoring/summary"+params(m),null);
  assertEquals(events,db.queryForObject("select count(*) from execution_event where execution_id=?",Long.class,e));assertEquals("OPEN",current.get("status").asText());assertEquals("SUSPENDED",execution(e).get("status").asText());assertEquals("REVOKED",get(m).get("status").asText());
  UUID fresh=lot("OC-H-001"),freshE=id(start(approved(fresh,lot("OC-H-001"))));nc(fresh);assertNotEquals(snapshot,taskFor(freshE,"WAREHOUSE").get("ola_rule_id"));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("update containment_task set ack_due_at=now() where id=?",id(original)));
 }
 @Test void onTimeAcknowledgmentAndLateClosureKeepIndependentDurationsAndDenominators()throws Exception{
  rule("CONTAINMENT","WAREHOUSE",30,60);UUID a=lot("OC-H-001"),m=approved(a,lot("OC-H-001")),e=id(start(m));nc(a);var t=taskFor(e,"WAREHOUSE");
  clock.value.set(clock.instant().plusSeconds(20*60));good("almacen","/tasks/"+id(t)+"/acknowledge",data(0,Map.of()));
  var pending=good("geo","/monitoring/summary"+params(m)+"&area=WAREHOUSE",null);assertEquals(1,pending.get("responseTimes").get(0).get("ack_count").asInt());assertEquals(0,pending.get("responseTimes").get(0).get("close_count").asInt());assertTrue(pending.get("responseTimes").get(0).get("close_mean_seconds").isNull());
  clock.value.set(clock.instant().plusSeconds(50*60));good("almacen","/tasks/"+id(t)+"/complete",data(1,Map.of()));
  var closed=taskFor(e,"WAREHOUSE");assertEquals("COMPLETED_ON_TIME",closed.get("ack_ola_status").asText());assertEquals("COMPLETED_LATE",closed.get("close_ola_status").asText());assertTrue(closed.get("alerts").isEmpty());
  var report=good("geo","/monitoring/summary"+params(m)+"&area=WAREHOUSE",null);var timing=report.get("responseTimes").get(0);assertEquals(1200,timing.get("ack_mean_seconds").asDouble(),0.001);assertEquals(4200,timing.get("close_median_seconds").asDouble(),0.001);assertEquals(1,report.get("nonconformities").get("OPEN").asInt());assertEquals(1,report.get("executions").get("SUSPENDED").asInt());
 }
 @Test void missingAndExplicitlyDisabledOlaAreNeverMarkedOverdue()throws Exception{
  rule("RETROSPECTIVE_REVIEW","WAREHOUSE",null,null);UUID a=lot("OC-H-001"),m=approved(a,lot("OC-H-001")),e=id(start(m));good("produccion","/executions/"+e+"/complete",data(0,Map.of()));nc(a);clock.value.set(clock.instant().plusSeconds(86400*2));
  for(var t:execution(e).get("tasks")){assertEquals("NOT_CONFIGURED",t.get("ack_ola_status").asText());assertEquals("NOT_CONFIGURED",t.get("close_ola_status").asText());assertTrue(t.get("alerts").isEmpty());}
 }
 @Test void rulesAreAuthorizedAuditedIdempotentAndConcurrentVersionsConflict()throws Exception{
  var body=ruleBody("CONTAINMENT","GEO",15,90);UUID key=UUID.randomUUID();
  assertEquals(403,call("calidad","/monitoring/ola-rules",body,key).status);
  var created=call("geo","/monitoring/ola-rules",body,key);assertEquals(200,created.status,created.raw);assertEquals(id(created.data),id(call("geo","/monitoring/ola-rules",body,key).data));
  assertEquals(1,db.queryForObject("select count(*) from execution_event where ola_rule_id=?",Integer.class,id(created.data)));
  var next=ruleBody("CONTAINMENT","GEO",20,120);try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);List<Future<Reply>> fs=new ArrayList<>();for(int i=0;i<2;i++)fs.add(pool.submit(()->{latch.await();return call("geo","/monitoring/ola-rules",next,UUID.randomUUID());}));latch.countDown();var statuses=new ArrayList<Integer>();for(var f:fs)statuses.add(f.get(15,TimeUnit.SECONDS).status);Collections.sort(statuses);assertEquals(List.of(200,409),statuses);}
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("update ola_rule set ack_minutes=999 where id=?",id(created.data)));
  assertEquals(400,call("geo","/monitoring/ola-rules",ruleBody("CONTAINMENT","GEO",0,90),UUID.randomUUID()).status);
 }
 @Test void emptyRangesInvalidFiltersAndAuthenticationAreExplicit()throws Exception{
  var empty=good("almacen","/monitoring/summary?from=2000-01-01T00:00:00Z&to=2000-01-02T00:00:00Z",null);assertTrue(empty.get("tasks").isEmpty());assertTrue(empty.get("responseTimes").isEmpty());assertEquals(0,empty.get("executions").get("COMPLETED").asInt());assertEquals(0,empty.get("alerts").get("overdue_tasks").asInt());
  for(String query:List.of("?limit=101","?area=ADMIN","?executionStatus=INVALID","?cursor=broken","?from=2000-01-01T00:00:00Z&to=2026-01-01T00:00:00Z"))assertEquals(400,call("geo","/monitoring/tasks"+query,null,null).status);
  assertEquals(401,call(null,"/monitoring/summary",null,null).status);
 }
 @Test void keysetAsOfPreventsDuplicatesAndFreezesStateAcrossConcurrentChanges()throws Exception{
  UUID a=lot("OC-H-001"),b=lot("OC-H-001"),m=approved(a,b),e=id(start(m));nc(a);var asOf=clock.instant();String query=params(m)+"&limit=2&asOf="+asOf;
  var first=good("geo","/monitoring/tasks"+query,null);assertEquals(4,first.get("total").asInt());Set<String> ids=new HashSet<>();for(var t:first.get("items"))assertTrue(ids.add(t.get("id").asText()));
  clock.value.set(asOf.plusSeconds(120));var original=taskFor(e,"WAREHOUSE");good("almacen","/tasks/"+id(original)+"/acknowledge",data(0,Map.of()));nc(b);
  String cursor=first.get("nextCursor").asText();while(cursor!=null){var page=good("geo","/monitoring/tasks"+query+"&cursor="+cursor,null);assertEquals(4,page.get("total").asInt());for(var t:page.get("items")){assertTrue(ids.add(t.get("id").asText()));assertEquals("OPEN",t.get("status").asText());}cursor=page.get("nextCursor").isNull()?null:page.get("nextCursor").asText();}
  assertEquals(4,ids.size());assertEquals(8,execution(e).get("tasks").size());
 }
 @Test void snapshotCreationCanRaceNewRuleWithoutTornDeadlines()throws Exception{
  rule("CONTAINMENT","WAREHOUSE",11,22);UUID a=lot("OC-H-001"),e=id(start(approved(a,lot("OC-H-001"))));var body=ruleBody("CONTAINMENT","WAREHOUSE",33,44);
  try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);var update=pool.submit(()->{latch.await();return good("geo","/monitoring/ola-rules",body);});var incident=pool.submit(()->{latch.await();return nc(a);});latch.countDown();assertNotNull(update.get(15,TimeUnit.SECONDS));assertNotNull(incident.get(15,TimeUnit.SECONDS));}
  var t=taskFor(e,"WAREHOUSE");var rule=db.queryForMap("select * from ola_rule where id=?",UUID.fromString(t.get("ola_rule_id").asText()));Instant created=Instant.parse(t.get("created_at").asText());assertEquals(created.plusSeconds(((Number)rule.get("ack_minutes")).longValue()*60),Instant.parse(t.get("ack_due_at").asText()));assertEquals(created.plusSeconds(((Number)rule.get("close_minutes")).longValue()*60),Instant.parse(t.get("close_due_at").asText()));
 }

 @Test void multipleCausesCountCasesOnceAndMedianExcludesOpenClosures()throws Exception{
  UUID a=lot("OC-H-001"),b=lot("OC-H-001"),m=approved(a,b),e=id(start(m));nc(a);var first=taskFor(e,"WAREHOUSE");clock.value.set(clock.instant().plusSeconds(60));nc(b);
  var all=execution(e).get("tasks");JsonNode second=null;for(var t:all)if(t.get("target_role").asText().equals("WAREHOUSE")&&!t.get("id").equals(first.get("id")))second=t;assertNotNull(second);
  clock.value.set(clock.instant().plusSeconds(60));good("almacen","/tasks/"+id(first)+"/acknowledge",data(0,Map.of()));clock.value.set(clock.instant().plusSeconds(180));good("almacen","/tasks/"+id(second)+"/acknowledge",data(0,Map.of()));clock.value.set(clock.instant().plusSeconds(300));good("almacen","/tasks/"+id(first)+"/complete",data(1,Map.of()));
  var report=good("geo","/monitoring/summary"+params(m)+"&area=WAREHOUSE",null);assertEquals(2,report.get("nonconformities").get("OPEN").asInt());assertEquals(1,report.get("mixes").get("REVOKED").asInt());assertEquals(1,report.get("executions").get("SUSPENDED").asInt());var t=report.get("responseTimes").get(0);assertEquals(2,t.get("ack_count").asInt());assertEquals(180,t.get("ack_median_seconds").asDouble(),0.001);assertEquals(180,t.get("ack_mean_seconds").asDouble(),0.001);assertEquals(1,t.get("close_count").asInt());assertEquals(600,t.get("close_mean_seconds").asDouble(),0.001);
 }
 HttpResponse<String> raw(String actor,String path)throws Exception{
  var builder=HttpRequest.newBuilder(URI.create("http://localhost:18380/api"+path));if(actor!=null)builder.header("Authorization","Basic "+Base64.getEncoder().encodeToString((actor+":Simulacion123!").getBytes(StandardCharsets.UTF_8)));return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void jsonExportIsCompleteBoundedAndReadOnlyWithoutSecrets()throws Exception{
  UUID a=lot("OC-H-001"),m=approved(a,lot("OC-H-001")),e=id(start(m));nc(a);long events=db.queryForObject("select count(*) from execution_event",Long.class);
  var report=good("geo","/reports/executions/"+e+".json",null);assertTrue(report.get("legend").asText().contains("SOLO SIMULACIÓN"));assertEquals(e.toString(),report.get("execution").get("id").asText());assertEquals(2,report.get("lots").size());assertEquals(1,report.get("nonconformities").size());assertEquals(1,report.get("holds").size());assertEquals(4,report.get("tasks").size());assertTrue(report.get("olaRules").size()>0);assertTrue(report.get("lotEvents").size()>=13);assertTrue(report.get("mixEvents").size()>=6);assertTrue(report.get("executionEvents").size()>=6);
  Set<String> ids=new HashSet<>();for(var t:report.get("tasks"))assertTrue(ids.add(t.get("id").asText()));assertFalse(report.toString().contains("old-hash"));assertFalse(report.toString().contains("password"));assertEquals(events,db.queryForObject("select count(*) from execution_event",Long.class));
  assertEquals(401,raw(null,"/reports/executions/"+e+".json").statusCode());assertEquals(404,raw("geo","/reports/executions/"+UUID.randomUUID()+".json").statusCode());
  db.update("insert into execution_event(id,execution_id,actor,role,event_type,reason,evidence,correlation_id,payload) select gen_random_uuid(),?,'geo','GEO','SYNTHETIC','Bound fixture','EV',gen_random_uuid(),'synthetic' from generate_series(1,2001)",e);
  assertEquals(422,raw("geo","/reports/executions/"+e+".json").statusCode());
 }
 @Test void csvPagesAreExhaustiveAndNeutralizeFormulasWhileJsonKeepsOriginalEvidence()throws Exception{
  UUID a=lot("OC-H-001"),m=approved(a,lot("OC-H-001")),e=id(start(m));String hostile="=HYPERLINK(\"https://invalid.example\")",evidence="EV,\"quoted\"\r\nnext";
  good("tintoreria","/lots/"+a+"/nonconformities",data(lv(a),Map.of("origin","POST_RELEASE","responsible","calidad","reason",hostile,"evidence",evidence)));
  var jsonReport=good("geo","/reports/executions/"+e+".json",null);assertEquals(hostile,jsonReport.get("tasks").get(0).get("reason").asText());assertEquals(evidence,jsonReport.get("tasks").get(0).get("evidence").asText());
  var first=raw("geo","/reports/tasks.csv"+params(m)+"&limit=2");assertEquals(200,first.statusCode(),first.body());assertTrue(first.headers().firstValue("Content-Type").orElse("").contains("text/csv"));assertEquals("4",first.headers().firstValue("X-Total-Count").orElse(""));assertTrue(first.body().contains("\"'=HYPERLINK"));assertTrue(first.body().contains("EV,\"\"quoted\"\"  next"));assertTrue(first.body().contains("SOLO SIMULACIÓN"));
  var second=raw("geo","/reports/tasks.csv"+params(m)+"&limit=2&asOf="+first.headers().firstValue("X-As-Of").orElseThrow()+"&cursor="+first.headers().firstValue("X-Next-Cursor").orElseThrow());assertEquals(200,second.statusCode(),second.body());assertTrue(second.headers().firstValue("X-Next-Cursor").isEmpty());String combined=first.body()+second.body();for(var t:jsonReport.get("tasks")){String id=t.get("id").asText();assertEquals(1,(combined.length()-combined.replace(id,"").length())/id.length());}
  assertEquals(401,raw(null,"/reports/tasks.csv").statusCode());assertEquals(400,raw("geo","/reports/tasks.csv?limit=101").statusCode());String malformed=Base64.getUrlEncoder().withoutPadding().encodeToString("bad-date|bad-id".getBytes(StandardCharsets.UTF_8));assertEquals(400,raw("geo","/reports/tasks.csv?cursor="+malformed).statusCode());
 }

 @Test void paginatedExecutionFilterKeepsItsAsOfWhenParentIncidentCloses()throws Exception{
  UUID a=lot("OC-H-001"),m=approved(a,lot("OC-H-001")),e=id(start(m));nc(a);var at=clock.instant();String query=params(m)+"&executionStatus=SUSPENDED&limit=2&asOf="+at;
  var first=good("geo","/monitoring/tasks"+query,null);assertEquals(4,first.get("total").asInt());clock.value.set(at.plusSeconds(60));closeTasks(e);good("produccion","/executions/"+e+"/close-incident",data(1,Map.of()));
  var second=good("geo","/monitoring/tasks"+query+"&cursor="+first.get("nextCursor").asText(),null);assertEquals(4,second.get("total").asInt());assertEquals(2,second.get("items").size());for(var task:second.get("items")){assertEquals("OPEN",task.get("status").asText());assertEquals(0,task.get("version").asInt());assertEquals("SUSPENDED",task.get("execution_status").asText());}
 }
}
