package com.proco.simulator.nonconformity;

import com.proco.simulator.audit.AuditLog;
import com.proco.simulator.inventory.HoldStore;
import com.proco.simulator.mixing.MixRevocations;
import com.proco.simulator.reception.*;
import com.proco.simulator.shared.RuleException;
import com.proco.simulator.workflow.CommandRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;

@Service
public class NcService {
    private final JdbcTemplate db;
    private final NcStore cases;
    private final HoldStore holds;
    private final CommandRunner runner;
    private final AuditLog audit;
    private final MixRevocations revocations;
    private final com.proco.simulator.supplier.SupplierCaseRegistry supplierCases;

    public NcService(JdbcTemplate db, NcStore cases, HoldStore holds, CommandRunner runner, AuditLog audit,MixRevocations revocations,com.proco.simulator.supplier.SupplierCaseRegistry supplierCases) {this.supplierCases=supplierCases;
        this.db=db; this.cases=cases; this.holds=holds; this.runner=runner; this.audit=audit;this.revocations=revocations;
    }

    public Lot execute(UUID lotId, UUID ncId, String action, NcCommand c, UUID key, String actor, String role) {
        String fingerprint="NC:"+lotId+":"+ncId+":"+action+":"+c;
        return runner.run(lotId,key,actor,role,fingerprint,()->{
            String permission=switch(action) {
                case "open" -> "WAREHOUSE,QUALITY,LAB_TECH,LAB_APPROVER,PRODUCTION_REQUESTER,PRODUCTION_APPROVER,GEO,DYEING";
                case "opinion" -> "LAB_APPROVER";
                case "resolve","reject" -> "QUALITY";
                default -> throw new RuleException(404,"Acción de NC inexistente");
            };
            require(Arrays.asList(permission.split(",")).contains(role),403,"Rol no autorizado para esta acción de NC");
            Lot lot=runner.lock(lotId,c.version());
            NcPolicy.mutable(lot);
            UUID caseId=ncId;
            if(action.equals("open")) {
                require(c.origin()!=null && Set.of("DOCUMENTARY","LABORATORY","POST_RELEASE","OTHER").contains(c.origin()),422,"Origen de NC inválido");
                require(!"POST_RELEASE".equals(c.origin()) || lot.qualityState().equals("RELEASED"),422,"Origen posterior requiere lote liberado");
                require(c.responsible()!=null && db.queryForObject("select count(*) from app_user where username=? and role='QUALITY'",Integer.class,c.responsible())==1,422,"El responsable debe ser un usuario de Calidad existente");
                require(!actor.equals(c.responsible()),422,"Asigna un responsable distinto del reportante para permitir decisión independiente");
                caseId=UUID.randomUUID();
                db.update("insert into non_conformity(id,lot_id,origin,responsible,opened_by,reason,evidence) values (?,?,?,?,?,?,?)",
                        caseId,lotId,c.origin(),c.responsible(),actor,c.reason(),c.evidence());
                holds.formalize(lotId,caseId,actor,c.reason(),c.evidence(),key);
            } else {
                var nc=cases.get(lotId,caseId);
                require("OPEN".equals(nc.get("status")),409,"La NC ya tiene decisión definitiva");
                if(action.equals("opinion")) {
                    require(c.outcome()!=null && Set.of("CONFORMING","NONCONFORMING").contains(c.outcome()),422,"Resultado de dictamen inválido");
                    db.update("insert into nc_opinion(id,nc_id,author,outcome,reason,evidence,based_on_version) values (?,?,?,?,?,?,?)",
                            UUID.randomUUID(),caseId,actor,c.outcome(),c.reason(),c.evidence(),lot.version()+1);
                } else {
                    var opinion=cases.latestOpinion(caseId);
                    boolean lift=action.equals("resolve");
                    NcPolicy.decision(lot,nc,opinion,actor,lift?"CONFORMING":"NONCONFORMING");
                    String disposition;
                    if(lift) {
                        NcPolicy.liftable(lot);
                        require("LIFT_HOLD".equals(c.disposition()),422,"El levantamiento debe indicar LIFT_HOLD");
                        holds.lift(lotId,caseId,actor,c.reason(),c.evidence());
                        disposition="LIFT_HOLD";
                    } else {
                        require(c.disposition()!=null && Set.of("RETURN_TO_SUPPLIER","SCRAP").contains(c.disposition()),422,"Indica disposición RETURN_TO_SUPPLIER o SCRAP");
                        db.update("update lot set quality_state='REJECTED',inventory_state='BLOCKED' where id=?",lotId);
                        revocations.revoke(lotId,caseId,actor,c.reason(),c.evidence(),key,"REJECTED");
                        disposition=c.disposition();
                    }
                    db.update("update non_conformity set status=?,disposition=?,decided_by=?,decided_at=now(),decision_reason=?,decision_evidence=? where id=?",
                            lift?"CLOSED":"REJECTED",disposition,actor,c.reason(),c.evidence(),caseId);
                    if(!lift && disposition.equals("RETURN_TO_SUPPLIER"))supplierCases.create(lotId,caseId,actor,role,c.reason(),c.evidence(),key,false);
                }
            }
            Lot result=runner.advance(lotId,lot.version());
            audit.append(lotId,actor,role,"NC_"+action.toUpperCase(),state(lot),state(result),c.reason(),c.evidence(),key,
                    "ncId="+caseId+"; "+fingerprint,"SIM-2");
            return result;
        });
    }
    private static String state(Lot lot) { return lot.qualityState()+" / "+lot.inventoryState(); }
}
