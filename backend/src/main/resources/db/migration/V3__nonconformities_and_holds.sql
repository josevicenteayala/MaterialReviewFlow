-- Add schema only: no backfill or UPDATE of existing material, users or audit.
ALTER TABLE lot DROP CONSTRAINT lot_quality_state_check;
ALTER TABLE lot ADD CONSTRAINT lot_quality_state_check
    CHECK (quality_state IN ('UNDER_VERIFICATION','UNDER_TEST','HELD','RELEASED','REJECTED'));
ALTER TABLE lot ADD CONSTRAINT rejected_inventory_blocked
    CHECK (quality_state <> 'REJECTED' OR inventory_state = 'BLOCKED');

CREATE TABLE non_conformity (
    id uuid PRIMARY KEY,
    lot_id uuid NOT NULL REFERENCES lot(id),
    origin varchar(30) NOT NULL CHECK (origin IN ('DOCUMENTARY','LABORATORY','POST_RELEASE','OTHER')),
    status varchar(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED','REJECTED')),
    responsible varchar(50) NOT NULL REFERENCES app_user(username),
    opened_by varchar(50) NOT NULL REFERENCES app_user(username),
    reason text NOT NULL, evidence text NOT NULL,
    opened_at timestamptz NOT NULL DEFAULT now(),
    disposition varchar(30) CHECK (disposition IN ('LIFT_HOLD','RETURN_TO_SUPPLIER','SCRAP')),
    decided_by varchar(50) REFERENCES app_user(username),
    decided_at timestamptz, decision_reason text, decision_evidence text,
    UNIQUE(id, lot_id),
    CHECK ((status = 'OPEN' AND disposition IS NULL AND decided_by IS NULL AND decided_at IS NULL)
        OR (status <> 'OPEN' AND disposition IS NOT NULL AND decided_by IS NOT NULL
            AND decided_by <> opened_by AND decided_at IS NOT NULL
            AND decision_reason IS NOT NULL AND decision_evidence IS NOT NULL)),
    CHECK (status <> 'CLOSED' OR disposition = 'LIFT_HOLD'),
    CHECK (status <> 'REJECTED' OR disposition IN ('RETURN_TO_SUPPLIER','SCRAP'))
);
CREATE TABLE nc_opinion (
    id uuid PRIMARY KEY,
    nc_id uuid NOT NULL REFERENCES non_conformity(id),
    author varchar(50) NOT NULL REFERENCES app_user(username),
    outcome varchar(20) NOT NULL CHECK (outcome IN ('CONFORMING','NONCONFORMING')),
    reason text NOT NULL, evidence text NOT NULL,
    based_on_version bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER opinion_immutable BEFORE UPDATE OR DELETE ON nc_opinion
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_change();
CREATE TABLE inventory_hold (
    id uuid PRIMARY KEY,
    lot_id uuid NOT NULL REFERENCES lot(id),
    nc_id uuid,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','LIFTED')),
    opened_by varchar(50) NOT NULL REFERENCES app_user(username),
    reason text NOT NULL, evidence text NOT NULL,
    correlation_id uuid NOT NULL,
    opened_at timestamptz NOT NULL DEFAULT now(),
    closed_by varchar(50) REFERENCES app_user(username),
    closed_at timestamptz, close_reason text, close_evidence text,
    FOREIGN KEY (nc_id, lot_id) REFERENCES non_conformity(id, lot_id),
    CHECK ((status = 'ACTIVE' AND closed_by IS NULL AND closed_at IS NULL)
        OR (status = 'LIFTED' AND nc_id IS NOT NULL AND closed_by IS NOT NULL
            AND closed_at IS NOT NULL AND close_reason IS NOT NULL AND close_evidence IS NOT NULL))
);
CREATE INDEX active_holds_by_lot ON inventory_hold(lot_id) WHERE status = 'ACTIVE';
CREATE INDEX nc_by_lot ON non_conformity(lot_id, opened_at);
CREATE INDEX opinions_by_nc ON nc_opinion(nc_id, based_on_version DESC);

-- Both sides of the invariant are checked at COMMIT, within the same command transaction.
CREATE FUNCTION check_active_hold_inventory() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE material_id uuid;
BEGIN
    IF TG_TABLE_NAME = 'lot' THEN material_id := NEW.id;
    ELSE material_id := NEW.lot_id;
    END IF;
    IF EXISTS (SELECT 1 FROM inventory_hold h JOIN lot l ON l.id = h.lot_id
        WHERE h.lot_id = material_id AND h.status = 'ACTIVE' AND l.inventory_state <> 'BLOCKED') THEN
        RAISE EXCEPTION 'Active inventory holds require BLOCKED inventory' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER hold_inventory_guard AFTER INSERT OR UPDATE ON inventory_hold
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_active_hold_inventory();
CREATE CONSTRAINT TRIGGER lot_hold_guard AFTER INSERT OR UPDATE ON lot
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_active_hold_inventory();
