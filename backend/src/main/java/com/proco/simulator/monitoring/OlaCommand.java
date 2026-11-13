package com.proco.simulator.monitoring;
import jakarta.validation.constraints.*;
public record OlaCommand(@NotNull @PositiveOrZero Long version,@NotBlank String taskType,@NotBlank String targetRole,
 @Min(1) @Max(525600) Integer ackMinutes,@Min(1) @Max(525600) Integer closeMinutes,
 @NotNull @Min(0) @Max(10080) Integer dueSoonMinutes,@NotBlank @Size(max=2000) String reason,@NotBlank @Size(max=2000) String evidence) {}
