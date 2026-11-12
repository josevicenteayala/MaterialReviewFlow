import {test,expect,Page,APIRequestContext} from '@playwright/test';
const headers=(user:string)=>({Authorization:'Basic '+Buffer.from(user+':Simulacion123!').toString('base64')});
async function post(request:APIRequestContext,user:string,path:string,data:unknown){
  const r=await request.post('/api'+path,{headers:{...headers(user),'Idempotency-Key':crypto.randomUUID()},data});
  expect(r.status(),await r.text()).toBe(200);return r.json();
}
async function fixture(request:APIRequestContext){
  const name='SPRINT2-'+crypto.randomUUID();
  let lot=await post(request,'almacen','/receipts',{version:0,reason:'Fixture nueva',evidence:'EV-FIXTURE',orderCode:'OC-H-001',supplierLot:name,productionDate:'2026-01-01',quantity:1,location:'Q-S2'});
  for(const [user,action,fields] of [['calidad','inspect',{conforms:true}],['almacen','sample',{sampleCode:'M-S2'}],['tecnico','test',{resultValue:15}],['aprobador','release',{}],['almacen','available',{}]] as const){
    lot=await post(request,user,`/lots/${lot.id}/${action}`,{version:lot.version,reason:'Paso fixture',evidence:'EV-FIXTURE',...fields});
  }
  return lot;
}
async function login(page:Page,user:string){await page.goto('/');await page.getByLabel('Usuario').selectOption(user);await page.getByLabel('Contraseña').fill('Simulacion123!');await page.getByRole('button',{name:'Entrar a la simulación'}).click();await expect(page.getByRole('heading',{name:'Recepción y liberación'})).toBeVisible();}
async function select(page:Page,name:string){await page.getByRole('button').filter({has:page.locator('b',{hasText:name})}).click();}
async function actor(page:Page,user:string,name:string){await page.getByRole('button',{name:'Salir',exact:true}).click();await login(page,user);await select(page,name);}
async function report(page:Page,owner='calidad'){
  await page.getByText('Reportar no conformidad y bloquear',{exact:true}).click();
  await page.getByLabel('Responsable de Calidad').selectOption(owner);
  await page.getByLabel('Motivo de la NC',{exact:true}).fill('Incidencia posterior documentada');
  await page.getByLabel('Evidencia de la NC',{exact:true}).fill('EV-NC-S2');
  await page.getByRole('button',{name:'Abrir NC y bloquear',exact:true}).click();
}
async function opinion(page:Page,outcome:string){
  const nc=page.getByTestId('nc-case');await nc.getByLabel('Dictamen técnico').selectOption(outcome);
  await nc.getByLabel('Motivo del dictamen').fill('Evaluación técnica independiente');await nc.getByLabel('Evidencia del dictamen').fill('EV-DICTAMEN');
  await nc.getByRole('button',{name:'Registrar dictamen NC'}).click();await expect(nc.getByText('Evaluación técnica independiente',{exact:true})).toBeVisible();
}
async function decision(page:Page,disposition:string){
  const nc=page.getByTestId('nc-case');await nc.getByRole('combobox',{name:'Disposición',exact:true}).selectOption(disposition);
  await nc.getByLabel('Motivo de la decisión NC').fill('Decisión formal de Calidad');await nc.getByLabel('Evidencia de la decisión NC').fill('EV-DECISION');
}

test('bloqueo posterior conserva liberación; levantamiento explícito exige Almacén',async({page,request})=>{
  const lot=await fixture(request);await login(page,'almacen');await select(page,lot.supplierLot);await report(page);
  await expect(page.getByText('NC posterior: evaluar y decidir levantamiento o rechazo',{exact:true})).toBeVisible();
  await expect(page.getByTestId('nc-case')).toHaveCount(1);
  await actor(page,'aprobador',lot.supplierLot);await opinion(page,'CONFORMING');
  await actor(page,'calidad',lot.supplierLot);await decision(page,'LIFT_HOLD');await page.getByRole('button',{name:'Levantar bloqueo NC',exact:true}).click();
  await expect(page.getByText('Cerrada · bloqueo levantado',{exact:true})).toBeVisible();
  await expect(page.getByText('Confirmar segregación y disponibilidad',{exact:true})).toBeVisible();
  await actor(page,'almacen',lot.supplierLot);await page.getByLabel('Motivo de la decisión',{exact:true}).fill('Segregación física confirmada');await page.getByLabel('Evidencia / referencia al checklist').fill('EV-SEGREGACION');await page.getByRole('button',{name:'Confirmar disponibilidad',exact:true}).click();
  await expect(page.getByText('Uso individual disponible',{exact:true})).toBeVisible();
  await expect(page.locator('.timeline li').filter({hasText:'Liberación técnica'})).toHaveCount(1);
  await page.screenshot({path:'test-results/sprint2-lift.png',fullPage:true});
});

test('reportante de Calidad no decide; responsable distinto rechaza definitivamente',async({page,request})=>{
  const lot=await fixture(request);await login(page,'calidad');await select(page,lot.supplierLot);await report(page,'calidad2');await expect(page.getByTestId('nc-case')).toHaveCount(1);
  await actor(page,'aprobador',lot.supplierLot);await opinion(page,'NONCONFORMING');
  await actor(page,'calidad',lot.supplierLot);await decision(page,'SCRAP');await expect(page.getByRole('button',{name:'Confirmar rechazo definitivo'})).toBeDisabled();
  await actor(page,'calidad2',lot.supplierLot);await decision(page,'SCRAP');await page.getByRole('button',{name:'Confirmar rechazo definitivo'}).click();
  await expect(page.getByText('Rechazo definitivo; inventario bloqueado',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Confirmar rechazo definitivo'})).toHaveCount(0);
  await expect(page.getByText('Reportar no conformidad y bloquear',{exact:true})).toHaveCount(0);
  await expect(page.getByTestId('nc-case').getByText('Rechazo definitivo',{exact:true})).toBeVisible();
  await expect(page.locator('.timeline li')).toHaveCount(9);
  await page.screenshot({path:'test-results/sprint2-rejected.png',fullPage:true});
});

test('respuesta NC perdida tras commit: misma clave y cuerpo tras recarga y cambio de identidad',async({page,request})=>{
  const lot=await fixture(request);const attempts:{key:string;body:string}[]=[];
  await login(page,'almacen');await select(page,lot.supplierLot);
  await page.route(`**/api/lots/${lot.id}/nonconformities`,async route=>{
    if(route.request().method()!=='POST'){await route.continue();return;}
    attempts.push({key:route.request().headers()['idempotency-key'],body:route.request().postData()!});
    if(attempts.length===1){const r=await route.fetch();expect(r.status()).toBe(200);await route.abort('failed');}
    else await route.continue();
  });
  await report(page);await expect(page.getByRole('heading',{name:'Comando sin confirmar'})).toBeVisible();
  await expect(page.getByRole('button',{name:'Nueva recepción'})).toBeDisabled();
  await page.reload();await login(page,'calidad');await expect(page.getByRole('heading',{name:'Comando sin confirmar'})).toHaveCount(0);
  await page.getByRole('button',{name:'Salir',exact:true}).click();await login(page,'almacen');
  await expect(page.getByRole('heading',{name:'Comando sin confirmar'})).toBeVisible();
  await page.getByRole('button',{name:'Reintentar el mismo comando'}).click();
  await expect(page.getByTestId('nc-case')).toHaveCount(1);await expect(page.getByRole('heading',{name:'Comando sin confirmar'})).toHaveCount(0);
  expect(attempts).toHaveLength(2);expect(attempts[1]).toEqual(attempts[0]);
  const cases=await request.get(`/api/lots/${lot.id}/nonconformities`,{headers:headers('almacen')});expect(await cases.json()).toHaveLength(1);
  const holds=await request.get(`/api/lots/${lot.id}/holds`,{headers:headers('almacen')});expect(await holds.json()).toHaveLength(1);
  const events=await request.get(`/api/lots/${lot.id}/timeline`,{headers:headers('almacen')});expect((await events.json()).filter((e:{event_type:string})=>e.event_type==='NC_OPEN')).toHaveLength(1);
});

async function receiptForm(page:Page,name:string,quantity='1'){
  await page.getByRole('button',{name:'Nueva recepción'}).click();await page.getByLabel('Orden de compra').selectOption('OC-H-001');
  await page.getByLabel('Lote del proveedor').fill(name);await page.getByLabel('Fecha de fabricación').fill('2026-01-01');await page.getByLabel('Cantidad (kg)').fill(quantity);
  await page.getByLabel('Ubicación',{exact:true}).fill('Q-S2');await page.getByLabel('Motivo',{exact:true}).fill('Recepción de prueba');await page.getByLabel('Evidencia de recepción').fill('EV-RETRY');
}

test('recepción con respuesta 5xx conserva clave hasta reintentar',async({page,request})=>{
  const name='RETRY-'+crypto.randomUUID();const keys:string[]=[];await login(page,'almacen');
  await page.route('**/api/receipts',async route=>{keys.push(route.request().headers()['idempotency-key']);if(keys.length===1)await route.fulfill({status:503,body:'upstream unavailable'});else await route.continue();});
  await receiptForm(page,name);await page.getByRole('button',{name:'Registrar en cuarentena'}).click();await expect(page.getByRole('heading',{name:'Comando sin confirmar'})).toBeVisible();
  await page.getByRole('button',{name:'Reintentar el mismo comando'}).click();await expect(page.getByRole('heading',{name,exact:true})).toBeVisible();expect(keys).toHaveLength(2);expect(keys[1]).toBe(keys[0]);
  const lots=await request.get('/api/lots',{headers:headers('almacen')});expect((await lots.json()).filter((l:{supplierLot:string})=>l.supplierLot===name)).toHaveLength(1);
});

test('rechazo 422 definitivo permite corregir con una nueva clave',async({page})=>{
  const keys:string[]=[];await login(page,'almacen');await page.route('**/api/receipts',async route=>{keys.push(route.request().headers()['idempotency-key']);await route.continue();});
  const name='CORRECT-'+crypto.randomUUID();await receiptForm(page,name,'10001');await page.getByRole('button',{name:'Registrar en cuarentena'}).click();
  await expect(page.getByRole('alert')).toContainText('saldo de la OC');await expect(page.getByRole('heading',{name:'Comando sin confirmar'})).toHaveCount(0);
  await page.getByLabel('Cantidad (kg)').fill('1');await page.getByRole('button',{name:'Registrar en cuarentena'}).click();await expect(page.getByRole('heading',{name,exact:true})).toBeVisible();
  expect(keys).toHaveLength(2);expect(keys[0]).not.toBe(keys[1]);
});

test('fallo de consulta tras POST confirmado no crea comando incierto ni repite POST',async({page})=>{
  await login(page,'almacen');let confirmed=false;let posts=0;
  await page.route('**/api/receipts',async route=>{posts++;const r=await route.fetch();confirmed=true;await route.fulfill({response:r});});
  await page.route('**/api/lots',async route=>{if(confirmed)await route.fulfill({status:503,body:'read failure'});else await route.continue();});
  const name='CONFIRMED-'+crypto.randomUUID();await receiptForm(page,name);await page.getByRole('button',{name:'Registrar en cuarentena'}).click();
  await expect(page.getByRole('alert')).toContainText('Comando confirmado. No se repite');await expect(page.getByRole('heading',{name:'Comando sin confirmar'})).toHaveCount(0);expect(posts).toBe(1);
});

test('respuesta 2xx incompleta tras commit conserva el comando para confirmación',async({page,request})=>{
  await login(page,'almacen');const name='INCOMPLETE-'+crypto.randomUUID();const attempts:{key:string;body:string}[]=[];
  await page.route('**/api/receipts',async route=>{
    attempts.push({key:route.request().headers()['idempotency-key'],body:route.request().postData()!});
    if(attempts.length===1){const r=await route.fetch();expect(r.status()).toBe(200);await route.fulfill({status:200,contentType:'application/json',body:'{}'});}
    else await route.continue();
  });
  await receiptForm(page,name);await page.getByRole('button',{name:'Registrar en cuarentena'}).click();await expect(page.getByRole('heading',{name:'Comando sin confirmar'})).toBeVisible();
  await page.getByRole('button',{name:'Reintentar el mismo comando'}).click();await expect(page.getByRole('heading',{name,exact:true})).toBeVisible();expect(attempts).toHaveLength(2);expect(attempts[1]).toEqual(attempts[0]);
  const lots=await request.get('/api/lots',{headers:headers('almacen')});expect((await lots.json()).filter((l:{supplierLot:string})=>l.supplierLot===name)).toHaveLength(1);
});
