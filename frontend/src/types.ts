export type User = {username: string; role: string};
export type Lot = {
  id: string; orderCode: string; supplierLot: string; productionDate: string;
  quantity: number; location: string; qualityState: string; inventoryState: string;
  version: number; cycle: number; sampleCode: string | null; resultValue: number | null;
  reference: string; supplier: string; testName: string; unit: string;
  minimum: number; maximum: number; ruleVersion: string; activeHolds: number;
};
export type AuditEvent = {
  id: string; event_type: string; actor: string; role: string; occurred_at: string;
  from_state: string | null; to_state: string; reason: string; evidence: string;
  correlation_id: string; rule_version: string;
};
export type Catalog = {code: string; reference: string; supplier: string};
export type Opinion = {
  id: string; author: string; outcome: string; reason: string; evidence: string;
  based_on_version: number; created_at: string;
};
export type NonConformity = {
  id: string; lotId: string; origin: string; status: string; responsible: string;
  openedBy: string; reason: string; evidence: string; openedAt: string;
  disposition: string | null; decidedBy: string | null; decidedAt: string | null;
  decisionReason: string | null; decisionEvidence: string | null; opinions: Opinion[];
};
export type Hold = {
  id: string; nc_id: string | null; status: string; opened_by: string; opened_at: string;
  reason: string; evidence: string; closed_by: string | null; closed_at: string | null;
  close_reason: string | null; close_evidence: string | null;
};
