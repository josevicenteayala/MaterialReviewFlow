package com.proco.simulator.inventory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class HoldStore {
    private final JdbcTemplate db;
    public HoldStore(JdbcTemplate db) { this.db = db; }

    public void place(UUID lot, UUID nc, String actor, String reason, String evidence, UUID correlation) {
        db.update("insert into inventory_hold(id,lot_id,nc_id,opened_by,reason,evidence,correlation_id) values (?,?,?,?,?,?,?)",
                UUID.randomUUID(), lot, nc, actor, reason, evidence, correlation);
        db.update("update lot set inventory_state='BLOCKED' where id=?", lot);
    }

    public void formalize(UUID lot, UUID nc, String actor, String reason, String evidence, UUID correlation) {
        int associated = db.update("update inventory_hold set nc_id=? where lot_id=? and nc_id is null and status='ACTIVE'", nc, lot);
        if (associated == 0) place(lot, nc, actor, reason, evidence, correlation);
        else db.update("update lot set inventory_state='BLOCKED' where id=?", lot);
    }

    public void lift(UUID lot, UUID nc, String actor, String reason, String evidence) {
        db.update("update inventory_hold set status='LIFTED',closed_by=?,closed_at=now(),close_reason=?,close_evidence=? where lot_id=? and nc_id=? and status='ACTIVE'",
                actor, reason, evidence, lot, nc);
        // Explicit decision returns to quarantine, never directly to AVAILABLE.
        db.update("update lot set inventory_state=case when exists(select 1 from inventory_hold where lot_id=? and status='ACTIVE') then 'BLOCKED' else 'QUARANTINE' end where id=?", lot, lot);
    }

    public List<Map<String, Object>> list(UUID lot) {
        return db.queryForList("select * from inventory_hold where lot_id=? order by opened_at,id", lot);
    }
}
