package com.proco.simulator.supplier;
import com.proco.simulator.audit.AuditLog;
import com.proco.simulator.workflow.CommandRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;
@Service
public class SupplierCaseService {
 private final JdbcTemplate db;private final SupplierStore store;private final CommandRunner runner;private final AuditLog audit;private final SupplierCaseRegistry registry;
 public SupplierCaseService(JdbcTemplate db,SupplierStore store,CommandRunner runner,AuditLog audit,SupplierCaseRegistry registry){this.db=db;this.store=store;this.runner=runner;this.audit=audit;this.registry=registry;}
 public Map<String,Object> execute(UUID id,String action,SupplierCaseCommand c,UUID key,String actor,String role){
  String payload="SUPPLIER_CASE:"+id+":"+action+":"+c;
  return runner.runSupplier(id,false,key,actor,role,payload,()->{
   require(role.equals("PROCUREMENT"),403,"Solo Abastecimiento gestiona el caso comercial");
   if(action.equals("adopt")){
    require(c.lotId()!=null&&c.ncId()!=null,422,"Indica lote y NC histórica");runner.lock(c.lotId(),c.version());
    db.queryForList("select id from containment_task where nc_id=? and task_type='REJECTION_COORDINATION' and target_role='PROCUREMENT' order by id for update",c.ncId());
    return store.supplierCase(registry.create(c.lotId(),c.ncId(),actor,role,c.reason(),c.evidence(),key,true));
   }
   require(Set.of("contact","response","disposition","close").contains(action),404,"Acción comercial inexistente");
   var initial=store.supplierCase(id);UUID lot=(UUID)initial.get("lot_id");db.queryForList("select id from lot where id=? for update",lot);
   db.queryForList("select t.id from containment_task t join supplier_case_task x on x.task_id=t.id where x.supplier_case_id=? order by t.id for update of t",id);
   db.queryForList("select id from supplier_case where id=? for update",id);var current=store.supplierCase(id);String from=(String)current.get("status"),next;
   require(((Number)current.get("version")).longValue()==c.version(),409,"Versión del caso comercial desactualizada");require(!from.equals("CLOSED_ADMINISTRATIVELY"),409,"Caso comercial cerrado");require(SupplierPolicy.text(c.communicationRef()),422,"Referencia de comunicación simulada obligatoria");
   switch(action){
    case "contact"->{require(from.equals("OPEN"),409,"Contacto exige caso abierto");next="SUPPLIER_CONTACT_RECORDED";db.update("update supplier_case set status=?,version=version+1,contact_by=?,contacted_at=now(),contact_ref=?,contact_reason=?,contact_evidence=? where id=?",next,actor,c.communicationRef(),c.reason(),c.evidence(),id);}
    case "response"->{require(from.equals("SUPPLIER_CONTACT_RECORDED"),409,"Registra contacto antes de respuesta");require(c.response()!=null&&Set.of("ACCEPTED","DISPUTED","NO_RESPONSE").contains(c.response()),422,"Respuesta simulada inválida");next="RESPONSE_RECORDED";db.update("update supplier_case set status=?,version=version+1,response_by=?,responded_at=now(),response_ref=?,response_outcome=?,response_reason=?,response_evidence=? where id=?",next,actor,c.communicationRef(),c.response(),c.reason(),c.evidence(),id);}
    case "disposition"->{require(from.equals("RESPONSE_RECORDED"),409,"Disposición exige respuesta registrada");require(c.direction()!=null&&Set.of("RETURN_REQUESTED","CREDIT_REQUESTED","OTHER").contains(c.direction()),422,"Orientación comercial inválida");require(SupplierPolicy.text(c.treatment()),422,"Documenta tratamiento comercial, especialmente disputas/sin respuesta");next="DISPOSITION_DOCUMENTED";db.update("update supplier_case set status=?,version=version+1,disposition_by=?,disposition_at=now(),disposition_ref=?,commercial_direction=?,treatment=?,disposition_reason=?,disposition_evidence=? where id=?",next,actor,c.communicationRef(),c.direction(),c.treatment(),c.reason(),c.evidence(),id);}
    case "close"->{require(from.equals("DISPOSITION_DOCUMENTED"),409,"Cierre exige disposición documentada");require(!actor.equals(current.get("disposition_by")),422,"Cierre requiere otro usuario de Abastecimiento distinto del autor de disposición");require(Boolean.FALSE.equals(c.physicalReturnConfirmed())&&Boolean.FALSE.equals(c.erpPosted()),422,"Declara explícitamente physicalReturnConfirmed=false y erpPosted=false");
     require(db.queryForObject("select count(*) from supplier_case_task x join containment_task t on t.id=x.task_id where x.supplier_case_id=? and t.status<>'CLOSED'",Integer.class,id)==0,422,"Completa las tareas Procurement vinculadas antes de cerrar el caso");
     next="CLOSED_ADMINISTRATIVELY";db.update("update supplier_case set status=?,version=version+1,closed_by=?,closed_at=now(),close_ref=?,close_reason=?,close_evidence=? where id=?",next,actor,c.communicationRef(),c.reason(),c.evidence(),id);}
    default->throw new com.proco.simulator.shared.RuleException(404,"Acción comercial inexistente");
   }
   audit.appendSupplier(null,id,lot,actor,role,"SUPPLIER_"+action.toUpperCase(),from,next,c.reason(),c.evidence(),key,payload);return store.supplierCase(id);
  },store::supplierCase,r->(UUID)r.get("id"));
 }
}
