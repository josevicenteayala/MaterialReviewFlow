package com.proco.simulator.monitoring;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.*;
import jakarta.validation.Valid;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {
 private final MonitoringStore store;private final OlaService rules;private final Clock clock;
 public MonitoringController(MonitoringStore store,OlaService rules,Clock clock){this.store=store;this.rules=rules;this.clock=clock;}
 @GetMapping("/{view:summary|tasks}") @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> view(@PathVariable String view,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,@RequestParam(required=false) Instant asOf,
  @RequestParam(required=false) String area,@RequestParam(required=false) String montageCode,@RequestParam(required=false) String executionStatus,@RequestParam(required=false) String taskStatus,@RequestParam(required=false) Integer limit,@RequestParam(required=false) String cursor){
  var f=MonitoringFilter.of(from,to,asOf,area,montageCode,executionStatus,taskStatus,limit,cursor,clock);var result=view.equals("tasks")?store.tasks(f):store.summary(f);result.put("generatedAt",clock.instant());return result;
 }
 @GetMapping("/ola-rules") public List<Map<String,Object>> rules(){return rules.all();}
 @GetMapping("/ola-rules/{id}") public Map<String,Object> rule(@PathVariable UUID id){return rules.get(id);}
 @PostMapping("/ola-rules") public Map<String,Object> rule(@Valid @RequestBody OlaCommand c,@RequestHeader("Idempotency-Key") UUID key,Authentication a){return rules.create(c,key,a.getName(),a.getAuthorities().iterator().next().getAuthority().substring(5));}
}
