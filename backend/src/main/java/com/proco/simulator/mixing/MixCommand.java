package com.proco.simulator.mixing;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public record MixCommand(
 @NotNull @PositiveOrZero Long version,@NotBlank @Size(max=2000) String reason,@NotBlank @Size(max=2000) String evidence,
 @Size(max=80) String montageCode,Instant validUntil,@Size(max=50) List<@NotNull @Valid Item> items,
 @Size(max=20) String outcome,@Size(max=20) String decision) {
 public record Item(@NotNull UUID lotId,@NotNull @DecimalMin("0.001") @Digits(integer=11,fraction=3) BigDecimal quantity) {}
}
