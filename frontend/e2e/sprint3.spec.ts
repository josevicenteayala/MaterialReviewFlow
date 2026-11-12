import {test,expect,Page,APIRequestContext} from '@playwright/test';
test.describe.configure({timeout:60_000});
const headers=(user:string)=>({Authorization:'Basic '+Buffer.from(user+':Simulacion123!').toString('base64')});
async function get(request:APIRequestContext,path:string){const r=await request.get('/api'+path,{headers:headers('almacen')});expect(r.status(),await r.text()).toBe(200);return r.json();}
async function post(request:APIRequestContext,user:string,path:string,version:number,fields:Record<string,unknown>={}){const r=await request.post('/api'+path,{headers:{...headers(user),'Idempotency-Key':crypto.randomUUID()},data:{version,reason:'Fixture Sprint 3',evidence:'EV-S3-FIXTURE',...fields}});expect(r.status(),await r.text()).toBe(200);return r.json();}
async function lot(request:APIRequestContext,order='OC-H-001'){
 let l=await post(request,'almacen','/receipts',0,{orderCode:order,supplierLot:'E2E-MIX-'+crypto.randomUUID(),productionDate:'2026-01-01',quantity:1,location:'Q-E2E-S3'});
 for(const [user,action,fields] of [['calidad','inspect',{conforms:true}],['almacen','sample',{sampleCode:'M-S3'}],['tecnico','test',{resultValue:order==='OC-H-001'?15:500}],['aprobador','release',{}],['almacen','available',{}]] as const)l=await post(request,user,`/lots/${l.id}/${action}`,l.version,fields);
 return l;
}
async function login(page:Page,user:string){await page.goto('/');await page.getByLabel('Usuario').selectOption(user);await page.getByLabel('Contraseña').fill('Simulacion123!');await page.getByRole('button',{name:'Entrar a la simulación'}).click();await expect(page.getByRole('heading',{name:'Recepción y liberación',exact:true})).toBeVisible();}
async function mixes(page:Page){await page.getByRole('button',{name:'Mezclas por montaje',exact:true}).click();await expect(page.getByRole('heading',{name:'Autorización de mezclas',exact:true})).toBeVisible();}
async function selectMix(page:Page,montage:string){await page.locator('.mixing button.lot').filter({has:page.locator('b',{hasText:montage})}).click();await expect(page.getByRole('heading',{name:'Expediente de mezcla · '+montage,exact:true})).toBeVisible();}
async function actor(page:Page,user:string,montage:string){await page.getByRole('button',{name:'Salir',exact:true}).click();await login(page,user);await mixes(page);await selectMix(page,montage);}
async function create(page:Page,montage:string,a:string,b:string){
 await page.getByRole('button',{name:'Nueva solicitud de mezcla',exact:true}).click();await page.getByLabel('Montaje de destino').fill(montage);
 await page.getByLabel('Vigencia hasta (hora local)').fill(new Date(Date.now()+172800000).toISOString().slice(0,16));
 // Clear the defaults first so the exact fixture lots can be selected in either order.
 await page.getByRole('combobox',{name:'Lote de mezcla 1',exact:true}).selectOption('');await page.getByRole('combobox',{name:'Lote de mezcla 2',exact:true}).selectOption('');
 await page.getByRole('combobox',{name:'Lote de mezcla 1',exact:true}).selectOption(a);await page.getByRole('combobox',{name:'Lote de mezcla 2',exact:true}).selectOption(b);
 await page.getByLabel('Cantidad lote 1 (kg)').fill('0.4');await page.getByLabel('Cantidad lote 2 (kg)').fill('0.5');
 await page.getByLabel('Motivo de mezcla',{exact:true}).fill('Combinación documentada de prueba');await page.getByLabel('Evidencia de mezcla',{exact:true}).fill('EV-MIX-E2E');
 await page.getByRole('button',{name:'Registrar solicitud de mezcla',exact:true}).click();
}
async function evaluation(page:Page,text:string){await page.getByLabel('Motivo de evaluación').fill(text);await page.getByLabel('Evidencia de evaluación').fill('EV-CONCEPTO-E2E');}
async function fixtureMix(request:APIRequestContext,a:string,b:string,authorized=false){
 let m=await post(request,'tejeduria','/mix-requests',0,{montageCode:'MONTAJE-'+crypto.randomUUID(),validUntil:new Date(Date.now()+172800000).toISOString(),items:[{lotId:a,quantity:0.4},{lotId:b,quantity:0.5}]});
 if(authorized){for(const u of ['aprobador','geo','calidad'])m=await post(request,u,`/mix-requests/${m.id}/assessments`,m.version,{outcome:'FAVORABLE'});m=await post(request,'produccion',`/mix-requests/${m.id}/decision`,m.version,{decision:'AUTHORIZE'});}
 return m;
}

test('US-06 a US-08: solicitud, bandejas, conceptos independientes y autorización exacta',async({page,request})=>{
 const a=await lot(request),b=await lot(request),montage='UI-MIX-'+Date.now();await login(page,'tejeduria');await mixes(page);await create(page,montage,a.id,b.id);
 await expect(page.getByRole('heading',{name:'Expediente de mezcla · '+montage,exact:true})).toBeVisible();
 await page.getByLabel('Filtrar por montaje').fill(montage);await page.getByRole('combobox',{name:'Estado de mezcla',exact:true}).selectOption('UNDER_REVIEW');await page.getByRole('combobox',{name:'Área pendiente',exact:true}).selectOption('GEO');await expect(page.locator('.mixing button.lot')).toHaveCount(1);
 await actor(page,'produccion',montage);await evaluation(page,'Todavía faltan conceptos');await expect(page.getByRole('button',{name:'Confirmar decisión de mezcla'})).toBeDisabled();
 for(const user of ['aprobador','geo','calidad']){await actor(page,user,montage);await evaluation(page,'Concepto favorable de '+user);await page.getByRole('button',{name:'Registrar concepto de mezcla'}).click();await expect(page.getByText('Concepto favorable de '+user,{exact:true}).first()).toBeVisible();}
 await actor(page,'produccion',montage);await evaluation(page,'Aprobación independiente para montaje exacto');await page.getByRole('button',{name:'Confirmar decisión de mezcla'}).click();
 await expect(page.locator('.mixing .detail>.sectiontitle .badge')).toHaveText('Autorizada');await expect(page.locator('.mix-timeline li')).toHaveCount(5);
 await page.getByRole('button',{name:'Consultar autorización vigente'}).click();await expect(page.getByRole('status')).toContainText('Autorización vigente para '+montage);
 for(const l of [a,b]){const current=await get(request,`/lots/${l.id}`);expect(current.quantity).toBe(1);expect(current.version).toBe(l.version);expect(current.inventoryState).toBe('AVAILABLE');}
 await page.screenshot({path:'test-results/sprint3-authorized.png',fullPage:true});
});

test('Tintorería obligatoria y concepto desfavorable conducen a denegación terminal',async({page,request})=>{
 const a=await lot(request),b=await lot(request,'OC-E-001');let m=await fixtureMix(request,a.id,b.id);
 for(const u of ['aprobador','geo','calidad'])m=await post(request,u,`/mix-requests/${m.id}/assessments`,m.version,{outcome:'FAVORABLE'});
 await login(page,'produccion');await mixes(page);await selectMix(page,m.montageCode);await evaluation(page,'Sin concepto tintóreo');await expect(page.getByRole('button',{name:'Confirmar decisión de mezcla'})).toBeDisabled();
 await actor(page,'tintoreria',m.montageCode);await page.getByRole('combobox',{name:'Concepto de mezcla',exact:true}).selectOption('UNFAVORABLE');await evaluation(page,'Diferencia tintórea simulada');await page.getByRole('button',{name:'Registrar concepto de mezcla'}).click();await expect(page.getByText('Diferencia tintórea simulada',{exact:true}).first()).toBeVisible();
 await actor(page,'produccion',m.montageCode);await evaluation(page,'Denegación por concepto desfavorable');await expect(page.getByRole('button',{name:'Confirmar decisión de mezcla'})).toBeDisabled();await page.getByRole('combobox',{name:'Decisión de Producción',exact:true}).selectOption('DENY');await page.getByRole('button',{name:'Confirmar decisión de mezcla'}).click();
 await expect(page.locator('.mixing .detail>.sectiontitle .badge')).toHaveText('Denegada');await expect(page.getByRole('button',{name:'Confirmar decisión de mezcla'})).toHaveCount(0);await expect(page.locator('.mix-timeline li')).toHaveCount(6);
 await page.screenshot({path:'test-results/sprint3-denied.png',fullPage:true});
});

test('US-09: NC desde Tintorería revoca y el levantamiento no reactiva',async({page,request})=>{
 const a=await lot(request),b=await lot(request),m=await fixtureMix(request,a.id,b.id,true);
 await login(page,'tintoreria');await mixes(page);await selectMix(page,m.montageCode);await page.getByRole('button',{name:'Ver lote '+a.supplierLot,exact:true}).click();
 await expect(page.getByRole('heading',{name:a.supplierLot,exact:true})).toBeVisible();await page.getByText('Reportar no conformidad y bloquear',{exact:true}).click();await page.getByLabel('Responsable de Calidad').selectOption('calidad');await page.getByLabel('Motivo de la NC',{exact:true}).fill('NC de mezcla desde Tintorería');await page.getByLabel('Evidencia de la NC',{exact:true}).fill('EV-NC-MIX');await page.getByRole('button',{name:'Abrir NC y bloquear'}).click();
 await expect(page.getByText('NC posterior: evaluar y decidir levantamiento o rechazo',{exact:true})).toBeVisible();await mixes(page);await selectMix(page,m.montageCode);
 await expect(page.locator('.mixing .detail>.sectiontitle .badge')).toHaveText('Revocada');await expect(page.locator('.mix-timeline li')).toHaveCount(6);
 const cases=await get(request,`/lots/${a.id}/nonconformities`);let current=await get(request,`/lots/${a.id}`);current=await post(request,'aprobador',`/lots/${a.id}/nonconformities/${cases[0].id}/opinion`,current.version,{outcome:'CONFORMING'});current=await post(request,'calidad',`/lots/${a.id}/nonconformities/${cases[0].id}/resolve`,current.version,{disposition:'LIFT_HOLD'});await post(request,'almacen',`/lots/${a.id}/available`,current.version);
 await page.getByRole('button',{name:'Actualizar mezclas'}).click();await expect(page.locator('.mixing .detail>.sectiontitle .badge')).toHaveText('Revocada');await expect(page.getByRole('button',{name:'Consultar autorización vigente'})).toHaveCount(0);
 const final=await get(request,`/mix-requests/${m.id}`);expect(final.status).toBe('REVOKED');expect(final.usable).toBe(false);expect(final.approver).toBe('produccion');await page.screenshot({path:'test-results/sprint3-revoked.png',fullPage:true});
});

test('respuesta de mezcla perdida: reintento tras recarga conserva clave/cuerpo y tipo de agregado',async({page,request})=>{
 const a=await lot(request),b=await lot(request),montage='RETRY-MIX-'+Date.now();const attempts:{key:string;body:string}[]=[];
 await login(page,'tejeduria');await mixes(page);
 await page.route('**/api/mix-requests',async route=>{if(route.request().method()!=='POST'){await route.continue();return;}attempts.push({key:route.request().headers()['idempotency-key'],body:route.request().postData()!});if(attempts.length===1){const r=await route.fetch();expect(r.status()).toBe(200);await route.abort('failed');}else await route.continue();});
 await create(page,montage,a.id,b.id);await expect(page.getByRole('heading',{name:'Comando sin confirmar'})).toBeVisible();await expect(page.getByRole('button',{name:'Nueva solicitud de mezcla'})).toBeDisabled();
 await page.reload();await login(page,'tejeduria');await page.getByRole('button',{name:'Reintentar el mismo comando'}).click();await expect(page.getByRole('heading',{name:'Expediente de mezcla · '+montage,exact:true})).toBeVisible();expect(attempts).toHaveLength(2);expect(attempts[1]).toEqual(attempts[0]);
 const all=await get(request,'/mix-requests');expect(all.filter((m:{montageCode:string})=>m.montageCode===montage)).toHaveLength(1);
 await page.getByRole('button',{name:'Materiales y NC',exact:true}).click();const lots=await get(request,'/lots');await expect(page.locator('button.lot')).toHaveCount(lots.length);
});
