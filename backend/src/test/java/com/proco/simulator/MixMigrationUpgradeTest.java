package com.proco.simulator;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MixMigrationUpgradeTest {
 @Test void v4PreservesEveryV3ValueAndOldCommandTargets(){
  String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://localhost:5432/proco_test"),user=System.getenv().getOrDefault("DB_USER","proco"),password=System.getenv().getOrDefault("DB_PASSWORD","proco-local");
  var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));String schema="mix_upgrade_"+UUID.randomUUID().toString().replace("-","");admin.execute("create schema "+schema);
  try{
   var source=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
   Flyway.configure().dataSource(source).schemas(schema).target("3").load().migrate();var db=new JdbcTemplate(source);
   db.update("insert into app_user values ('reporter','old-hash','WAREHOUSE'),('owner','old-hash','QUALITY'),('reviewer','old-hash','LAB_APPROVER')");
   UUID lot=UUID.randomUUID(),nc=UUID.randomUUID(),key=UUID.randomUUID();
   db.update("insert into lot(id,order_code,supplier_lot,production_date,quantity,location,quality_state,inventory_state,version) values (?,'OC-H-001','LEGACY-S3','2026-01-01',10,'Q-OLD','RELEASED','BLOCKED',9)",lot);
   db.update("insert into non_conformity(id,lot_id,origin,responsible,opened_by,reason,evidence) values (?,?,'POST_RELEASE','owner','reporter','original NC','EV-OLD')",nc,lot);
   db.update("insert into nc_opinion(id,nc_id,author,outcome,reason,evidence,based_on_version) values (?,?,'reviewer','CONFORMING','original opinion','EV-OLD',9)",UUID.randomUUID(),nc);
   db.update("insert into inventory_hold(id,lot_id,nc_id,opened_by,reason,evidence,correlation_id) values (?,?,?,'reporter','original hold','EV-OLD',?)",UUID.randomUUID(),lot,nc,key);
   db.update("insert into audit_event(id,lot_id,actor,role,event_type,reason,evidence,rule_version,correlation_id,payload) values (?,?,'reporter','WAREHOUSE','LEGACY_RELEASE','original','EV-OLD','SIM-1',?,'original command')",UUID.randomUUID(),lot,key);
   db.update("insert into command_result values ('reporter',?,'unchanged fingerprint',?)",key,lot);
   var tables=List.of("app_user","specification","purchase_order","lot","non_conformity","nc_opinion","inventory_hold","audit_event","command_result");Map<String,List<String>> before=new HashMap<>();for(String table:tables)before.put(table,rows(db,table));
   Flyway.configure().dataSource(source).schemas(schema).load().migrate();
   for(String table:tables)assertEquals(before.get(table),rows(db,table),table);
   assertEquals(0,db.queryForObject("select count(*) from command_result where mix_id is not null",Integer.class));
   assertEquals(0,db.queryForObject("select count(*) from mix_request",Integer.class));assertEquals(0,db.queryForObject("select count(*) from mix_event",Integer.class));
   assertEquals(4,db.queryForObject("select max(version::integer) from flyway_schema_history where success",Integer.class));
  }finally{admin.execute("drop schema "+schema+" cascade");}
 }
 private List<String> rows(JdbcTemplate db,String table){return db.queryForList("select (to_jsonb(t)-'mix_id')::text from "+table+" t order by (to_jsonb(t)-'mix_id')::text",String.class);}
}
