package com.proco.simulator.inventory;
import com.proco.simulator.reception.Lot;
import static com.proco.simulator.shared.RuleException.require;
public final class InventoryPolicy {
 private InventoryPolicy(){}
 public static void available(Lot l){require(l.qualityState().equals("RELEASED") && l.inventoryState().equals("QUARANTINE") && l.quantity().signum()>0,422,"Requiere liberación vigente, cantidad positiva y ausencia de bloqueos");}
}
