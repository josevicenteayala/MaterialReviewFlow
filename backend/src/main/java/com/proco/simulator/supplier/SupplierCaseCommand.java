package com.proco.simulator.supplier;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record SupplierCaseCommand(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=2000) String reason,@NotBlank @Size(max=2000) String evidence,
 UUID lotId,UUID ncId,@Size(max=200) String communicationRef,@Size(max=20) String response,@Size(max=30) String direction,@Size(max=2000) String treatment,
 @AssertFalse Boolean physicalReturnConfirmed,@AssertFalse Boolean erpPosted) {}
