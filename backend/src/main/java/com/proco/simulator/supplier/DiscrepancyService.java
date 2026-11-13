package com.proco.simulator.supplier;
import com.proco.simulator.audit.AuditLog;
import com.proco.simulator.reception.ReceiptService;
import com.proco.simulator.workflow.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.math.BigDecimal;
import java.time.*;
import static com.proco.simulator.shared.RuleException.require;
@Service
public class DiscrepancyService {
 private final JdbcTemplate db;private final SupplierStore store;private final CommandRunner runner;private final AuditLog audit;private final ReceiptService receipts;private final Clock clock;
 public DiscrepancyService(JdbcTemplate db,SupplierStore store,CommandRunner runner,AuditLog audit,ReceiptService receipts,Clock clock){this.db=db;this.store=store;this.runner=runner;this.audit=audit;this.receipts=receipts;this.clock=clock;}
 public Map<String,Object> execute(UUID id,String action,DiscrepancyCommand c,UUID key,String actor,String role){
  String fingerprint="DISCREPANCY:"+id+":"+action+":"+c;
  return runner.runSupplier(id,true,key,actor,role,fingerprint,()->{
   String required=switch(action){case "create","confirm-count","accept"->"WAREHOUSE";case "quality"->"QUALITY";case "review"->"PROCUREMENT";case "cancel"->"WAREHOUSE,PROCUREMENT";default->throw new com.proco.simulator.shared.RuleException(404,"Acción de diferencia inexistente");};
   require(Arrays.asList(required.split(",")).contains(role),403,"Rol no autorizado para esta acción de diferencia");
   if(action.equals("create"))return create(c,key,actor,role,fingerprint);
   var initial=store.discrepancy(id);String order=(String)initial.get("order_code");lockOrder(order);
   db.queryForList("select id from receipt_discrepancy where id=? for update",id);var d=store.discrepancy(id);String from=(String)d.get("status"),next=from;UUID lot=null;
   require(((Number)d.get("version")).longValue()==c.version(),409,"Versión de diferencia desactualizada");require(Set.of("OPEN","UNDER_REVIEW","AUTHORIZED").contains(from),409,"Diferencia ya resuelta");
   switch(action){
    case "confirm-count"->{require(from.equals("OPEN"),409,"El conteo ya está confirmado");next="UNDER_REVIEW";db.update("update receipt_discrepancy set status='UNDER_REVIEW',version=version+1,count_confirmed_by=?,count_confirmed_at=now(),count_reason=?,count_evidence=? where id=?",actor,c.reason(),c.evidence(),id);}
    case "quality"->{require(from.equals("UNDER_REVIEW")&&Boolean.TRUE.equals(d.get("technical_finding")),422,"Calidad participa solo con hallazgo técnico/documental y conteo confirmado");require(d.get("quality_by")==null,409,"El concepto ya existe; corrección requiere un nuevo expediente");require(c.outcome()!=null&&Set.of("FAVORABLE","UNFAVORABLE").contains(c.outcome()),422,"Concepto inválido");db.update("update receipt_discrepancy set version=version+1,quality_by=?,quality_at=now(),quality_outcome=?,quality_reason=?,quality_evidence=? where id=?",actor,c.outcome(),c.reason(),c.evidence(),id);}
    case "review"->{
     require(Set.of("UNDER_REVIEW","AUTHORIZED").contains(from),409,"Confirma el conteo antes de autorizar");
     require(!actor.equals(d.get("reported_by"))&&!actor.equals(d.get("count_confirmed_by"))&&!actor.equals(d.get("quality_by")),422,"Autorización comercial requiere identidad independiente");
     require(!Boolean.TRUE.equals(d.get("technical_finding"))||"FAVORABLE".equals(d.get("quality_outcome")),422,"Hallazgo exige concepto favorable de Calidad");
     SupplierPolicy.quantity(c.proposedAcceptedQuantity(),(BigDecimal)d.get("observed_quantity"),balance(order));
     require(c.proposedAcceptedQuantity().compareTo((BigDecimal)d.get("observed_quantity"))==0||SupplierPolicy.text(c.remainderPlan()),422,"Documenta el remanente no aceptado y su segregación simulada");
     next="AUTHORIZED";db.update("update receipt_discrepancy set status='AUTHORIZED',version=version+1,review_by=?,reviewed_at=now(),review_reason=?,review_evidence=?,proposed_accepted_quantity=?,remainder_plan=? where id=?",actor,c.reason(),c.evidence(),c.proposedAcceptedQuantity(),c.remainderPlan(),id);
    }
    case "accept"->{
     require(from.equals("AUTHORIZED"),409,"Aceptación exige autorización de Abastecimiento");require(!actor.equals(d.get("review_by")),422,"Almacén y autorización comercial deben ser independientes");
     BigDecimal accepted=(BigDecimal)d.get("proposed_accepted_quantity");SupplierPolicy.quantity(accepted,(BigDecimal)d.get("observed_quantity"),balance(order));next=SupplierPolicy.result(accepted,(BigDecimal)d.get("observed_quantity"));
     if(accepted.signum()>0){var command=new Command(0L,"Aceptación de diferencia "+id+": "+c.reason(),c.evidence(),order,(String)d.get("supplier_lot"),((java.sql.Date)d.get("production_date")).toLocalDate(),accepted,(String)d.get("location"),null,null,null);lot=receipts.create(command,key,actor,role).id();}
     db.update("update receipt_discrepancy set status=?,version=version+1,accepted_quantity=?,accepted_lot_id=?,resolved_by=?,resolved_at=now(),resolution_reason=?,resolution_evidence=? where id=?",next,accepted,lot,actor,c.reason(),c.evidence(),id);
    }
    case "cancel"->{require((role.equals("WAREHOUSE")&&from.equals("OPEN"))||(role.equals("PROCUREMENT")&&!from.equals("OPEN")),403,"Cancelación no autorizada en esta etapa");next="CANCELLED_WITH_REASON";db.update("update receipt_discrepancy set status='CANCELLED_WITH_REASON',version=version+1,accepted_quantity=0,resolved_by=?,resolved_at=now(),resolution_reason=?,resolution_evidence=? where id=?",actor,c.reason(),c.evidence(),id);}
   }
   audit.appendSupplier(id,null,lot,actor,role,"DISCREPANCY_"+action.toUpperCase().replace('-','_'),from,next,c.reason(),c.evidence(),key,fingerprint);return store.discrepancy(id);
  },store::discrepancy,r->(UUID)r.get("id"));
 }
 private void lockOrder(String order){require(order!=null&&!db.queryForList("select code from purchase_order where code=? for update",order).isEmpty(),422,"OC inexistente");}
 private BigDecimal balance(String order){return db.queryForObject("select p.expected_quantity-(select coalesce(sum(l.quantity),0) from lot l where l.order_code=p.code) from purchase_order p where p.code=?",BigDecimal.class,order);}
 private Map<String,Object> create(DiscrepancyCommand c,UUID key,String actor,String role,String payload){
  require(c.version()==0,409,"Una diferencia nueva requiere versión 0");require(SupplierPolicy.text(c.orderCode())&&SupplierPolicy.text(c.supplierLot())&&SupplierPolicy.text(c.location())&&SupplierPolicy.text(c.supplierDocumentRef())&&c.productionDate()!=null&&c.documentedQuantity()!=null&&c.observedQuantity()!=null&&c.technicalFinding()!=null,422,"Faltan datos de pre-recepción");require(!c.productionDate().isAfter(LocalDate.now(clock)),422,"Fecha de fabricación futura");lockOrder(c.orderCode());BigDecimal available=balance(c.orderCode());
  require(c.documentedQuantity().compareTo(c.observedQuantity())!=0||c.observedQuantity().compareTo(available)>0||c.technicalFinding(),422,"No hay diferencia, exceso ni hallazgo que justifique pre-recepción excepcional");
  UUID id=UUID.randomUUID();db.update("insert into receipt_discrepancy(id,order_code,supplier_lot,production_date,location,supplier_document_ref,documented_quantity,observed_quantity,balance_at_report,technical_finding,reported_by,reason,evidence) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",id,c.orderCode(),c.supplierLot(),c.productionDate(),c.location(),c.supplierDocumentRef(),c.documentedQuantity(),c.observedQuantity(),available,c.technicalFinding(),actor,c.reason(),c.evidence());
  audit.appendSupplier(id,null,null,actor,role,"DISCREPANCY_OPENED",null,"OPEN",c.reason(),c.evidence(),key,payload);return store.discrepancy(id);
 }
}
