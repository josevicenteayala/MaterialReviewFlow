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
}
