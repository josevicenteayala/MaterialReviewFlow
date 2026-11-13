package com.proco.simulator.supplier;
import com.proco.simulator.audit.AuditLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;
/** Invoked inside material rejection, or explicit legacy adoption; never opens a second transaction. */
@Service
public class SupplierCaseRegistry {
 private final JdbcTemplate db;private final AuditLog audit;
 public SupplierCaseRegistry(JdbcTemplate db,AuditLog audit){this.db=db;this.audit=audit;}
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID create(UUID lot,UUID nc,String actor,String role,String reason,String evidence,UUID key,boolean legacy){
  var rows=db.queryForList("select n.*,l.order_code,l.quality_state,l.inventory_state from non_conformity n join lot l on l.id=n.lot_id where n.id=? and l.id=?",nc,lot);require(!rows.isEmpty(),404,"NC/lote inexistente");var n=rows.getFirst();
  require("REJECTED".equals(n.get("status"))&&"RETURN_TO_SUPPLIER".equals(n.get("disposition"))&&"REJECTED".equals(n.get("quality_state"))&&"BLOCKED".equals(n.get("inventory_state")),422,"Caso comercial exige rechazo técnico RETURN_TO_SUPPLIER");
  require(db.queryForObject("select count(*) from supplier_case where nc_id=?",Integer.class,nc)==0,409,"La NC ya tiene caso comercial");UUID id=UUID.randomUUID();
  db.update("insert into supplier_case(id,nc_id,lot_id,order_code,opened_by,reason,evidence) values (?,?,?,?,?,?,?)",id,nc,lot,n.get("order_code"),actor,reason,evidence);
  // Reuse every existing task for this rejection; no synthetic execution or duplicate task.
  db.update("insert into supplier_case_task(supplier_case_id,task_id) select ?,id from containment_task where nc_id=? and task_type='REJECTION_COORDINATION' and target_role='PROCUREMENT'",id,nc);
  audit.appendSupplier(null,id,lot,actor,role,legacy?"LEGACY_REJECTION_ADOPTED":"SUPPLIER_CASE_OPENED",null,"OPEN",reason,evidence,key,"ncId="+nc+"; physicalReturnConfirmed=false; erpPosted=false");return id;
 }
}
