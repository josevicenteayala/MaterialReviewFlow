-- Additive Sprint 4. No historical material, mixture or audit rows are rewritten.
CREATE TABLE montage_execution (
 id uuid PRIMARY KEY, mix_request_id uuid NOT NULL UNIQUE REFERENCES mix_request(id),
 montage_code varchar(80) NOT NULL, status varchar(20) NOT NULL CHECK(status IN ('IN_PROGRESS','SUSPENDED','COMPLETED','CANCELLED')),
 started_by varchar(50) NOT NULL REFERENCES app_user(username), started_at timestamptz NOT NULL DEFAULT now(),
 start_reason text NOT NULL, start_evidence text NOT NULL,
 suspended_at timestamptz, suspension_reason text, ended_at timestamptz,
 ended_by varchar(50) REFERENCES app_user(username), disposition text, end_evidence text,
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 CHECK(status<>'SUSPENDED' OR (suspended_at IS NOT NULL AND suspension_reason IS NOT NULL)),
 CHECK((status IN ('COMPLETED','CANCELLED'))=(ended_at IS NOT NULL AND ended_by IS NOT NULL AND disposition IS NOT NULL AND end_evidence IS NOT NULL))
);
CREATE UNIQUE INDEX one_running_montage ON montage_execution(montage_code) WHERE status='IN_PROGRESS';
CREATE TABLE containment_task (
 id uuid PRIMARY KEY, source_event_id uuid NOT NULL, nc_id uuid REFERENCES non_conformity(id),
 lot_id uuid NOT NULL REFERENCES lot(id), mix_id uuid NOT NULL REFERENCES mix_request(id),
 execution_id uuid NOT NULL REFERENCES montage_execution(id),
 task_type varchar(40) NOT NULL CHECK(task_type IN ('CONTAINMENT','RETROSPECTIVE_REVIEW','REJECTION_COORDINATION')),
 target_role varchar(30) NOT NULL CHECK(target_role IN ('WAREHOUSE','QUALITY','PRODUCTION_REQUESTER','GEO','DYEING','PROCUREMENT')),
 status varchar(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','ACKNOWLEDGED','CLOSED')),
 created_at timestamptz NOT NULL DEFAULT now(), reason text NOT NULL, evidence text NOT NULL,
 acknowledged_by varchar(50) REFERENCES app_user(username), acknowledged_at timestamptz, ack_reason text, ack_evidence text,
 closed_by varchar(50) REFERENCES app_user(username), closed_at timestamptz, close_reason text, close_evidence text,
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 UNIQUE(source_event_id,execution_id,task_type,target_role),
 CHECK((status<>'OPEN')=(acknowledged_at IS NOT NULL AND acknowledged_by IS NOT NULL AND ack_reason IS NOT NULL AND ack_evidence IS NOT NULL)),
 CHECK((status='CLOSED')=(closed_at IS NOT NULL AND closed_by IS NOT NULL AND close_reason IS NOT NULL AND close_evidence IS NOT NULL)),
 CHECK(closed_by IS NULL OR closed_by=acknowledged_by)
);
CREATE INDEX tasks_by_area ON containment_task(target_role,status,created_at);
CREATE INDEX tasks_by_execution ON containment_task(execution_id,id);
CREATE TABLE execution_event (
 id uuid PRIMARY KEY, execution_id uuid REFERENCES montage_execution(id), task_id uuid REFERENCES containment_task(id),
 lot_id uuid REFERENCES lot(id), mix_id uuid REFERENCES mix_request(id), nc_id uuid REFERENCES non_conformity(id),
 actor varchar(50) NOT NULL REFERENCES app_user(username), role varchar(30) NOT NULL,
 event_type varchar(40) NOT NULL, from_state varchar(20), to_state varchar(20),
 reason text NOT NULL, evidence text NOT NULL, correlation_id uuid NOT NULL,
 rule_version varchar(30) NOT NULL DEFAULT 'SIM-4', payload text NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX execution_events_by_execution ON execution_event(execution_id,occurred_at);
CREATE TRIGGER execution_event_immutable BEFORE UPDATE OR DELETE ON execution_event FOR EACH ROW EXECUTE FUNCTION prevent_audit_change();
ALTER TABLE command_result ADD COLUMN execution_id uuid REFERENCES montage_execution(id);
ALTER TABLE command_result ADD COLUMN task_id uuid REFERENCES containment_task(id);
ALTER TABLE command_result DROP CONSTRAINT command_target_xor;
ALTER TABLE command_result ADD CONSTRAINT command_target_xor CHECK(num_nonnulls(lot_id,mix_id,execution_id,task_id)=1);

CREATE FUNCTION protect_execution() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Execution history is retained' USING ERRCODE='23514'; END IF;
 IF ROW(NEW.id,NEW.mix_request_id,NEW.montage_code,NEW.started_by,NEW.started_at,NEW.start_reason,NEW.start_evidence)
 IS DISTINCT FROM ROW(OLD.id,OLD.mix_request_id,OLD.montage_code,OLD.started_by,OLD.started_at,OLD.start_reason,OLD.start_evidence)
 OR OLD.status IN ('COMPLETED','CANCELLED') OR NEW.version<>OLD.version+1
 OR (OLD.status='SUSPENDED' AND NEW.status='IN_PROGRESS') THEN
 RAISE EXCEPTION 'Invalid execution mutation' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER execution_guard BEFORE UPDATE OR DELETE ON montage_execution FOR EACH ROW EXECUTE FUNCTION protect_execution();
CREATE FUNCTION protect_containment_task() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Tasks are retained' USING ERRCODE='23514'; END IF;
 IF ROW(NEW.id,NEW.source_event_id,NEW.nc_id,NEW.lot_id,NEW.mix_id,NEW.execution_id,NEW.task_type,NEW.target_role,NEW.created_at,NEW.reason,NEW.evidence)
 IS DISTINCT FROM ROW(OLD.id,OLD.source_event_id,OLD.nc_id,OLD.lot_id,OLD.mix_id,OLD.execution_id,OLD.task_type,OLD.target_role,OLD.created_at,OLD.reason,OLD.evidence)
 OR NEW.version<>OLD.version+1 OR NOT ((OLD.status='OPEN' AND NEW.status='ACKNOWLEDGED') OR (OLD.status='ACKNOWLEDGED' AND NEW.status='CLOSED'))
 OR (OLD.status='ACKNOWLEDGED' AND ROW(NEW.acknowledged_by,NEW.acknowledged_at,NEW.ack_reason,NEW.ack_evidence) IS DISTINCT FROM ROW(OLD.acknowledged_by,OLD.acknowledged_at,OLD.ack_reason,OLD.ack_evidence)) THEN
 RAISE EXCEPTION 'Invalid task mutation' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER containment_task_guard BEFORE UPDATE OR DELETE ON containment_task FOR EACH ROW EXECUTE FUNCTION protect_containment_task();
-- Deferred guard: a revoked authorization cannot retain a running simulated execution.
CREATE FUNCTION check_execution_authorization() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE mix uuid;
BEGIN
 IF TG_TABLE_NAME='mix_request' THEN mix:=NEW.id; ELSE mix:=NEW.mix_request_id; END IF;
 IF EXISTS(SELECT 1 FROM montage_execution e JOIN mix_request m ON m.id=e.mix_request_id
 WHERE e.mix_request_id=mix AND e.status='IN_PROGRESS' AND m.status<>'AUTHORIZED') THEN
 RAISE EXCEPTION 'Running execution requires authorized mix' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER execution_authorization_guard AFTER INSERT OR UPDATE ON montage_execution DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_execution_authorization();
CREATE CONSTRAINT TRIGGER revoked_execution_guard AFTER UPDATE ON mix_request DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_execution_authorization();
