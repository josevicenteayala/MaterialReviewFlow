package com.proco.simulator;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionMigrationUpgradeTest {
 @Test void v5PreservesEveryV4ValueIncludingAuthorizedMixAndIdempotency(){
  String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://localhost:5432/proco_test"),user=System.getenv().getOrDefault("DB_USER","proco"),password=System.getenv().getOrDefault("DB_PASSWORD","proco-local");
  var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));String schema="execution_upgrade_"+UUID.randomUUID().toString().replace("-","");admin.execute("create schema "+schema);
  try{
   var source=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
   Flyway.configure().dataSource(source).schemas(schema).target("4").load().migrate();var db=new JdbcTemplate(source);
   db.update("insert into app_user values ('requester','old-hash','PRODUCTION_REQUESTER'),('approver','old-hash','PRODUCTION_APPROVER')");
   UUID a=UUID.randomUUID(),b=UUID.randomUUID(),mix=UUID.randomUUID(),key=UUID.randomUUID();
   for(UUID lot:List.of(a,b))db.update("insert into lot(id,order_code,supplier_lot,production_date,quantity,location,quality_state,inventory_state,version) values (?,'OC-H-001',?,'2026-01-01',10,'SIM','RELEASED','AVAILABLE',5)",lot,lot.toString());
   db.update("insert into mix_request(id,montage_code,requester,reason,evidence,rule_code,rule_version,dyeing_required,valid_until) values (?,'LEGACY-M','requester','Original','EV-OLD','MANUAL_BASIC','MIX-SIM-1',false,now()+interval '1 day')",mix);
   int ordinal=0;for(UUID lot:List.of(a,b))db.update("insert into mix_request_lot values (?,?,?,1,5,'HILO-DEMO',?,'2026-01-01')",mix,lot,ordinal++,lot.toString());
   db.update("update mix_request set status='AUTHORIZED',sealed=true,approver='approver',decided_at=now(),decision_reason='Original decision',decision_evidence='EV-OLD',version=4 where id=?",mix);
   db.update("insert into mix_event(id,mix_id,actor,role,event_type,reason,evidence,correlation_id,rule_version,payload) values (?,?,'approver','PRODUCTION_APPROVER','AUTHORIZED','Original','EV-OLD',?,'SIM-3','original')",UUID.randomUUID(),mix,key);
   db.update("insert into command_result(actor,command_key,fingerprint,mix_id) values ('approver',?,'original fingerprint',?)",key,mix);
   var tables=db.queryForList("select tablename from pg_tables where schemaname=? and tablename<>'flyway_schema_history' order by tablename",String.class,schema);
   Map<String,List<String>> before=new HashMap<>();for(String t:tables)before.put(t,rows(db,t));
   Flyway.configure().dataSource(source).schemas(schema).target("5").load().migrate();
   for(String t:tables)assertEquals(before.get(t),rows(db,t),t);
   assertEquals(5,db.queryForObject("select max(version::integer) from flyway_schema_history where success",Integer.class));
   assertEquals(0,db.queryForObject("select count(*) from montage_execution",Integer.class));
   assertEquals(0,db.queryForObject("select count(*) from command_result where execution_id is not null or task_id is not null",Integer.class));
  }finally{admin.execute("drop schema "+schema+" cascade");}
 }
 private List<String> rows(JdbcTemplate db,String table){return db.queryForList("select (to_jsonb(t)-'execution_id'-'task_id')::text from "+table+" t order by (to_jsonb(t)-'execution_id'-'task_id')::text",String.class);}
}
