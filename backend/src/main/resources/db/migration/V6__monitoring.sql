-- Sprint 5: historical V5 values and audit records remain unchanged.
ALTER TABLE montage_execution ALTER COLUMN status TYPE varchar(32);
ALTER TABLE montage_execution DROP CONSTRAINT montage_execution_status_check;
ALTER TABLE montage_execution ADD CONSTRAINT montage_execution_status_check CHECK(status IN ('IN_PROGRESS','SUSPENDED','COMPLETED','CANCELLED','CLOSED_WITH_INCIDENT'));
DO $$ DECLARE name text; BEGIN
 FOR name IN SELECT conname FROM pg_constraint WHERE conrelid='montage_execution'::regclass AND contype='c' AND pg_get_constraintdef(oid) LIKE '%ended_at%'
 LOOP EXECUTE format('ALTER TABLE montage_execution DROP CONSTRAINT %I',name); END LOOP;
END $$;
ALTER TABLE montage_execution ADD CONSTRAINT execution_terminal_details CHECK((status IN ('COMPLETED','CANCELLED','CLOSED_WITH_INCIDENT'))=(ended_at IS NOT NULL AND ended_by IS NOT NULL AND disposition IS NOT NULL AND end_evidence IS NOT NULL));
CREATE OR REPLACE FUNCTION protect_execution() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Execution history is retained' USING ERRCODE='23514'; END IF;
 IF ROW(NEW.id,NEW.mix_request_id,NEW.montage_code,NEW.started_by,NEW.started_at,NEW.start_reason,NEW.start_evidence)
 IS DISTINCT FROM ROW(OLD.id,OLD.mix_request_id,OLD.montage_code,OLD.started_by,OLD.started_at,OLD.start_reason,OLD.start_evidence)
 OR OLD.status IN ('COMPLETED','CANCELLED','CLOSED_WITH_INCIDENT') OR NEW.version<>OLD.version+1
 OR NOT ((OLD.status='IN_PROGRESS' AND NEW.status IN ('SUSPENDED','COMPLETED','CANCELLED')) OR (OLD.status='SUSPENDED' AND NEW.status IN ('CLOSED_WITH_INCIDENT','CANCELLED'))) THEN
 RAISE EXCEPTION 'Invalid execution mutation' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;

CREATE TABLE ola_rule (
 id uuid PRIMARY KEY, task_type varchar(40) NOT NULL CHECK(task_type IN ('CONTAINMENT','RETROSPECTIVE_REVIEW','REJECTION_COORDINATION')),
 target_role varchar(30) NOT NULL CHECK(target_role IN ('WAREHOUSE','QUALITY','PRODUCTION_REQUESTER','GEO','DYEING','PROCUREMENT')),
 version bigint NOT NULL CHECK(version>0), label varchar(20) NOT NULL DEFAULT 'DEMO' CHECK(label='DEMO'),
 ack_minutes integer CHECK(ack_minutes BETWEEN 1 AND 525600), close_minutes integer CHECK(close_minutes BETWEEN 1 AND 525600),
 due_soon_minutes integer NOT NULL CHECK(due_soon_minutes BETWEEN 0 AND 10080),
 reason text NOT NULL, evidence text NOT NULL, created_by varchar(50) REFERENCES app_user(username),
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(task_type,target_role,version)
);
CREATE TRIGGER ola_rule_immutable BEFORE UPDATE OR DELETE ON ola_rule FOR EACH ROW EXECUTE FUNCTION prevent_audit_change();
INSERT INTO ola_rule(id,task_type,target_role,version,ack_minutes,close_minutes,due_soon_minutes,reason,evidence)
SELECT md5('DEMO-OLA-1:'||r)::uuid,'CONTAINMENT',r,1,30,240,10,'SOLO SIMULACIÓN — minutos corridos ficticios, no OLA aprobado','DEMO-OLA-1'
FROM unnest(ARRAY['WAREHOUSE','QUALITY','PRODUCTION_REQUESTER','GEO','DYEING']) r;
INSERT INTO ola_rule(id,task_type,target_role,version,ack_minutes,close_minutes,due_soon_minutes,reason,evidence)
VALUES ('55555555-5555-4555-8555-555555555555','REJECTION_COORDINATION','PROCUREMENT',1,60,480,10,'SOLO SIMULACIÓN — coordinación ficticia','DEMO-OLA-1');
ALTER TABLE containment_task ADD COLUMN ola_rule_id uuid REFERENCES ola_rule(id);
ALTER TABLE containment_task ADD COLUMN ack_due_at timestamptz;
ALTER TABLE containment_task ADD COLUMN close_due_at timestamptz;
ALTER TABLE containment_task ADD COLUMN ola_due_soon_minutes integer;
CREATE FUNCTION snapshot_task_ola() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rule ola_rule%ROWTYPE;
BEGIN
 SELECT * INTO rule FROM ola_rule WHERE task_type=NEW.task_type AND target_role=NEW.target_role ORDER BY version DESC LIMIT 1;
 NEW.ola_rule_id:=rule.id; NEW.ola_due_soon_minutes:=rule.due_soon_minutes;
 NEW.ack_due_at:=NEW.created_at+rule.ack_minutes*interval '1 minute';
 NEW.close_due_at:=NEW.created_at+rule.close_minutes*interval '1 minute';
 RETURN NEW;
END $$;
CREATE TRIGGER task_ola_snapshot BEFORE INSERT ON containment_task FOR EACH ROW EXECUTE FUNCTION snapshot_task_ola();
CREATE FUNCTION protect_task_ola() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.ola_rule_id,NEW.ack_due_at,NEW.close_due_at,NEW.ola_due_soon_minutes) IS DISTINCT FROM ROW(OLD.ola_rule_id,OLD.ack_due_at,OLD.close_due_at,OLD.ola_due_soon_minutes) THEN
 RAISE EXCEPTION 'Task OLA snapshot is immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER task_ola_immutable BEFORE UPDATE ON containment_task FOR EACH ROW EXECUTE FUNCTION protect_task_ola();
ALTER TABLE execution_event ADD COLUMN ola_rule_id uuid REFERENCES ola_rule(id);
ALTER TABLE command_result ADD COLUMN ola_rule_id uuid REFERENCES ola_rule(id);
ALTER TABLE command_result DROP CONSTRAINT command_target_xor;
ALTER TABLE command_result ADD CONSTRAINT command_target_xor CHECK(num_nonnulls(lot_id,mix_id,execution_id,task_id,ola_rule_id)=1);
CREATE INDEX tasks_by_created_id ON containment_task(created_at DESC,id DESC);
CREATE INDEX execution_by_started ON montage_execution(started_at);
CREATE INDEX nc_by_opened ON non_conformity(opened_at);
CREATE INDEX mix_by_created ON mix_request(created_at);
