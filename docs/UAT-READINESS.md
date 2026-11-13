# Preparación técnica para UAT — PROCO SIM-0.2

Fecha: 13-11-2026, America/Bogota. Base exacta revisada: **main `91688b6d6d2f7b547802d708c9429b2ebc6cc02f`**, PR #5 de Sprint 6 integrado. Rama de este reporte: `codex/uat-readiness`. Guía: [PROCO_PLAN_VALIDACION_FUNCIONAL_UAT_v1.md](PROCO_PLAN_VALIDACION_FUNCIONAL_UAT_v1.md), conservada sin modificar.

**Conclusión de preparación:** se dispone de entorno local aislado, identidades, saldo y recorridos para facilitar S01–S10 bajo los límites del simulador. No se ha realizado UAT con responsables de PROCO, no existe acta de aceptación y no se declara aprobado el procedimiento ni habilitado el uso operativo. Las transiciones usadas para preparar fixtures son evidencia técnica, no resultados de taller.

## 1. CI del commit integrado

[GitHub Actions 43518095726](https://github.com/josevicenteayala/MaterialReviewFlow/actions/runs/43518095726), evento push de main, SHA completo anterior: **completed / success**. Se consultaron los pasos y logs de esa ejecución; no se atribuyen resultados de una rama al merge.

| Paso ejecutado en CI | Resultado observado |
|---|---|
| Maven verify / Java 21 / PostgreSQL real | `Tests run: 82, Failures: 0, Errors: 0, Skipped: 0` |
| npm ci, TypeScript y build React/Vite | SUCCESS; build en 887 ms |
| Instalación de Chromium | SUCCESS |
| Start isolated Docker Compose application | SUCCESS; construcción y acceso autenticado vía Nginx |
| Playwright | `26 passed (1.6m)` |
| Persistencia tras reinicio | `result: PASS`, comparación de filas y SHA-256 de 16 tablas |
| Recolección de logs y artefacto verification | SUCCESS |

El snapshot remoto incluyó 8 ejecuciones, 27 tareas, 3 diferencias, 3 casos comerciales y 2 enlaces caso/tarea. Son fixtures de CI, distintos de la base del taller. La prueba comprueba persistencia de los datos incluidos; no equivale a una prueba de backup/restauración ni aceptación por las áreas. Logs descargados localmente en `/tmp/proco-uat-main-ci.log` y metadatos en `/tmp/proco-uat-main-ci.json` (archivos temporales).

## 2. Revisión de requisitos, historia y migraciones

Se contrastaron la guía UAT, la especificación MVP original, los diagramas D1–D4, el procedimiento SIM-0.2 (texto de la copia DOCX conservada), PLAN/API, diseños y verificaciones de Sprints 1–6, y los servicios/UI actuales. Las fuentes originales y sus hashes siguen en [requirements/SOURCES.md](requirements/SOURCES.md). Los planes describen requisitos e hipótesis; sus instrucciones de implementación no se ejecutan como una nueva fase.

| Migración | Alcance revisado | Checksum Flyway aplicado en UAT |
|---|---|---:|
| V1 schema | Usuarios, especificaciones, OC, lote, auditoría append-only, resultados idempotentes | -226806153 |
| V2 simulation catalog | Dos referencias y OC ficticias de 10.000 kg; rangos SIM-1 | 829274014 |
| V3 nonconformities and holds | NC, dictámenes inmutables, holds; REJECTED exige BLOCKED y control diferido de bloqueo activo | 807684693 |
| V4 mixing | Combinación/snapshots/conceptos inmutables, autorización, revocación e idempotencia de mezcla | 899149864 |
| V5 execution containment | Ejecuciones, tareas por área, auditoría y controles de mutación/versión | -903699125 |
| V6 monitoring | CLOSED_WITH_INCIDENT, preservación de COMPLETED históricos; OLA DEMO versionados y snapshots | 196843797 |
| V7 receiving discrepancies and supplier cases | Diferencias, caso comercial por NC, enlaces a tareas, ledger; flags físico/ERP siempre false | -51692958 |

Las siete filas de `flyway_schema_history` tienen `success=true`. No se editaron SQL, servicios, frontend, tests ni Compose. V3–V7 amplían el esquema/controles sin reescribir decisiones históricas; V2/V4/V6 incorporan catálogos DEMO. El arranque añade usuarios/fixtures mediante claves idempotentes. La revisión y el arranque vacío no sustituyen los tests de upgrade de CI.

La historia actual integra Sprints 1–6. V1/V2 corresponden al ciclo inicial; Sprint 2 añadió V3, Sprint 3 V4 (PR #2), Sprint 4 V5 (PR #3), Sprint 5 V6 (PR #4, `2985958`) y Sprint 6 V7 (PR #5, `91688b6`). Se mantienen las evidencias históricas con sus SHA/resultados originales. Solo se corrigen en README, PLAN y verificación de Sprint 6 las referencias desactualizadas a su integración/CI pendiente.

## 3. Entorno preparado y aislamiento

| Recurso | Valor comprobado |
|---|---|
| Proyecto Compose nuevo | `proco-uat-91688b6` |
| Volumen nuevo de PostgreSQL | `proco-uat-91688b6_proco-data` |
| Base / usuario | `proco` / `proco` (credenciales ficticias locales de Compose) |
| Frontend / API vía Nginx | http://127.0.0.1:18098 / http://127.0.0.1:18098/api |
| Backend directo | http://127.0.0.1:18097 |
| PostgreSQL | 127.0.0.1:55442 |
| Interfaces publicadas | 127.0.0.1, según docker-compose.yml |

Se comprobó que estos puertos estaban libres y que no existía el proyecto/volumen UAT antes del arranque. Los seis proyectos anteriores (`procedureproco`, `proco-sprint2` a `proco-sprint6`) permanecieron detenidos; sus volúmenes no se montaron ni consultaron para escribir. Solo se insertaron datos ficticios por la API de la nueva base. No se ejecutó `down -v`, DELETE, TRUNCATE ni modificación de cantidades/OC existentes.

Arranque realizado desde la raíz del repositorio:

```sh
DB_PORT=55442 BACKEND_PORT=18097 FRONTEND_PORT=18098 docker compose -p proco-uat-91688b6 up -d --build
```

Para retomar **este entorno ya preparado**, desde la raíz del proyecto y con los mismos puertos libres:

```sh
DB_PORT=55442 BACKEND_PORT=18097 FRONTEND_PORT=18098 docker compose -p proco-uat-91688b6 start
curl -fsS -u 'almacen:Simulacion123!' http://127.0.0.1:18098/api/me
```

Si el primer curl falla, esperar a que PostgreSQL/backend terminen de iniciar y repetir la consulta. Para detener conservando los datos:

```sh
DB_PORT=55442 BACKEND_PORT=18097 FRONTEND_PORT=18098 docker compose -p proco-uat-91688b6 stop
```

No ejecutar Maven/Playwright completos contra esta base: sus fixtures cambiarían la preparación y consumirían saldo. La CI ya usó sus propias bases. Para otro taller desde cero, crear **otro nombre de proyecto/volumen y puertos libres**, fijar este SHA y crear nuevos datos desde la UI/API; no borrar ni restaurar silenciosamente el volumen preparado. Un clon nuevo con Compose solo trae las cinco semillas estándar, no los 14 fixtures locales que se detallan abajo.

## 4. Usuarios, datos y estado inicial

Se verificaron por HTTP autenticado `/api/me` las **11 cuentas**, con respuesta correcta y rol esperado. Contraseña exclusivamente local de simulación: `Simulacion123!`.

| Cuenta | Rol / uso |
|---|---|
| almacen | WAREHOUSE: recepción, conteo, muestra, aceptación y disponibilidad |
| calidad | QUALITY: inspección y responsable de NC si no la reportó |
| calidad2 | QUALITY: responsable alterno independiente cuando reporta calidad |
| tecnico | LAB_TECH: ensayo |
| aprobador | LAB_APPROVER: liberación, dictamen NC y concepto de mezcla |
| tejeduria | PRODUCTION_REQUESTER: solicitud/inicio y tarea de su área |
| produccion | PRODUCTION_APPROVER: autorización y cierre del montaje |
| geo | GEO: concepto, tarea, seguimiento y versiones OLA DEMO |
| tintoreria | DYEING: concepto, reporte de NC y tarea cuando aplica |
| abastecimiento | PROCUREMENT: autorización de cantidad y gestión del caso comercial |
| abastecimiento2 | PROCUREMENT: cierre independiente del autor de disposición |

**Datos comprobados:** 5 lotes estándar más 14 preparados, total 19; 13 RELEASED/AVAILABLE, 5 en proceso, 1 HELD/BLOCKED. Dos referencias DEMO (hilo/elastano), dos OC, seis reglas OLA DEMO. Saldo inicial posterior a la preparación: OC-H-001 **9.740 kg** (260 registrados) y OC-E-001 **9.845 kg** (155 registrados), ambas con máximo 10.000. El rechazo consume saldo igualmente; el cierre comercial no lo repone. Los 14 lotes preparados son de **10 kg** cada uno.

Se prepararon mediante API existente: recepción/inspección/muestra/ensayo/liberación/disponibilidad para los lotes que requieren ese estado, un ensayo fallido y NC para iniciar S05, conceptos/autorizaciones para S09/S10 y comienzo de montaje S10. Cada comando usa una clave UUID distinta, motivo explícito `Preparación técnica UAT; no aceptación funcional` y evidencia prefijada `UAT-PREP-91688b6-`. No se efectuaron rechazos ni cierres de los escenarios por anticipado.

S03 se inicia en el taller: no hay diferencia ni caso comercial precargados; sus datos de entrada están listos en la matriz. Tampoco hay tareas de contención todavía: se crearán al reportar las NC de S09/S10. Esto evita mostrar resultados finales como si los hubieran decidido los participantes. Las consultas iniciales de dashboard no tienen tiempos de respuesta ni vencimientos que mostrar.

## 5. Matriz de ejecución propuesta S01–S10

Para cada cambio de actor: **Salir → Usuario → Contraseña → Entrar a la simulación**, abrir la pestaña y volver a seleccionar el expediente. Desmarcar «Solo pendientes de mi área» si oculta el lote. Todas las acciones requieren motivo y evidencia; usar `UAT-Sxx-<fecha>-<rol>-<paso>`. Capturar UUID, versión y hora UTC además del nombre visible. La evidencia de la tabla es la que **debe obtenerse en el taller**, no un resultado ya aceptado.

| Escenario | Actor / cuentas en orden | Pasos de interfaz | Datos necesarios y preparados | Resultado esperado bajo reglas actuales | Evidencia a recoger |
|---|---|---|---|---|---|
| **S01 Recepción normal** | almacen → calidad → almacen → tecnico → aprobador → almacen | Materiales y NC → Nueva recepción → Registrar en cuarentena; seleccionar lote → Inspección conforme; Registrar muestra; Registrar ensayo; Aprobar liberación técnica; Confirmar disponibilidad | Crear `UAT-S01-TALLER-<sufijo>` en OC-H-001, 10 kg, fecha 2026-01-01, ubicación UAT, muestra única y resistencia 15. `UAT-S01` ya recibido permite empezar desde inspección si se omite el alta en la demostración. Variante elastano: OC-E-001, ensayo 500 | Alta UNDER_VERIFICATION/QUARANTINE; liberación RELEASED/QUARANTINE y disponibilidad separada RELEASED/AVAILABLE. Sin solicitud de mezcla | Capturas de alta y dos estados finales; `/api/lots/{id}/timeline`, seis eventos y actores; anotar si se empezó con fixture |
| **S02 Etiqueta/documento incompleto** | calidad; almacen para consultar/abrir NC | Materiales y NC → UAT-S02 → motivo de etiqueta incompleta → Retener por hallazgo; consultar Historial de bloqueos. Para formalizar: almacen → Reportar no conformidad y bloquear → origen Documental → responsable calidad | UAT-S02 en verificación, 10 kg; evidencia textual de etiqueta ilegible/no concordante. Los datos obligatorios del alta permanecen informados | HELD/BLOCKED; reinspección sola no levanta hold; sin disponibilidad ni liberación. Formalización NC explícita, no automática | Captura Retenido/Bloqueado; hold y NC si se abre; timeline antes/después. Registrar brecha de fotografía/dato físico |
| **S03 Diferencias de recepción** | almacen → calidad si hallazgo → abastecimiento → almacen | Recepción y proveedor → Nueva diferencia de recepción → OC/documento/conteo → Registrar diferencia → Confirmar conteo observado; si se marcó hallazgo, calidad registra concepto; abastecimiento informa propuesta/plan y Autorizar cantidad propuesta; almacen Ejecutar aceptación autorizada → Ver lote aceptado | OC-H-001, nombres únicos: A documento100/observado90/aceptado90; B 20000/20000, propuesta20000 debe denegarse y luego aceptar30 con plan19970; C 100/0/0 con plan; D 100/90, autorizar90, registrar otra recepción de cantidad **saldo actual−80**, intentar aceptar90 y revisar a80. Ejecutar D al final o en otro entorno pues consume casi todo el saldo | Apertura sin lote/reserva; aceptado ≤ observado y saldo al aceptar; lote solo por cantidad positiva en cuarentena. B no ingresa remanente; C no crea lote; D deniega sin perder expediente y exige revisión comercial explícita | Cantidades/documento/saldo antes y después; UUID diferencia/lote; `/api/receipt-discrepancies/{id}/timeline`; captura del 422 y de la revisión. No usar D con otras recepciones simultáneas durante el taller |
| **S04 Ensayo no conforme** | tecnico → almacen o tecnico abre NC → calidad; aprobador dictamina si se decide | Materiales y NC → UAT-S04 → Registrar ensayo con valor5; Reportar no conformidad y bloquear → Laboratorio → responsable calidad; aprobador Registrar dictamen NC No conforme; consultar restricciones | UAT-S04 UNDER_TEST/QUARANTINE, muestra `UAT-M-S04`, resultado pendiente; rango ficticio10–20. Este lote es distinto del de reensayo S05 | Ensayo deja HELD/BLOCKED; el hold automático debe formalizarse como NC. No se libera ni habilita stock. Decidir reevaluación o rechazo en taller sin confundirlo con cierre automático | Resultado5, hold activo, NC y dictamen; timeline; botón liberación no habilitado. Si se rechaza, documentar destino simulado y no reutilizar para S05 |
| **S05 Reensayo favorable** | calidad → almacen → tecnico → aprobador → calidad → aprobador → almacen | UAT-S05 → Inspección conforme; Registrar muestra nueva; ensayo15; comprobar aún BLOCKED; aprobador Registrar dictamen NC Conforme **después** del ensayo; responsable calidad → Levantar bloqueo NC; aprobador libera; almacen confirma disponibilidad | UAT-S05 HELD/BLOCKED con ensayo5 y NC ya abierta por tecnico, responsable calidad; nueva muestra `UAT-M-S05-R2` | Nuevo ciclo conserva ensayo5 en auditoría; favorable solo no desbloquea. Levantamiento explícito deja cuarentena; liberación y disponibilidad posteriores. Dictamen debe corresponder a versión vigente | Captura antes/después del levantamiento; ambos ensayos en timeline, dictamen vigente, decisión y actores. No liberar entre dictamen y decisión: lo invalidaría o sería denegado |
| **S06 Liberación sin autorización de mezcla** | tejeduria; produccion consulta | Mezclas por montaje: comprobar que no hay solicitud para `UAT-S06`; Montajes y tareas → Autorización para iniciar: no ofrece ese montaje. Opcional solicitar mezcla de los dos lotes y comprobar pendientes sin aprobar | UAT-S06-A/B RELEASED/AVAILABLE; 1 kg de cada uno; no mezclar con lotes de otros escenarios | Disponibilidad individual no autoriza montaje. Sin autorización no hay inicio posible; solicitud UNDER_REVIEW tampoco basta. UI oculta/inhabilita la opción en lugar de enviar un comando inválido | Captura de ambos lotes y selector sin autorización; si se crea solicitud, blockers y requiredRoles. La denegación HTTP se verifica en CI; una comprobación API manual debe registrarse aparte |
| **S07 Fechas/referencias distintas** | tejeduria → aprobador → geo → calidad → tintoreria → produccion | Mezclas por montaje → Nueva solicitud de mezcla, montaje `UAT-S07`, vigencia futura, 1kg por lote; cada área Registrar concepto de mezcla Favorable; produccion Confirmar decisión de mezcla; Consultar autorización vigente | UAT-S07-A HILO-DEMO fecha2026-01-01, UAT-S07-B ELASTANO-DEMO fecha2026-03-15. Misma referencia con fechas distintas puede probarse con otro lote H nuevo. Motivos/evidencias por área | Requiere Lab/GEO/Calidad y, en esta combinación con elastano, Tintorería. AUTHORIZED solo para combinación/montaje/vigencia exactos; sin consumir kilos. No existen tolerancias automáticas30/60d | IDs/versiones de lotes/mezcla, fechas, conceptos, regla MIX-SIM-1, vigencia y `/api/mix-requests/{id}/timeline` |
| **S08 Riesgo tintóreo / NC** | tejeduria → Lab/GEO/Calidad → tintoreria → produccion; variante tintoreria reporta NC | Crear `UAT-S08-NEG` con UAT-S08-A/B; conceptos favorables de Lab/GEO/Calidad; Tintorería selecciona Desfavorable y registra; Producción no puede autorizar, puede Denegar. Para revocación: nueva solicitud `UAT-S08-REV` con conceptos favorables y autorización; luego Tintorería → Ver lote → Reportar NC POST_RELEASE responsable calidad | A hilo/B elastano, 10kg cada uno, 1kg solicitados; evidencia ficticia «diferencia de tono». Solicitudes separadas porque un concepto es inmutable. No iniciar montaje en esta variante | Falta/desfavorable de Tintorería impide aprobación. NC posterior bloquea lote y revoca mezcla autorizada; no detecta tono automáticamente ni representa un ensayo tintóreo real | Captura de impedimento y denegación; nueva mezcla autorizada/revocada, NC/hold y correlación. No reducir S08 original solo a «concepto obligatorio» |
| **S09 NC tras comenzar montaje** | tejeduria → tintoreria → almacen/calidad/tejeduria/geo/tintoreria → produccion | Montajes y tareas → elegir autorización UAT-S09 → Iniciar montaje simulado; Tintorería en Materiales y NC reporta POST_RELEASE del lote B, responsable calidad; revisar mezcla/montaje; cada destinatario Acusar tarea y Completar tarea con evidencia propia; produccion Cerrar incidente con disposición | UAT-S09-A/B, mezcla ya AUTHORIZED con cuatro conceptos, sin ejecución todavía. No rechazar ni abrir NC con otro lote por error. Para demostrar levantamiento usar dictamen/resolve por separado, no para reanudar | Lote BLOCKED, mezcla REVOKED, ejecución SUSPENDED y cinco tareas CONTAINMENT (incluye Tintorería por elastano); cierre solo con todas CLOSED → CLOSED_WITH_INCIDENT, nunca COMPLETED normal/reinicio. NC puede seguir abierta si no se decide técnicamente | Capturas correlacionadas de lote, mezcla, montaje y tareas; `/api/executions/{id}/timeline` y Exportar expediente JSON; Seguimiento y OLA por montaje, acuses/cierres por área |
| **S10 Rechazo y proveedor** | tintoreria → aprobador → calidad → abastecimiento → abastecimiento2; demás áreas cierran sus tareas si se cierra incidente | Materiales y NC UAT-S10-A: reportar POST_RELEASE responsable calidad; aprobador dictamen No conforme; calidad Disposición Rechazar · devolución prevista y Confirmar rechazo definitivo. Recepción y proveedor → Casos de proveedor: contacto, respuesta, disposición; Abrir ejecución de la tarea → abastecimiento acusa/completa su tarea. abastecimiento2 confirma casilla sin retorno/ERP y Cerrar caso administrativamente | UAT-S10-A/B RELEASED/AVAILABLE y montaje UAT-S10 ya IN_PROGRESS; aún no hay NC/rechazo/caso. Referencias `SIM-COM-UAT-S10-*`; respuesta ACCEPTED, DISPUTED o NO_RESPONSE explícita; tratamiento ficticio y dirección RETURN_REQUESTED/CREDIT_REQUESTED/OTHER | REJECTED/BLOCKED definitivo; caso por NC y tarea Procurement existente enlazada, sin duplicarla. Cierre exige pasos completos, todas las tareas **Procurement enlazadas** CLOSED y otra identidad. No devuelve físicamente, no contabiliza ERP ni restaura saldo. Cierre de incidente aparte exige todas las áreas | UUID NC/caso/tarea/ejecución, timelines material/caso/ejecución, tratamiento y dos identidades; flags false; saldo OC sin reposición. Export JSON ejecución no sustituye timeline comercial |

**Orden sugerido:** Taller1 S01,S02,S03 A–C,S04,S05; Taller2 S06,S07,S08,S09,S10. Ejecutar S03 D (cambio de saldo) al final o en entorno adicional: no requiere cambiar la OC, pero una recepción normal grande consume su saldo. Las cantidades se recalculan en la UI/API al momento; no introducir un saldo supuesto como si siguiera vigente.

## 6. Evidencia y condiciones para facilitar

Se comprobaron localmente el login de GEO y las cinco pestañas mediante Chromium, sin decisiones de negocio ni errores JavaScript/alertas de error. Capturas técnicas en `/tmp/proco-uat-{materials,mixing,execution,monitoring,supplier}.png`; resultado en `/tmp/proco-uat-ui.json`. Se inspeccionaron visualmente Materiales y Seguimiento. Esto verifica acceso/navegación, no demuestra la aceptación de las diez filas.

Para evidencia del taller: guardar captura antes/después, usuario/área, SHA, versión Flyway, UUID, versión y correlación. Las rutas de timeline de lotes, mezclas, ejecuciones, diferencias y casos existen; solo las ejecuciones tienen export JSON y las tareas CSV paginado. No hay botón genérico para exportar el expediente S03/S10. Obtener JSON de sus GET/timeline con herramientas de desarrollo o consulta autenticada de solo lectura, sin incluir cabeceras de credenciales. Los resultados CI y los del taller deben tener carpetas/fechas distintas.

Usar la ficha de la guía §E y registrar cada DEC-01..12; mantener todas **PENDIENTES** hasta decisión del responsable. Las diez filas tienen estado inicial **NO EJECUTADO por las áreas**. Asignar nombre/cargo/área, resultado observado, APROBADO / APROBADO CON OBSERVACIONES / RECHAZADO / NO EJECUTADO y evidencia en el taller. Las cuentas ficticias no constituyen firmas de los responsables.

## 7. Limitaciones, efecto y corrección mínima propuesta

| Limitación comprobada | Efecto en la demostración | Tratamiento mínimo para UAT; sin nueva funcionalidad |
|---|---|---|
| Cinco semillas genéricas no separan escenarios destructivos | Una NC de S09 revocaría mezclas de otros casos que reutilicen el lote | Resuelto en preparación con 14 lotes API separados; conservar manifiesto e identificadores. No reutilizar pares entre filas |
| Vigencia temporal real y autorización inmutable | S09/S10 dejan de ser utilizables al vencer; parar contenedores no detiene UTC | Vigencias exactas al final del reporte. Antes del taller consultar elegibilidad. Si vencen, crear nueva solicitud, conceptos y montaje con sufijo; no editar históricos. Si S10 antiguo ya iniciado no permite avanzar, documentar cancelación y preparar otro caso |
| S03 D consume casi todo el saldo de hilo | Impide otras recepciones aunque los lotes sigan disponibles | Ejecutarlo al final o en otro proyecto; nunca aumentar la OC o borrar lotes para ocultar el efecto |
| Código de lote/fecha obligatorios en alta, sin fotos/adjuntos | S02 no permite guardar literalmente un alta sin esos datos ni verificar etiqueta física | Registrar hallazgo de ilegibilidad/discordancia en evidencia y usar Retener; dejar la brecha anotada para aprobación de Calidad. No relajar validación sin decisión de negocio |
| Hallazgo/ensayo fallido crea hold, no expediente NC automático | Sin formalización no se ofrece una decisión NC; reinspección no desbloquea | Incluir paso Reportar NC y responsable independiente en S02/S04; S05 ya tiene NC formal. No duplicar reglas para la demo |
| Dictamen NC ligado a versión actual y segregación | Un cambio posterior invalida el dictamen; quien reporta no puede resolver | Registrar dictamen después de reensayo; resolver con el responsable asignado. Usar calidad2 si reportó calidad y abastecimiento2 para cierre comercial |
| S07 no valida compatibilidad industrial, ni 30/60d automáticos | Fechas/referencias distintas no prueban tolerancias reales | Mostrar hipótesis MIX-SIM-1 y conceptos manuales; registrar DEC-05/06 antes de pedir cambios |
| S08 original contempla NC/revocación, no solo un veto de concepto | Demostración limitada al concepto dejaría cobertura funcional incompleta | Guion en dos solicitudes NEG/REV; reporte NC posterior por Tintorería en variante REV. No inferir medición de tono real |
| No hay tasks/alertas vencidas inicialmente; OLA30/240 y60/480min | Dashboard inicial sin tiempos no es fallo; el taller no puede esperar 8h para mostrar vencimiento de cierre | Crear tareas durante S09/S10 y refrescar. Si se requiere demo rápida, GEO puede crear reglas DEMO1/2min **antes de esas tareas**, con evidencia y aprobación del guion; no se hizo en preparación. No cambiar reloj ni timestamps. Reglas previas/snapshots permanecen |
| Bandejas S03/S10 limitadas a últimos100; filtros locales | Talleres repetidos podrían ocultar expediente antiguo | Registrar UUID y usar apertura por identificador; mantener este proyecto acotado. No hace falta nueva paginación para diez escenarios |
| Acceso localhost y Basic ficticio; un rol por cuenta | No es una instalación compartida por LAN ni identidad corporativa | Demostración facilitada local o ventanas independientes cambiando identidades. Cerrar sesión y refrescar tras decisiones de otra área. Cualquier despliegue remoto seguro requiere alcance separado |
| Reintentos inciertos bloquean nuevos comandos por identidad | Interrumpir la red puede dejar la sesión esperando confirmación | Usar «Reintentar el mismo comando», misma clave/cuerpo; no borrar sessionStorage para saltar el control. Negativas de concurrencia/red requieren guion técnico separado y evidencia |
| Sin sublotes, transporte, movimiento físico, ERP, correo ni anexos binarios | S09 controla lote completo; S10 no acredita devolución física ni crédito | Mantener alcance de simulación visible y registrar DEC-03/04/07/09/10/12. No se propone implementar estas funciones para poder afirmar aceptación |

No se identificó un defecto técnico bloqueante de acceso/preparación que exija modificar código. Las correcciones actuales son documentales sobre el estado de integración de Sprint 6, más este reporte y la conservación de la guía. Si el taller descubre una contradicción con una regla ratificada, abrir BUG con evidencia y proponer PR técnico separado; no cambiar reglas de negocio durante la sesión.

## 8. Decisión pendiente de los responsables

Listo técnicamente para **convocar y facilitar** el taller bajo los límites indicados; no equivale a GO funcional ni productivo. Faltan participantes/fechas/responsables designados, ejecución manual documentada S01–S10, ratificación o rechazo de DEC-01..12 y acta con evidencias. Mantener el resultado de todas las áreas como NO EJECUTADO hasta su participación. El plan no autoriza comunicaciones a proveedores ni integración real.

## Anexo A. Manifiesto del entorno local preparado

Preparado 2026-11-13T22:27:43.043457+00:00. Son UUID de este volumen local; no están sembrados por el repositorio y no se deben copiar a otra base esperando que existan. Todos los datos son sintéticos.

| Escenario/lote visible | UUID | Estado inicial / versión |
|---|---|---|
| UAT-S01 | `ebceec12-269c-4c88-90c6-fdfceeac78cc` | UNDER_VERIFICATION/QUARANTINE v0 |
| UAT-S02 | `8152313e-b176-48b9-b246-9ecb5dc140cd` | UNDER_VERIFICATION/QUARANTINE v0 |
| UAT-S04 | `9895fdb3-f9a9-4766-bee3-b784696f3621` | UNDER_TEST/QUARANTINE v2 |
| UAT-S05 | `62b805b7-f69c-4081-8706-605f331e3a95` | HELD/BLOCKED v4 |
| UAT-S06-A | `596887ff-ca11-4c42-ac7a-de028eadaae0` | RELEASED/AVAILABLE v5 |
| UAT-S06-B | `88342e49-3f43-45ab-9910-ec0bd6aa623b` | RELEASED/AVAILABLE v5 |
| UAT-S07-A | `21b8ddad-f30a-4e85-af3e-09240792fee0` | RELEASED/AVAILABLE v5 |
| UAT-S07-B | `4147c19e-491e-4f7f-bebc-cb749496bf8f` | RELEASED/AVAILABLE v5 |
| UAT-S08-A | `c9625b03-0513-49ff-90cb-0fc0f77747b5` | RELEASED/AVAILABLE v5 |
| UAT-S08-B | `d0f7d597-0429-4f8c-9b15-f69e20a063a5` | RELEASED/AVAILABLE v5 |
| UAT-S09-A | `eb49b62d-37ce-4b1c-a785-d6626db08ef0` | RELEASED/AVAILABLE v5 |
| UAT-S09-B | `05f58220-9e61-4886-8882-7bbb428c3b6c` | RELEASED/AVAILABLE v5 |
| UAT-S10-A | `b40f059b-0d7f-4545-b6b5-d28d50b7b5e7` | RELEASED/AVAILABLE v5 |
| UAT-S10-B | `e5de6cdd-7a07-4a2f-a692-91dd53112d18` | RELEASED/AVAILABLE v5 |

| Preparación asociada | Identificador y vigencia |
|---|---|
| Mezcla UAT-S09 AUTHORIZED | `6ac825e1-e6c5-4ace-bcea-d1652084b872`; vence **2026-11-20T22:27:47.975218Z UTC** |
| Mezcla UAT-S10 AUTHORIZED | `89b6d1dc-3708-493d-afe2-3138fc043b06`; vence **2026-11-20T22:27:49.175136Z UTC** |
| Ejecución UAT-S10 IN_PROGRESS | `f3fe18d3-ac4a-468b-87d5-fbbb3d66b7d4` |
| NC inicial S05 OPEN | `ca9a290c-f9ec-4b7c-b983-dd99a7814f65`; reportó tecnico, responsable calidad |

El registro temporal de preparación `/tmp/proco-uat-fixtures.json` conserva cuerpos y claves de los comandos sin credenciales. El anexo permite ubicar los expedientes aunque se pierda ese archivo temporal. Para repetir desde cero se debe usar un proyecto nuevo y los pasos de la matriz; no se entrega un nuevo mecanismo de carga de datos.

## Anexo B. Comprobaciones locales observadas

- Construcción/arranque completo del proyecto aislado: código de salida 0; PostgreSQL healthy, API autenticada y frontend accesibles.
- Once GET `/api/me` con cuentas de la tabla: correctos. Catálogo/estados/cantidades contrastados con consultas SQL de solo lectura a este volumen.
- Navegación Chromium por las cinco vistas: PASS, sin `pageerror` ni alertas de error. No se ejecutaron S01–S10 manuales ni una nueva regresión Maven/Playwright completa contra UAT.
- Reinicio exclusivo del proyecto UAT: **PASS**, igualdad exacta de cantidad de filas y SHA-256 en **24 tablas**, incluidas las siete filas de Flyway, usuarios, catálogo y datos preparados. Resultado local `/tmp/proco-uat-restart.json`.

| Tabla local seleccionada | Filas conservadas | SHA-256 antes = después |
|---|---:|---|
| flyway_schema_history | 7 | `ab8a2681026db4f61fc69a6b70bc71474370658431a376d6dd4fddc0cf9aa32d` |
| app_user | 11 | `493b30fb6387f5a773cf833309a45c71d7397ded99c90094439734ae5a905b60` |
| purchase_order | 2 | `4a5dbb427c92ff86fb2d7e893c269bd86391cc1d144910c03efb3e7d2ec45136` |
| lot | 19 | `402e721dbc59d07480c33da5b9d62eeaf7817f94ca5f50bde96078b7e15b08f4` |
| non_conformity | 1 | `68b0c0930e3f4d00d0379253ef0b9b18673bada0f5bddf4df8b2b4393591c531` |
| mix_request | 2 | `e01fd7ffc6dc1db4446ecedf8d36d805f142ddfa1b0138da1fdce0f6a1745488` |
| montage_execution | 1 | `df92056ef6da0f52073fee33bbc0861b2349059afc0bd022da6e915bede313b8` |
| containment_task | 0 | `37517e5f3dc66819f61f5a7bb8ace1921282415f10551d2defa5c3eb0985b570` |
| receipt_discrepancy | 0 | `37517e5f3dc66819f61f5a7bb8ace1921282415f10551d2defa5c3eb0985b570` |
| supplier_case | 0 | `37517e5f3dc66819f61f5a7bb8ace1921282415f10551d2defa5c3eb0985b570` |

Las tablas de tareas/diferencias/casos todavía vacías solo prueban la conservación de su estado inicial local. La evidencia de persistencia con datos S03/S10 y tareas no vacías procede de la CI del SHA exacto, indicada en §1. No se falsificaron casos finalizados para llenar el dashboard.

Al finalizar la preparación se detiene el proyecto UAT conservando su volumen, de acuerdo con la preferencia previa de mantener servidores apagados hasta necesitarlos. Iniciarlo con el comando §3 antes del taller y verificar vigencias. Los proyectos anteriores permanecen detenidos.
