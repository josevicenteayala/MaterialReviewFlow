package com.proco.simulator.mixing;
import java.time.Instant;
import java.util.*;
public record MixRequest(UUID id,String montageCode,String status,String requester,String approver,
 String reason,String evidence,String ruleCode,String ruleVersion,boolean dyeingRequired,long reviewRevision,long version,
 Instant createdAt,Instant validUntil,Instant decidedAt,String decisionReason,String decisionEvidence,
 Instant revokedAt,String revocationReason,List<Map<String,Object>> items,List<Map<String,Object>> assessments,
 List<String> requiredRoles,List<String> materialBlockers,List<String> blockers,boolean usable,Instant checkedAt) {}
