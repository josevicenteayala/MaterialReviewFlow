package com.proco.simulator.execution;

import com.proco.simulator.audit.AuditLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Executes inside the original material transaction, after ordered mix locks. Never locks another lot. */
@Service
public class ExecutionContainment {
 private final JdbcTemplate db;private final AuditLog audit;
 public ExecutionContainment(JdbcTemplate db,AuditLog audit){this.db=db;this.audit=audit;}
 public void affect(UUID lot,UUID mix,UUID nc,String actor,String role,String reason,String evidence,UUID correlation,String cause){
  var executions=db.queryForList("select e.*,m.dyeing_required from montage_execution e join mix_request m on m.id=e.mix_request_id where m.id=? order by e.id for update of e",mix);
  for(var e:executions){
   String state=(String)e.get("status");if(state.equals("CANCELLED"))continue;
   UUID id=(UUID)e.get("id");boolean rejection=cause.equals("REJECTED");
   String type=rejection?"REJECTION_COORDINATION":state.equals("COMPLETED")?"RETROSPECTIVE_REVIEW":"CONTAINMENT";
   if(state.equals("IN_PROGRESS"))db.update("update montage_execution set status='SUSPENDED',suspended_at=now(),suspension_reason=?,version=version+1 where id=?",reason,id);
   List<String> roles=new ArrayList<>(rejection?List.of("PROCUREMENT"):List.of("WAREHOUSE","QUALITY","PRODUCTION_REQUESTER","GEO"));
   if(!rejection && Boolean.TRUE.equals(e.get("dyeing_required")))roles.add("DYEING");
   boolean created=false;
   for(String target:roles){
    UUID task=UUID.randomUUID();
    int inserted=db.update("insert into containment_task(id,source_event_id,nc_id,lot_id,mix_id,execution_id,task_type,target_role,reason,evidence) values (?,?,?,?,?,?,?,?,?,?) on conflict(source_event_id,execution_id,task_type,target_role) do nothing",task,correlation,nc,lot,mix,id,type,target,reason,evidence);
    if(inserted==1){created=true;audit.appendExecution(id,task,lot,mix,nc,actor,role,"TASK_CREATED",null,"OPEN",reason,evidence,correlation,"cause="+cause+"; target="+target+"; type="+type);}
   }
   if(created)audit.appendExecution(id,null,lot,mix,nc,actor,role,state.equals("IN_PROGRESS")?"SUSPENDED":type.equals("RETROSPECTIVE_REVIEW")?"RETROSPECTIVE_ALERT":"CONTAINMENT_REQUIRED",state,state.equals("IN_PROGRESS")?"SUSPENDED":state,reason,evidence,correlation,"cause="+cause);
  }
 }
}
