package com.proco.simulator.monitoring;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.*;

@Repository
public class MonitoringStore {
 private final JdbcTemplate db;
 public MonitoringStore(JdbcTemplate db){this.db=db;}
 private static Timestamp ts(java.time.Instant i){return Timestamp.from(i);}
 private record Sql(String text,List<Object> args){}
 private static final String OUTCOME="case when e.status='COMPLETED' and e.suspended_at is not null then 'LEGACY_INCIDENT_CLOSURE' else e.status end";
 private static void executionConditions(StringBuilder q,List<Object> a,MonitoringFilter f){
  if(f.montageCode()!=null){q.append(" and e.montage_code=?");a.add(f.montageCode());}
  if(f.executionStatus()!=null){q.append(" and ("+OUTCOME+")=?");a.add(f.executionStatus());}
 }
 private Sql taskScope(MonitoringFilter f){
  StringBuilder q=new StringBuilder("""
   with task_scope as (select t.*,e.montage_code,e.status as execution_status,r.version as ola_rule_version,
    case when e.ended_at<=? then case when e.status='COMPLETED' and e.suspended_at is not null then 'LEGACY_INCIDENT_CLOSURE' else e.status end when e.suspended_at<=? then 'SUSPENDED' else 'IN_PROGRESS' end as execution_outcome_at,
    case when t.closed_at<=? then 'CLOSED' when t.acknowledged_at<=? then 'ACKNOWLEDGED' else 'OPEN' end as state_at
   from containment_task t join montage_execution e on e.id=t.execution_id left join ola_rule r on r.id=t.ola_rule_id
   where t.created_at>=? and t.created_at<? and t.created_at<=?
   """);
  List<Object> a=new ArrayList<>(List.of(ts(f.asOf()),ts(f.asOf()),ts(f.asOf()),ts(f.asOf()),ts(f.from()),ts(f.to()),ts(f.asOf())));
  if(f.area()!=null){q.append(" and t.target_role=?");a.add(f.area());}
  if(f.montageCode()!=null){q.append(" and e.montage_code=?");a.add(f.montageCode());}
  q.append(") , tasks as (select * from task_scope where true");if(f.executionStatus()!=null){q.append(" and execution_outcome_at=?");a.add(f.executionStatus());}if(f.taskStatus()!=null){q.append(" and state_at=?");a.add(f.taskStatus());}q.append(") ");
  return new Sql(q.toString(),a);
 }
 public Map<String,Object> tasks(MonitoringFilter f){
  Sql scope=taskScope(f);var a=new ArrayList<>(scope.args());String after="";
  if(f.afterTime()!=null){after=" where (created_at,id)<(?,?)";a.add(ts(f.afterTime()));a.add(f.afterId());}a.add(f.limit()+1);
  var rows=db.queryForList(scope.text()+"select * from tasks"+after+" order by created_at desc,id desc limit ?",a.toArray());
  boolean more=rows.size()>f.limit();if(more)rows=new ArrayList<>(rows.subList(0,f.limit()));
  List<Map<String,Object>> projected=rows.stream().map(r->OlaPolicy.project(r,f.asOf())).toList();String next=null;
  if(more){var last=rows.getLast();next=MonitoringFilter.cursor(OlaPolicy.instant(last.get("created_at")),(UUID)last.get("id"));}
  var result=new LinkedHashMap<String,Object>();result.put("legend","SOLO SIMULACIÓN");result.put("asOf",f.asOf());result.put("from",f.from());result.put("to",f.to());result.put("items",projected);result.put("nextCursor",next);result.put("limit",f.limit());
  result.put("total",db.queryForObject(scope.text()+"select count(*) from tasks",Long.class,scope.args().toArray()));return result;
 }
 private Sql entityScope(MonitoringFilter f,String entity){
  String alias=switch(entity){case "montage_execution"->"e";case "non_conformity"->"n";default->"m";};
  String date=switch(entity){case "montage_execution"->"started_at";case "non_conformity"->"opened_at";default->"created_at";};
  var q=new StringBuilder(" from "+entity+" "+alias+" where "+alias+"."+date+">=? and "+alias+"."+date+"<? and "+alias+"."+date+"<=?");
  List<Object> a=new ArrayList<>(List.of(ts(f.from()),ts(f.to()),ts(f.asOf())));
  if(alias.equals("e")){
   executionConditions(q,a,f);
   if(f.area()!=null){q.append(" and exists(select 1 from containment_task ar where ar.execution_id=e.id and ar.target_role=? and ar.created_at<=?)");a.add(f.area());a.add(ts(f.asOf()));}
  }else if(f.area()!=null||f.montageCode()!=null||f.executionStatus()!=null){
   q.append(" and exists(select 1 from montage_execution e where "+(alias.equals("m")?"e.mix_request_id=m.id":"exists(select 1 from mix_request_lot i where i.request_id=e.mix_request_id and i.lot_id=n.lot_id)")+" and e.started_at<=?");a.add(ts(f.asOf()));executionConditions(q,a,f);
   if(f.area()!=null){q.append(" and exists(select 1 from containment_task ar where ar.execution_id=e.id "+(alias.equals("n")?"and ar.nc_id=n.id ":"")+"and ar.target_role=? and ar.created_at<=?)");a.add(f.area());a.add(ts(f.asOf()));}q.append(")");
  }
  return new Sql(q.toString(),a);
 }
 private Map<String,Long> counts(Sql scope,String expr,List<String> keys){
  Map<String,Long> result=new LinkedHashMap<>();for(String key:keys)result.put(key,0L);
  for(var r:db.queryForList("select "+expr+" as state,count(*) as n"+scope.text()+" group by 1",scope.args().toArray()))result.put((String)r.get("state"),((Number)r.get("n")).longValue());return result;
 }
 public Map<String,Object> summary(MonitoringFilter f){
  Sql t=taskScope(f),e=entityScope(f,"montage_execution"),n=entityScope(f,"non_conformity"),m=entityScope(f,"mix_request");
  var result=new LinkedHashMap<String,Object>();result.put("legend","SOLO SIMULACIÓN — OLA DEMO, minutos corridos UTC");result.put("asOf",f.asOf());result.put("from",f.from());result.put("to",f.to());
  result.put("executions",counts(e,OUTCOME,List.of("IN_PROGRESS","SUSPENDED","COMPLETED","CANCELLED","CLOSED_WITH_INCIDENT","LEGACY_INCIDENT_CLOSURE")));
  result.put("nonconformities",counts(n,"n.status",List.of("OPEN","CLOSED","REJECTED")));
  result.put("mixes",counts(m,"m.status",List.of("UNDER_REVIEW","AUTHORIZED","DENIED","REVOKED")));
  result.put("tasks",db.queryForList(t.text()+"select target_role,task_type,state_at as status,count(*) as count from tasks group by target_role,task_type,state_at order by target_role,task_type,state_at",t.args().toArray()));
  var args=new ArrayList<>(t.args());args.add(ts(f.asOf()));args.add(ts(f.asOf()));
  String ack="(ack_due_at < ? and state_at='OPEN')",close="(close_due_at < ? and state_at<>'CLOSED')";
  result.put("alerts",db.queryForMap(t.text()+"select count(*) filter(where ack_overdue or close_overdue) as overdue_tasks,coalesce(sum(ack_overdue::int+close_overdue::int),0) as overdue_milestones from (select coalesce("+ack+",false) as ack_overdue,coalesce("+close+",false) as close_overdue from tasks) a",args.toArray()));
  var times=new ArrayList<>(t.args());times.add(ts(f.asOf()));times.add(ts(f.asOf()));times.add(ts(f.asOf()));times.add(ts(f.asOf()));times.add(ts(f.asOf()));times.add(ts(f.asOf()));
  result.put("responseTimes",db.queryForList(t.text()+"""
   select target_role,task_type,
    count(*) filter(where acknowledged_at<=? and acknowledged_at>=created_at) as ack_count,
    avg(extract(epoch from acknowledged_at-created_at)) filter(where acknowledged_at<=? and acknowledged_at>=created_at) as ack_mean_seconds,
    percentile_cont(0.5) within group(order by extract(epoch from acknowledged_at-created_at)) filter(where acknowledged_at<=? and acknowledged_at>=created_at) as ack_median_seconds,
    count(*) filter(where closed_at<=? and closed_at>=created_at) as close_count,
    avg(extract(epoch from closed_at-created_at)) filter(where closed_at<=? and closed_at>=created_at) as close_mean_seconds,
    percentile_cont(0.5) within group(order by extract(epoch from closed_at-created_at)) filter(where closed_at<=? and closed_at>=created_at) as close_median_seconds
   from tasks group by target_role,task_type order by target_role,task_type
   """,times.toArray()));
  result.put("definitions",Map.of("cohort","created_at para tarea/mezcla; started_at para ejecución; opened_at para NC; [from,to)","durations","Segundos corridos desde creación; n de acuses observados y n de cierres observados por separado. Media/mediana null si n=0. Tareas abiertas excluidas del tiempo de cierre.","filters","Área y montaje/estado de ejecución restringen entidades relacionadas mediante EXISTS. Estado de tarea afecta solo tareas/tiempos. COMPLETED excluye LEGACY_INCIDENT_CLOSURE.","pagination","asOf y keyset estabilizan tareas ante nuevos inserts y acciones posteriores; otros contadores reflejan estado actual en una transacción de lectura."));return result;
 }
}
