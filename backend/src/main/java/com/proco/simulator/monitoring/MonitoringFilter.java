package com.proco.simulator.monitoring;

import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static com.proco.simulator.shared.RuleException.require;

public record MonitoringFilter(Instant from,Instant to,Instant asOf,String area,String montageCode,
 String executionStatus,String taskStatus,int limit,Instant afterTime,UUID afterId) {
 public static MonitoringFilter of(Instant from,Instant to,Instant asOf,String area,String montage,String execution,String task,Integer limit,String cursor,Clock clock){
  Instant now=clock.instant();if(asOf==null)asOf=now;asOf=asOf.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  require(!asOf.isAfter(now),400,"asOf no puede ser futuro");
  if(from==null)from=asOf.minus(Duration.ofDays(30));if(to==null)to=asOf.plusMillis(1);
  require(from.isBefore(to) && Duration.between(from,to).compareTo(Duration.ofDays(366))<=0,400,"Rango UTC debe ser positivo y máximo 366 días");
  area=blank(area);montage=blank(montage);execution=blank(execution);task=blank(task);
  require(area==null||OlaService.AREAS.contains(area),400,"Área inválida");
  require(montage==null||montage.length()<=80,400,"Montaje demasiado largo");
  require(execution==null||Set.of("IN_PROGRESS","SUSPENDED","COMPLETED","CLOSED_WITH_INCIDENT","CANCELLED","LEGACY_INCIDENT_CLOSURE").contains(execution),400,"Estado de ejecución inválido");
  require(task==null||Set.of("OPEN","ACKNOWLEDGED","CLOSED").contains(task),400,"Estado de tarea inválido");
  int size=limit==null?25:limit;require(size>=1&&size<=100,400,"Límite de página 1–100");Instant time=null;UUID id=null;
  if(cursor!=null){try{String[] parts=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8).split("\\|",-1);require(parts.length==2,400,"Cursor inválido");time=Instant.parse(parts[0]);id=UUID.fromString(parts[1]);require(!time.isAfter(asOf),400,"Cursor posterior a asOf");}catch(RuntimeException ex){throw new com.proco.simulator.shared.RuleException(400,"Cursor inválido");}}
  return new MonitoringFilter(from,to,asOf,area,montage,execution,task,size,time,id);
 }
 private static String blank(String s){return s==null||s.isBlank()?null:s;}
 public static String cursor(Instant time,UUID id){return Base64.getUrlEncoder().withoutPadding().encodeToString((time+"|"+id).getBytes(StandardCharsets.UTF_8));}
}
