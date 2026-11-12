package com.proco.simulator.workflow;
import com.proco.simulator.shared.RuleException;
import com.proco.simulator.reception.*;
import com.proco.simulator.audit.AuditLog;
import com.proco.simulator.quality.InspectionPolicy;
import com.proco.simulator.laboratory.LaboratoryPolicy;
import com.proco.simulator.inventory.InventoryPolicy;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.proco.simulator.inventory.HoldStore;
import java.util.*;
import java.time.LocalDate;
import static com.proco.simulator.shared.RuleException.require;
@Service
public class WorkflowService {
 private final JdbcTemplate db; private final LotStore lots; private final AuditLog audit; private final CommandRunner runner; private final HoldStore holds;
 public WorkflowService(JdbcTemplate db,LotStore lots,AuditLog audit,CommandRunner runner,HoldStore holds){this.db=db;this.lots=lots;this.audit=audit;this.runner=runner;this.holds=holds;}
 public Lot execute(UUID id,String action,Command c,UUID key,String actor,String role){
  String fingerprint=id+":"+action+":"+c;
  return runner.run(id,key,actor,role,fingerprint,()->{
   String expected=switch(action){case "create","available"->"WAREHOUSE";case "inspect"->"QUALITY";case "sample"->"QUALITY,WAREHOUSE";case "test"->"LAB_TECH";case "release"->"LAB_APPROVER";default->throw new RuleException(404,"Acción inexistente");};
   require(Arrays.asList(expected.split(",")).contains(role),403,"El rol no está autorizado para esta acción");
   if(action.equals("create"))return create(c,key,actor,role,fingerprint);
   Lot l=runner.lock(id,c.version());
   require(!l.qualityState().equals("REJECTED"),409,"El rechazo es definitivo; el lote no admite más transiciones");
   switch(action){
    case "inspect"->{
     InspectionPolicy.inspectable(l);require(c.conforms()!=null,422,"Indica resultado documental");
     if(c.conforms()) db.update("update lot set quality_state='UNDER_TEST',cycle=cycle+1,sample_code=null,result_value=null,tested_by=null where id=?",id);
     else { db.update("update lot set quality_state='HELD' where id=?",id); holds.place(id,null,actor,c.reason(),c.evidence(),key); }
    }
    case "sample"->{require(l.qualityState().equals("UNDER_TEST"),409,"La muestra requiere inspección conforme");require(l.sampleCode()==null,409,"Ya existe muestra para este ciclo");require(c.sampleCode()!=null&&!c.sampleCode().isBlank(),422,"Código de muestra requerido");db.update("update lot set sample_code=? where id=?",c.sampleCode(),id);}
    case "test"->{require(l.qualityState().equals("UNDER_TEST"),409,"Lote fuera de pruebas");require(l.sampleCode()!=null,422,"Falta muestra trazable");require(l.resultValue()==null,409,"El ensayo ya fue registrado");require(c.resultValue()!=null,422,"Resultado requerido");boolean pass=LaboratoryPolicy.passes(l,c.resultValue());db.update("update lot set result_value=?,tested_by=?,quality_state=? where id=?",c.resultValue(),actor,pass?"UNDER_TEST":"HELD",id);if(!pass)holds.place(id,null,actor,c.reason(),c.evidence(),key);}
    case "release"->{LaboratoryPolicy.releasable(l,actor);db.update("update lot set quality_state='RELEASED' where id=?",id);}
    case "available"->{InventoryPolicy.available(l);db.update("update lot set inventory_state='AVAILABLE' where id=?",id);}
    default->throw new RuleException(404,"Acción inexistente");
   }
   Lot result=runner.advance(id,c.version());audit.append(id,actor,role,action.toUpperCase(),state(l),state(result),c.reason(),c.evidence(),key,c.toString());return result;
  });
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
  Lot result=lots.get(id);audit.append(id,actor,role,"RECEIVED",null,state(result),c.reason(),c.evidence(),key,c.toString());return result;
 }
 private String state(Lot l){return l.qualityState()+" / "+l.inventoryState();}
}
