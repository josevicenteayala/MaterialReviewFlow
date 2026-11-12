package com.proco.simulator.nonconformity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;

@Repository
public class NcStore {
    private final JdbcTemplate db;
    public NcStore(JdbcTemplate db) { this.db = db; }

    public List<NonConformity> list(UUID lot) {
        return db.query("select * from non_conformity where lot_id=? order by opened_at,id", (r,n) -> {
            var decided = r.getTimestamp("decided_at");
            UUID id = r.getObject("id",UUID.class);
            return new NonConformity(id, lot, r.getString("origin"), r.getString("status"), r.getString("responsible"),
                    r.getString("opened_by"), r.getString("reason"), r.getString("evidence"), r.getTimestamp("opened_at").toInstant(),
                    r.getString("disposition"), r.getString("decided_by"), decided == null ? null : decided.toInstant(),
                    r.getString("decision_reason"), r.getString("decision_evidence"),
                    db.queryForList("select * from nc_opinion where nc_id=? order by based_on_version,id", id));
        }, lot);
    }

    public Map<String,Object> get(UUID lot, UUID nc) {
        var rows = db.queryForList("select * from non_conformity where lot_id=? and id=?", lot, nc);
        require(!rows.isEmpty(),404,"NC inexistente en este lote");
        return rows.getFirst();
    }

    public Map<String,Object> latestOpinion(UUID nc) {
        var rows = db.queryForList("select * from nc_opinion where nc_id=? order by based_on_version desc limit 1", nc);
        require(!rows.isEmpty(),422,"Falta dictamen técnico de Laboratorio");
        return rows.getFirst();
    }
}
