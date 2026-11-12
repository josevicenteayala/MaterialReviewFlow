package com.proco.simulator.workflow;
import com.proco.simulator.reception.*;
import com.proco.simulator.audit.AuditLog;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.validation.Valid;
import java.util.*;
@RestController
@RequestMapping("/api")
public class WorkflowController {
 private final WorkflowService service;private final LotStore lots;private final AuditLog audit;private final JdbcTemplate db;
 public WorkflowController(WorkflowService service,LotStore lots,AuditLog audit,JdbcTemplate db){this.service=service;this.lots=lots;this.audit=audit;this.db=db;}
 @GetMapping("/me") Map<String,String> me(Authentication a){return Map.of("username",a.getName(),"role",role(a));}
 @GetMapping("/catalog") List<Map<String,Object>> catalog(){return db.queryForList("select p.*,s.* from purchase_order p join specification s on s.reference=p.reference order by code");}
 @GetMapping("/lots") List<Lot> list(){return lots.all();}
 @GetMapping("/lots/{id}") Lot get(@PathVariable UUID id){return lots.get(id);}
 @GetMapping("/lots/{id}/timeline") List<Map<String,Object>> timeline(@PathVariable UUID id){lots.get(id);return audit.timeline(id);}
 @PostMapping("/receipts") Lot create(@Valid @RequestBody Command c,@RequestHeader("Idempotency-Key") UUID key,Authentication a){return service.execute(null,"create",c,key,a.getName(),role(a));}
 @PostMapping("/lots/{id}/{action}") Lot action(@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody Command c,@RequestHeader("Idempotency-Key") UUID key,Authentication a){return service.execute(id,action,c,key,a.getName(),role(a));}
 private static String role(Authentication a){return a.getAuthorities().iterator().next().getAuthority().substring(5);}
}
