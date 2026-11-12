import {useEffect,useState,type FormEvent} from 'react';
import type {Lot,User,AuditEvent} from './types';
import type {Mix} from './mixingTypes';
import {read,submit} from './api';

type Props={user:User;credentials:string;lots:Lot[];selected:string|null;onSelect:(id:string|null)=>void;refreshToken:number;disabled:boolean;onBusy:(busy:boolean)=>void;onPending:()=>void;onError:(message:string)=>void;onConfirmed:(mix:Mix)=>Promise<void>;onLot:(id:string)=>void};
const roles:Record<string,string>={PRODUCTION_REQUESTER:'Tejeduría',PRODUCTION_APPROVER:'Producción',LAB_APPROVER:'Laboratorio',GEO:'GEO',QUALITY:'Calidad',DYEING:'Tintorería'};
const status:Record<string,string>={UNDER_REVIEW:'En evaluación',AUTHORIZED:'Autorizada',DENIED:'Denegada',REVOKED:'Revocada'};
const human=(text:string)=>Object.entries(roles).reduce((s,[code,name])=>s.replaceAll(code,name),text);
const localTomorrow=()=>{const d=new Date(Date.now()+86400000);return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16);};

export function Mixing(p:Props){
 const [mixes,setMixes]=useState<Mix[]>([]),[events,setEvents]=useState<AuditEvent[]>([]),[creating,setCreating]=useState(false);
 const [filter,setFilter]=useState('ALL'),[area,setArea]=useState('ALL'),[montageFilter,setMontageFilter]=useState('');
 const [montage,setMontage]=useState(''),[until,setUntil]=useState(localTomorrow),[reason,setReason]=useState(''),[evidence,setEvidence]=useState('');
 const eligible=p.lots.filter(l=>l.qualityState==='RELEASED'&&l.inventoryState==='AVAILABLE'&&l.activeHolds===0);
 const [items,setItems]=useState([{lotId:eligible[0]?.id||'',quantity:'1'},{lotId:eligible[1]?.id||'',quantity:'1'}]);
 const [outcome,setOutcome]=useState('FAVORABLE'),[decision,setDecision]=useState('AUTHORIZE'),[checkMessage,setCheckMessage]=useState('');
 const mix=mixes.find(m=>m.id===p.selected);
 async function load(){setMixes(await read<Mix[]>('/mix-requests',p.credentials));}
 useEffect(()=>{let cancelled=false;read<Mix[]>('/mix-requests',p.credentials).then(ms=>{if(!cancelled)setMixes(ms);}).catch(e=>{if(!cancelled)p.onError(e.message);});return()=>{cancelled=true;};},[p.credentials,p.refreshToken]);
 useEffect(()=>{setReason('');setEvidence('');setCheckMessage('');setCreating(false);},[p.selected,p.user.username]);
 useEffect(()=>{let cancelled=false;setEvents([]);if(p.selected)read<AuditEvent[]>(`/mix-requests/${p.selected}/timeline`,p.credentials).then(es=>{if(!cancelled)setEvents(es);}).catch(e=>{if(!cancelled)p.onError(e.message);});return()=>{cancelled=true;};},[p.selected,mix?.version,p.credentials]);
 async function command(path:string,body:unknown,label:string){
  p.onBusy(true);p.onError('');let confirmed=false;
  try{const result=await submit<Mix>(p.user.username,p.credentials,path,body,label);confirmed=true;setMixes(old=>[result,...old.filter(m=>m.id!==result.id)]);p.onSelect(result.id);setCreating(false);setReason('');setEvidence('');await p.onConfirmed(result);}
  catch(e){p.onError((confirmed?'Comando confirmado. No se repite; falló la actualización de consultas: ':'')+(e as Error).message);}
  finally{p.onPending();p.onBusy(false);}
 }
 async function create(e:FormEvent){e.preventDefault();await command('/mix-requests',{version:0,reason,evidence,montageCode:montage,validUntil:new Date(until).toISOString(),items:items.map(i=>({lotId:i.lotId,quantity:Number(i.quantity)}))},'Solicitar mezcla '+montage);}
 async function act(action:string){if(!mix)return;await command(`/mix-requests/${mix.id}/${action}`,{version:mix.version,reason,evidence,...(action==='assessments'?{outcome}:{decision})},action==='assessments'?'Concepto de '+roles[p.user.role]:'Decisión de Producción');}
 async function check(){if(!mix)return;p.onBusy(true);p.onError('');try{await read<Mix>(`/mix-requests/${mix.id}/authorization?montageCode=${encodeURIComponent(mix.montageCode)}`,p.credentials);setCheckMessage('Autorización vigente para '+mix.montageCode+' y los lotes/cantidades de este expediente.');await load();}catch(e){setCheckMessage('');p.onError((e as Error).message);await load().catch(()=>{});}finally{p.onBusy(false);}}
 const visible=mixes.filter(m=>(filter==='ALL'||m.status===filter)&&m.montageCode.toLowerCase().includes(montageFilter.toLowerCase())&&(area==='ALL'||(m.status==='UNDER_REVIEW'&&(area==='PRODUCTION_APPROVER'||(m.requiredRoles.includes(area)&&!m.assessments.some(a=>a.role===area))))));
 const ready=!p.disabled&&!!reason.trim()&&!!evidence.trim();
 const assessing=mix&&mix.status==='UNDER_REVIEW'&&['LAB_APPROVER','GEO','QUALITY','DYEING'].includes(p.user.role)&&!mix.assessments.some(a=>a.role===p.user.role);
 const deciding=mix&&mix.status==='UNDER_REVIEW'&&p.user.role==='PRODUCTION_APPROVER';
 return <section className="mixing"><div className="sectiontitle"><h2>Mezclas por montaje</h2>{p.user.role==='PRODUCTION_REQUESTER'&&<button disabled={p.disabled} onClick={()=>{setCreating(true);setReason('');setEvidence('');}}>Nueva solicitud de mezcla</button>}</div>
 <p className="next">SOLO SIMULACIÓN — una autorización no reserva ni consume existencias.</p>
 {creating&&<section className="card"><h3>Solicitar mezcla</h3><form onSubmit={create}><fieldset disabled={p.disabled}>
  <div className="gridform"><label>Montaje de destino<input required maxLength={80} value={montage} onChange={e=>setMontage(e.target.value)}/></label><label>Vigencia hasta (hora local)<input required type="datetime-local" value={until} onChange={e=>setUntil(e.target.value)}/></label></div>
  <p className="notice">Vigencia propuesta editable. La revisión usa criterios ficticios por referencia; no tolerancias productivas.</p>
  {items.map((item,index)=><div className="mix-item-input" key={index}><label>Lote de mezcla {index+1}<select required value={item.lotId} onChange={e=>setItems(old=>old.map((i,n)=>n===index?{...i,lotId:e.target.value}:i))}><option value="">Seleccionar lote elegible</option>{eligible.map(l=><option key={l.id} value={l.id} disabled={items.some((i,n)=>n!==index&&i.lotId===l.id)}>{l.supplierLot} · {l.reference} · {l.quantity} kg</option>)}</select></label><label>Cantidad lote {index+1} (kg)<input required type="number" min="0.001" step="0.001" value={item.quantity} onChange={e=>setItems(old=>old.map((i,n)=>n===index?{...i,quantity:e.target.value}:i))}/></label>{items.length>2&&<button className="secondary" type="button" onClick={()=>setItems(old=>old.filter((_,n)=>n!==index))}>Quitar lote {index+1}</button>}</div>)}
  <button className="secondary" type="button" disabled={items.length>=50} onClick={()=>setItems(old=>[...old,{lotId:'',quantity:'1'}])}>Agregar lote</button>
  <label>Motivo de mezcla<textarea required maxLength={2000} value={reason} onChange={e=>setReason(e.target.value)}/></label><label>Evidencia de mezcla<input required maxLength={2000} value={evidence} onChange={e=>setEvidence(e.target.value)}/></label>
  <div className="actions"><button disabled={!ready||items.some(i=>!i.lotId||Number(i.quantity)<=0)}>Registrar solicitud de mezcla</button><button className="secondary" type="button" onClick={()=>setCreating(false)}>Cancelar solicitud</button></div>
 </fieldset></form></section>}
 <div className="columns"><section className="card queue"><div className="sectiontitle"><h3>Bandeja de mezclas</h3><button className="link" disabled={p.disabled} onClick={()=>load().catch(e=>p.onError(e.message))}>Actualizar mezclas</button></div>
 <label>Filtrar por montaje<input value={montageFilter} onChange={e=>setMontageFilter(e.target.value)}/></label>
 <label>Estado de mezcla<select value={filter} onChange={e=>setFilter(e.target.value)}><option value="ALL">Todos</option>{Object.entries(status).map(([code,name])=><option key={code} value={code}>{name}</option>)}</select></label>
 <label>Área pendiente<select value={area} onChange={e=>setArea(e.target.value)}><option value="ALL">Todas</option>{Object.entries(roles).filter(([r])=>r!=='PRODUCTION_REQUESTER').map(([code,name])=><option key={code} value={code}>{name}</option>)}</select></label>
 {visible.length===0&&<p className="empty">Sin solicitudes para estos filtros.</p>}
 {visible.map(m=><button key={m.id} className={'lot '+(p.selected===m.id?'selected':'')} onClick={()=>p.onSelect(m.id)}><div><b>{m.montageCode}</b><span className={'badge '+m.status}>{status[m.status]}</span></div><p>{m.items.length} lotes · {m.requester}</p><small>{m.dyeingRequired?'Requiere Tintorería':'Lab · GEO · Calidad'}</small></button>)}
 </section><section className="card detail">{!mix?<p className="empty">Selecciona una solicitud para revisar combinación, conceptos y decisión.</p>:<>
 <div className="sectiontitle"><h3>Expediente de mezcla · {mix.montageCode}</h3><span className={'badge '+mix.status}>{status[mix.status]}</span></div>
 <p className="notice">{mix.id} · Versión {mix.version} · Revisión de combinación {mix.reviewRevision}</p><dl><div><dt>Solicitante</dt><dd>{mix.requester}</dd></div><div><dt>Vigencia hasta</dt><dd>{new Date(mix.validUntil).toLocaleString('es-CO')}</dd></div><div><dt>Regla ficticia</dt><dd>{mix.ruleCode} / {mix.ruleVersion}</dd></div><div><dt>Tintorería</dt><dd>{mix.dyeingRequired?'Obligatoria por regla de servidor':'No obligatoria'}</dd></div></dl>
 <p>{mix.reason}</p><p className="evidence">Evidencia: {mix.evidence}</p>
 <h4>Combinación exacta</h4><div className="mix-table"><table><thead><tr><th>Lote</th><th>Cantidad</th><th>Estado actual</th><th>Expediente</th></tr></thead><tbody>{mix.items.map(i=><tr key={i.lot_id}><td>{i.supplier_lot}<small>{i.reference} · {i.production_date.slice(0,10)}</small></td><td>{i.quantity} kg</td><td>{({AVAILABLE:'Disponible',BLOCKED:'Bloqueado',QUARANTINE:'Cuarentena'} as Record<string,string>)[i.inventory_state]||i.inventory_state} · v{i.current_lot_version}</td><td><button className="link" onClick={()=>p.onLot(i.lot_id)}>Ver lote {i.supplier_lot}</button></td></tr>)}</tbody></table></div>
 <p className="notice">Control por lote completo. No se validan cono/caja, set, puesto ni máquina: esos datos no están disponibles.</p>
 <h4>Conceptos requeridos</h4><div className="role-concepts">{mix.requiredRoles.map(role=>{const a=mix.assessments.find(x=>x.role===role);return <div key={role}><b>{roles[role]}</b><span>{!a?'Pendiente':a.outcome==='FAVORABLE'?'Favorable':'Desfavorable'}</span></div>;})}</div>
 {mix.assessments.map(a=><article className="opinion" key={a.id}><b>{roles[a.role]} · {a.outcome==='FAVORABLE'?'Favorable':'Desfavorable'}</b><p>{a.reason}</p><p>Evidencia: {a.evidence}</p><small>{a.author} · {a.rule_version} · revisión {a.review_revision}</small></article>)}
 {mix.blockers.length>0&&<div className="mix-blockers"><b>Impedimentos para autorizar</b><ul>{mix.blockers.map((b,i)=><li key={i}>{human(b)}</li>)}</ul></div>}
 {(assessing||deciding)&&<form onSubmit={e=>{e.preventDefault();void act(assessing?'assessments':'decision');}}><fieldset disabled={p.disabled}>
 {assessing&&<label>Concepto de mezcla<select value={outcome} onChange={e=>setOutcome(e.target.value)}><option value="FAVORABLE">Favorable</option><option value="UNFAVORABLE">Desfavorable</option></select></label>}
 {deciding&&<label>Decisión de Producción<select value={decision} onChange={e=>setDecision(e.target.value)}><option value="AUTHORIZE">Autorizar combinación exacta</option><option value="DENY">Denegar solicitud</option></select></label>}
 <label>Motivo de evaluación<textarea required maxLength={2000} value={reason} onChange={e=>setReason(e.target.value)}/></label><label>Evidencia de evaluación<input required maxLength={2000} value={evidence} onChange={e=>setEvidence(e.target.value)}/></label>
 <button disabled={!ready||(assessing&&mix.materialBlockers.length>0)||(deciding&&decision==='AUTHORIZE'&&mix.blockers.length>0)}>{assessing?'Registrar concepto de mezcla':'Confirmar decisión de mezcla'}</button>
 </fieldset></form>}
 {mix.decidedAt&&<div className="next">Decisión histórica: {mix.approver} · {new Date(mix.decidedAt).toLocaleString('es-CO')}<p>{mix.decisionReason}</p><small>Evidencia: {mix.decisionEvidence}</small></div>}
 {mix.status==='REVOKED'&&<p className="error">Autorización revocada: {mix.revocationReason}. Resolver la NC no reactiva esta mezcla; se requiere nueva solicitud.</p>}
 {mix.status==='DENIED'&&<p className="notice">La denegación es definitiva para esta solicitud. Una reevaluación requiere un expediente nuevo.</p>}
 {mix.status==='AUTHORIZED'&&<><p className="next">{mix.usable?'Autorización elegible en la última consulta':'Autorización histórica sin elegibilidad actual'}</p><button className="secondary" disabled={p.disabled} onClick={check}>Consultar autorización vigente</button></>}
 {checkMessage&&<p role="status" className="next">{checkMessage}</p>}
 <h4>Historial de mezcla</h4><ol className="timeline mix-timeline">{events.map(e=><li key={e.id}><div className="sectiontitle"><b>{({REQUESTED:'Solicitud registrada',ASSESSED:'Concepto registrado',AUTHORIZED:'Mezcla autorizada',DENIED:'Mezcla denegada',REVOKED:'Autorización revocada',COMMAND_DENIED:'Intento rechazado'} as Record<string,string>)[e.event_type]||e.event_type}</b><time>{new Date(e.occurred_at).toLocaleString('es-CO')}</time></div><small>{e.actor} · {roles[e.role]||e.role}</small><p>{e.reason}</p><p className="evidence">Evidencia: {e.evidence}</p><small>{e.from_state||'Inicio'} → {e.to_state} · {e.correlation_id}</small></li>)}</ol>
 </>}</section></div></section>;
}
