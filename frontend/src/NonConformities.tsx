import {useState} from 'react';
import type {User, Lot, NonConformity, Hold} from './types';

type Props = {
  lot: Lot; user: User; cases: NonConformity[]; holds: Hold[]; owners: string[]; disabled: boolean;
  onCommand: (path: string, fields: Record<string, unknown>, label: string) => Promise<boolean>;
};
const names: Record<string,string> = {OPEN:'Abierta',CLOSED:'Cerrada · bloqueo levantado',REJECTED:'Rechazo definitivo',
  CONFORMING:'Conforme',NONCONFORMING:'No conforme',LIFT_HOLD:'Levantar bloqueo',
  RETURN_TO_SUPPLIER:'Devolución prevista',SCRAP:'Baja prevista'};

export function NonConformities({lot,user,cases,holds,owners,disabled,onCommand}: Props) {
  const [origin,setOrigin] = useState(lot.qualityState === 'RELEASED' ? 'POST_RELEASE' : 'OTHER');
  const [responsible,setResponsible] = useState(owners.find(o=>o!==user.username)||owners[0]||'');
  const [reason,setReason] = useState('');
  const [evidence,setEvidence] = useState('');
  const terminal = lot.qualityState === 'REJECTED';
  async function open(e: React.FormEvent) {
    e.preventDefault();
    if(await onCommand(`/lots/${lot.id}/nonconformities`,{origin,responsible,reason,evidence},'Abrir NC y bloquear')) {
      setReason('');setEvidence('');
    }
  }
  return <section className="nc-panel">
    <div className="sectiontitle"><h3>No conformidades y bloqueos</h3><span className="badge BLOCKED">{lot.activeHolds} bloqueos activos</span></div>
    {lot.inventoryState === 'BLOCKED' && <p className="notice">La reinspección y el ensayo no desbloquean. Se exige dictamen y decisión explícita de Calidad; Almacén confirma después la disponibilidad.</p>}
    {lot.inventoryState === 'BLOCKED' && (lot.activeHolds === 0 || holds.some(h=>h.status==='ACTIVE'&&!h.nc_id)) &&
      <p className="next">Hay un bloqueo sin expediente formal. Abre una NC para documentar su evaluación y eventual levantamiento.</p>}
    {!terminal && <details className="report-nc"><summary>Reportar no conformidad y bloquear</summary>
      <form onSubmit={open}><fieldset disabled={disabled}>
        <label>Origen de la NC<select value={origin} onChange={e=>setOrigin(e.target.value)}>
          <option value="DOCUMENTARY">Documental</option><option value="LABORATORY">Laboratorio</option>
          {lot.qualityState==='RELEASED'&&<option value="POST_RELEASE">Posterior a liberación</option>}<option value="OTHER">Otro</option>
        </select></label>
        <label>Responsable de Calidad<select value={responsible} onChange={e=>setResponsible(e.target.value)}>
          {owners.map(owner=><option key={owner} disabled={owner===user.username}>{owner}</option>)}
        </select></label>
        <label>Motivo de la NC<textarea required maxLength={2000} value={reason} onChange={e=>setReason(e.target.value)}/></label>
        <label>Evidencia de la NC<input required maxLength={2000} value={evidence} onChange={e=>setEvidence(e.target.value)}/></label>
        <button disabled={!responsible||!reason.trim()||!evidence.trim()}>Abrir NC y bloquear</button>
      </fieldset></form>
    </details>}
    {cases.length===0&&<p className="notice">Sin expedientes de NC registrados.</p>}
    {cases.map(nc=><Case key={nc.id} nc={nc} lot={lot} user={user} disabled={disabled||terminal} onCommand={onCommand}/>)}
    {holds.length>0&&<details className="holds"><summary>Historial de bloqueos ({holds.length})</summary>
      {holds.map(h=><div key={h.id}><b>{h.status==='ACTIVE'?'Activo':'Levantado'}</b> · {h.reason}<p className="notice">{h.id} · {h.opened_by} · {h.nc_id?`NC ${h.nc_id}`:'Pendiente de formalización'}</p>
        {h.closed_by&&<p>{h.closed_by}: {h.close_reason} · {h.close_evidence}</p>}</div>)}
    </details>}
    {terminal&&<p className="next">Rechazo definitivo. Inventario bloqueado. La disposición indica el destino previsto; no se ha ejecutado una devolución ni baja física.</p>}
  </section>;
}
function Case({nc,lot,user,disabled,onCommand}: Omit<Props,'cases'|'holds'|'owners'> & {nc:NonConformity}) {
  const [outcome,setOutcome]=useState('CONFORMING');
  const [disposition,setDisposition]=useState('LIFT_HOLD');
  const [reason,setReason]=useState('');
  const [evidence,setEvidence]=useState('');
  const latest=nc.opinions.at(-1);
  const fresh=latest?.based_on_version===lot.version;
  const canDecide=user.role==='QUALITY'&&user.username===nc.responsible&&user.username!==nc.openedBy;
  const lift=disposition==='LIFT_HOLD';
  const supported=fresh&&latest?.outcome===(lift?'CONFORMING':'NONCONFORMING');
  const tested=!!lot.sampleCode&&lot.resultValue!==null&&lot.resultValue>=lot.minimum&&lot.resultValue<=lot.maximum&&['UNDER_TEST','RELEASED'].includes(lot.qualityState);
  async function act(action:string,fields:Record<string,unknown>) {
    if(await onCommand(`/lots/${lot.id}/nonconformities/${nc.id}/${action}`,{reason,evidence,...fields},`NC ${nc.id}: ${action}`)) {
      setReason('');setEvidence('');
    }
  }
  return <article className="nc-case" data-testid="nc-case">
    <div className="sectiontitle"><h4>NC {nc.id.slice(0,8)}</h4><span className={'badge '+nc.status}>{names[nc.status]}</span></div>
    <code>{nc.id}</code><p>{nc.reason}</p><p className="evidence">Evidencia: {nc.evidence}</p>
    <small>Origen: {nc.origin} · Reportó: {nc.openedBy} · Responsable: {nc.responsible}</small>
    {nc.opinions.map(op=><div className="opinion" key={op.id}><b>Dictamen: {names[op.outcome]}</b><p>{op.reason}</p><p>Evidencia: {op.evidence}</p><small>{op.author} · versión {op.based_on_version} · {op.based_on_version===lot.version?'Vigente':'Histórico'}</small></div>)}
    {nc.status==='OPEN'&&<>
      <p className="notice">{latest?(fresh?'Dictamen vigente para decidir.':'El expediente cambió: Laboratorio debe emitir un dictamen actualizado.'):'Pendiente de dictamen del aprobador de Laboratorio.'}</p>
      {user.role==='LAB_APPROVER'&&<form onSubmit={e=>{e.preventDefault();void act('opinion',{outcome});}}><fieldset disabled={disabled}>
        <label>Dictamen técnico<select value={outcome} onChange={e=>setOutcome(e.target.value)}><option value="CONFORMING">Conforme</option><option value="NONCONFORMING">No conforme</option></select></label>
        <label>Motivo del dictamen<textarea required maxLength={2000} value={reason} onChange={e=>setReason(e.target.value)}/></label>
        <label>Evidencia del dictamen<input required maxLength={2000} value={evidence} onChange={e=>setEvidence(e.target.value)}/></label>
        <button disabled={!reason.trim()||!evidence.trim()}>Registrar dictamen NC</button>
      </fieldset></form>}
      {user.role==='QUALITY'&&<form onSubmit={e=>{e.preventDefault();void act(lift?'resolve':'reject',{disposition});}}><fieldset disabled={disabled}>
        <label>Disposición<select value={disposition} onChange={e=>setDisposition(e.target.value)}>
          <option value="LIFT_HOLD">Levantar bloqueo de esta NC</option><option value="RETURN_TO_SUPPLIER">Rechazar · devolución prevista</option><option value="SCRAP">Rechazar · baja prevista</option>
        </select></label>
        <label>Motivo de la decisión NC<textarea required maxLength={2000} value={reason} onChange={e=>setReason(e.target.value)}/></label>
        <label>Evidencia de la decisión NC<input required maxLength={2000} value={evidence} onChange={e=>setEvidence(e.target.value)}/></label>
        {!canDecide&&<p className="notice">Debe decidir el responsable asignado, distinto del reportante.</p>}
        {lift&&!tested&&<p className="notice">Falta subsanación, muestra o ensayo conforme vigente.</p>}
        <button disabled={!canDecide||!supported||(lift&&!tested)||!reason.trim()||!evidence.trim()}>{lift?'Levantar bloqueo NC':'Confirmar rechazo definitivo'}</button>
      </fieldset></form>}
    </>}
    {nc.status!=='OPEN'&&<p className="next">{names[nc.disposition||'']} · {nc.decidedBy}<br/>{nc.decisionReason}<br/>Evidencia de decisión: {nc.decisionEvidence}</p>}
  </article>;
}
