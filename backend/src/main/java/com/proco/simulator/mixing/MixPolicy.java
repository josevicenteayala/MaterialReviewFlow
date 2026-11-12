package com.proco.simulator.mixing;
import com.proco.simulator.reception.Lot;
import java.math.BigDecimal;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;
public final class MixPolicy {
 private MixPolicy() {}
 public static List<String> required(boolean dyeing){return dyeing?List.of("LAB_APPROVER","GEO","QUALITY","DYEING"):List.of("LAB_APPROVER","GEO","QUALITY");}
 public static boolean eligible(Lot lot,BigDecimal quantity){return lot.qualityState().equals("RELEASED")&&lot.inventoryState().equals("AVAILABLE")&&lot.activeHolds()==0&&quantity.signum()>0&&quantity.compareTo(lot.quantity())<=0;}
 public static void independent(String requester,String actor){require(!actor.equals(requester),422,"El solicitante no puede aprobar o denegar su propia mezcla");}
 public static void underReview(MixRequest mix){require(mix.status().equals("UNDER_REVIEW"),409,"La solicitud tiene decisión terminal; crea una nueva solicitud");}
}
