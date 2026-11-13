package com.proco.simulator.supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;
@Repository
public class SupplierStore {
 private final JdbcTemplate db;
 public SupplierStore(JdbcTemplate db){this.db=db;}
 private static final String DISCREPANCY="""
  select d.*,p.supplier,p.reference,p.expected_quantity,
   p.expected_quantity-(select coalesce(sum(l.quantity),0) from lot l where l.order_code=d.order_code) as current_balance,
   greatest(d.documented_quantity-d.observed_quantity,0) as documented_shortage,
   greatest(d.observed_quantity-d.documented_quantity,0) as documented_surplus,
   greatest(d.observed_quantity-d.balance_at_report,0) as excess_over_balance_at_report,
   d.observed_quantity-coalesce(d.accepted_quantity,0) as unaccepted_or_pending_quantity
  from receipt_discrepancy d join purchase_order p on p.code=d.order_code
  """;
 public Map<String,Object> discrepancy(UUID id){var r=db.queryForList(DISCREPANCY+" where d.id=?",id);require(!r.isEmpty(),404,"Diferencia inexistente");return r.getFirst();}
 public List<Map<String,Object>> discrepancies(String order,String status,int limit){checkLimit(limit);var q=new StringBuilder(DISCREPANCY+" where true");List<Object>a=new ArrayList<>();if(order!=null){q.append(" and d.order_code=?");a.add(order);}if(status!=null){q.append(" and d.status=?");a.add(status);}q.append(" order by d.reported_at desc,d.id desc limit ?");a.add(limit);return db.queryForList(q.toString(),a.toArray());}
 public Map<String,Object> supplierCase(UUID id){var rows=db.queryForList("select c.*,p.supplier,p.reference,l.supplier_lot,l.quality_state,l.inventory_state from supplier_case c join lot l on l.id=c.lot_id join purchase_order p on p.code=c.order_code where c.id=?",id);require(!rows.isEmpty(),404,"Caso comercial inexistente");var result=new LinkedHashMap<>(rows.getFirst());result.put("tasks",db.queryForList("select t.* from containment_task t join supplier_case_task x on x.task_id=t.id where x.supplier_case_id=? order by t.id",id));return result;}
 public List<Map<String,Object>> supplierCases(UUID lot,String status,int limit){checkLimit(limit);var q=new StringBuilder("select c.*,p.supplier,l.supplier_lot,(select count(*) from supplier_case_task x join containment_task t on t.id=x.task_id where x.supplier_case_id=c.id and t.status<>'CLOSED') as pending_tasks from supplier_case c join lot l on l.id=c.lot_id join purchase_order p on p.code=c.order_code where true");List<Object>a=new ArrayList<>();if(lot!=null){q.append(" and c.lot_id=?");a.add(lot);}if(status!=null){q.append(" and c.status=?");a.add(status);}q.append(" order by c.opened_at desc,c.id desc limit ?");a.add(limit);return db.queryForList(q.toString(),a.toArray());}
 private static void checkLimit(int limit){require(limit>=1&&limit<=100,400,"Límite debe estar entre 1 y 100");}
}
