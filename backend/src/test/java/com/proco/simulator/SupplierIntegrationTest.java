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

@SpringBootTest(classes={SimulatorApplication.class,SupplierIntegrationTest.TimeConfig.class},webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties="server.port=18480")
class SupplierIntegrationTest {
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
  var b=HttpRequest.newBuilder(URI.create("http://localhost:18480/api"+path)).timeout(Duration.ofSeconds(20));
  if(user!=null)b.header("Authorization","Basic "+Base64.getEncoder().encodeToString((user+":Simulacion123!").getBytes(StandardCharsets.UTF_8)));
  if(body!=null)b.header("Content-Type","application/json").header("Idempotency-Key",key.toString()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  var r=client.send(b.build(),HttpResponse.BodyHandlers.ofString());return new Reply(r.statusCode(),r.body().isBlank()?json.createObjectNode():json.readTree(r.body()),r.body());
 }
 JsonNode good(String user,String path,Map<String,Object> body)throws Exception{var r=call(user,path,body,UUID.randomUUID());assertEquals(200,r.status,r.raw);return r.data;}
 Map<String,Object> data(long version,Map<String,Object> extra){var m=new LinkedHashMap<String,Object>();m.put("version",version);m.put("reason","Prueba Sprint 6");m.put("evidence","EV-S3");m.putAll(extra);return m;}
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

 String order(int expected){String code="S6-"+UUID.randomUUID();db.update("insert into purchase_order values (?,'Proveedor Ficticio','HILO-DEMO',?)",code,expected);return code;}
 Map<String,Object> discrepancyBody(String order,int doc,int observed,boolean finding){return data(0,Map.of("orderCode",order,"supplierLot","S6-"+UUID.randomUUID(),"productionDate","2026-01-01","location","PRE-SIM","supplierDocumentRef","DOC-SIM-S6","documentedQuantity",doc,"observedQuantity",observed,"technicalFinding",finding));}
 JsonNode difference(String order,int doc,int observed,boolean finding)throws Exception{return good("almacen","/receipt-discrepancies",discrepancyBody(order,doc,observed,finding));}
 JsonNode discrepancy(UUID id)throws Exception{return good("geo","/receipt-discrepancies/"+id,null);}
 JsonNode dAction(UUID id,String actor,String action,Map<String,Object> extra)throws Exception{return good(actor,"/receipt-discrepancies/"+id+"/"+action,data(discrepancy(id).get("version").asLong(),extra));}
 void authorizeDifference(UUID id,int quantity)throws Exception{dAction(id,"almacen","confirm-count",Map.of());dAction(id,"abastecimiento","review",Map.of("proposedAcceptedQuantity",quantity,"remainderPlan","Segregado SOLO SIMULACIÓN, sin movimiento físico"));}
 JsonNode supplier(UUID id)throws Exception{return good("geo","/supplier-cases/"+id,null);}
 JsonNode supplierAction(UUID id,String actor,String action,Map<String,Object> extra)throws Exception{var body=data(supplier(id).get("version").asLong(),Map.of("communicationRef","SIM-COM-"+action));body.putAll(extra);return good(actor,"/supplier-cases/"+id+"/"+action,body);}
 UUID reject(UUID lot,UUID nc,String disposition)throws Exception{good("aprobador","/lots/"+lot+"/nonconformities/"+nc+"/opinion",data(lv(lot),Map.of("outcome","NONCONFORMING")));good("calidad","/lots/"+lot+"/nonconformities/"+nc+"/reject",data(lv(lot),Map.of("disposition",disposition)));return disposition.equals("SCRAP")?null:db.queryForObject("select id from supplier_case where nc_id=?",UUID.class,nc);}
 void commercialSteps(UUID id,String response)throws Exception{supplierAction(id,"abastecimiento","contact",Map.of());supplierAction(id,"abastecimiento","response",Map.of("response",response));supplierAction(id,"abastecimiento","disposition",Map.of("direction","RETURN_REQUESTED","treatment","Gestión ficticia documentada, sin retorno físico"));}
 @Test void missingOrderCodeIsReportedAsIncompletePreReceiptWithoutCreatingBusinessData()throws Exception{
  String oc=order(100);var original=discrepancyBody(oc,100,90,false);
  for(int variant=0;variant<4;variant++){
   var body=new LinkedHashMap<>(original);
   switch(variant){case 0->body.remove("orderCode");case 1->body.put("orderCode",null);case 2->body.put("orderCode","");case 3->body.put("orderCode","   ");}
   UUID key=UUID.randomUUID();var reply=call("almacen","/receipt-discrepancies",body,key);
   assertEquals(422,reply.status,reply.raw);assertTrue(reply.raw.contains("Faltan datos de pre-recepción"),reply.raw);
   assertEquals(0,db.queryForObject("select count(*) from command_result where command_key=?",Integer.class,key));
  }
  assertEquals(0,db.queryForObject("select count(*) from receipt_discrepancy where supplier_lot=?",Integer.class,original.get("supplierLot")));
  assertEquals(0,db.queryForObject("select count(*) from lot where order_code=?",Integer.class,oc));
  var unknown=new LinkedHashMap<>(original);unknown.put("orderCode","UNKNOWN-S6-"+UUID.randomUUID().toString().substring(0,8));
  var reply=call("almacen","/receipt-discrepancies",unknown,UUID.randomUUID());assertEquals(422,reply.status,reply.raw);assertTrue(reply.raw.contains("OC inexistente"),reply.raw);
 }
 @Test void s03ShortageAndDocumentSurplusPreserveFactsAndOnlyCreateAcceptedQuarantine()throws Exception{
  for(int[] qty:List.of(new int[]{100,90},new int[]{90,100})){
   String oc=order(100);var d=difference(oc,qty[0],qty[1],false);UUID id=id(d);assertEquals(0,db.queryForObject("select count(*) from lot where order_code=?",Integer.class,oc));assertEquals(qty[0],d.get("documented_quantity").asInt());assertEquals(qty[1],d.get("observed_quantity").asInt());assertEquals(Math.max(qty[0]-qty[1],0),d.get("documented_shortage").asInt());assertEquals(Math.max(qty[1]-qty[0],0),d.get("documented_surplus").asInt());authorizeDifference(id,qty[1]);
   var accepted=dAction(id,"almacen","accept",Map.of());assertEquals("ACCEPTED_OBSERVED",accepted.get("status").asText());var lot=good("almacen","/lots/"+accepted.get("accepted_lot_id").asText(),null);assertEquals(qty[1],lot.get("quantity").asInt());assertEquals("UNDER_VERIFICATION",lot.get("qualityState").asText());assertEquals("QUARANTINE",lot.get("inventoryState").asText());assertEquals(100,db.queryForObject("select expected_quantity from purchase_order where code=?",Integer.class,oc));
   assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("update receipt_discrepancy set documented_quantity=1,version=version+1 where id=?",id));assertEquals(409,call("almacen","/receipt-discrepancies/"+id+"/accept",data(3,Map.of()),UUID.randomUUID()).status);
  }
 }
 @Test void excessAndZeroAcceptanceDoNotInventStockOrSpendDocumentedQuantity()throws Exception{
  String oc=order(30);var d=difference(oc,40,40,false);UUID id=id(d);assertEquals(10,d.get("excess_over_balance_at_report").asInt());dAction(id,"almacen","confirm-count",Map.of());assertEquals(422,call("abastecimiento","/receipt-discrepancies/"+id+"/review",data(1,Map.of("proposedAcceptedQuantity",40,"remainderPlan","SIM")),UUID.randomUUID()).status);
  dAction(id,"abastecimiento","review",Map.of("proposedAcceptedQuantity",30,"remainderPlan","10 kg no aceptados, segregación ficticia"));var accepted=dAction(id,"almacen","accept",Map.of());assertEquals("ACCEPTED_PARTIAL",accepted.get("status").asText());assertEquals(10,accepted.get("unaccepted_or_pending_quantity").asInt());assertEquals(30,db.queryForObject("select sum(quantity) from lot where order_code=?",Integer.class,oc));
  var zero=difference(order(100),100,0,false);authorizeDifference(id(zero),0);var result=dAction(id(zero),"almacen","accept",Map.of());assertEquals("EXCESS_NOT_ACCEPTED",result.get("status").asText());assertTrue(result.get("accepted_lot_id").isNull());
 }
 @Test void rolesQualityConditionAndCancellationAreEnforced()throws Exception{
  String oc=order(100);var body=discrepancyBody(oc,100,90,true);assertEquals(403,call("abastecimiento","/receipt-discrepancies",body,UUID.randomUUID()).status);var d=good("almacen","/receipt-discrepancies",body);UUID id=id(d);
  assertEquals(409,call("almacen","/receipt-discrepancies/"+id+"/accept",data(0,Map.of()),UUID.randomUUID()).status);dAction(id,"almacen","confirm-count",Map.of());
  assertEquals(422,call("abastecimiento","/receipt-discrepancies/"+id+"/review",data(1,Map.of("proposedAcceptedQuantity",90)),UUID.randomUUID()).status);
  dAction(id,"calidad","quality",Map.of("outcome","UNFAVORABLE"));assertEquals(422,call("abastecimiento","/receipt-discrepancies/"+id+"/review",data(2,Map.of("proposedAcceptedQuantity",90)),UUID.randomUUID()).status);assertEquals("CANCELLED_WITH_REASON",dAction(id,"abastecimiento","cancel",Map.of()).get("status").asText());
  var good=difference(oc,100,90,true);dAction(id(good),"almacen","confirm-count",Map.of());dAction(id(good),"calidad","quality",Map.of("outcome","FAVORABLE"));dAction(id(good),"abastecimiento","review",Map.of("proposedAcceptedQuantity",90));assertEquals("ACCEPTED_OBSERVED",dAction(id(good),"almacen","accept",Map.of()).get("status").asText());
  var arithmetic=difference(order(100),100,90,false);dAction(id(arithmetic),"almacen","confirm-count",Map.of());assertEquals(422,call("calidad","/receipt-discrepancies/"+id(arithmetic)+"/quality",data(1,Map.of("outcome","FAVORABLE")),UUID.randomUUID()).status);
 }
 @Test void changedBalanceRequiresExplicitRevisedAuthorization()throws Exception{
  String oc=order(100);var d=difference(oc,100,90,false);authorizeDifference(id(d),90);
  good("almacen","/receipts",data(0,Map.of("orderCode",oc,"supplierLot","OTHER-"+UUID.randomUUID(),"productionDate","2026-01-01","quantity",20,"location","Q")));
  assertEquals(422,call("almacen","/receipt-discrepancies/"+id(d)+"/accept",data(2,Map.of()),UUID.randomUUID()).status);assertEquals("AUTHORIZED",discrepancy(id(d)).get("status").asText());
  dAction(id(d),"abastecimiento","review",Map.of("proposedAcceptedQuantity",80,"remainderPlan","10 no aceptados SIM"));assertEquals("ACCEPTED_PARTIAL",dAction(id(d),"almacen","accept",Map.of()).get("status").asText());assertEquals(100,db.queryForObject("select sum(quantity) from lot where order_code=?",Integer.class,oc));
 }
 @Test void acceptanceIdempotencyAndConcurrentResolutionCreateOneLot()throws Exception{
  String oc=order(100);UUID key=UUID.randomUUID();var body=discrepancyBody(oc,100,90,false);var one=call("almacen","/receipt-discrepancies",body,key);assertEquals(200,one.status,one.raw);assertEquals(one.data.get("id"),call("almacen","/receipt-discrepancies",body,key).data.get("id"));UUID id=id(one.data);authorizeDifference(id,90);
  try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);List<Future<Reply>> fs=new ArrayList<>();for(int i=0;i<2;i++)fs.add(pool.submit(()->{latch.await();return call("almacen","/receipt-discrepancies/"+id+"/accept",data(2,Map.of()),UUID.randomUUID());}));latch.countDown();var statuses=new ArrayList<Integer>();for(var f:fs)statuses.add(f.get(15,TimeUnit.SECONDS).status);Collections.sort(statuses);assertEquals(List.of(200,409),statuses);}
  assertEquals(1,db.queryForObject("select count(*) from lot where order_code=?",Integer.class,oc));
 }
 @Test void normalReceiptAndDiscrepancyAcceptShareOrderLock()throws Exception{
  String oc=order(100);var d=difference(oc,100,90,false);authorizeDifference(id(d),90);
  try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);var first=pool.submit(()->{latch.await();return call("almacen","/receipt-discrepancies/"+id(d)+"/accept",data(2,Map.of()),UUID.randomUUID());});var second=pool.submit(()->{latch.await();return call("almacen","/receipts",data(0,Map.of("orderCode",oc,"supplierLot","NORMAL-"+UUID.randomUUID(),"productionDate","2026-01-01","quantity",20,"location","Q")),UUID.randomUUID());});latch.countDown();var statuses=new ArrayList<>(List.of(first.get(15,TimeUnit.SECONDS).status,second.get(15,TimeUnit.SECONDS).status));Collections.sort(statuses);assertEquals(List.of(200,422),statuses);}
  assertTrue(db.queryForObject("select sum(quantity)<=100 from lot where order_code=?",Boolean.class,oc));
 }
 @Test void supplierCaseFollowsRejectedMaterialWithoutChangingTechnicalStateOrBalance()throws Exception{
  UUID a=lot("OC-H-001"),n=nc(a),id=reject(a,n,"RETURN_TO_SUPPLIER");var opened=supplier(id);assertTrue(opened.get("tasks").isEmpty());assertEquals("OPEN",opened.get("status").asText());long v=lv(a);
  assertEquals(403,call("calidad","/supplier-cases/"+id+"/contact",data(0,Map.of("communicationRef","SIM")),UUID.randomUUID()).status);commercialSteps(id,"DISPUTED");
  var close=data(3,Map.of("communicationRef","SIM-CLOSE","physicalReturnConfirmed",false,"erpPosted",false));assertEquals(422,call("abastecimiento","/supplier-cases/"+id+"/close",close,UUID.randomUUID()).status);
  var done=good("abastecimiento2","/supplier-cases/"+id+"/close",close);assertEquals("CLOSED_ADMINISTRATIVELY",done.get("status").asText());assertFalse(done.get("physical_return_confirmed").asBoolean());assertFalse(done.get("erp_posted").asBoolean());assertEquals(v,lv(a));assertEquals("REJECTED",done.get("quality_state").asText());assertEquals("BLOCKED",done.get("inventory_state").asText());assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("update supplier_case set physical_return_confirmed=true,version=version+1 where id=?",id));
 }
 @Test void linkedProcurementTasksAreReusedAndGateAdministrativeClose()throws Exception{
  UUID a=lot("OC-H-001"),m=approved(a,lot("OC-H-001")),e=id(start(m)),n=nc(a),id=reject(a,n,"RETURN_TO_SUPPLIER");assertEquals(1,supplier(id).get("tasks").size());assertEquals(1,db.queryForObject("select count(*) from containment_task where nc_id=? and target_role='PROCUREMENT'",Integer.class,n));commercialSteps(id,"NO_RESPONSE");
  assertEquals(422,call("abastecimiento2","/supplier-cases/"+id+"/close",data(3,Map.of("communicationRef","SIM-CLOSE","physicalReturnConfirmed",false,"erpPosted",false)),UUID.randomUUID()).status);
  for(var t:supplier(id).get("tasks")){good("abastecimiento","/tasks/"+t.get("id").asText()+"/acknowledge",data(0,Map.of()));good("abastecimiento","/tasks/"+t.get("id").asText()+"/complete",data(1,Map.of()));}
  supplierAction(id,"abastecimiento2","close",Map.of("physicalReturnConfirmed",false,"erpPosted",false));assertEquals("SUSPENDED",execution(e).get("status").asText());assertEquals("REVOKED",get(m).get("status").asText());
 }
 @Test void scrapNeverCreatesSupplierReturnCase()throws Exception{UUID a=lot("OC-H-001"),n=nc(a);reject(a,n,"SCRAP");assertEquals(0,db.queryForObject("select count(*) from supplier_case where nc_id=?",Integer.class,n));assertEquals(422,call("abastecimiento","/supplier-cases",data(lv(a),Map.of("lotId",a,"ncId",n)),UUID.randomUUID()).status);}

 @Test void acceptanceRollsBackLotAuditAndCaseOnInjectedFailureAndReplaysSafely()throws Exception{
  for(String table:List.of("lot","supplier_workflow_event")){
   String oc=order(100);var d=difference(oc,100,90,false);UUID id=id(d);authorizeDifference(id,90);UUID key=UUID.randomUUID();String suffix=UUID.randomUUID().toString().replace("-","");
   String condition=table.equals("lot")?"NEW.order_code='"+oc+"'":"NEW.discrepancy_id='"+id+"'::uuid and NEW.event_type='DISCREPANCY_ACCEPT'";
   db.execute("create function fail_s6_"+suffix+"() returns trigger language plpgsql as $$ begin if "+condition+" then raise exception 'forced S6 failure' using errcode='23514'; end if; return NEW; end $$");db.execute("create trigger fail_s6_"+suffix+" before insert on "+table+" for each row execute function fail_s6_"+suffix+"()");
   var body=data(2,Map.of());try{var r=call("almacen","/receipt-discrepancies/"+id+"/accept",body,key);assertEquals(409,r.status,r.raw);assertEquals("AUTHORIZED",discrepancy(id).get("status").asText());assertEquals(2,discrepancy(id).get("version").asInt());assertEquals(0,db.queryForObject("select count(*) from lot where order_code=?",Integer.class,oc));assertEquals(0,db.queryForObject("select count(*) from command_result where command_key=?",Integer.class,key));}finally{db.execute("drop trigger fail_s6_"+suffix+" on "+table);db.execute("drop function fail_s6_"+suffix+"()");}
   var result=call("almacen","/receipt-discrepancies/"+id+"/accept",body,key);assertEquals(200,result.status,result.raw);assertEquals(result.data.get("accepted_lot_id"),call("almacen","/receipt-discrepancies/"+id+"/accept",body,key).data.get("accepted_lot_id"));assertEquals(1,db.queryForObject("select count(*) from lot where order_code=?",Integer.class,oc));assertEquals(1,db.queryForObject("select count(*) from supplier_workflow_event where discrepancy_id=? and event_type='DISCREPANCY_ACCEPT'",Integer.class,id));
  }
 }
 @Test void failedSupplierRegistrationRollsBackNewTechnicalRejectionAndProcurementTask()throws Exception{
  UUID a=lot("OC-H-001"),e=id(start(approved(a,lot("OC-H-001")))),n=nc(a);good("aprobador","/lots/"+a+"/nonconformities/"+n+"/opinion",data(lv(a),Map.of("outcome","NONCONFORMING")));long v=lv(a);UUID key=UUID.randomUUID();var body=data(v,Map.of("disposition","RETURN_TO_SUPPLIER"));String suffix=UUID.randomUUID().toString().replace("-","");
  db.execute("create function fail_case_"+suffix+"() returns trigger language plpgsql as $$ begin if NEW.nc_id='"+n+"'::uuid then raise exception 'forced case failure' using errcode='23514'; end if; return NEW; end $$");db.execute("create trigger fail_case_"+suffix+" before insert on supplier_case for each row execute function fail_case_"+suffix+"()");
  try{var r=call("calidad","/lots/"+a+"/nonconformities/"+n+"/reject",body,key);assertEquals(409,r.status,r.raw);assertEquals(v,lv(a));assertEquals("RELEASED",db.queryForObject("select quality_state from lot where id=?",String.class,a));assertEquals("OPEN",db.queryForObject("select status from non_conformity where id=?",String.class,n));assertEquals(0,db.queryForObject("select count(*) from supplier_case where nc_id=?",Integer.class,n));assertEquals(0,db.queryForObject("select count(*) from containment_task where nc_id=? and target_role='PROCUREMENT'",Integer.class,n));assertEquals(0,db.queryForObject("select count(*) from command_result where command_key=?",Integer.class,key));assertEquals(1,db.queryForObject("select count(*) from inventory_hold where nc_id=? and status='ACTIVE'",Integer.class,n));assertEquals("SUSPENDED",execution(e).get("status").asText());}finally{db.execute("drop trigger fail_case_"+suffix+" on supplier_case");db.execute("drop function fail_case_"+suffix+"()");}
  assertEquals(200,call("calidad","/lots/"+a+"/nonconformities/"+n+"/reject",body,key).status);assertEquals(200,call("calidad","/lots/"+a+"/nonconformities/"+n+"/reject",body,key).status);assertEquals(1,db.queryForObject("select count(*) from supplier_case where nc_id=?",Integer.class,n));assertEquals(1,db.queryForObject("select count(*) from containment_task where nc_id=? and target_role='PROCUREMENT'",Integer.class,n));
 }
 @Test void twoDifferentAcceptancesCannotExceedPurchaseOrder()throws Exception{
  String oc=order(100);UUID one=id(difference(oc,100,70,false)),two=id(difference(oc,100,70,false));authorizeDifference(one,70);authorizeDifference(two,70);
  try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);List<Future<Reply>> fs=new ArrayList<>();for(UUID id:List.of(one,two))fs.add(pool.submit(()->{latch.await();return call("almacen","/receipt-discrepancies/"+id+"/accept",data(2,Map.of()),UUID.randomUUID());}));latch.countDown();var statuses=new ArrayList<Integer>();for(var f:fs)statuses.add(f.get(15,TimeUnit.SECONDS).status);Collections.sort(statuses);assertEquals(List.of(200,422),statuses);}
  assertEquals(70,db.queryForObject("select sum(quantity) from lot where order_code=?",Integer.class,oc));
 }
 @Test void supplierCaseClosureAndTaskCompletionSerializeWithoutDeadlock()throws Exception{
  UUID a=lot("OC-H-001"),e=id(start(approved(a,lot("OC-H-001")))),n=nc(a),id=reject(a,n,"RETURN_TO_SUPPLIER");commercialSteps(id,"ACCEPTED");var task=supplier(id).get("tasks").get(0);String taskPath="/tasks/"+task.get("id").asText();good("abastecimiento",taskPath+"/acknowledge",data(0,Map.of()));
  try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);var close=pool.submit(()->{latch.await();return call("abastecimiento2","/supplier-cases/"+id+"/close",data(3,Map.of("communicationRef","SIM-CLOSE","physicalReturnConfirmed",false,"erpPosted",false)),UUID.randomUUID());});var complete=pool.submit(()->{latch.await();return call("abastecimiento",taskPath+"/complete",data(1,Map.of()),UUID.randomUUID());});latch.countDown();assertTrue(Set.of(200,422).contains(close.get(15,TimeUnit.SECONDS).status));assertEquals(200,complete.get(15,TimeUnit.SECONDS).status);}
  if(!supplier(id).get("status").asText().equals("CLOSED_ADMINISTRATIVELY"))supplierAction(id,"abastecimiento2","close",Map.of("physicalReturnConfirmed",false,"erpPosted",false));assertEquals("SUSPENDED",execution(e).get("status").asText());
 }
 @Test void commercialCommandsAreVersionedIdempotentAndCannotSkipStagesOrClaimPhysicalReturn()throws Exception{
  UUID a=lot("OC-H-001"),n=nc(a),id=reject(a,n,"RETURN_TO_SUPPLIER");var invalid=data(0,Map.of("communicationRef","SIM","response","ACCEPTED"));assertEquals(409,call("abastecimiento","/supplier-cases/"+id+"/response",invalid,UUID.randomUUID()).status);UUID key=UUID.randomUUID();var contact=data(0,Map.of("communicationRef","SIM-CONTACT"));assertEquals(200,call("abastecimiento","/supplier-cases/"+id+"/contact",contact,key).status);assertEquals(200,call("abastecimiento","/supplier-cases/"+id+"/contact",contact,key).status);assertEquals(1,db.queryForObject("select count(*) from supplier_workflow_event where supplier_case_id=? and event_type='SUPPLIER_CONTACT'",Integer.class,id));assertEquals(409,call("abastecimiento","/supplier-cases/"+id+"/response",data(1,Map.of("communicationRef","SIM-RESPONSE","response","ACCEPTED")),key).status);assertEquals(400,call("abastecimiento2","/supplier-cases/"+id+"/close",data(1,Map.of("physicalReturnConfirmed",true,"erpPosted",false)),UUID.randomUUID()).status);
 }
 @Test void legacyRejectionRequiresExplicitAdoptionAndNeverRewritesLotOrNc()throws Exception{
  UUID lot=lot("OC-H-001"),nc=UUID.randomUUID();db.update("update lot set quality_state='REJECTED',inventory_state='BLOCKED',version=version+1 where id=?",lot);
  db.update("insert into non_conformity(id,lot_id,origin,status,responsible,opened_by,reason,evidence,disposition,decided_by,decided_at,decision_reason,decision_evidence) values (?,?,'POST_RELEASE','REJECTED','calidad','almacen','Synthetic legacy','EV','RETURN_TO_SUPPLIER','calidad',now(),'Legacy decision','EV')",nc,lot);long version=lv(lot);
  var before=db.queryForObject("select to_jsonb(n)::text from non_conformity n where id=?",String.class,nc);UUID key=UUID.randomUUID();var body=data(version,Map.of("lotId",lot,"ncId",nc));var adopted=call("abastecimiento","/supplier-cases",body,key);assertEquals(200,adopted.status,adopted.raw);assertEquals(id(adopted.data),id(call("abastecimiento","/supplier-cases",body,key).data));assertEquals(version,lv(lot));assertEquals(before,db.queryForObject("select to_jsonb(n)::text from non_conformity n where id=?",String.class,nc));assertEquals(409,call("abastecimiento2","/supplier-cases",body,UUID.randomUUID()).status);
 }

 @Test void oneRejectionLinksEveryExistingProcurementTaskAcrossExecutions()throws Exception{
  UUID a=lot("OC-H-001"),b=lot("OC-H-001"),one=id(start(approved(a,b))),two=id(start(approved(a,b))),n=nc(a),id=reject(a,n,"RETURN_TO_SUPPLIER");
  var tasks=supplier(id).get("tasks");assertEquals(2,tasks.size());assertEquals(2,db.queryForObject("select count(*) from containment_task where nc_id=? and target_role='PROCUREMENT'",Integer.class,n));commercialSteps(id,"ACCEPTED");
  for(int i=0;i<tasks.size();i++){
   assertEquals(422,call("abastecimiento2","/supplier-cases/"+id+"/close",data(3,Map.of("communicationRef","SIM-CLOSE","physicalReturnConfirmed",false,"erpPosted",false)),UUID.randomUUID()).status);
   String path="/tasks/"+tasks.get(i).get("id").asText();good("abastecimiento",path+"/acknowledge",data(0,Map.of()));good("abastecimiento",path+"/complete",data(1,Map.of()));
  }
  supplierAction(id,"abastecimiento2","close",Map.of("physicalReturnConfirmed",false,"erpPosted",false));assertEquals("SUSPENDED",execution(one).get("status").asText());assertEquals("SUSPENDED",execution(two).get("status").asText());assertEquals(1,db.queryForObject("select count(*) from supplier_case where nc_id=?",Integer.class,n));
 }
}
