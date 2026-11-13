package com.proco.simulator.execution;

import static com.proco.simulator.shared.RuleException.require;

public final class ExecutionPolicy {
 private ExecutionPolicy() {}
 public static String terminal(String state,String action){
  if(action.equals("cancel"))return "CANCELLED";
  if(action.equals("complete")){require(state.equals("IN_PROGRESS"),409,"Completar producción exige IN_PROGRESS; usa close-incident para cerrar un incidente suspendido");return "COMPLETED";}
  require(action.equals("close-incident") && state.equals("SUSPENDED"),409,"Cerrar incidente exige SUSPENDED");return "CLOSED_WITH_INCIDENT";
 }
 public static String outcome(String status,boolean suspended){return status.equals("COMPLETED") && suspended?"LEGACY_INCIDENT_CLOSURE":status;}
}
