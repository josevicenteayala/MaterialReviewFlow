-- Additive supplier workflows. No historical orders, quantities, decisions or events are rewritten.
CREATE TABLE receipt_discrepancy (
 id uuid PRIMARY KEY, order_code varchar(40) NOT NULL REFERENCES purchase_order(code), supplier_lot varchar(80) NOT NULL,
 production_date date NOT NULL, location varchar(100) NOT NULL, supplier_document_ref varchar(200) NOT NULL,
 documented_quantity numeric(14,3) NOT NULL CHECK(documented_quantity>=0), observed_quantity numeric(14,3) NOT NULL CHECK(observed_quantity>=0),
 balance_at_report numeric(14,3) NOT NULL, technical_finding boolean NOT NULL,
 reported_by varchar(50) NOT NULL REFERENCES app_user(username), reported_at timestamptz NOT NULL DEFAULT now(), reason text NOT NULL, evidence text NOT NULL,
 status varchar(32) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','UNDER_REVIEW','AUTHORIZED','ACCEPTED_OBSERVED','ACCEPTED_PARTIAL','EXCESS_NOT_ACCEPTED','CANCELLED_WITH_REASON')),
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 count_confirmed_by varchar(50) REFERENCES app_user(username), count_confirmed_at timestamptz, count_reason text, count_evidence text,
 quality_by varchar(50) REFERENCES app_user(username), quality_at timestamptz, quality_outcome varchar(20) CHECK(quality_outcome IN ('FAVORABLE','UNFAVORABLE')), quality_reason text, quality_evidence text,
 review_by varchar(50) REFERENCES app_user(username), reviewed_at timestamptz, review_reason text, review_evidence text,
 proposed_accepted_quantity numeric(14,3) CHECK(proposed_accepted_quantity>=0 AND proposed_accepted_quantity<=observed_quantity), remainder_plan text,
 accepted_quantity numeric(14,3) CHECK(accepted_quantity>=0 AND accepted_quantity<=observed_quantity), accepted_lot_id uuid UNIQUE REFERENCES lot(id),
 resolved_by varchar(50) REFERENCES app_user(username), resolved_at timestamptz, resolution_reason text, resolution_evidence text,
 CHECK(review_by IS NULL OR (review_by<>reported_by AND review_by<>count_confirmed_by AND (quality_by IS NULL OR review_by<>quality_by))),
 CHECK(status<>'AUTHORIZED' OR (count_confirmed_by IS NOT NULL AND review_by IS NOT NULL AND proposed_accepted_quantity IS NOT NULL)),
 CHECK(status NOT IN ('ACCEPTED_OBSERVED','ACCEPTED_PARTIAL','EXCESS_NOT_ACCEPTED') OR (accepted_quantity IS NOT NULL AND review_by IS NOT NULL AND resolved_by<>review_by)),
 CHECK((status IN ('ACCEPTED_OBSERVED','ACCEPTED_PARTIAL','EXCESS_NOT_ACCEPTED','CANCELLED_WITH_REASON'))=(resolved_at IS NOT NULL AND resolved_by IS NOT NULL AND resolution_reason IS NOT NULL AND resolution_evidence IS NOT NULL)),
 CHECK((accepted_quantity>0)=(accepted_lot_id IS NOT NULL)),
 CHECK(status<>'ACCEPTED_OBSERVED' OR (accepted_quantity=observed_quantity AND accepted_quantity>0)),
 CHECK(status<>'ACCEPTED_PARTIAL' OR (accepted_quantity>0 AND accepted_quantity<observed_quantity)),
 CHECK(status NOT IN ('EXCESS_NOT_ACCEPTED','CANCELLED_WITH_REASON') OR accepted_quantity=0)
);
CREATE INDEX discrepancies_by_order ON receipt_discrepancy(order_code,reported_at DESC);
CREATE TABLE supplier_case (
 id uuid PRIMARY KEY, nc_id uuid NOT NULL UNIQUE REFERENCES non_conformity(id), lot_id uuid NOT NULL REFERENCES lot(id), order_code varchar(40) NOT NULL REFERENCES purchase_order(code),
 status varchar(40) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','SUPPLIER_CONTACT_RECORDED','RESPONSE_RECORDED','DISPOSITION_DOCUMENTED','CLOSED_ADMINISTRATIVELY')),
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0), opened_by varchar(50) NOT NULL REFERENCES app_user(username), opened_at timestamptz NOT NULL DEFAULT now(), reason text NOT NULL, evidence text NOT NULL,
 contact_by varchar(50) REFERENCES app_user(username), contacted_at timestamptz, contact_ref varchar(200), contact_reason text, contact_evidence text,
 response_by varchar(50) REFERENCES app_user(username), responded_at timestamptz, response_ref varchar(200), response_outcome varchar(20) CHECK(response_outcome IN ('ACCEPTED','DISPUTED','NO_RESPONSE')), response_reason text, response_evidence text,
 disposition_by varchar(50) REFERENCES app_user(username), disposition_at timestamptz, disposition_ref varchar(200), commercial_direction varchar(30) CHECK(commercial_direction IN ('RETURN_REQUESTED','CREDIT_REQUESTED','OTHER')), treatment text, disposition_reason text, disposition_evidence text,
 closed_by varchar(50) REFERENCES app_user(username), closed_at timestamptz, close_ref varchar(200), close_reason text, close_evidence text,
 physical_return_confirmed boolean NOT NULL DEFAULT false CHECK(NOT physical_return_confirmed), erp_posted boolean NOT NULL DEFAULT false CHECK(NOT erp_posted),
 CHECK(status<>'CLOSED_ADMINISTRATIVELY' OR (closed_by IS NOT NULL AND closed_at IS NOT NULL AND close_ref IS NOT NULL AND closed_by<>disposition_by))
);
CREATE INDEX supplier_cases_by_lot ON supplier_case(lot_id,opened_at DESC);
CREATE TABLE supplier_case_task (
 supplier_case_id uuid NOT NULL REFERENCES supplier_case(id), task_id uuid NOT NULL UNIQUE REFERENCES containment_task(id), PRIMARY KEY(supplier_case_id,task_id)
);
CREATE TRIGGER supplier_case_task_immutable BEFORE UPDATE OR DELETE ON supplier_case_task FOR EACH ROW EXECUTE FUNCTION prevent_audit_change();
CREATE TABLE supplier_workflow_event (
 id uuid PRIMARY KEY, discrepancy_id uuid REFERENCES receipt_discrepancy(id), supplier_case_id uuid REFERENCES supplier_case(id), lot_id uuid REFERENCES lot(id),
 actor varchar(50) NOT NULL REFERENCES app_user(username), role varchar(30) NOT NULL, event_type varchar(50) NOT NULL,
 from_state varchar(40), to_state varchar(40), reason text NOT NULL, evidence text NOT NULL, correlation_id uuid NOT NULL, payload text NOT NULL,
 rule_version varchar(20) NOT NULL DEFAULT 'SIM-6', occurred_at timestamptz NOT NULL DEFAULT now(),
 CHECK(num_nonnulls(discrepancy_id,supplier_case_id)<=1), CHECK(event_type='COMMAND_DENIED' OR num_nonnulls(discrepancy_id,supplier_case_id)=1)
);
CREATE INDEX discrepancy_events ON supplier_workflow_event(discrepancy_id,occurred_at);
CREATE INDEX supplier_case_events ON supplier_workflow_event(supplier_case_id,occurred_at);
CREATE TRIGGER supplier_workflow_event_immutable BEFORE UPDATE OR DELETE ON supplier_workflow_event FOR EACH ROW EXECUTE FUNCTION prevent_audit_change();
ALTER TABLE command_result ADD COLUMN discrepancy_id uuid REFERENCES receipt_discrepancy(id);
ALTER TABLE command_result ADD COLUMN supplier_case_id uuid REFERENCES supplier_case(id);
ALTER TABLE command_result DROP CONSTRAINT command_target_xor;
ALTER TABLE command_result ADD CONSTRAINT command_target_xor CHECK(num_nonnulls(lot_id,mix_id,execution_id,task_id,ola_rule_id,discrepancy_id,supplier_case_id)=1);

CREATE FUNCTION protect_discrepancy() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Discrepancies are retained' USING ERRCODE='23514'; END IF;
 IF ROW(NEW.id,NEW.order_code,NEW.supplier_lot,NEW.production_date,NEW.location,NEW.supplier_document_ref,NEW.documented_quantity,NEW.observed_quantity,NEW.balance_at_report,NEW.technical_finding,NEW.reported_by,NEW.reported_at,NEW.reason,NEW.evidence)
 IS DISTINCT FROM ROW(OLD.id,OLD.order_code,OLD.supplier_lot,OLD.production_date,OLD.location,OLD.supplier_document_ref,OLD.documented_quantity,OLD.observed_quantity,OLD.balance_at_report,OLD.technical_finding,OLD.reported_by,OLD.reported_at,OLD.reason,OLD.evidence)
 OR NEW.version<>OLD.version+1 OR NOT ((OLD.status='OPEN' AND NEW.status IN ('UNDER_REVIEW','CANCELLED_WITH_REASON')) OR (OLD.status='UNDER_REVIEW' AND NEW.status IN ('UNDER_REVIEW','AUTHORIZED','CANCELLED_WITH_REASON')) OR (OLD.status='AUTHORIZED' AND NEW.status IN ('AUTHORIZED','ACCEPTED_OBSERVED','ACCEPTED_PARTIAL','EXCESS_NOT_ACCEPTED','CANCELLED_WITH_REASON'))) THEN
 RAISE EXCEPTION 'Invalid discrepancy mutation' USING ERRCODE='23514'; END IF;
 IF OLD.quality_by IS NOT NULL AND ROW(NEW.quality_by,NEW.quality_at,NEW.quality_outcome,NEW.quality_reason,NEW.quality_evidence) IS DISTINCT FROM ROW(OLD.quality_by,OLD.quality_at,OLD.quality_outcome,OLD.quality_reason,OLD.quality_evidence) THEN
 RAISE EXCEPTION 'Quality opinion is immutable' USING ERRCODE='23514'; END IF;
 IF OLD.count_confirmed_by IS NOT NULL AND ROW(NEW.count_confirmed_by,NEW.count_confirmed_at,NEW.count_reason,NEW.count_evidence) IS DISTINCT FROM ROW(OLD.count_confirmed_by,OLD.count_confirmed_at,OLD.count_reason,OLD.count_evidence) THEN
 RAISE EXCEPTION 'Confirmed count is immutable' USING ERRCODE='23514'; END IF;
 IF NEW.accepted_lot_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM lot l WHERE l.id=NEW.accepted_lot_id AND l.order_code=NEW.order_code AND l.supplier_lot=NEW.supplier_lot AND l.quantity=NEW.accepted_quantity) THEN RAISE EXCEPTION 'Accepted lot must match discrepancy decision' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER discrepancy_immutable BEFORE UPDATE OR DELETE ON receipt_discrepancy FOR EACH ROW EXECUTE FUNCTION protect_discrepancy();
CREATE FUNCTION protect_supplier_case() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Supplier cases are retained' USING ERRCODE='23514'; END IF;
 IF ROW(NEW.id,NEW.nc_id,NEW.lot_id,NEW.order_code,NEW.opened_by,NEW.opened_at,NEW.reason,NEW.evidence) IS DISTINCT FROM ROW(OLD.id,OLD.nc_id,OLD.lot_id,OLD.order_code,OLD.opened_by,OLD.opened_at,OLD.reason,OLD.evidence)
 OR NEW.version<>OLD.version+1 OR NOT ((OLD.status='OPEN' AND NEW.status='SUPPLIER_CONTACT_RECORDED') OR (OLD.status='SUPPLIER_CONTACT_RECORDED' AND NEW.status='RESPONSE_RECORDED') OR (OLD.status='RESPONSE_RECORDED' AND NEW.status='DISPOSITION_DOCUMENTED') OR (OLD.status='DISPOSITION_DOCUMENTED' AND NEW.status='CLOSED_ADMINISTRATIVELY')) THEN
 RAISE EXCEPTION 'Invalid supplier case transition' USING ERRCODE='23514'; END IF;
 IF OLD.contact_by IS NOT NULL AND ROW(NEW.contact_by,NEW.contacted_at,NEW.contact_ref,NEW.contact_reason,NEW.contact_evidence) IS DISTINCT FROM ROW(OLD.contact_by,OLD.contacted_at,OLD.contact_ref,OLD.contact_reason,OLD.contact_evidence) THEN RAISE EXCEPTION 'Contact evidence is immutable' USING ERRCODE='23514'; END IF;
 IF OLD.response_by IS NOT NULL AND ROW(NEW.response_by,NEW.responded_at,NEW.response_ref,NEW.response_outcome,NEW.response_reason,NEW.response_evidence) IS DISTINCT FROM ROW(OLD.response_by,OLD.responded_at,OLD.response_ref,OLD.response_outcome,OLD.response_reason,OLD.response_evidence) THEN RAISE EXCEPTION 'Response evidence is immutable' USING ERRCODE='23514'; END IF;
 IF OLD.disposition_by IS NOT NULL AND ROW(NEW.disposition_by,NEW.disposition_at,NEW.disposition_ref,NEW.commercial_direction,NEW.treatment,NEW.disposition_reason,NEW.disposition_evidence) IS DISTINCT FROM ROW(OLD.disposition_by,OLD.disposition_at,OLD.disposition_ref,OLD.commercial_direction,OLD.treatment,OLD.disposition_reason,OLD.disposition_evidence) THEN RAISE EXCEPTION 'Disposition evidence is immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER supplier_case_guard BEFORE UPDATE OR DELETE ON supplier_case FOR EACH ROW EXECUTE FUNCTION protect_supplier_case();
CREATE FUNCTION check_supplier_case_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM non_conformity n JOIN lot l ON l.id=n.lot_id WHERE n.id=NEW.nc_id AND l.id=NEW.lot_id AND l.order_code=NEW.order_code AND n.status='REJECTED' AND n.disposition='RETURN_TO_SUPPLIER' AND l.quality_state='REJECTED' AND l.inventory_state='BLOCKED') THEN
 RAISE EXCEPTION 'Supplier case requires technical RETURN_TO_SUPPLIER rejection' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER supplier_case_source BEFORE INSERT ON supplier_case FOR EACH ROW EXECUTE FUNCTION check_supplier_case_source();
CREATE FUNCTION check_supplier_task_link() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM supplier_case c JOIN containment_task t ON t.nc_id=c.nc_id WHERE c.id=NEW.supplier_case_id AND t.id=NEW.task_id AND t.task_type='REJECTION_COORDINATION' AND t.target_role='PROCUREMENT') THEN
 RAISE EXCEPTION 'Supplier link requires matching rejection coordination task' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER supplier_task_source BEFORE INSERT ON supplier_case_task FOR EACH ROW EXECUTE FUNCTION check_supplier_task_link();
