package com.proco.simulator.monitoring;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
 private final ReportStore reports;private final MonitoringStore monitoring;private final Clock clock;
 public ReportController(ReportStore reports,MonitoringStore monitoring,Clock clock){this.reports=reports;this.monitoring=monitoring;this.clock=clock;}
 @GetMapping("/executions/{id}.json") @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public ResponseEntity<Map<String,Object>> execution(@PathVariable UUID id){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"proco-execution-"+id+".json\"").body(reports.execution(id));}
 @GetMapping("/tasks.csv") @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public ResponseEntity<String> tasks(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,@RequestParam(required=false) Instant asOf,
  @RequestParam(required=false) String area,@RequestParam(required=false) String montageCode,@RequestParam(required=false) String executionStatus,@RequestParam(required=false) String taskStatus,@RequestParam(required=false) Integer limit,@RequestParam(required=false) String cursor){
  var f=MonitoringFilter.of(from,to,asOf,area,montageCode,executionStatus,taskStatus,limit,cursor,clock);var page=monitoring.tasks(f);
  var response=ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"proco-tasks.csv\"").header("X-As-Of",f.asOf().toString()).header("X-Total-Count",page.get("total").toString());
  if(page.get("nextCursor")!=null)response.header("X-Next-Cursor",page.get("nextCursor").toString());return response.body(ReportStore.csv(page,clock.instant()));
 }
}
