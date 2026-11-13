package com.proco.simulator.reception;
import com.proco.simulator.workflow.Command;
import com.proco.simulator.audit.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.time.LocalDate;
import static com.proco.simulator.shared.RuleException.require;
/** Shared receipt creation: caller owns CommandRunner transaction. */
@Service
public class ReceiptService {
 private final JdbcTemplate db;private final LotStore lots;private final AuditLog audit;
 public ReceiptService(JdbcTemplate db,LotStore lots,AuditLog audit){this.db=db;this.lots=lots;this.audit=audit;}
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public Lot create(Command c,UUID key,String actor,String role){
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
