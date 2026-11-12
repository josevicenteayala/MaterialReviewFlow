package com.proco.simulator.mixing;

import com.proco.simulator.audit.AuditLog;
import com.proco.simulator.reception.*;
import com.proco.simulator.workflow.CommandRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;

@Service
public class MixService {
 private final JdbcTemplate db;private final CommandRunner runner;private final MixStore mixes;private final LotStore lots;private final AuditLog audit;private final Clock clock;
 public MixService(JdbcTemplate db,CommandRunner runner,MixStore mixes,LotStore lots,AuditLog audit,Clock clock){this.db=db;this.runner=runner;this.mixes=mixes;this.lots=lots;this.audit=audit;this.clock=clock;}
 public MixRequest execute(UUID id,String action,MixCommand c,UUID key,String actor,String role){
  String fingerprint="MIX:"+id+":"+action+":"+c;
  return runner.runMix(id,key,actor,role,fingerprint,()->{
   String expected=switch(action){case "create"->"PRODUCTION_REQUESTER";case "assessments"->"LAB_APPROVER,GEO,QUALITY,DYEING";case "decision"->"PRODUCTION_APPROVER";default->"";};
   require(!expected.isEmpty(),404,"Acción de mezcla inexistente");require(Arrays.asList(expected.split(",")).contains(role),403,"Rol no autorizado para esta acción de mezcla");
   if(action.equals("create"))return create(c,key,actor,role,fingerprint);
   List<UUID> ids=mixes.lotIds(id);require(!ids.isEmpty(),404,"Solicitud de mezcla inexistente");lockLots(ids);
   db.queryForList("select id from mix_request where id=? for update",id);
   MixRequest mix=mixes.get(id);require(mix.version()==c.version(),409,"Versión de mezcla desactualizada");MixPolicy.underReview(mix);
   String next="UNDER_REVIEW",event;
   if(action.equals("assessments")){
    require(mix.materialBlockers().isEmpty(),422,String.join("; ",mix.materialBlockers()));
    require(Set.of("FAVORABLE","UNFAVORABLE").contains(c.outcome()==null?"":c.outcome()),422,"Concepto debe ser FAVORABLE o UNFAVORABLE");
    require(db.queryForObject("select count(*) from mix_assessment where request_id=? and role=?",Integer.class,id,role)==0,409,"El área ya emitió concepto; una corrección requiere nueva solicitud");
    db.update("insert into mix_assessment(id,request_id,role,author,outcome,reason,evidence,based_on_mix_version,review_revision,rule_version) values (?,?,?,?,?,?,?,?,?,?)",
     UUID.randomUUID(),id,role,actor,c.outcome(),c.reason(),c.evidence(),mix.version(),mix.reviewRevision(),mix.ruleVersion());event="ASSESSED";
   }else{
    MixPolicy.independent(mix.requester(),actor);
    require(Set.of("AUTHORIZE","DENY").contains(c.decision()==null?"":c.decision()),422,"Decisión debe ser AUTHORIZE o DENY");
    if(c.decision().equals("AUTHORIZE"))require(mix.blockers().isEmpty(),422,String.join("; ",mix.blockers()));
    next=c.decision().equals("AUTHORIZE")?"AUTHORIZED":"DENIED";event=next;
    require(db.update("update mix_request set status=?,approver=?,decided_at=now(),decision_reason=?,decision_evidence=?,version=version+1 where id=? and version=?",next,actor,c.reason(),c.evidence(),id,c.version())==1,409,"Conflicto de versión de mezcla");
   }
   if(action.equals("assessments"))require(db.update("update mix_request set version=version+1 where id=? and version=?",id,c.version())==1,409,"Conflicto de versión de mezcla");
   audit.appendMix(id,actor,role,event,mix.status(),next,c.reason(),c.evidence(),key,fingerprint);return mixes.get(id);
  },mixes::get,MixRequest::id);
 }
 private MixRequest create(MixCommand c,UUID key,String actor,String role,String payload){
  require(c.version()==0,409,"Solicitud nueva requiere versión 0");
  require(c.montageCode()!=null&&!c.montageCode().isBlank()&&c.validUntil()!=null&&c.validUntil().isAfter(clock.instant()),422,"Montaje y vigencia futura son obligatorios");
  require(c.items()!=null&&c.items().size()>=2,422,"Se requieren al menos dos lotes diferentes");
  var ids=c.items().stream().map(MixCommand.Item::lotId).toList();require(new HashSet<>(ids).size()==ids.size(),422,"No se permite repetir un lote en la mezcla");
  lockLots(ids);List<Lot> material=new ArrayList<>();
  for(var item:c.items()){Lot lot=lots.get(item.lotId());require(MixPolicy.eligible(lot,item.quantity()),422,"Lote no elegible o cantidad excedida: "+lot.supplierLot());material.add(lot);}
  MixRule rule=MixStore.selectRule(material.stream().map(Lot::reference).toList(),mixes.referenceRules());UUID id=UUID.randomUUID();
  db.update("insert into mix_request(id,montage_code,requester,reason,evidence,rule_code,rule_version,dyeing_required,valid_until) values (?,?,?,?,?,?,?,?,?)",
   id,c.montageCode(),actor,c.reason(),c.evidence(),rule.code(),rule.version(),rule.dyeingRequired(),java.sql.Timestamp.from(c.validUntil()));
  for(int i=0;i<c.items().size();i++){var item=c.items().get(i);Lot lot=material.get(i);
   db.update("insert into mix_request_lot(request_id,lot_id,ordinal,quantity,lot_version_at_request,reference,supplier_lot,production_date) values (?,?,?,?,?,?,?,?)",
    id,lot.id(),i,item.quantity(),lot.version(),lot.reference(),lot.supplierLot(),lot.productionDate());}
  db.update("update mix_request set sealed=true where id=?",id);audit.appendMix(id,actor,role,"REQUESTED",null,"UNDER_REVIEW",c.reason(),c.evidence(),key,payload);return mixes.get(id);
 }
 private void lockLots(Collection<UUID> ids){
  for(UUID id:ids.stream().sorted(Comparator.comparing(UUID::toString)).toList())require(!db.queryForList("select id from lot where id=? for update",id).isEmpty(),404,"Lote inexistente: "+id);
 }
}
