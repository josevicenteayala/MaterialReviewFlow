package com.proco.simulator;
import org.junit.jupiter.api.Test;
import com.proco.simulator.reception.Lot;
import com.proco.simulator.laboratory.LaboratoryPolicy;
import com.proco.simulator.shared.RuleException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class LaboratoryPolicyTest {
 Lot lot(){return new Lot(null,null,null,null,BigDecimal.ONE,null,"UNDER_TEST","QUARANTINE",0,1,"M",new BigDecimal("15"),"tecnico",null,null,null,null,null,new BigDecimal("10"),new BigDecimal("20"),"SIM-1",0);}
 @Test void boundariesAndSegregation(){var l=lot();assertTrue(LaboratoryPolicy.passes(l,new BigDecimal("10")));assertTrue(LaboratoryPolicy.passes(l,new BigDecimal("20")));assertFalse(LaboratoryPolicy.passes(l,new BigDecimal("20.01")));assertThrows(RuleException.class,()->LaboratoryPolicy.releasable(l,"tecnico"));assertDoesNotThrow(()->LaboratoryPolicy.releasable(l,"aprobador"));}
}
