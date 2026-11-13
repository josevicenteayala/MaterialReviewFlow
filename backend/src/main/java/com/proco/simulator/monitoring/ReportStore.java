package com.proco.simulator.monitoring;

import com.proco.simulator.execution.ExecutionPolicy;
import com.proco.simulator.mixing.MixStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;

@Repository
public class ReportStore {
 private final JdbcTemplate db;private final MixStore mixes;private final Clock clock;
 public ReportStore(JdbcTemplate db,MixStore mixes,Clock clock){this.db=db;this.mixes=mixes;this.clock=clock;}
 private List<Map<String,Object>> bounded(String sql,Object...args){var rows=db.queryForList(sql+" limit 2001",args);require(rows.size()<=2000,422,"Expediente supera 2000 filas en una sección; acota la revisión, no se exporta evidencia truncada");return rows;}
 public Map<String,Object> execution(UUID id){
  Instant at=clock.instant();var roots=db.queryForList("select * from montage_execution where id=?",id);require(!roots.isEmpty(),404,"Ejecución inexistente");var e=roots.getFirst();UUID mix=(UUID)e.get("mix_request_id");
  e.put("outcome",ExecutionPolicy.outcome((String)e.get("status"),e.get("suspended_at")!=null));
  var result=new LinkedHashMap<String,Object>();result.put("legend","SOLO SIMULACIÓN — referencias textuales, sin firma electrónica ni operación física");result.put("generatedAt",at);result.put("schemaVersion","SIM-5");result.put("execution",e);result.put("mix",mixes.get(mix));
  result.put("lots",bounded("select l.* from lot l join mix_request_lot i on i.lot_id=l.id where i.request_id=? order by l.id",mix));
  result.put("nonconformities",bounded("select n.* from non_conformity n join mix_request_lot i on i.lot_id=n.lot_id where i.request_id=? order by n.id",mix));
  result.put("ncOpinions",bounded("select o.* from nc_opinion o join non_conformity n on n.id=o.nc_id join mix_request_lot i on i.lot_id=n.lot_id where i.request_id=? order by o.id",mix));
  result.put("holds",bounded("select h.* from inventory_hold h join mix_request_lot i on i.lot_id=h.lot_id where i.request_id=? order by h.id",mix));
  result.put("tasks",bounded("select t.*,r.version as ola_rule_version from containment_task t left join ola_rule r on r.id=t.ola_rule_id where execution_id=? order by t.id",id).stream().map(t->OlaPolicy.project(t,at)).toList());
  result.put("olaRules",bounded("select distinct r.* from ola_rule r join containment_task t on t.ola_rule_id=r.id where t.execution_id=? order by r.id",id));
  result.put("lotEvents",bounded("select a.* from audit_event a join mix_request_lot i on i.lot_id=a.lot_id where i.request_id=? order by a.id",mix));
  result.put("mixEvents",bounded("select * from mix_event where mix_id=? order by id",mix));
  result.put("executionEvents",bounded("select * from execution_event where execution_id=? order by id",id));
  return result;
 }
 public static String csv(Map<String,Object> page,Instant generated){
  String[] fields={"id","execution_id","montage_code","target_role","task_type","status","ola_rule_id","ola_rule_version","ack_due_at","close_due_at","ack_ola_status","close_ola_status","reason","evidence","ack_evidence","close_evidence"};
  var out=new StringBuilder("\"legend\",\"generated_at\",\"as_of\"");for(String f:fields)out.append(',').append(CsvPolicy.cell(f));out.append("\r\n");
  for(Object item:(List<?>)page.get("items")){
   Map<?,?> row=(Map<?,?>)item;out.append(CsvPolicy.cell("SOLO SIMULACIÓN")).append(',').append(CsvPolicy.cell(generated)).append(',').append(CsvPolicy.cell(page.get("asOf")));
   for(String f:fields){Object value=row.get(f);if(f.endsWith("_at") && value!=null)value=OlaPolicy.instant(value);out.append(',').append(CsvPolicy.cell(value));}out.append("\r\n");
  }
  return out.toString();
 }
}
