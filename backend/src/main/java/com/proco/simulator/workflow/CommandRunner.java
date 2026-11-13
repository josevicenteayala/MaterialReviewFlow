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
import java.util.function.Function;
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
        try { return transact(key,actor,fingerprint,"lot_id",command,lots::get,Lot::id); }
        catch (RuleException ex) {
            audit.denied(lotId,actor,role,ex.getMessage(),key,fingerprint);
            throw ex;
        }
    }

    public <T> T runMix(UUID mixId, UUID key, String actor, String role, String fingerprint,
            Supplier<T> command, Function<UUID,T> load, Function<T,UUID> identify) {
        try { return transact(key,actor,fingerprint,"mix_id",command,load,identify); }
        catch (RuleException ex) {
            audit.deniedMix(mixId,actor,role,ex.getMessage(),key,fingerprint);
            throw ex;
        }
    }

    /** Internal allowlist only: target column never originates in a request. */
    public <T> T runExecution(UUID id, boolean task, UUID key, String actor, String role, String fingerprint,
            Supplier<T> command, Function<UUID,T> load, Function<T,UUID> identify) {
        try { return transact(key,actor,fingerprint,task?"task_id":"execution_id",command,load,identify); }
        catch (RuleException ex) {
            audit.deniedExecution(id,task,actor,role,ex.getMessage(),key,fingerprint);
            throw ex;
        }
    }

    private <T> T transact(UUID key,String actor,String fingerprint,String target,
            Supplier<T> command,Function<UUID,T> load,Function<T,UUID> identify) {
        return tx.execute(status -> {
            db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",actor+key);
            var replay=db.queryForList("select fingerprint,lot_id,mix_id,execution_id,task_id from command_result where actor=? and command_key=?",actor,key);
            if(!replay.isEmpty()) {
                var row=replay.getFirst();
                require(fingerprint.equals(row.get("fingerprint")) && row.get(target)!=null,409,
                        "Clave de idempotencia reutilizada con otro comando");
                return load.apply((UUID)row.get(target));
            }
            T result=command.get();
            UUID id=identify.apply(result);
            db.update("insert into command_result(actor,command_key,fingerprint,"+target+") values (?,?,?,?)",
                    actor,key,fingerprint,id);
            return result;
        });
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
