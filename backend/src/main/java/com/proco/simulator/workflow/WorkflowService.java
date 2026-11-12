package com.proco.simulator.workflow;
import com.proco.simulator.shared.RuleException;
import com.proco.simulator.reception.*;
import com.proco.simulator.audit.AuditLog;
import com.proco.simulator.quality.InspectionPolicy;
import com.proco.simulator.laboratory.LaboratoryPolicy;
import com.proco.simulator.inventory.InventoryPolicy;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.time.LocalDate;
import static com.proco.simulator.shared.RuleException.require;
@Service
public class WorkflowService {
 private final JdbcTemplate db; private final LotStore lots; private final AuditLog audit; private final TransactionTemplate tx;
 public WorkflowService(JdbcTemplate db,LotStore lots,AuditLog audit,org.springframework.transaction.PlatformTransactionManager tm){this.db=db;this.lots=lots;this.audit=audit;this.tx=new TransactionTemplate(tm);}
 public Lot execute(UUID id,String action,Command c,UUID key,String actor,String role){
  String fingerprint=id+":"+action+":"+c;
  try{return tx.execute(status->{
   // Serialize duplicate commands by actor and key, even when no lot exists yet.
   db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",actor+key);
   var replay=db.queryForList("select fingerprint,lot_id from command_result where actor=? and command_key=?",actor,key);
   if(!replay.isEmpty()){require(fingerprint.equals(replay.getFirst().get("fingerprint")),409,"Clave de idempotencia reutilizada con otro comando");return lots.get((UUID)replay.getFirst().get("lot_id"));}
   String expected=switch(action){case "create","available"->"WAREHOUSE";case "inspect"->"QUALITY";case "sample"->"QUALITY,WAREHOUSE";case "test"->"LAB_TECH";case "release"->"LAB_APPROVER";default->throw new RuleException(404,"Acción inexistente");};
   require(Arrays.asList(expected.split(",")).contains(role),403,"El rol no está autorizado para esta acción");
   if(action.equals("create"))return create(c,key,actor,role,fingerprint);
   require(id!=null,404,"Lote inexistente");
   require(!db.queryForList("select id from lot where id=? for update",id).isEmpty(),404,"Lote inexistente");
   Lot l=lots.get(id);require(l.version()==c.version(),409,"Versión desactualizada; actualiza el expediente");
   switch(action){
    case "inspect"->{
     InspectionPolicy.inspectable(l);require(c.conforms()!=null,422,"Indica resultado documental");
     if(c.conforms()) db.update("update lot set quality_state='UNDER_TEST',inventory_state='QUARANTINE',cycle=cycle+1,sample_code=null,result_value=null,tested_by=null where id=?",id);
     else db.update("update lot set quality_state='HELD',inventory_state='BLOCKED' where id=?",id);
    }
    case "sample"->{require(l.qualityState().equals("UNDER_TEST"),409,"La muestra requiere inspección conforme");require(l.sampleCode()==null,409,"Ya existe muestra para este ciclo");require(c.sampleCode()!=null&&!c.sampleCode().isBlank(),422,"Código de muestra requerido");db.update("update lot set sample_code=? where id=?",c.sampleCode(),id);}
    case "test"->{require(l.qualityState().equals("UNDER_TEST"),409,"Lote fuera de pruebas");require(l.sampleCode()!=null,422,"Falta muestra trazable");require(l.resultValue()==null,409,"El ensayo ya fue registrado");require(c.resultValue()!=null,422,"Resultado requerido");boolean pass=LaboratoryPolicy.passes(l,c.resultValue());db.update("update lot set result_value=?,tested_by=?,quality_state=?,inventory_state=? where id=?",c.resultValue(),actor,pass?"UNDER_TEST":"HELD",pass?"QUARANTINE":"BLOCKED",id);}
    case "release"->{LaboratoryPolicy.releasable(l,actor);db.update("update lot set quality_state='RELEASED' where id=?",id);}
    case "available"->{InventoryPolicy.available(l);db.update("update lot set inventory_state='AVAILABLE' where id=?",id);}
    default->throw new RuleException(404,"Acción inexistente");
   }
   db.update("update lot set version=version+1 where id=? and version=?",id,c.version());
   Lot result=lots.get(id);audit.append(id,actor,role,action.toUpperCase(),state(l),state(result),c.reason(),c.evidence(),key,c.toString());remember(actor,key,fingerprint,id);return result;
  });}catch(RuleException ex){audit.denied(id,actor,role,ex.getMessage(),key,fingerprint);throw ex;}
 }
 private Lot create(Command c,UUID key,String actor,String role,String fingerprint){
  require(c.version()==0,409,"Una recepción nueva requiere versión 0");
  require(c.orderCode()!=null && c.supplierLot()!=null&&!c.supplierLot().isBlank()&&c.productionDate()!=null&&c.quantity()!=null&&c.location()!=null&&!c.location().isBlank(),422,"Faltan OC, lote, fecha, cantidad o ubicación");
  require(!c.productionDate().isAfter(LocalDate.now(java.time.ZoneOffset.UTC)),422,"La fecha de fabricación no puede ser futura");
  var orders=db.queryForList("select * from purchase_order where code=? for update",c.orderCode());require(!orders.isEmpty(),422,"OC inexistente");
  var received=db.queryForObject("select coalesce(sum(quantity),0) from lot where order_code=?",java.math.BigDecimal.class,c.orderCode());
  require(received.add(c.quantity()).compareTo((java.math.BigDecimal)orders.getFirst().get("expected_quantity"))<=0,422,"La recepción supera el saldo de la OC");
  require(db.queryForObject("select count(*) from lot where order_code=? and supplier_lot=?",Integer.class,c.orderCode(),c.supplierLot())==0,409,"Lote de proveedor duplicado en esta OC");
  UUID id=UUID.randomUUID();db.update("insert into lot(id,order_code,supplier_lot,production_date,quantity,location,quality_state,inventory_state) values (?,?,?,?,?,?,'UNDER_VERIFICATION','QUARANTINE')",id,c.orderCode(),c.supplierLot(),c.productionDate(),c.quantity(),c.location());
  Lot result=lots.get(id);audit.append(id,actor,role,"RECEIVED",null,state(result),c.reason(),c.evidence(),key,c.toString());remember(actor,key,fingerprint,id);return result;
 }
 private void remember(String actor,UUID key,String fp,UUID id){db.update("insert into command_result values (?,?,?,?)",actor,key,fp,id);}
 private String state(Lot l){return l.qualityState()+" / "+l.inventoryState();}
}
