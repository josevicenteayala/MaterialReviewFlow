package com.proco.simulator.mixing;

import com.proco.simulator.shared.RuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static com.proco.simulator.shared.RuleException.require;

@Repository
public class MixStore {
 private final JdbcTemplate db;private final Clock clock;
 public MixStore(JdbcTemplate db,Clock clock){this.db=db;this.clock=clock;}
 public Map<String,MixRule> referenceRules(){
  Map<String,MixRule> rules=new HashMap<>();
  for(var r:db.queryForList("select rr.reference,r.* from mix_reference_rule rr join mix_rule r on r.code=rr.rule_code and r.version=rr.rule_version"))
   rules.put((String)r.get("reference"),new MixRule((String)r.get("code"),(String)r.get("version"),(Boolean)r.get("dyeing_required"),(String)r.get("description")));
  return rules;
 }
 public static MixRule selectRule(Collection<String> refs,Map<String,MixRule> rules){
  MixRule chosen=null;
  for(String ref:refs){MixRule rule=rules.get(ref);require(rule!=null,422,"Referencia sin regla ficticia de mezcla: "+ref);if(chosen==null||rule.dyeingRequired())chosen=rule;}
  require(chosen!=null,422,"Faltan referencias de mezcla");return chosen;
 }
 public List<UUID> lotIds(UUID mix){return db.queryForList("select lot_id from mix_request_lot where request_id=? order by lot_id",UUID.class,mix);}
 public MixRequest get(UUID id){
  var rows=db.queryForList("select * from mix_request where id=?",id);require(!rows.isEmpty(),404,"Solicitud de mezcla inexistente");
  return view(rows.getFirst(),db.queryForList(ITEM_SQL+" where i.request_id=? order by i.ordinal",id),
   db.queryForList("select * from mix_assessment where request_id=? order by based_on_mix_version,id",id),referenceRules(),clock.instant());
 }
 public List<MixRequest> all(){
  var rows=db.queryForList("select * from mix_request order by created_at desc,id");if(rows.isEmpty())return List.of();
  var items=group(db.queryForList(ITEM_SQL+" order by i.request_id,i.ordinal"));
  var assessments=group(db.queryForList("select * from mix_assessment order by request_id,based_on_mix_version,id"));
  var rules=referenceRules();Instant now=clock.instant();
  return rows.stream().map(r->view(r,items.getOrDefault(r.get("id"),List.of()),assessments.getOrDefault(r.get("id"),List.of()),rules,now)).toList();
 }
 private static Map<Object,List<Map<String,Object>>> group(List<Map<String,Object>> rows){
  Map<Object,List<Map<String,Object>>> grouped=new HashMap<>();for(var row:rows)grouped.computeIfAbsent(row.get("request_id"),k->new ArrayList<>()).add(row);return grouped;
 }
 private static final String ITEM_SQL="""
  select i.*,l.quality_state,l.inventory_state,l.version as current_lot_version,l.quantity as lot_quantity,
   (select count(*) from inventory_hold h where h.lot_id=l.id and h.status='ACTIVE') as active_holds
  from mix_request_lot i join lot l on l.id=i.lot_id
  """;
 private MixRequest view(Map<String,Object> r,List<Map<String,Object>> items,List<Map<String,Object>> assessments,Map<String,MixRule> rules,Instant now){
  List<String> material=new ArrayList<>();
  if(!instant(r.get("valid_until")).isAfter(now))material.add("Vigencia vencida");
  if(items.size()<2)material.add("Se requieren dos lotes diferentes");
  for(var item:items){
   String prefix="Lote "+item.get("supplier_lot")+": ";
   if(!"RELEASED".equals(item.get("quality_state"))||!"AVAILABLE".equals(item.get("inventory_state"))||number(item,"active_holds")>0)material.add(prefix+"no está liberado/disponible o tiene bloqueo");
   if(number(item,"current_lot_version")!=number(item,"lot_version_at_request"))material.add(prefix+"versión cambió; crea nueva solicitud");
   if(((BigDecimal)item.get("quantity")).compareTo((BigDecimal)item.get("lot_quantity"))>0)material.add(prefix+"cantidad excedida");
  }
  try{
   MixRule current=selectRule(items.stream().map(i->(String)i.get("reference")).toList(),rules);
   if(!current.code().equals(r.get("rule_code"))||!current.version().equals(r.get("rule_version"))||current.dyeingRequired()!=(Boolean)r.get("dyeing_required"))material.add("Regla de compatibilidad cambió; crea nueva solicitud");
  }catch(RuleException ex){material.add(ex.getMessage());}
  List<String> required=MixPolicy.required((Boolean)r.get("dyeing_required"));
  List<String> blockers=new ArrayList<>(material);Set<String> favorable=new HashSet<>();
  for(var assessment:assessments){
   if(!"FAVORABLE".equals(assessment.get("outcome")))blockers.add("Concepto desfavorable de "+assessment.get("role"));
   if(number(assessment,"review_revision")!=number(r,"review_revision")||!assessment.get("rule_version").equals(r.get("rule_version"))||number(assessment,"based_on_mix_version")>number(r,"version"))blockers.add("Concepto desactualizado de "+assessment.get("role"));
   else if("FAVORABLE".equals(assessment.get("outcome")))favorable.add((String)assessment.get("role"));
  }
  for(String role:required)if(!favorable.contains(role))blockers.add("Falta concepto favorable de "+role);
  return new MixRequest((UUID)r.get("id"),(String)r.get("montage_code"),(String)r.get("status"),(String)r.get("requester"),(String)r.get("approver"),
   (String)r.get("reason"),(String)r.get("evidence"),(String)r.get("rule_code"),(String)r.get("rule_version"),(Boolean)r.get("dyeing_required"),number(r,"review_revision"),number(r,"version"),
   instant(r.get("created_at")),instant(r.get("valid_until")),instant(r.get("decided_at")),(String)r.get("decision_reason"),(String)r.get("decision_evidence"),instant(r.get("revoked_at")),(String)r.get("revocation_reason"),
   items,assessments,required,List.copyOf(material),List.copyOf(blockers),"AUTHORIZED".equals(r.get("status"))&&blockers.isEmpty(),now);
 }
 private static long number(Map<String,Object> row,String name){return ((Number)row.get(name)).longValue();}
 private static Instant instant(Object value){return value==null?null:((Timestamp)value).toInstant();}
}
