-- Additive schema only; existing lots, users, decisions and idempotency rows are not rewritten.
CREATE TABLE mix_rule (
 code varchar(40) NOT NULL, version varchar(30) NOT NULL, dyeing_required boolean NOT NULL,
 description text NOT NULL, PRIMARY KEY(code,version)
);
INSERT INTO mix_rule VALUES
 ('MANUAL_BASIC','MIX-SIM-1',false,'Hipótesis ficticia: Lab, GEO y Calidad; sin tolerancias automáticas'),
 ('MANUAL_DYEING','MIX-SIM-1',true,'Hipótesis ficticia: Lab, GEO, Calidad y Tintorería obligatorios');
CREATE TABLE mix_reference_rule (
 reference varchar(40) PRIMARY KEY REFERENCES specification(reference),
 rule_code varchar(40) NOT NULL, rule_version varchar(30) NOT NULL,
 FOREIGN KEY(rule_code,rule_version) REFERENCES mix_rule(code,version)
);
INSERT INTO mix_reference_rule VALUES ('HILO-DEMO','MANUAL_BASIC','MIX-SIM-1'),('ELASTANO-DEMO','MANUAL_DYEING','MIX-SIM-1');
CREATE TABLE mix_request (
 id uuid PRIMARY KEY, montage_code varchar(80) NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'UNDER_REVIEW' CHECK(status IN ('UNDER_REVIEW','AUTHORIZED','DENIED','REVOKED')),
 requester varchar(50) NOT NULL REFERENCES app_user(username),
 approver varchar(50) REFERENCES app_user(username), reason text NOT NULL, evidence text NOT NULL,
 rule_code varchar(40) NOT NULL, rule_version varchar(30) NOT NULL, dyeing_required boolean NOT NULL,
 review_revision bigint NOT NULL DEFAULT 1 CHECK(review_revision>0),
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0), sealed boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT now(), valid_until timestamptz NOT NULL,
 decided_at timestamptz, decision_reason text, decision_evidence text,
 revoked_at timestamptz, revocation_reason text,
 FOREIGN KEY(rule_code,rule_version) REFERENCES mix_rule(code,version),
 CHECK(approver IS NULL OR approver<>requester),
 CHECK((status='UNDER_REVIEW' AND approver IS NULL AND decided_at IS NULL)
  OR (status<>'UNDER_REVIEW' AND approver IS NOT NULL AND decided_at IS NOT NULL AND decision_reason IS NOT NULL AND decision_evidence IS NOT NULL)),
 CHECK(status<>'REVOKED' OR (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL))
);
CREATE TABLE mix_request_lot (
 request_id uuid NOT NULL REFERENCES mix_request(id), lot_id uuid NOT NULL REFERENCES lot(id),
 ordinal integer NOT NULL CHECK(ordinal>=0), quantity numeric(14,3) NOT NULL CHECK(quantity>0),
 lot_version_at_request bigint NOT NULL, reference varchar(40) NOT NULL,
 supplier_lot varchar(80) NOT NULL, production_date date NOT NULL,
 PRIMARY KEY(request_id,lot_id), UNIQUE(request_id,ordinal)
);
CREATE INDEX mix_items_by_lot ON mix_request_lot(lot_id,request_id);
CREATE TABLE mix_assessment (
 id uuid PRIMARY KEY, request_id uuid NOT NULL REFERENCES mix_request(id),
 role varchar(30) NOT NULL CHECK(role IN ('LAB_APPROVER','GEO','QUALITY','DYEING')),
 author varchar(50) NOT NULL REFERENCES app_user(username),
 outcome varchar(20) NOT NULL CHECK(outcome IN ('FAVORABLE','UNFAVORABLE')),
 reason text NOT NULL, evidence text NOT NULL, based_on_mix_version bigint NOT NULL,
 review_revision bigint NOT NULL, rule_version varchar(30) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(request_id,role)
);
CREATE TRIGGER mix_assessment_immutable BEFORE UPDATE OR DELETE ON mix_assessment
 FOR EACH ROW EXECUTE FUNCTION prevent_audit_change();
CREATE TABLE mix_event (
 id uuid PRIMARY KEY, mix_id uuid REFERENCES mix_request(id), actor varchar(50) NOT NULL REFERENCES app_user(username),
 role varchar(30) NOT NULL, event_type varchar(40) NOT NULL, from_state varchar(20), to_state varchar(20),
 reason text NOT NULL, evidence text NOT NULL, correlation_id uuid NOT NULL,
 rule_version varchar(30) NOT NULL, payload text NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX mix_events_by_request ON mix_event(mix_id,occurred_at);
CREATE TRIGGER mix_event_immutable BEFORE UPDATE OR DELETE ON mix_event
 FOR EACH ROW EXECUTE FUNCTION prevent_audit_change();
ALTER TABLE command_result ALTER COLUMN lot_id DROP NOT NULL;
ALTER TABLE command_result ADD COLUMN mix_id uuid REFERENCES mix_request(id);
ALTER TABLE command_result ADD CONSTRAINT command_target_xor CHECK((lot_id IS NULL)<>(mix_id IS NULL));

CREATE FUNCTION protect_mix_definition() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.montage_code,NEW.requester,NEW.reason,NEW.evidence,NEW.rule_code,NEW.rule_version,NEW.dyeing_required,NEW.review_revision,NEW.created_at,NEW.valid_until)
 IS DISTINCT FROM ROW(OLD.montage_code,OLD.requester,OLD.reason,OLD.evidence,OLD.rule_code,OLD.rule_version,OLD.dyeing_required,OLD.review_revision,OLD.created_at,OLD.valid_until)
 OR (OLD.sealed AND NOT NEW.sealed) THEN RAISE EXCEPTION 'Mix definition is immutable' USING ERRCODE='23514'; END IF;
 IF OLD.status IN ('DENIED','REVOKED') OR (OLD.status='AUTHORIZED' AND NEW.status<>'REVOKED') THEN
  RAISE EXCEPTION 'Terminal mix decision cannot be rewritten' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER mix_definition_immutable BEFORE UPDATE ON mix_request FOR EACH ROW EXECUTE FUNCTION protect_mix_definition();
CREATE FUNCTION protect_mix_items() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP<>'INSERT' OR EXISTS(SELECT 1 FROM mix_request WHERE id=NEW.request_id AND sealed) THEN
  RAISE EXCEPTION 'Mix items are immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER mix_items_immutable BEFORE INSERT OR UPDATE OR DELETE ON mix_request_lot
 FOR EACH ROW EXECUTE FUNCTION protect_mix_items();
CREATE FUNCTION check_mix_materials() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE bad boolean;
BEGIN
 IF TG_TABLE_NAME='mix_request' THEN
  SELECT EXISTS(SELECT 1 FROM mix_request m WHERE m.id=NEW.id AND m.status='AUTHORIZED'
   AND (NOT m.sealed OR (SELECT count(*) FROM mix_request_lot i WHERE i.request_id=m.id)<2
    OR EXISTS(SELECT 1 FROM mix_request_lot i JOIN lot l ON l.id=i.lot_id WHERE i.request_id=m.id
      AND (l.quality_state<>'RELEASED' OR l.inventory_state<>'AVAILABLE' OR l.version<>i.lot_version_at_request
       OR i.quantity>l.quantity OR EXISTS(SELECT 1 FROM inventory_hold h WHERE h.lot_id=l.id AND h.status='ACTIVE'))))) INTO bad;
 ELSE
  SELECT EXISTS(SELECT 1 FROM mix_request_lot i JOIN mix_request m ON m.id=i.request_id JOIN lot l ON l.id=i.lot_id
   WHERE i.lot_id=NEW.id AND m.status='AUTHORIZED'
    AND (l.quality_state<>'RELEASED' OR l.inventory_state<>'AVAILABLE' OR l.version<>i.lot_version_at_request
     OR i.quantity>l.quantity OR EXISTS(SELECT 1 FROM inventory_hold h WHERE h.lot_id=l.id AND h.status='ACTIVE'))) INTO bad;
 END IF;
 IF bad THEN RAISE EXCEPTION 'Authorized mix requires eligible unchanged lots' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER mix_material_guard AFTER INSERT OR UPDATE ON mix_request
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_mix_materials();
CREATE CONSTRAINT TRIGGER material_mix_guard AFTER UPDATE ON lot
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_mix_materials();
