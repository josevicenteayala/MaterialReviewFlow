package com.proco.simulator.execution;

import jakarta.validation.constraints.*;

public record ExecutionCommand(@NotNull @PositiveOrZero Long version,
 @NotBlank @Size(max=2000) String reason, @NotBlank @Size(max=2000) String evidence,
 @Size(max=80) String montageCode) {}
