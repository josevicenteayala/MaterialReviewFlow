package com.proco.simulator;

import com.proco.simulator.monitoring.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MonitoringMigrationUpgradeTest {
 @Test void v6PreservesLegacyIncidentCompletionAndAllV5Rows(){
  String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://localhost:5432/proco_test"),user=System.getenv().getOrDefault("DB_USER","proco"),password=System.getenv().getOrDefault("DB_PASSWORD","proco-local");
  var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));String schema="monitoring_upgrade_"+UUID.randomUUID().toString().replace("-","");admin.execute("create schema "+schema);
  try{
   var source=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
   Flyway.configure().dataSource(source).schemas(schema).target("5").load().migrate();var db=new JdbcTemplate(source);
   db.update("insert into app_user values ('requester','old-hash','PRODUCTION_REQUESTER'),('approver','old-hash','PRODUCTION_APPROVER'),('quality','old-hash','QUALITY')");
   UUID a=UUID.randomUUID(),b=UUID.randomUUID(),mix=UUID.randomUUID(),e=UUID.randomUUID(),task=UUID.randomUUID(),nc=UUID.randomUUID(),key=UUID.randomUUID();
   for(UUID l:List.of(a,b))db.update("insert into lot(id,order_code,supplier_lot,production_date,quantity,location,quality_state,inventory_state,version) values (?,'OC-H-001',?,'2026-01-01',10,'SIM','RELEASED','AVAILABLE',5)",l,l.toString());
   db.update("insert into mix_request(id,montage_code,requester,reason,evidence,rule_code,rule_version,dyeing_required,valid_until) values (?,'LEGACY-S5','requester','Original','EV-OLD','MANUAL_BASIC','MIX-SIM-1',false,now()+interval '1 day')",mix);
   int ordinal=0;for(UUID l:List.of(a,b))db.update("insert into mix_request_lot values (?,?,?,1,5,'HILO-DEMO',?,'2026-01-01')",mix,l,ordinal++,l.toString());
   db.update("update mix_request set status='AUTHORIZED',sealed=true,approver='approver',decided_at=now(),decision_reason='Original',decision_evidence='EV-OLD',version=4 where id=?",mix);
   db.update("insert into montage_execution(id,mix_request_id,montage_code,status,started_by,start_reason,start_evidence) values (?,?,'LEGACY-S5','IN_PROGRESS','requester','Original start','EV-OLD')",e,mix);
   new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(tx->{
    db.update("update montage_execution set status='SUSPENDED',suspended_at=now(),suspension_reason='Original incident',version=1 where id=?",e);
    db.update("update mix_request set status='REVOKED',revoked_at=now(),revocation_reason='Original NC',version=5 where id=?",mix);
    db.update("update lot set inventory_state='BLOCKED',version=6 where id=?",a);
    db.update("insert into non_conformity(id,lot_id,origin,responsible,opened_by,reason,evidence) values (?,?,'POST_RELEASE','quality','requester','Original NC','EV-OLD')",nc,a);
    db.update("insert into inventory_hold(id,lot_id,nc_id,opened_by,reason,evidence,correlation_id) values (?,?,?,'requester','Original hold','EV-OLD',?)",UUID.randomUUID(),a,nc,key);
   });
   db.update("insert into containment_task(id,source_event_id,nc_id,lot_id,mix_id,execution_id,task_type,target_role,reason,evidence) values (?,?,?,?,?,?,'CONTAINMENT','QUALITY','Original task','EV-OLD')",task,key,nc,a,mix,e);
   db.update("update containment_task set status='ACKNOWLEDGED',acknowledged_by='quality',acknowledged_at=now(),ack_reason='Original ack',ack_evidence='EV-ACK',version=1 where id=?",task);
   db.update("update containment_task set status='CLOSED',closed_by='quality',closed_at=now(),close_reason='Original close',close_evidence='EV-CLOSE',version=2 where id=?",task);
   db.update("update montage_execution set status='COMPLETED',ended_at=now(),ended_by='approver',disposition='V5 incident disposition',end_evidence='EV-END',version=2 where id=?",e);
   db.update("insert into execution_event(id,execution_id,task_id,actor,role,event_type,reason,evidence,correlation_id,payload) values (?,?,?,'approver','PRODUCTION_APPROVER','COMPLETED','Original event','EV-END',?,'unaltered payload')",UUID.randomUUID(),e,task,key);
   db.update("insert into command_result(actor,command_key,fingerprint,execution_id) values ('approver',?,'V5 fingerprint',?)",key,e);
   var tables=db.queryForList("select tablename from pg_tables where schemaname=? and tablename<>'flyway_schema_history' order by tablename",String.class,schema);Map<String,List<String>> before=new HashMap<>();for(String t:tables)before.put(t,rows(db,t));
   Flyway.configure().dataSource(source).schemas(schema).target("6").load().migrate();for(String t:tables)assertEquals(before.get(t),rows(db,t),t);
   assertEquals(6,db.queryForObject("select max(version::integer) from flyway_schema_history where success",Integer.class));
   var historical=db.queryForMap("select * from containment_task where id=?",task);assertNull(historical.get("ola_rule_id"));assertNull(historical.get("ack_due_at"));assertEquals("NOT_CONFIGURED",OlaPolicy.project(historical,Instant.now()).get("ack_ola_status"));
   Instant now=Instant.now().plusSeconds(1);var filter=MonitoringFilter.of(now.minusSeconds(86400),now.plusSeconds(1),null,null,"LEGACY-S5",null,null,25,null,Clock.fixed(now,ZoneOffset.UTC));
   var counts=(Map<?,?>)new MonitoringStore(db).summary(filter).get("executions");assertEquals(1L,counts.get("LEGACY_INCIDENT_CLOSURE"));assertEquals(0L,counts.get("COMPLETED"));
   assertEquals("COMPLETED",db.queryForObject("select status from montage_execution where id=?",String.class,e));
  }finally{admin.execute("drop schema "+schema+" cascade");}
 }
 private List<String> rows(JdbcTemplate db,String table){String value="(to_jsonb(t)-'ola_rule_id'-'ack_due_at'-'close_due_at'-'ola_due_soon_minutes')::text";return db.queryForList("select "+value+" from "+table+" t order by "+value,String.class);}
}
