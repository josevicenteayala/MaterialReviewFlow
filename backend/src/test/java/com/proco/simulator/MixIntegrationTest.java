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

@SpringBootTest(classes={SimulatorApplication.class,MixIntegrationTest.TimeConfig.class},webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties="server.port=18180")
class MixIntegrationTest {
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
  var b=HttpRequest.newBuilder(URI.create("http://localhost:18180/api"+path)).timeout(Duration.ofSeconds(20));
  if(user!=null)b.header("Authorization","Basic "+Base64.getEncoder().encodeToString((user+":Simulacion123!").getBytes(StandardCharsets.UTF_8)));
  if(body!=null)b.header("Content-Type","application/json").header("Idempotency-Key",key.toString()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  var r=client.send(b.build(),HttpResponse.BodyHandlers.ofString());return new Reply(r.statusCode(),r.body().isBlank()?json.createObjectNode():json.readTree(r.body()),r.body());
 }
 JsonNode good(String user,String path,Map<String,Object> body)throws Exception{var r=call(user,path,body,UUID.randomUUID());assertEquals(200,r.status,r.raw);return r.data;}
 Map<String,Object> data(long version,Map<String,Object> extra){var m=new LinkedHashMap<String,Object>();m.put("version",version);m.put("reason","Prueba Sprint 3");m.put("evidence","EV-S3");m.putAll(extra);return m;}
 UUID lot(String order)throws Exception{return lot(order,"2026-01-01");}
 UUID lot(String order,String productionDate)throws Exception{
  var result=good("almacen","/receipts",data(0,Map.of("orderCode",order,"supplierLot","S3-"+UUID.randomUUID(),"productionDate",productionDate,"quantity",1,"location","Q-S3")));
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

 @Test void s3_01_02RequestNeedsExactEligibleDistinctLotsAndDoesNotReserve()throws Exception{
  UUID a=lot("OC-H-001"),b=lot("OC-H-001");
  assertEquals(0,db.queryForObject("select count(*) from mix_request_lot where lot_id in (?,?)",Integer.class,a,b));
  var body=requestBody(a,b);
  assertEquals(403,call("almacen","/mix-requests",body,UUID.randomUUID()).status);
  var duplicate=new LinkedHashMap<>(body);duplicate.put("items",List.of(Map.of("lotId",a,"quantity",1),Map.of("lotId",a,"quantity",1)));
  assertEquals(422,call("tejeduria","/mix-requests",duplicate,UUID.randomUUID()).status);
  var excessive=new LinkedHashMap<>(body);excessive.put("items",List.of(Map.of("lotId",a,"quantity",2),Map.of("lotId",b,"quantity",1)));
  assertEquals(422,call("tejeduria","/mix-requests",excessive,UUID.randomUUID()).status);
  var negative=new LinkedHashMap<>(body);negative.put("items",List.of(Map.of("lotId",a,"quantity",-1),Map.of("lotId",b,"quantity",1)));
  assertEquals(400,call("tejeduria","/mix-requests",negative,UUID.randomUUID()).status);
  UUID id=UUID.fromString(good("tejeduria","/mix-requests",body).get("id").asText());
  assertEquals("UNDER_REVIEW",get(id).get("status").asText());assertFalse(get(id).get("usable").asBoolean());
  assertEquals(2,count("mix_request_lot",id));assertEquals(0,count("mix_assessment",id));
  assertEquals(0,new java.math.BigDecimal("2").compareTo(db.queryForObject("select sum(quantity) from lot where id in (?,?)",java.math.BigDecimal.class,a,b)));
  assertEquals(422,call("almacen","/mix-requests/"+id+"/authorization?montageCode="+body.get("montageCode"),null,null).status);
 }
 @Test void s3_03_06_07ConceptsRemainValidAcrossAreasAndDecisionIsIndependent()throws Exception{
  UUID id=mix(lot("OC-H-001"),lot("OC-H-001"));
  assertEquals(422,call("produccion","/mix-requests/"+id+"/decision",data(0,Map.of("decision","AUTHORIZE")),UUID.randomUUID()).status);
  assertEquals(403,call("tejeduria","/mix-requests/"+id+"/decision",data(0,Map.of("decision","AUTHORIZE")),UUID.randomUUID()).status);
  assertEquals(403,call("geo","/mix-requests/"+id+"/decision",data(0,Map.of("decision","AUTHORIZE")),UUID.randomUUID()).status);
  assertEquals(403,call("tejeduria","/mix-requests/"+id+"/assessments",data(0,Map.of("outcome","FAVORABLE")),UUID.randomUUID()).status);
  favorable(id);assertTrue(get(id).get("blockers").isEmpty());
  assertEquals(409,call("calidad","/mix-requests/"+id+"/assessments",data(3,Map.of("outcome","FAVORABLE")),UUID.randomUUID()).status);
  assertEquals(409,call("produccion","/mix-requests/"+id+"/decision",data(0,Map.of("decision","AUTHORIZE")),UUID.randomUUID()).status);
  var approved=authorize(id);assertEquals("AUTHORIZED",approved.get("status").asText());assertTrue(approved.get("usable").asBoolean());assertEquals(3,approved.get("assessments").size());
  assertEquals(200,call("geo","/mix-requests/"+id+"/authorization?montageCode="+approved.get("montageCode").asText(),null,null).status);
  assertEquals(422,call("geo","/mix-requests/"+id+"/authorization?montageCode=OTHER",null,null).status);
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("update mix_request set montage_code='CHANGED' where id=?",id));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("update mix_request_lot set quantity=0.1 where request_id=?",id));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("delete from mix_assessment where request_id=?",id));
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("delete from mix_event where mix_id=?",id));
 }
 @Test void s3_04_05DyeingRequiredByServerAndAnyUnfavorableOpinionBlocks()throws Exception{
  UUID id=mix(lot("OC-H-001"),lot("OC-E-001"));assertTrue(get(id).get("dyeingRequired").asBoolean());
  favorable(id);assertEquals(422,call("produccion","/mix-requests/"+id+"/decision",data(3,Map.of("decision","AUTHORIZE")),UUID.randomUUID()).status);
  assess(id,"tintoreria","FAVORABLE");assertTrue(authorize(id).get("usable").asBoolean());
  UUID no=mix(lot("OC-H-001"),lot("OC-H-001"));favorable(no);assess(no,"tintoreria","UNFAVORABLE");
  assertEquals(422,call("produccion","/mix-requests/"+no+"/decision",data(4,Map.of("decision","AUTHORIZE")),UUID.randomUUID()).status);
  var denied=good("produccion","/mix-requests/"+no+"/decision",data(4,Map.of("decision","DENY")));assertEquals("DENIED",denied.get("status").asText());
  assertEquals(409,call("produccion","/mix-requests/"+no+"/decision",data(5,Map.of("decision","AUTHORIZE")),UUID.randomUUID()).status);
 }
 @Test void s3_11IdempotencyCoversRequestAssessmentAndDecision()throws Exception{
  UUID key=UUID.randomUUID();var body=requestBody(lot("OC-H-001"),lot("OC-H-001"));
  var first=call("tejeduria","/mix-requests",body,key);assertEquals(200,first.status,first.raw);
  assertEquals(first.data.get("id"),call("tejeduria","/mix-requests",body,key).data.get("id"));
  var changed=new LinkedHashMap<>(body);changed.put("montageCode","CHANGED");assertEquals(409,call("tejeduria","/mix-requests",changed,key).status);
  UUID id=UUID.fromString(first.data.get("id").asText());UUID assessmentKey=UUID.randomUUID();var assessment=data(0,Map.of("outcome","FAVORABLE"));
  assertEquals(200,call("aprobador","/mix-requests/"+id+"/assessments",assessment,assessmentKey).status);
  assertEquals(200,call("aprobador","/mix-requests/"+id+"/assessments",assessment,assessmentKey).status);assertEquals(1,count("mix_assessment",id));
  assess(id,"geo","FAVORABLE");assess(id,"calidad","FAVORABLE");UUID decisionKey=UUID.randomUUID();var decision=data(3,Map.of("decision","AUTHORIZE"));
  assertEquals(200,call("produccion","/mix-requests/"+id+"/decision",decision,decisionKey).status);
  assertEquals(200,call("produccion","/mix-requests/"+id+"/decision",decision,decisionKey).status);assertEquals(5,db.queryForObject("select count(*) from mix_event where mix_id=? and event_type<>'COMMAND_DENIED'",Integer.class,id));
 }
 @Test void expiryIsCheckedWithoutRewritingHistoricalDecision()throws Exception{
  UUID id=mix(lot("OC-H-001"),lot("OC-H-001"));favorable(id);authorize(id);
  clock.value.set(clock.instant().plusSeconds(90000));var expired=get(id);assertEquals("AUTHORIZED",expired.get("status").asText());assertFalse(expired.get("usable").asBoolean());
  assertEquals(422,call("almacen","/mix-requests/"+id+"/authorization?montageCode="+expired.get("montageCode").asText(),null,null).status);
 }

 long lotVersion(UUID id){return db.queryForObject("select version from lot where id=?",Long.class,id);}
 UUID nc(UUID lot,String reporter)throws Exception{
  good(reporter,"/lots/"+lot+"/nonconformities",data(lotVersion(lot),Map.of("origin","POST_RELEASE","responsible","calidad")));
  return db.queryForObject("select id from non_conformity where lot_id=? order by opened_at desc limit 1",UUID.class,lot);
 }
 void closeNc(UUID lot,UUID nc)throws Exception{
  good("aprobador","/lots/"+lot+"/nonconformities/"+nc+"/opinion",data(lotVersion(lot),Map.of("outcome","CONFORMING")));
  good("calidad","/lots/"+lot+"/nonconformities/"+nc+"/resolve",data(lotVersion(lot),Map.of("disposition","LIFT_HOLD")));
  good("almacen","/lots/"+lot+"/available",data(lotVersion(lot),Map.of()));
 }
 @Test void s3_08_09NcRevokesAllAffectedMixesAndClosingDoesNotReactivate()throws Exception{
  UUID a=lot("OC-H-001"),b=lot("OC-H-001");UUID first=mix(a,b),second=mix(b,a);favorable(first);favorable(second);authorize(first);authorize(second);
  UUID caseId=nc(a,"tintoreria");
  for(UUID id:List.of(first,second)){
   var revoked=get(id);assertEquals("REVOKED",revoked.get("status").asText());assertFalse(revoked.get("usable").asBoolean());
   assertEquals("produccion",revoked.get("approver").asText());assertFalse(revoked.get("decidedAt").isNull());assertEquals(5,revoked.get("version").asLong());
   assertEquals(1,db.queryForObject("select count(*) from mix_event where mix_id=? and event_type='AUTHORIZED'",Integer.class,id));
   assertEquals(1,db.queryForObject("select count(*) from mix_event where mix_id=? and event_type='REVOKED' and actor='tintoreria'",Integer.class,id));
  }
  assertEquals("BLOCKED",db.queryForObject("select inventory_state from lot where id=?",String.class,a));
  closeNc(a,caseId);
  assertEquals("REVOKED",get(first).get("status").asText());assertFalse(get(first).get("usable").asBoolean());
  assertEquals(409,call("produccion","/mix-requests/"+first+"/decision",data(5,Map.of("decision","AUTHORIZE")),UUID.randomUUID()).status);
  UUID fresh=mix(a,b);favorable(fresh);assertTrue(authorize(fresh).get("usable").asBoolean());
 }
 @Test void stalePendingRequestCannotReuseConceptsAfterLotReevaluation()throws Exception{
  UUID a=lot("OC-H-001"),b=lot("OC-H-001"),id=mix(a,b);favorable(id);
  UUID caseId=nc(a,"geo");
  assertEquals(422,call("tejeduria","/mix-requests",requestBody(a,b),UUID.randomUUID()).status);
  closeNc(a,caseId);assertEquals("UNDER_REVIEW",get(id).get("status").asText());
  assertEquals(422,call("produccion","/mix-requests/"+id+"/decision",data(version(id),Map.of("decision","AUTHORIZE")),UUID.randomUUID()).status);
  assertTrue(get(id).get("materialBlockers").toString().contains("versión"));
 }
 @Test void s3_10ConcurrentNcAndApprovalCannotLeaveUsableAuthorization()throws Exception{
  var pool=Executors.newFixedThreadPool(2);
  try{
   for(int i=0;i<4;i++){
    UUID a=lot("OC-H-001"),b=lot("OC-H-001"),id=mix(a,b);favorable(id);long v=version(id),lv=lotVersion(a);
    var start=new CountDownLatch(1);
    var approve=pool.submit(()->{start.await();return call("produccion","/mix-requests/"+id+"/decision",data(v,Map.of("decision","AUTHORIZE")),UUID.randomUUID());});
    var block=pool.submit(()->{start.await();return call("geo","/lots/"+a+"/nonconformities",data(lv,Map.of("origin","POST_RELEASE","responsible","calidad")),UUID.randomUUID());});
    start.countDown();var ar=approve.get(15,TimeUnit.SECONDS);var br=block.get(15,TimeUnit.SECONDS);
    assertEquals(200,br.status,br.raw);assertTrue(Set.of(200,409,422).contains(ar.status),ar.raw);
    var result=get(id);assertFalse(result.get("usable").asBoolean());assertTrue(Set.of("UNDER_REVIEW","REVOKED").contains(result.get("status").asText()));
    assertEquals("BLOCKED",db.queryForObject("select inventory_state from lot where id=?",String.class,a));
    assertEquals(0,db.queryForObject("select count(*) from mix_request where id=? and status='AUTHORIZED'",Integer.class,id));
   }
  }finally{pool.shutdownNow();}
 }
 @Test void twoNcOnSharedMixesUseConsistentLockOrderAndRevokeOnce()throws Exception{
  UUID a=lot("OC-H-001"),b=lot("OC-H-001"),one=mix(a,b),two=mix(b,a);favorable(one);favorable(two);authorize(one);authorize(two);
  var pool=Executors.newFixedThreadPool(2);try{
   var tasks=List.<Callable<UUID>>of(()->nc(a,"geo"),()->nc(b,"tintoreria"));
   for(var result:pool.invokeAll(tasks,20,TimeUnit.SECONDS))assertNotNull(result.get());
   for(UUID id:List.of(one,two)){assertEquals("REVOKED",get(id).get("status").asText());assertEquals(1,db.queryForObject("select count(*) from mix_event where mix_id=? and event_type='REVOKED'",Integer.class,id));}
  }finally{pool.shutdownNow();}
 }
 @Test void failureDuringRevocationRollsBackNcHoldLotAndMixTogether()throws Exception{
  UUID a=lot("OC-H-001"),id=mix(a,lot("OC-H-001"));favorable(id);authorize(id);long lv=lotVersion(a);
  String suffix=UUID.randomUUID().toString().replace("-","");UUID key=UUID.randomUUID();
  db.execute("create function fail_mix_"+suffix+"() returns trigger language plpgsql as $$ begin if NEW.event_type='REVOKED' and NEW.mix_id='"+id+"'::uuid then raise exception 'forced revocation failure' using errcode='23514'; end if; return NEW; end $$");
  db.execute("create trigger fail_mix_"+suffix+" before insert on mix_event for each row execute function fail_mix_"+suffix+"()");
  try{
   var failed=call("tintoreria","/lots/"+a+"/nonconformities",data(lv,Map.of("origin","POST_RELEASE","responsible","calidad")),key);assertEquals(409,failed.status,failed.raw);
   assertEquals(lv,lotVersion(a));assertEquals("AVAILABLE",db.queryForObject("select inventory_state from lot where id=?",String.class,a));
   assertEquals(0,db.queryForObject("select count(*) from inventory_hold where lot_id=?",Integer.class,a));assertEquals(0,db.queryForObject("select count(*) from non_conformity where lot_id=?",Integer.class,a));
   assertEquals("AUTHORIZED",get(id).get("status").asText());assertTrue(get(id).get("usable").asBoolean());assertEquals(4,version(id));
   assertEquals(0,db.queryForObject("select count(*) from command_result where command_key=?",Integer.class,key));
  }finally{db.execute("drop trigger fail_mix_"+suffix+" on mix_event");db.execute("drop function fail_mix_"+suffix+"()");}
 }
 @Test void definitiveRejectionPreservesRevocationAndDatabaseProtectsUnrevokedAuthorization()throws Exception{
  UUID a=lot("OC-H-001"),b=lot("OC-H-001"),id=mix(a,b);favorable(id);authorize(id);
  assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("update lot set inventory_state='BLOCKED',version=version+1 where id=?",a));
  UUID caseId=nc(a,"geo");
  good("aprobador","/lots/"+a+"/nonconformities/"+caseId+"/opinion",data(lotVersion(a),Map.of("outcome","NONCONFORMING")));
  good("calidad","/lots/"+a+"/nonconformities/"+caseId+"/reject",data(lotVersion(a),Map.of("disposition","RETURN_TO_SUPPLIER")));
  assertEquals("REJECTED",db.queryForObject("select quality_state from lot where id=?",String.class,a));assertEquals("REVOKED",get(id).get("status").asText());
  assertEquals(422,call("tejeduria","/mix-requests",requestBody(a,b),UUID.randomUUID()).status);
 }

 @Test void noNormativeDateThresholdAndStaleAssessmentContextCannotAuthorize()throws Exception{
  UUID a=lot("OC-H-001","2026-01-01"),b=lot("OC-H-001","2026-05-01"),id=mix(a,b);favorable(id);assertTrue(authorize(id).get("usable").asBoolean());
  UUID old=mix(a,b);
  db.update("insert into mix_assessment(id,request_id,role,author,outcome,reason,evidence,based_on_mix_version,review_revision,rule_version) values (?,?,'LAB_APPROVER','aprobador','FAVORABLE','synthetic old context','EV-OLD',0,0,'MIX-SIM-0')",UUID.randomUUID(),old);
  assess(old,"geo","FAVORABLE");assess(old,"calidad","FAVORABLE");
  assertEquals(422,call("produccion","/mix-requests/"+old+"/decision",data(version(old),Map.of("decision","AUTHORIZE")),UUID.randomUUID()).status);
  assertTrue(get(old).get("blockers").toString().contains("desactualizado"));
 }
}
