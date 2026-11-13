package com.proco.simulator.execution;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;

@Repository
public class ExecutionStore {
 private final java.time.Clock clock;private final JdbcTemplate db;
 public ExecutionStore(JdbcTemplate db,java.time.Clock clock){this.db=db;this.clock=clock;}
 public Map<String,Object> get(UUID id){
  var rows=db.queryForList("select * from montage_execution where id=?",id);
  require(!rows.isEmpty(),404,"Ejecución inexistente");
  var result=new LinkedHashMap<>(rows.getFirst());
  result.put("outcome",ExecutionPolicy.outcome((String)result.get("status"),result.get("suspended_at")!=null));
  result.put("tasks",project(db.queryForList("select t.*,r.version as ola_rule_version from containment_task t left join ola_rule r on r.id=t.ola_rule_id where execution_id=? order by t.created_at,t.id",id)));
  return result;
 }
 private List<Map<String,Object>> project(List<Map<String,Object>> rows){var at=clock.instant();return rows.stream().map(r->com.proco.simulator.monitoring.OlaPolicy.project(r,at)).toList();}
 public List<Map<String,Object>> all(){
  // One batched join/aggregate; no per-execution queries.
  return db.queryForList("""
   select e.*, count(t.id) as task_count, count(t.id) filter(where t.status<>'CLOSED') as pending_tasks
   from montage_execution e left join containment_task t on t.execution_id=e.id
   group by e.id order by e.started_at desc,e.id
   """).stream().map(r->{r.put("outcome",ExecutionPolicy.outcome((String)r.get("status"),r.get("suspended_at")!=null));return r;}).toList();
 }
 public Map<String,Object> task(UUID id){var rows=db.queryForList("select * from containment_task where id=?",id);require(!rows.isEmpty(),404,"Tarea inexistente");return com.proco.simulator.monitoring.OlaPolicy.project(rows.getFirst(),clock.instant());}
 public List<Map<String,Object>> tasks(String role){return project(db.queryForList("""
  select t.*,r.version as ola_rule_version,e.montage_code,e.status as execution_status from containment_task t
  join montage_execution e on e.id=t.execution_id left join ola_rule r on r.id=t.ola_rule_id where t.target_role=? order by t.created_at desc,t.id
  """,role));}
}
