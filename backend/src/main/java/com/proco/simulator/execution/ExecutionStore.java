package com.proco.simulator.execution;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;

@Repository
public class ExecutionStore {
 private final JdbcTemplate db;
 public ExecutionStore(JdbcTemplate db){this.db=db;}
 public Map<String,Object> get(UUID id){
  var rows=db.queryForList("select * from montage_execution where id=?",id);
  require(!rows.isEmpty(),404,"Ejecución inexistente");
  var result=new LinkedHashMap<>(rows.getFirst());
  result.put("tasks",db.queryForList("select * from containment_task where execution_id=? order by created_at,id",id));
  return result;
 }
 public List<Map<String,Object>> all(){
  // One batched join/aggregate; no per-execution queries.
  return db.queryForList("""
   select e.*, count(t.id) as task_count, count(t.id) filter(where t.status<>'CLOSED') as pending_tasks
   from montage_execution e left join containment_task t on t.execution_id=e.id
   group by e.id order by e.started_at desc,e.id
   """);
 }
 public Map<String,Object> task(UUID id){var rows=db.queryForList("select * from containment_task where id=?",id);require(!rows.isEmpty(),404,"Tarea inexistente");return rows.getFirst();}
 public List<Map<String,Object>> tasks(String role){return db.queryForList("""
  select t.*,e.montage_code,e.status as execution_status from containment_task t
  join montage_execution e on e.id=t.execution_id where t.target_role=? order by t.created_at desc,t.id
  """,role);}
}
