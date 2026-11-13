package com.proco.simulator.audit;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import java.util.*;
@Service
public class AuditLog {
 private final JdbcTemplate db;
 public AuditLog(JdbcTemplate db){this.db=db;}
 public void append(UUID lot,String actor,String role,String event,String from,String to,String reason,String evidence,UUID correlation,String payload){
  append(lot,actor,role,event,from,to,reason,evidence,correlation,payload,"SIM-1");
 }
 public void append(UUID lot,String actor,String role,String event,String from,String to,String reason,String evidence,UUID correlation,String payload,String ruleVersion){
  db.update("insert into audit_event(id,lot_id,actor,role,event_type,from_state,to_state,reason,evidence,rule_version,correlation_id,payload) values (?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),lot,actor,role,event,from,to,reason,evidence,ruleVersion,correlation,payload);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void denied(UUID lot,String actor,String role,String reason,UUID correlation,String payload){
  UUID existing=lot!=null && Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from lot where id=?)",Boolean.class,lot))?lot:null;
  append(existing,actor,role,"COMMAND_DENIED",null,null,reason,"Intento rechazado",correlation,payload,payload.startsWith("NC:")?"SIM-2":"SIM-1");
 }
 public List<Map<String,Object>> timeline(UUID lot){return db.queryForList("select * from audit_event where lot_id=? order by occurred_at,id",lot);}
 public void appendMix(UUID mix,String actor,String role,String event,String from,String to,String reason,String evidence,UUID correlation,String payload){
  db.update("insert into mix_event(id,mix_id,actor,role,event_type,from_state,to_state,reason,evidence,correlation_id,rule_version,payload) values (?,?,?,?,?,?,?,?,?,?,'SIM-3',?)",
    UUID.randomUUID(),mix,actor,role,event,from,to,reason,evidence,correlation,payload);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void deniedMix(UUID mix,String actor,String role,String reason,UUID correlation,String payload){
  UUID existing=mix!=null && Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from mix_request where id=?)",Boolean.class,mix))?mix:null;
  appendMix(existing,actor,role,"COMMAND_DENIED",null,null,reason,"Intento rechazado",correlation,payload);
 }
 public List<Map<String,Object>> mixTimeline(UUID mix){return db.queryForList("select * from mix_event where mix_id=? order by occurred_at,id",mix);}


 public void appendExecution(UUID execution,UUID task,UUID lot,UUID mix,UUID nc,String actor,String role,String event,String from,String to,String reason,String evidence,UUID correlation,String payload){
  db.update("insert into execution_event(id,execution_id,task_id,lot_id,mix_id,nc_id,actor,role,event_type,from_state,to_state,reason,evidence,correlation_id,payload) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
   UUID.randomUUID(),execution,task,lot,mix,nc,actor,role,event,from,to,reason,evidence,correlation,payload);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void deniedExecution(UUID id,boolean task,String actor,String role,String reason,UUID correlation,String payload){
  UUID execution=null,taskId=null;
  if(id!=null && task){var rows=db.queryForList("select execution_id from containment_task where id=?",id);if(!rows.isEmpty()){execution=(UUID)rows.getFirst().get("execution_id");taskId=id;}}
  else if(id!=null && Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from montage_execution where id=?)",Boolean.class,id)))execution=id;
  appendExecution(execution,taskId,null,null,null,actor,role,reason.startsWith("Revisión requerida")?"REVIEW_REQUIRED":"COMMAND_DENIED",null,null,reason,"Intento rechazado",correlation,payload);
 }
 public List<Map<String,Object>> executionTimeline(UUID id){return db.queryForList("select * from execution_event where execution_id=? order by occurred_at,id",id);}
}
