package com.proco.simulator.mixing;
import com.proco.simulator.audit.AuditLog;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import jakarta.validation.Valid;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;
@RestController
@RequestMapping("/api/mix-requests")
public class MixController {
 private final MixService service;private final MixStore store;private final AuditLog audit;
 public MixController(MixService service,MixStore store,AuditLog audit){this.service=service;this.store=store;this.audit=audit;}
 @GetMapping @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public List<MixRequest> all(){return store.all();}
 @GetMapping("/rules") public Map<String,MixRule> rules(){return store.referenceRules();}
 @GetMapping("/{id}") @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public MixRequest get(@PathVariable UUID id){return store.get(id);}
 @GetMapping("/{id}/authorization") @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public MixRequest authorization(@PathVariable UUID id,@RequestParam String montageCode){var mix=store.get(id);require(montageCode.equals(mix.montageCode()),422,"La autorización pertenece a otro montaje");require(mix.usable(),422,"Autorización no utilizable: "+mix.status()+"; "+String.join("; ",mix.blockers()));return mix;}
 @GetMapping("/{id}/timeline") public List<Map<String,Object>> timeline(@PathVariable UUID id){store.get(id);return audit.mixTimeline(id);}
 @PostMapping public MixRequest create(@Valid @RequestBody MixCommand c,@RequestHeader("Idempotency-Key") UUID key,Authentication a){return service.execute(null,"create",c,key,a.getName(),role(a));}
 @PostMapping("/{id}/{action}") public MixRequest action(@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody MixCommand c,@RequestHeader("Idempotency-Key") UUID key,Authentication a){require(!action.equals("create"),404,"Acción de mezcla inexistente");return service.execute(id,action,c,key,a.getName(),role(a));}
 private static String role(Authentication a){return a.getAuthorities().iterator().next().getAuthority().substring(5);}
}
