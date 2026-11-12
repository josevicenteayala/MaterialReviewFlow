package com.proco.simulator;
import com.proco.simulator.mixing.MixPolicy;
import com.proco.simulator.reception.Lot;
import com.proco.simulator.shared.RuleException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class MixPolicyTest {
 @Test void segregatesIdentityAndChecksThreeIndependentControls(){
  var lot=new Lot(null,null,null,null,BigDecimal.ONE,null,"RELEASED","AVAILABLE",5,1,"M",new BigDecimal("15"),"tecnico",null,null,null,null,null,new BigDecimal("10"),new BigDecimal("20"),"SIM-1",0);
  assertTrue(MixPolicy.eligible(lot,BigDecimal.ONE));assertFalse(MixPolicy.eligible(lot,new BigDecimal("1.001")));assertFalse(MixPolicy.eligible(lot,BigDecimal.ZERO));
  assertThrows(RuleException.class,()->MixPolicy.independent("same","same"));assertDoesNotThrow(()->MixPolicy.independent("requester","approver"));assertEquals(4,MixPolicy.required(true).size());
 }
}
