package com.proco.simulator.supplier;
import com.proco.simulator.audit.AuditLog;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import jakarta.validation.Valid;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;
@RestController
@RequestMapping("/api")
public class SupplierController {
 private final DiscrepancyService discrepancies;private final SupplierCaseService cases;private final SupplierStore store;private final AuditLog audit;
 public SupplierController(DiscrepancyService discrepancies,SupplierCaseService cases,SupplierStore store,AuditLog audit){this.discrepancies=discrepancies;this.cases=cases;this.store=store;this.audit=audit;}
 @GetMapping("/receipt-discrepancies") public List<Map<String,Object>> discrepancies(@RequestParam(required=false) String orderCode,@RequestParam(required=false) String status,@RequestParam(defaultValue="100") int limit){return store.discrepancies(orderCode,status,limit);}
 @GetMapping("/receipt-discrepancies/{id}") public Map<String,Object> discrepancy(@PathVariable UUID id){return store.discrepancy(id);}
 @GetMapping("/receipt-discrepancies/{id}/timeline") public List<Map<String,Object>> discrepancyTimeline(@PathVariable UUID id){store.discrepancy(id);return audit.supplierTimeline(id,true);}
 @PostMapping("/receipt-discrepancies") public Map<String,Object> create(@Valid @RequestBody DiscrepancyCommand c,@RequestHeader("Idempotency-Key") UUID key,Authentication a){return discrepancies.execute(null,"create",c,key,a.getName(),role(a));}
 @PostMapping("/receipt-discrepancies/{id}/{action}") public Map<String,Object> discrepancy(@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody DiscrepancyCommand c,@RequestHeader("Idempotency-Key") UUID key,Authentication a){require(!action.equals("create"),404,"Acción inexistente");return discrepancies.execute(id,action,c,key,a.getName(),role(a));}
 @GetMapping("/supplier-cases") public List<Map<String,Object>> cases(@RequestParam(required=false) UUID lotId,@RequestParam(required=false) String status,@RequestParam(defaultValue="100") int limit){return store.supplierCases(lotId,status,limit);}
 @GetMapping("/supplier-cases/{id}") @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Map<String,Object> supplierCase(@PathVariable UUID id){return store.supplierCase(id);}
 @GetMapping("/supplier-cases/{id}/timeline") public List<Map<String,Object>> caseTimeline(@PathVariable UUID id){store.supplierCase(id);return audit.supplierTimeline(id,false);}
 @PostMapping("/supplier-cases") public Map<String,Object> adopt(@Valid @RequestBody SupplierCaseCommand c,@RequestHeader("Idempotency-Key") UUID key,Authentication a){return cases.execute(null,"adopt",c,key,a.getName(),role(a));}
 @PostMapping("/supplier-cases/{id}/{action}") public Map<String,Object> supplierCase(@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody SupplierCaseCommand c,@RequestHeader("Idempotency-Key") UUID key,Authentication a){require(!action.equals("adopt"),404,"Acción inexistente");return cases.execute(id,action,c,key,a.getName(),role(a));}
 private static String role(Authentication a){return a.getAuthorities().iterator().next().getAuthority().substring(5);}
}
