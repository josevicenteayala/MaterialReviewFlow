package com.proco.simulator.workflow;

import com.proco.simulator.audit.AuditLog;
import com.proco.simulator.reception.Lot;
import com.proco.simulator.reception.LotStore;
import com.proco.simulator.shared.RuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.function.Supplier;
import static com.proco.simulator.shared.RuleException.require;

/** All commands lock their key before the aggregate, including NC commands. */
@Service
public class CommandRunner {
    private final JdbcTemplate db;
    private final LotStore lots;
    private final AuditLog audit;
    private final TransactionTemplate tx;

    public CommandRunner(JdbcTemplate db, LotStore lots, AuditLog audit, PlatformTransactionManager tm) {
        this.db = db; this.lots = lots; this.audit = audit; this.tx = new TransactionTemplate(tm);
    }

    public Lot run(UUID lotId, UUID key, String actor, String role, String fingerprint, Supplier<Lot> command) {
        try {
            return tx.execute(status -> {
                db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", actor + key);
                var replay = db.queryForList("select fingerprint,lot_id from command_result where actor=? and command_key=?", actor, key);
                if (!replay.isEmpty()) {
                    require(fingerprint.equals(replay.getFirst().get("fingerprint")), 409,
                            "Clave de idempotencia reutilizada con otro comando");
                    return lots.get((UUID) replay.getFirst().get("lot_id"));
                }
                Lot result = command.get();
                db.update("insert into command_result values (?,?,?,?)", actor, key, fingerprint, result.id());
                return result;
            });
        } catch (RuleException ex) {
            // Called only AFTER the failed transaction has released aggregate locks.
            audit.denied(lotId, actor, role, ex.getMessage(), key, fingerprint);
            throw ex;
        }
    }

    public Lot lock(UUID id, long version) {
        require(id != null && !db.queryForList("select id from lot where id=? for update", id).isEmpty(), 404, "Lote inexistente");
        Lot lot = lots.get(id);
        require(lot.version() == version, 409, "Versión desactualizada; actualiza el expediente");
        return lot;
    }

    public Lot advance(UUID id, long version) {
        require(db.update("update lot set version=version+1 where id=? and version=?", id, version) == 1,
                409, "Conflicto de versión del expediente");
        return lots.get(id);
    }
}
