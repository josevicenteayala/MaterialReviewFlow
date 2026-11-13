package com.proco.simulator.supplier;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record DiscrepancyCommand(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=2000) String reason,@NotBlank @Size(max=2000) String evidence,
 @Size(max=40) String orderCode,@Size(max=80) String supplierLot,LocalDate productionDate,@Size(max=100) String location,@Size(max=200) String supplierDocumentRef,
 @DecimalMin("0") @Digits(integer=11,fraction=3) BigDecimal documentedQuantity,@DecimalMin("0") @Digits(integer=11,fraction=3) BigDecimal observedQuantity,Boolean technicalFinding,
 @DecimalMin("0") @Digits(integer=11,fraction=3) BigDecimal proposedAcceptedQuantity,@Size(max=2000) String remainderPlan,@Size(max=20) String outcome) {}
