package com.proco.simulator.mixing;

import com.proco.simulator.audit.AuditLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** Caller already owns the lot lock. Never acquires another lot after a mix lock. */
@Service
public class MixRevocations {
 private final JdbcTemplate db;private final AuditLog audit;private final com.proco.simulator.execution.ExecutionContainment containment;
 public MixRevocations(JdbcTemplate db,AuditLog audit,com.proco.simulator.execution.ExecutionContainment containment){this.db=db;this.audit=audit;this.containment=containment;}
 public void revoke(UUID lot,String actor,String reason,String evidence,UUID correlation,String cause){revoke(lot,null,actor,reason,evidence,correlation,cause);}
 public void revoke(UUID lot,UUID nc,String actor,String reason,String evidence,UUID correlation,String cause){
  var affected=db.queryForList("""
    select m.id,m.status from mix_request m where exists(select 1 from mix_request_lot i where i.request_id=m.id and i.lot_id=?)
    order by m.id for update
    """,lot);
  if(affected.isEmpty())return;
  String role=db.queryForObject("select role from app_user where username=?",String.class,actor);
  for(var row:affected){UUID id=(UUID)row.get("id");
   if("AUTHORIZED".equals(row.get("status"))){db.update("update mix_request set status='REVOKED',version=version+1,revoked_at=now(),revocation_reason=? where id=?",reason,id);
   audit.appendMix(id,actor,role,"REVOKED","AUTHORIZED","REVOKED",reason,evidence,correlation,"lotId="+lot+"; cause="+cause);}
   containment.affect(lot,id,nc,actor,role,reason,evidence,correlation,cause);
  }
 }
}
