package com.proco.simulator.execution;

import com.proco.simulator.audit.AuditLog;
import com.proco.simulator.mixing.MixStore;
import com.proco.simulator.workflow.CommandRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;

@Service
public class ExecutionService {
 private final JdbcTemplate db; private final MixStore mixes; private final ExecutionStore store;
 private final CommandRunner runner; private final AuditLog audit;
 public ExecutionService(JdbcTemplate db,MixStore mixes,ExecutionStore store,CommandRunner runner,AuditLog audit){this.db=db;this.mixes=mixes;this.store=store;this.runner=runner;this.audit=audit;}
 public void lockMix(UUID mix){
  var ids=mixes.lotIds(mix);require(!ids.isEmpty(),404,"Solicitud de mezcla inexistente");
  for(UUID lot:ids)db.queryForList("select id from lot where id=? for update",lot);
  db.queryForList("select id from mix_request where id=? for update",mix);
 }
 public Map<String,Object> start(UUID mixId,ExecutionCommand c,UUID key,String actor,String role){
  return runner.runExecution(null,false,key,actor,role,"EXECUTION:START:"+mixId+":"+c,()->{
   require(role.equals("PRODUCTION_REQUESTER"),403,"Solo Tejeduría puede iniciar ejecución");
   lockMix(mixId);var mix=mixes.get(mixId);
   require(mix.version()==c.version(),409,"Versión de mezcla desactualizada");
   require(mix.montageCode().equals(c.montageCode()),422,"Montaje no coincide con autorización");
   require(mix.usable(),422,"Autorización no utilizable: "+mix.status()+"; "+String.join("; ",mix.blockers()));
   require(db.queryForObject("select count(*) from montage_execution where mix_request_id=?",Integer.class,mixId)==0,409,"La autorización ya tiene ejecución");
   UUID id=UUID.randomUUID();
   db.update("insert into montage_execution(id,mix_request_id,montage_code,status,started_by,start_reason,start_evidence) values (?,?,?,'IN_PROGRESS',?,?,?)",id,mixId,mix.montageCode(),actor,c.reason(),c.evidence());
   audit.appendExecution(id,null,null,mixId,null,actor,role,"STARTED",null,"IN_PROGRESS",c.reason(),c.evidence(),key,c.toString());
   return store.get(id);
  },store::get,r->(UUID)r.get("id"));
 }
 public Map<String,Object> finish(UUID id,String action,ExecutionCommand c,UUID key,String actor,String role){
  return runner.runExecution(id,false,key,actor,role,"EXECUTION:"+id+":"+action+":"+c,()->{
   require(role.equals("PRODUCTION_APPROVER"),403,"Solo Producción decide cierre o cancelación");
   require(Set.of("complete","cancel").contains(action),404,"Acción de ejecución inexistente");
   var initial=store.get(id);UUID mix=(UUID)initial.get("mix_request_id");lockMix(mix);
   db.queryForList("select id from montage_execution where id=? for update",id);var e=store.get(id);
   require(((Number)e.get("version")).longValue()==c.version(),409,"Versión de ejecución desactualizada");
   String state=(String)e.get("status");require(Set.of("IN_PROGRESS","SUSPENDED").contains(state),409,"Ejecución terminal");
   require(!actor.equals(e.get("started_by")),422,"El decisor debe ser distinto del iniciador");
   db.queryForList("select id from containment_task where execution_id=? order by id for update",id);
   require(db.queryForObject("select count(*) from containment_task where execution_id=? and status<>'CLOSED'",Integer.class,id)==0,422,"Completa todas las tareas antes de decidir disposición");
   require(db.queryForObject("select count(*) from containment_task where execution_id=? and target_role='QUALITY' and (closed_by=? or closed_by=?)",Integer.class,id,actor,e.get("started_by"))==0,422,"Revisión de Calidad debe ser independiente");
   if(action.equals("complete") && state.equals("IN_PROGRESS"))require(mixes.get(mix).usable(),422,"Revisión requerida: autorización no vigente/utilizable; no se permite completar. Documenta cancelación o intervención");
   String next=action.equals("complete")?"COMPLETED":"CANCELLED";
   db.update("update montage_execution set status=?,ended_at=now(),ended_by=?,disposition=?,end_evidence=?,version=version+1 where id=?",next,actor,c.reason(),c.evidence(),id);
   audit.appendExecution(id,null,null,mix,null,actor,role,next,state,next,c.reason(),c.evidence(),key,c.toString());return store.get(id);
  },store::get,r->(UUID)r.get("id"));
 }
}
