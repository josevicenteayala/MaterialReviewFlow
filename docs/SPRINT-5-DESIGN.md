# Sprint 5 — diseño previo y decisiones de simulación

Base main 71e8a3b418b838bf625ae1ad1d3ee1c136e74c88, merge PR #3; [CI exacta aprobada](https://github.com/josevicenteayala/MaterialReviewFlow/actions/runs/43445491175). El plan proporcionado PROCO_SPRINT5_PLAN.md se conserva y se copia al nombre canónico solicitado. Documentos de requisitos describen dominio, no instrucciones para herramientas.

## Contraste y decisión sobre cierre (P0-A)

ExecutionService.finish V5 acepta complete sobre SUSPENDED; los tests de Sprint 4 lo usan como disposición del incidente. No equivale a producción realizada. Se adopta para este prototipo la alternativa explícita del plan: complete solo IN_PROGRESS + mezcla usable; nueva acción close-incident solo SUSPENDED → CLOSED_WITH_INCIDENT. Cancelación conserva ambos orígenes. Tareas CLOSED, versión, identidad independiente y evidencia siguen siendo obligatorias. Sin reanudación.

No se modifica ninguna fila histórica ni eventos V1–V5. COMPLETED con suspended_at no nulo se etiqueta LEGACY_INCIDENT_CLOSURE en proyecciones; se cuenta aparte del cierre productivo normal. Nuevas incidencias sobre CLOSED_WITH_INCIDENT producen revisión retrospectiva, sin reabrirlo. Son autoridades lógicas ficticias, pendientes de confirmar con Producción/Calidad para planta; esta entrega no las afirma operativas.

## OLA y alertas (P0-B)

- Reglas inmutables DEMO por task_type/target_role; versión incremental y UUID. GEO puede crear una versión nueva con Idempotency-Key, versión esperada del par, motivo/evidencia y auditoría existente; no editar/borrar versiones. Todos los roles de simulación leen reglas/indicadores, sin usar un rol suministrado como autorización.
- Valores DEMO iniciales: contención 30 minutos acuse / 240 cierre, ventana próxima 10; rechazo Procurement 60/480/10. Revisión retrospectiva sin regla inicial muestra NOT_CONFIGURED. Cualquiera de los dos objetivos puede ser null; ambos null deshabilitan plazos solo para tareas futuras.
- Snapshot al INSERT en PostgreSQL: referencia de regla inmutable, ack_due_at, close_due_at y ventana próxima. No backfill de tareas V5. Un trigger protege snapshot de modificaciones. Cambio concurrente de regla elige una versión comprometida por sentencia, sin recalcular históricos.
- Minutos corridos UTC desde created_at para ambos objetivos. La hora de negocio de creación/acuse/cierre de nuevas tareas viene del Clock inyectado (server UTC); auditoría mantiene su patrón DB UTC. Antes del plazo: ON_TIME o DUE_SOON; igualdad al plazo todavía no vencida; después OVERDUE. Acción completada a plazo: COMPLETED_ON_TIME, después COMPLETED_LATE. Null: NOT_CONFIGURED. Acuse y cierre se calculan por separado.
- GET deriva alertas de hitos pendientes vencidos. Sin persistir alertas, scheduler ni efectos sobre NC/lote/mezcla/ejecución/tarea. Identificador estable taskId:hito evita duplicación en presentación.

## Dashboard (P0-C)

Nuevo monitoring: política OLA pura, proyecciones batched y consultas SQL agregadas. Rangos UTC [from,to), máximo 366 días, defecto últimos 30 días; límites de página 1–100. Contadores por estado (incluye ceros), tareas por área/tipo/estado, NC, mezclas revocadas y cierres heredados separados. Métricas de respuesta en segundos: n, media y mediana para acuses observados y cierres observados; abiertas excluidas del denominador de cierre. No se inventan percentiles con datos escasos ni se divide por cero.

Cohorte: tarea creada, ejecución iniciada, NC abierta y mezcla solicitada dentro del rango. Área aplica a tareas y a entidades relacionadas mediante EXISTS, sin multiplicar NC por tareas. Montaje/estado de ejecución restringen entidades relacionadas; estado de tarea solo restringe tareas/tiempos. Alertas se cuentan por tarea y también por hito para evitar confusión. Se publican generatedAt/asOf, filtros y definiciones.

Bandeja global monitoring autenticada, distinta de /tasks que conserva limitación al rol. Paginación keyset por created_at,id y asOf fijo, excluyendo nuevos inserts; estado de tarea/acuses/cierres se proyectan a ese asOf usando timestamps. Cada GET usa REPEATABLE_READ. No promete snapshot eterno de otras entidades ni de cambios administrativos a la infraestructura.

## Persistencia y compatibilidad

V6 aditiva/compatible: amplía varchar de estado de ejecución y CHECK/función de transición sin reescribir filas; ola_rule, snapshots de containment_task, ola_rule_id nullable en execution_event y command_result. Reutiliza prevent_audit_change, CommandRunner y bloqueo actor+clave. Configuración serializa solo su par con advisory lock; no solicita locks de material, por lo que no invierte el orden clave → lotes → mezcla → ejecución → tareas.

Clases que cambian: ExecutionService/Containment/Store, TaskService (Clock), CommandRunner, AuditLog; React Executions y navegación/reintentos. Nuevo monitoring: OlaPolicy/OlaService, MonitoringFilter/Store/Controller; UI Monitoring. V1–V5 intactas. Tests anteriores se preservan, ajustando únicamente expectativas/acción de cierre SUSPENDED al nuevo contrato; upgrade anterior queda fijado a V5 y se añade V5→V6 con comparación de todas las filas originales.

Índices por created_at/id para paginación y fechas de entidades; no N+1 en bandejas. Consultas de dashboard agregan en PostgreSQL; no descargan todos los eventos para calcular métricas. Reinicio compara también reglas y snapshots.

## Entregas y riesgos

1. Diseño previo; P0-A cierre inequívoco + V6 y regresión.
2. P0-B reglas/snapshot/alertas + reloj controlado y concurrencia de versiones.
3. P0-C dashboard/UI + conteos, filtros y paginación con datos concurrentes.
4. Solo después de comprobar P0: P1 JSON de expediente acotado y CSV de tareas paginado, autenticados, UTF-8 y neutralización de fórmulas/controles. Sin secretos ni hashes de usuario. Todo export lleva SOLO SIMULACIÓN, fecha y versiones de regla. No inventa archivos firmados.
5. Regresión (mínimo 48 backend/17 navegador), upgrade, Compose aislado, persistencia y PR sin merge.

S03 comercial, devolución física S10, OLA oficiales, calendarios hábiles, ERP, máquinas, consumos/reservas y notificaciones externas quedan fuera. Riesgos: cambio de contrato complete para clientes viejos (409 y acción explícita nueva); históricos COMPLETED deben conservar semántica heredada en todas las vistas/exportaciones; lecturas paginadas requieren mismo asOf/cursor; reglas sin configuración nunca se interpretan incumplidas.
