package com.proco.simulator.nonconformity;

import jakarta.validation.constraints.*;

public record NcCommand(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max=2000) String reason,
        @NotBlank @Size(max=2000) String evidence,
        @Size(max=30) String origin,
        @Size(max=50) String responsible,
        @Size(max=20) String outcome,
        @Size(max=30) String disposition) {}
