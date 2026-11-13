package com.proco.simulator.execution;

import com.proco.simulator.audit.AuditLog;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import jakarta.validation.Valid;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ExecutionController {
 private final ExecutionService service;private final TaskService tasks;private final ExecutionStore store;private final AuditLog audit;
 public ExecutionController(ExecutionService service,TaskService tasks,ExecutionStore store,AuditLog audit){this.service=service;this.tasks=tasks;this.store=store;this.audit=audit;}
 @GetMapping("/executions") public List<Map<String,Object>> all(){return store.all();}
 @GetMapping("/executions/{id}") @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> get(@PathVariable UUID id){return store.get(id);}
 @GetMapping("/executions/{id}/timeline") public List<Map<String,Object>> timeline(@PathVariable UUID id){store.get(id);return audit.executionTimeline(id);}
 @PostMapping("/mix-requests/{id}/executions") public Map<String,Object> start(@PathVariable UUID id,@Valid @RequestBody ExecutionCommand c,@RequestHeader("Idempotency-Key") UUID key,Authentication a){return service.start(id,c,key,a.getName(),role(a));}
 @PostMapping("/executions/{id}/{action}") public Map<String,Object> finish(@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody ExecutionCommand c,@RequestHeader("Idempotency-Key") UUID key,Authentication a){return service.finish(id,action,c,key,a.getName(),role(a));}
 @GetMapping("/tasks") public List<Map<String,Object>> tasks(Authentication a){return store.tasks(role(a));}
 @PostMapping("/tasks/{id}/{action}") public Map<String,Object> task(@PathVariable UUID id,@PathVariable String action,@Valid @RequestBody ExecutionCommand c,@RequestHeader("Idempotency-Key") UUID key,Authentication a){return tasks.execute(id,action,c,key,a.getName(),role(a));}
 private static String role(Authentication a){return a.getAuthorities().iterator().next().getAuthority().substring(5);}
}
