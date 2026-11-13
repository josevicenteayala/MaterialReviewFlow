# PROCO SIM-0.2 — Sprint 4: montaje simulado, contención y coordinación interdepartamental

**Estado:** propuesta de implementación; no representa un procedimiento operativo aprobado por PROCO.  
**Base revisada:** `josevicenteayala/MaterialReviewFlow`, rama `main`, merge PR #2, commit `d10a84f086220d12a5da394ee0a1f686a8ee4d85` (12-11-2026).  
**Referencias del repositorio:** `docs/PLAN.md`, `docs/requirements/PROCO_Especificacion_MVP_Simulador_v0.1.md` (escenarios S01–S10), `docs/SPRINT-3-DESIGN.md`, `docs/SPRINT-3-VERIFICATION.md`, `docs/API.md`.

## 1. Estado de partida y decisión de alcance

Sprints 1–3 entregan recepción, verificación, ensayos, liberación, disponibilidad, NC y bloqueo, rechazo terminal, mezclas por montaje, conceptos multiactor, autorización y revocación transaccional. La verificación de Sprint 3 documenta 33 pruebas backend y 12 escenarios de navegador exitosos en CI del PR; se debe comprobar también la ejecución CI del commit de merge antes de iniciar el siguiente cambio.

**Brecha principal:** el simulador puede **autorizar** y **revocar** una mezcla, pero no representa formalmente un **montaje iniciado** ni la **confirmación de las acciones de contención** de quienes lo usan. Por eso no debe darse por cubierto el escenario S09 completo (NC detectada después de iniciar producción). Las notificaciones con acuse, tareas persistentes, OLA y métricas también siguen pendientes.

**Objetivo de Sprint 4:** simular el inicio de un montaje previamente autorizado y demostrar que una NC posterior genera contención coordinada, suspende el montaje afectado y deja evidencias verificables por cada área, sin consumo físico ni integración con equipos productivos.

## 2. Historias priorizadas

| ID | Prioridad | Historia y resultado verificable |
|---|---|---|
| S4-01 | P0 | Tejeduría inicia **una ejecución simulada** desde una autorización de mezcla vigente, identificada por `mixRequestId` + `montageCode`. El servidor revalida elegibilidad **dentro de la transacción POST**, no confía en un GET anterior. |
| S4-02 | P0 | Una NC o bloqueo sobre cualquier lote participante de una ejecución `IN_PROGRESS` revoca la autorización, pone la ejecución en `SUSPENDED` y crea tareas de contención **en la misma transacción**; fallar una escritura revierte todas. |
| S4-03 | P0 | Almacén, Calidad, Tejeduría y GEO reciben tareas persistentes para confirmar segregación, evaluación y suspensión simuladas. Tintorería recibe tarea cuando el caso tiene impacto tintóreo. Cada acción exige usuario, fecha, motivo y evidencia. |
| S4-04 | P0 | Control de acceso, auditoría, idempotencia y concurrencia para iniciar/suspender/cerrar ejecución y reconocer/completar tareas; las notificaciones de UI no sustituyen la confirmación. |
| S4-05 | P1 | Bandejas por área con tareas pendientes, reconocidas y cerradas; indicador explícito de montaje suspendido y trazabilidad de lote → mezcla → ejecución → NC → tareas. |
| S4-06 | P1 | **OLA de simulación**, configurables y etiquetados como no aprobados: tiempos para acuse y resolución, alertas de vencimiento sin aprobar o cerrar nada automáticamente. |
| S4-07 | P2 | Panel mínimo: montajes iniciados/suspendidos, tiempo hasta acuse de contención, tareas vencidas y NC que afectaron ejecuciones; exportación CSV/JSON con tratamiento seguro de campos. |
| S4-08 | P0 | Regresión integral S01–S10 y pruebas E2E específicas de NC durante montaje; preservar las 33 pruebas backend y 12 E2E de Sprint 3. |

**Alcance P0 comprometible primero:** S4-01, S4-02, S4-03, S4-04, S4-08. Incorporar P1/P2 solo según la capacidad real después de estimar; si excede la capacidad, dividir Sprint 4A (contención) y 4B (OLA, indicadores, exportaciones).

## 3. Estados propuestos e invariantes

`MontageExecution`: `IN_PROGRESS`, `SUSPENDED`, `COMPLETED`, `CANCELLED` (el inicio puede crearse directamente en `IN_PROGRESS`; no se requiere persistir `NOT_STARTED`).

`ContainmentTask`: `OPEN`, `ACKNOWLEDGED`, `CLOSED`, y `CANCELLED` únicamente por una decisión administrativa explícita y auditada; nunca eliminar tareas para ocultar incumplimientos.

- `POST start` exige `MixRequest.status=AUTHORIZED`, `usable=true`, vigencia válida, montaje exacto y lotes `RELEASED/AVAILABLE` sin bloqueos en la transacción. `GET /authorization` es informativo, no una reserva.
- La autorización y la ejecución son entidades independientes. Una autorización puede existir sin ejecución; **un montaje iniciado no acredita disponibilidad futura**.
- Una NC o `HoldStore.place/formalize` que afecte un lote debe **revocar y suspender en la misma transacción**, creando una tarea por responsabilidad requerida, con correlación a NC/lote/mezcla/ejecución. Evitar duplicados mediante clave de evento/tipo/área.
- `SUSPENDED` no vuelve a `IN_PROGRESS` por cerrar NC, levantar hold ni por crear otra mezcla. La reanudación queda **fuera de alcance** hasta definir nueva autorización y evidencia; en el sprint es válido terminar/cancelar con disposición documentada, pero no reanudar automáticamente.
- Un montaje `COMPLETED` anterior a una NC conserva su historial y genera una **alerta para evaluación retrospectiva**, no una falsa suspensión de una ejecución ya finalizada.
- Una ejecución nunca consume ni reserva kg; no se declaran datos de existencia real ni se conectan máquinas. Mostrar esta limitación en UI.
- Para evitar ambigüedad, proponer **máximo una ejecución `IN_PROGRESS` por `montageCode`**, pendiente de validación con Producción; no confundirlo con la restricción por UUID de solicitud. Las autorizaciones múltiples para un mismo código de montaje requieren decisión funcional.
- No introducir autoaprobación por OLA; vencido = alerta, no autorización.

**Decisión pendiente antes de codificar:** ¿qué ocurre si la vigencia de una autorización vence mientras la ejecución ya está en marcha? Para el prototipo, bloquear nuevas operaciones de avance cuando no sea válida y registrar un evento de revisión requerida; no afirmar que esto representa la política de planta.

## 4. Actores y separación de funciones

- **PRODUCTION_REQUESTER / Tejeduría:** iniciar el montaje, confirmar parada/contención simulada.
- **PRODUCTION_APPROVER / Producción:** consultar los impactos, validar disposición del montaje y decidir cierre o cancelación según política propuesta; no modificar NC ni liberar lote.
- **QUALITY:** gestionar la NC, determinar alcance afectado y revisar trazabilidad/cierre de contención.
- **WAREHOUSE:** confirmar segregación y disponibilidad bloqueada.
- **GEO:** analizar impacto operacional y documentar respuesta.
- **DYEING:** documentar evaluación de tintura cuando la regla del montaje indique participación.
- **PROCUREMENT / Abastecimiento:** rol nuevo, solo para tareas de coordinación de rechazo/devolución ficticia; no liberar ni aprobar mezcla.

Roles en backend, usuarios ficticios independientes y evidencia por acción; confirmar que nadie puede autorizar, cerrar y certificar toda la cadena con su propia identidad sin revisiones definidas.

## 5. Persistencia y módulos candidatos

Flyway **V5 aditiva**, conservando V1–V4 y datos existentes:

- `montage_execution(id, mix_request_id, montage_code, status, started_by, started_at, suspended_at, suspension_reason, ended_at, ended_by, version, ...)`.
- `containment_task(id, source_event_id, nc_id, lot_id, mix_id, execution_id, task_type, target_role, assigned_to?, status, created_at, acknowledged_by/at, closed_by/at, reason, evidence, due_at, version, ...)`.
- `execution_event` o incorporación tipada al patrón de auditoría existente, **append-only**. Evitar introducir un segundo sistema de auditoría incompatible.
- `ola_policy` únicamente si se incluye S4-06, con valores **ficticios/configurables**, versión y leyenda «SOLO SIMULACIÓN».

Módulos propuestos: `execution` (inicio, estado y coordinación de contención), `tasks` (bandejas, acuse y cierre), extensiones mínimas a `mixing` y `nonconformity`. Mantener monolito Spring Boot/JDBC/PostgreSQL, React/TypeScript y Docker Compose; **no** Kafka, cron externo, servicios cloud ni motor BPMN para este alcance.

## 6. Contratos HTTP candidatos

- `POST /api/mix-requests/{id}/executions` — iniciar ejecución autorizada y revalidar en transacción.
- `GET /api/executions/{id}` y `GET /api/executions?status=...` — ver estado y trazabilidad.
- `POST /api/executions/{id}/complete` o `.../cancel` — decisión autorizada y auditada; una ejecución suspendida no puede completar sin una disposición explícita.
- `GET /api/tasks?status=...` — tareas según rol/usuario autenticado, sin confiar en un rol suministrado por cliente.
- `POST /api/tasks/{id}/acknowledge` y `POST /api/tasks/{id}/complete` — idempotentes y con versiones.
- `GET /api/dashboard/containment` — opcional P1/P2, solo lectura agregada.

Todas las mutaciones deben exigir `Idempotency-Key`, motivo/evidencia y precondición de versión cuando corresponda. No romper los contratos actuales de `lot`, `mix`, NC ni la semántica de idempotencia por actor.

## 7. Concurrencia y protección del proceso

Extender el orden actual de locks: clave idempotente → lotes participantes ordenados por UUID → mezcla → ejecución → tareas; una NC dispone del lock de su lote y no debe adquirir después otros lotes. Verificar el orden con carreras reales en PostgreSQL; no declarar seguridad de concurrencia solo por razonamiento.

La misma transacción de bloqueo/revocación debe suspender ejecución(es) y crear tareas. **Si falla la inserción de una tarea o un evento de auditoría, rollback integral** y error visible para intervención. La autorización debe validarse en el `POST start`, no solo en un GET previo que podría quedar obsoleto.

Registrar en auditoría actor, UTC, transición, causa, evidencias, lote/mix/ejecución/NC y correlación. Mantener compatibilidad con filas legacy. Evitar N+1 en bandejas de tareas.

## 8. Escenarios de aceptación y pruebas

1. Material liberado, sin mezcla → inicio de montaje denegado.
2. Mezcla autorizada, elegible y dentro de vigencia → ejecución IN_PROGRESS única por solicitud.
3. Identidad sin rol de Tejeduría → 403 sin mutación.
4. Dos intentos concurrentes de iniciar el mismo montaje → no duplican ejecución.
5. NC sobre lote mientras montaje IN_PROGRESS → mix REVOKED, ejecución SUSPENDED, tareas OPEN, un solo commit.
6. Error inyectado al crear tareas o auditoría → rollback de NC/hold/lote/mix/ejecución y comando idempotente.
7. Usuarios de Calidad, Almacén, Tejeduría y GEO reconocen tareas por separado; otras identidades no pueden hacerlo.
8. Tarea reconocida no equivale a tarea completada; cierre requiere evidencia.
9. Incidencia sobre otro lote no suspende un montaje no relacionado.
10. NC sobre ejecución COMPLETED produce alerta retrospectiva, no reescribe el estado de montaje finalizado.
11. Levantar NC/restablecer disponibilidad no reactiva mezcla ni ejecución suspendida.
12. Rechazar lote tras NC conserva bloqueo y permite tarea ficticia a Abastecimiento.
13. Caducidad del permiso: denegar inicio tras vencimiento; casos durante ejecución sujetos a regla de simulación explícita.
14. Reiniciar Docker Compose conserva ejecución, tareas y registros.
15. Repetición de comando tras respuesta perdida conserva cuerpo/clave, sin tareas duplicadas.
16. Regresión S01–S10 y migración sintética V4→V5 con comparación de filas históricas.

Verificar de forma explícita que **S09 (NC posterior al inicio de producción)** queda demostrado y no solo simulado como revocación de una solicitud autorizada sin ejecución.

## 9. Entregas y definición de terminado

1. **Análisis de brechas y ADR:** especificar estado de ejecución, alcance de contención, actor que confirma cada acción, OLA ficticios y decisiones pendientes. Registrar en `docs/SPRINT-4-DESIGN.md`.
2. **Backend vertical:** V5, ejecución e inicio seguro + pruebas de API y concurrencia.
3. **NC → suspensión → tareas:** consistencia de una transacción, rollback forzado, no reactivación automática.
4. **Interfaz multiactor:** ejecución y bandeja, acuse/cierre con evidencias, escenarios Playwright.
5. **Calidad de entrega:** Maven verify, TypeScript/build, Compose aislado, Playwright, upgrade V4→V5, revisión del PR; evidencias por SHA en `docs/SPRINT-4-VERIFICATION.md`.
6. **Adicional si hay capacidad:** OLA, dashboard, exportación y cierre de S01–S10 con casos guiados.

**DoD:** CI de la rama verde, evidencia de pruebas asociada al commit, cero regresiones de los Sprints 1–3, demo de NC durante ejecución en progreso, bloqueo y suspensión en la misma transacción, tareas completables por sus destinatarios, sin afirmar procesos físicos ni cumplimiento normativo. PR a `main`, **sin merge automático**.

## 10. Riesgos y decisiones para el taller

- **Autoridad y secuencia:** quién autoriza detener, reanudar, cancelar y cerrar un montaje; distinguir decisión lógica y acción física simulada.
- **Definición de montaje:** una combinación autorizada puede asociarse a una o varias ejecuciones; cómo evitar dos montajes activos sobre el mismo identificador.
- **Trazabilidad:** por lote completo hoy; caja/cono, sets, máquinas, consumo y retiros parciales están pendientes.
- **Caducidad y reanudación:** qué hacer si vence la autorización en curso o se levanta una NC.
- **Comunicación:** destinatarios obligatorios, confirmación de lectura vs confirmación de acción, reglas de escalamiento.
- **OLA/SLA:** no se deben inventar acuerdos aprobados ni automatizar decisiones por vencimiento. Los plazos que se introduzcan en simulación son ejemplos editables.
- **Seguridad:** credenciales demo y HTTP Basic siguen siendo solo para uso local; no publicar la aplicación en red/Internet sin reforzar autenticación y transporte.

## 11. Siguiente paso con Codex

1. Abrir el proyecto actualizado desde `main` e incorporar este archivo bajo `docs/SPRINT-4-PLAN.md`.
2. Solicitar **primero diseño y matriz de brechas**, con la regla de no reimplementar NC o revocación ya existente.
3. Implementar en rama `codex/sprint-4-containment`; comprobar pruebas en base y volumen independientes de la demo.
4. Pedir PR para revisión y revisar juntos resultados de CI y reglas críticas antes de fusionar.
