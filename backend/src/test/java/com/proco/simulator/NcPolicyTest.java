package com.proco.simulator;

import com.proco.simulator.nonconformity.NcPolicy;
import com.proco.simulator.reception.Lot;
import com.proco.simulator.shared.RuleException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NcPolicyTest {
    Lot lot() { return new Lot(null,null,null,null,BigDecimal.ONE,null,"RELEASED","BLOCKED",9,1,"M",new BigDecimal("15"),"tecnico",null,null,null,null,null,new BigDecimal("10"),new BigDecimal("20"),"SIM-1",1); }
    @Test void independentOwnerAndCurrentOpinionAreRequired() {
        var nc=Map.<String,Object>of("responsible","calidad","opened_by","almacen");
        var op=Map.<String,Object>of("author","aprobador","based_on_version",9L,"outcome","CONFORMING");
        assertDoesNotThrow(()->NcPolicy.decision(lot(),nc,op,"calidad","CONFORMING"));
        assertThrows(RuleException.class,()->NcPolicy.decision(lot(),Map.of("responsible","calidad","opened_by","calidad"),op,"calidad","CONFORMING"));
        assertThrows(RuleException.class,()->NcPolicy.decision(lot(),nc,Map.of("author","calidad","based_on_version",9L,"outcome","CONFORMING"),"calidad","CONFORMING"));
        assertThrows(RuleException.class,()->NcPolicy.decision(lot(),nc,Map.of("author","aprobador","based_on_version",8L,"outcome","CONFORMING"),"calidad","CONFORMING"));
        assertThrows(RuleException.class,()->NcPolicy.decision(lot(),nc,op,"calidad2","CONFORMING"));
        assertDoesNotThrow(()->NcPolicy.liftable(lot()));
    }
}
