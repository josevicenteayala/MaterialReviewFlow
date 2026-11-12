package com.proco.simulator.laboratory;
import com.proco.simulator.reception.Lot;
import java.math.BigDecimal;
import static com.proco.simulator.shared.RuleException.require;
public final class LaboratoryPolicy {
 private LaboratoryPolicy(){}
 public static boolean passes(Lot l,BigDecimal value){return value.compareTo(l.minimum())>=0 && value.compareTo(l.maximum())<=0;}
 public static void releasable(Lot l,String actor){
  require(l.qualityState().equals("UNDER_TEST"),409,"El lote debe estar en pruebas");
  require(l.sampleCode()!=null && l.resultValue()!=null,422,"Falta muestra o ensayo crítico");
  require(!actor.equals(l.testedBy()),422,"El capturador no puede aprobar su ensayo crítico");
  require(passes(l,l.resultValue()),422,"Ensayo fuera de los criterios SIM-1");
  require(l.inventoryState().equals("QUARANTINE"),422,"Existe un bloqueo activo");
 }
}
