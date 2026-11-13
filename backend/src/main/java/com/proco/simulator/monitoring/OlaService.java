package com.proco.simulator.monitoring;
import com.proco.simulator.audit.AuditLog;
import com.proco.simulator.workflow.CommandRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;
@Service
public class OlaService {
 public static final Set<String> AREAS=Set.of("WAREHOUSE","QUALITY","PRODUCTION_REQUESTER","GEO","DYEING","PROCUREMENT");
 public static final Set<String> TYPES=Set.of("CONTAINMENT","RETROSPECTIVE_REVIEW","REJECTION_COORDINATION");
 private final JdbcTemplate db;private final CommandRunner runner;private final AuditLog audit;
 public OlaService(JdbcTemplate db,CommandRunner runner,AuditLog audit){this.db=db;this.runner=runner;this.audit=audit;}
 public List<Map<String,Object>> all(){return db.queryForList("select distinct on(task_type,target_role) * from ola_rule order by task_type,target_role,version desc");}
 public Map<String,Object> get(UUID id){var rows=db.queryForList("select * from ola_rule where id=?",id);require(!rows.isEmpty(),404,"Regla OLA inexistente");return rows.getFirst();}
 public Map<String,Object> create(OlaCommand c,UUID key,String actor,String role){
  return runner.runOla(key,actor,role,"OLA:"+c,()->{
   require(role.equals("GEO"),403,"Solo GEO configura nuevas versiones OLA DEMO");
   require(AREAS.contains(c.targetRole()) && TYPES.contains(c.taskType()),422,"Área o tipo de tarea inválido");
   db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,1))","OLA:"+c.taskType()+":"+c.targetRole());
   Long current=db.queryForObject("select coalesce(max(version),0) from ola_rule where task_type=? and target_role=?",Long.class,c.taskType(),c.targetRole());
   require(current==c.version().longValue(),409,"Versión OLA desactualizada; consulta la última del área/tipo");
   UUID id=UUID.randomUUID();db.update("insert into ola_rule(id,task_type,target_role,version,ack_minutes,close_minutes,due_soon_minutes,reason,evidence,created_by) values (?,?,?,?,?,?,?,?,?,?)",id,c.taskType(),c.targetRole(),current+1,c.ackMinutes(),c.closeMinutes(),c.dueSoonMinutes(),c.reason(),c.evidence(),actor);
   audit.appendOla(id,actor,role,c.reason(),c.evidence(),key,c.toString());return get(id);
  },this::get,r->(UUID)r.get("id"));
 }
}
