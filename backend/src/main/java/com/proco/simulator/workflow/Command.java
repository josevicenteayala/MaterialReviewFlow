package com.proco.simulator.workflow;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record Command(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=2000) String reason,@NotBlank @Size(max=2000) String evidence,@Size(max=40) String orderCode,@Size(max=80) String supplierLot,LocalDate productionDate,@DecimalMin("0.001") @Digits(integer=11,fraction=3) BigDecimal quantity,@Size(max=100) String location,Boolean conforms,@Size(max=80) String sampleCode,BigDecimal resultValue) {}
