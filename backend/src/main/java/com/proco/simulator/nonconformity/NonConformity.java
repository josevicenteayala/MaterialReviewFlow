package com.proco.simulator.nonconformity;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;

public record NonConformity(UUID id, UUID lotId, String origin, String status, String responsible,
        String openedBy, String reason, String evidence, Instant openedAt, String disposition,
        String decidedBy, Instant decidedAt, String decisionReason, String decisionEvidence,
        List<Map<String,Object>> opinions) {}
