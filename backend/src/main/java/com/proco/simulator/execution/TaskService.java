package com.proco.simulator.execution;

import com.proco.simulator.audit.AuditLog;
import com.proco.simulator.workflow.CommandRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;

@Service
public class TaskService {
 private final JdbcTemplate db;private final ExecutionStore store;private final ExecutionService executions;private final CommandRunner runner;private final AuditLog audit;
 public TaskService(JdbcTemplate db,ExecutionStore store,ExecutionService executions,CommandRunner runner,AuditLog audit){this.db=db;this.store=store;this.executions=executions;this.runner=runner;this.audit=audit;}
 public Map<String,Object> execute(UUID id,String action,ExecutionCommand c,UUID key,String actor,String role){
  return runner.runExecution(id,true,key,actor,role,"TASK:"+id+":"+action+":"+c,()->{
   var initial=store.task(id);require(role.equals(initial.get("target_role")),403,"Tarea de otra área");
   require(Set.of("acknowledge","complete").contains(action),404,"Acción de tarea inexistente");
   UUID mix=(UUID)initial.get("mix_id"),execution=(UUID)initial.get("execution_id");executions.lockMix(mix);
   db.queryForList("select id from montage_execution where id=? for update",execution);
   db.queryForList("select id from containment_task where id=? for update",id);var task=store.task(id);
   require(((Number)task.get("version")).longValue()==c.version(),409,"Versión de tarea desactualizada");
   String from=(String)task.get("status"),to;
   if(action.equals("acknowledge")){
    require(from.equals("OPEN"),409,"Solo se acusa una tarea abierta");to="ACKNOWLEDGED";
    db.update("update containment_task set status='ACKNOWLEDGED',acknowledged_by=?,acknowledged_at=now(),ack_reason=?,ack_evidence=?,version=version+1 where id=?",actor,c.reason(),c.evidence(),id);
   }else{
    require(from.equals("ACKNOWLEDGED"),409,"Completar requiere acuse previo");
    require(actor.equals(task.get("acknowledged_by")),403,"Solo el usuario que acusó puede completar");to="CLOSED";
    db.update("update containment_task set status='CLOSED',closed_by=?,closed_at=now(),close_reason=?,close_evidence=?,version=version+1 where id=?",actor,c.reason(),c.evidence(),id);
   }
   audit.appendExecution(execution,id,(UUID)task.get("lot_id"),mix,(UUID)task.get("nc_id"),actor,role,"TASK_"+to,from,to,c.reason(),c.evidence(),key,c.toString());return store.task(id);
  },store::task,r->(UUID)r.get("id"));
 }
}
