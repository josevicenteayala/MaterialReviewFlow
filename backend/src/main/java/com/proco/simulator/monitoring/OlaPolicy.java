package com.proco.simulator.monitoring;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** UTC elapsed time, with independent acknowledgment and closure milestones. No process mutations. */
public final class OlaPolicy {
 private OlaPolicy() {}
 public static Instant instant(Object value){return value==null?null:value instanceof Instant i?i:((Timestamp)value).toInstant();}
 public static String state(Instant due,Instant done,Instant now,int soonMinutes){
  if(due==null)return "NOT_CONFIGURED";
  if(done!=null)return done.isAfter(due)?"COMPLETED_LATE":"COMPLETED_ON_TIME";
  if(now.isAfter(due))return "OVERDUE";
  return !now.isBefore(due.minusSeconds(soonMinutes*60L))?"DUE_SOON":"ON_TIME";
 }
 public static Map<String,Object> project(Map<String,Object> source,Instant at){
  var row=new LinkedHashMap<>(source);
  Instant ack=instant(row.get("acknowledged_at")),close=instant(row.get("closed_at"));
  if(ack!=null && ack.isAfter(at))ack=null;if(close!=null && close.isAfter(at))close=null;
  if(row.containsKey("execution_outcome_at"))row.put("execution_status",row.get("execution_outcome_at"));
  row.put("version",close!=null?2L:ack!=null?1L:0L);
  row.put("status",close!=null?"CLOSED":ack!=null?"ACKNOWLEDGED":"OPEN");
  // A cursor's asOf freezes milestone visibility, including the associated evidence/actor.
  if(ack==null)for(String field:List.of("acknowledged_at","acknowledged_by","ack_reason","ack_evidence"))row.put(field,null);
  if(close==null)for(String field:List.of("closed_at","closed_by","close_reason","close_evidence"))row.put(field,null);
  int soon=row.get("ola_due_soon_minutes")==null?0:((Number)row.get("ola_due_soon_minutes")).intValue();
  String ackState=state(instant(row.get("ack_due_at")),ack,at,soon),closeState=state(instant(row.get("close_due_at")),close,at,soon);
  row.put("ack_ola_status",ackState);row.put("close_ola_status",closeState);row.put("ola_checked_at",at);
  var alerts=new ArrayList<Map<String,String>>();
  if(ackState.equals("OVERDUE"))alerts.add(Map.of("id",row.get("id")+":ACK","milestone","ACK","message","Acuse vencido — SOLO SIMULACIÓN"));
  if(closeState.equals("OVERDUE"))alerts.add(Map.of("id",row.get("id")+":CLOSE","milestone","CLOSE","message","Cierre vencido — SOLO SIMULACIÓN"));
  row.put("alerts",alerts);return row;
 }
}
