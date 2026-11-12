package com.proco.simulator.reception;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
public record Lot(UUID id,String orderCode,String supplierLot,LocalDate productionDate,BigDecimal quantity,String location,String qualityState,String inventoryState,long version,int cycle,String sampleCode,BigDecimal resultValue,String testedBy,Instant createdAt,String reference,String supplier,String testName,String unit,BigDecimal minimum,BigDecimal maximum,String ruleVersion) {}
