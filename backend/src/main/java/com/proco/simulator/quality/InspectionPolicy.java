package com.proco.simulator.quality;
import com.proco.simulator.reception.Lot;
import static com.proco.simulator.shared.RuleException.require;
public final class InspectionPolicy {
 private InspectionPolicy(){}
 public static void inspectable(Lot l){require(l.qualityState().equals("UNDER_VERIFICATION")||l.qualityState().equals("HELD"),409,"Solo se inspecciona un lote en verificación o retenido");}
}
