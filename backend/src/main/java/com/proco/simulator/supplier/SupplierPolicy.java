package com.proco.simulator.supplier;
import java.math.BigDecimal;
import static com.proco.simulator.shared.RuleException.require;
public final class SupplierPolicy {
 private SupplierPolicy(){}
 public static boolean text(String value){return value!=null&&!value.isBlank();}
 public static void quantity(BigDecimal accepted,BigDecimal observed,BigDecimal balance){
  require(accepted!=null&&accepted.signum()>=0&&accepted.compareTo(observed)<=0,422,"Aceptación debe estar entre cero y cantidad observada");
  require(accepted.compareTo(balance)<=0,422,"Cantidad aceptada supera el saldo actual de OC; revisa propuesta sin reservar saldo");
 }
 public static String result(BigDecimal accepted,BigDecimal observed){return accepted.signum()==0?"EXCESS_NOT_ACCEPTED":accepted.compareTo(observed)==0?"ACCEPTED_OBSERVED":"ACCEPTED_PARTIAL";}
}
