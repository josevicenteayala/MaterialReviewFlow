# Sprint 4 — diseño previo y decisiones de simulación

Base: main d10a84f086220d12a5da394ee0a1f686a8ee4d85. CI del merge aprobada: https://github.com/josevicenteayala/MaterialReviewFlow/actions/runs/43290438001. El documento proporcionado PROCO_SPRINT4_PLAN.md se conserva y se incorpora como SPRINT-4-PLAN.md. Ningún supuesto representa política aprobada de planta.

## Brechas y alcance P0

| Historia | Reutilización | Cambio |
|---|---|---|
| S4-01 | MixStore.usable, lotes y reglas de mezcla | execution: inicio transaccional con versión de mezcla y montaje exacto |
| S4-02 | HoldStore → MixRevocations, NC existente | extensión de revocación: suspensión y tareas atómicas, incluso si la mezcla ya estaba revocada |
| S4-03/04 | Roles, auditoría, CommandRunner, sessionStorage | tareas persistentes por área, acuse/cierre; ejecución completa/cancelada |
| S4-08 | 33 pruebas backend/12 navegador de Sprints 1–3 | S09 con ejecución real del simulador; carreras, rollback, upgrade y E2E |

Se ofrece UI mínima de ejecución y tareas necesaria para P0. No se incluye OLA, métricas, exportaciones, consumo/reserva, ERP, máquinas ni notificaciones externas. S01–S10 conservan sus límites funcionales previos; no se afirmará que tolerancias comerciales o procedimientos físicos pendientes estén completos.

## Decisiones antes de código

- Una ejecución por mixRequestId para siempre; máximo una IN_PROGRESS por montageCode (índice parcial). Una SUSPENDED no se reanuda; iniciar otra exige nueva mezcla autorizada. Código exacto, sin normalización silenciosa.
- POST start bloquea lotes ordenados, mezcla y revalida usable con Clock. No se confía en GET ni se modifica el inventario.
- NC/hold sobre IN_PROGRESS suspende y crea cuatro tareas (WAREHOUSE, QUALITY, PRODUCTION_REQUESTER, GEO), más DYEING según regla. Cada nuevo evento tiene su propio conjunto, también sobre SUSPENDED. Una COMPLETED conserva estado y recibe tareas RETROSPECTIVE_REVIEW con evento de alerta. CANCELLED conserva historial y no requiere nueva contención.
- Rechazo agrega tarea ficticia PROCUREMENT, incluso después de revocación previa. Se propaga ncId explícitamente desde HoldStore/NcService; no se infiere la NC más reciente.
- OPEN → ACKNOWLEDGED → CLOSED. Rol destinatario validado en servidor; quien acusa queda asignado y completa. Motivo/evidencia obligatorios para ambas acciones; columnas separadas y eventos inmutables conservan ambas. No se ofrece cancelación administrativa de tareas sin política de autoridad definida.
- Solo PRODUCTION_APPROVER decide complete/cancel. Exige versión, evidencia y todas las tareas CLOSED; QUALITY revisa con identidad distinta del iniciador y del decisor. El modelo actual tiene un rol por usuario, lo cual impide certificar todas las áreas con una identidad. COMPLETED/CANCELLED terminales.
- Completar IN_PROGRESS exige autorización aún usable. Si caduca, 422 y evento independiente REVIEW_REQUIRED tras rollback; no avance. Cancelar permite salida documentada tras tareas cerradas. Una SUSPENDED puede finalizar con disposición explícita y todas las tareas cerradas; jamás volver a IN_PROGRESS. No hay reloj externo ni suspensión automática por tiempo.
- Lecturas autenticadas de ejecuciones compartidas; bandeja de tareas limitada al rol autenticado; detalle de ejecución muestra toda la coordinación para trazabilidad. Sin parámetro de rol confiado al cliente.

## Clases, tablas y compatibilidad

V5 aditiva: montage_execution, containment_task, execution_event; command_result añade execution_id/task_id y reemplaza XOR por num_nonnulls=1. No reescribe filas legacy ni V1–V4. Se conserva auditoría append-only mediante prevent_audit_change.

CommandRunner generaliza la columna objetivo con lista interna fija y añade runners tipados para ejecución/tarea; misma cerradura actor+clave, fingerprint y replay actual. AuditLog añade eventos SIM-4 e intentos denegados REQUIRES_NEW. MixRevocations invoca ExecutionContainment después de bloquear mezclas ordenadas; HoldStore y NcService transmiten ncId. ExecutionService/ExecutionStore/ExecutionController y TaskService/TaskController reutilizan JDBC/Clock/roles, no duplican NC o mezcla.

Orden: clave idempotente → lotes ordenados → mezclas ordenadas → ejecución → tareas ordenadas. NC ya posee solo su lote y no solicita otros. Comandos de tarea bloquean participantes antes de mezcla/ejecución/tarea; evita carrera cierre/NC. Las mezclas relevantes se bloquean aun revocadas para serializar nuevas causas. Restricciones SQL protegen estados terminales/definición/avance de versión y unicidad. Índices para lotes, ejecución/tareas y bandeja; consultas batched sin N+1.

Riesgos: mayor contención en mezclas compartidas; carreras se probarán en PostgreSQL real. Nuevas columnas de idempotencia exigen INSERT explícito; pruebas de upgrade anteriores se fijan a su versión destino y se añade V4→V5. Cliente debe discriminar respuestas mix/execution/task/lot al reintentar; mantiene clave/cuerpo congelados existentes. La caducidad bloquea avance, pero una consulta no promete disponibilidad futura.

Pendiente de validación funcional de planta: autoridad física para detener, definición de montaje, reinicio, OLA, granularidad cono/caja y tratamiento industrial de caducidad. Estos puntos se resuelven solo como hipótesis explícitas del simulador arriba; sin integraciones.
