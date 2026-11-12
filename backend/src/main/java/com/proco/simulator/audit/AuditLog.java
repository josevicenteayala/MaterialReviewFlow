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
  append(existing,actor,role,"COMMAND_DENIED",null,null,reason,"Intento rechazado",correlation,payload);
 }
 public List<Map<String,Object>> timeline(UUID lot){return db.queryForList("select * from audit_event where lot_id=? order by occurred_at,id",lot);}
}
