package com.proco.simulator;
import com.proco.simulator.monitoring.OlaPolicy;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class OlaPolicyTest {
 @Test void boundariesUseUtcAndSeparateConfiguredCompletedAndPending(){
  Instant due=Instant.parse("2026-11-13T12:30:00Z");
  assertEquals("NOT_CONFIGURED",OlaPolicy.state(null,null,due,5));
  assertEquals("ON_TIME",OlaPolicy.state(due,null,due.minusSeconds(301),5));
  assertEquals("DUE_SOON",OlaPolicy.state(due,null,due.minusSeconds(300),5));
  assertEquals("DUE_SOON",OlaPolicy.state(due,null,due,5));
  assertEquals("OVERDUE",OlaPolicy.state(due,null,due.plusNanos(1),5));
  assertEquals("COMPLETED_ON_TIME",OlaPolicy.state(due,due,due.plusSeconds(100),5));
  assertEquals("COMPLETED_LATE",OlaPolicy.state(due,due.plusNanos(1),due.plusSeconds(100),5));
 }

 @Test void projectionExposesPublicStatesWithoutSqlHelpersOrMutatingSource(){
  Instant at=Instant.parse("2026-11-13T12:00:00Z");
  var source=new java.util.LinkedHashMap<String,Object>();source.put("id",java.util.UUID.randomUUID());source.put("execution_status","CLOSED_WITH_INCIDENT");source.put("execution_outcome_at","SUSPENDED");source.put("state_at","OPEN");source.put("status","CLOSED");source.put("version",2L);source.put("acknowledged_at",java.sql.Timestamp.from(at.plusSeconds(60)));source.put("closed_at",java.sql.Timestamp.from(at.plusSeconds(120)));
  var original=new java.util.LinkedHashMap<>(source);var projected=OlaPolicy.project(source,at);
  assertEquals("SUSPENDED",projected.get("execution_status"));assertEquals("OPEN",projected.get("status"));assertEquals(0L,projected.get("version"));assertFalse(projected.containsKey("execution_outcome_at"));assertFalse(projected.containsKey("state_at"));assertEquals(original,source);
  source.remove("execution_outcome_at");source.remove("state_at");assertEquals("CLOSED_WITH_INCIDENT",OlaPolicy.project(source,at).get("execution_status"));
 }
}
