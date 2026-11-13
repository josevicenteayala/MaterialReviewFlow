# PROCO SIM-0.2 — Sprint 5: seguimiento operativo, OLA de simulación y cierre verificable

**Estado:** propuesta funcional y técnica, **NO** procedimiento aprobado para planta.  
**Base revisada:** `main` commit `71e8a3b418b838bf625ae1ad1d3ee1c136e74c88`, merge PR #3 del 13-11-2026.  
**Aplicación:** [MaterialReviewFlow](https://github.com/josevicenteayala/MaterialReviewFlow).  
**Fuentes:** `docs/PLAN.md`, `docs/SPRINT-4-PLAN.md`, `docs/SPRINT-4-DESIGN.md`, `docs/SPRINT-4-VERIFICATION.md`, `docs/API.md` y código de `execution`, `mixing`, `nonconformity`, `inventory`.  
**Objetivo del siguiente incremento:** completar la capacidad de **seguir tiempos, pendientes, evidencias y resultados de simulación**, sin introducir decisiones automáticas de liberación ni alterar el alcance industrial.

## 1. Revisión de la línea base y brechas verificadas

El código de Sprint 4 incluye `montage_execution`, `containment_task`, `execution_event`, migración V5, acuse/cierre de tareas y contención coordinada cuando se bloquea un material. `MixRevocations` invoca la contención en la misma transacción de la no conformidad; hay pruebas de carreras NC/inicio, rollback, datos persistentes y interfaz multiactor. El PR #3 está fusionado. La ejecución anterior al merge `43443411772` terminó correctamente con 48 tests backend, 17 Playwright y verificación de persistencia. Verificar también CI del commit exacto de `main` antes de cerrar la entrega.

**Pendiente real respecto del alcance S4:** OLA de simulación, avisos de vencimiento, indicadores, exportaciones y cierre funcional de escenarios parcialmente implementados (notablemente S03 discrepancia de OC, S10 disposición comercial ficticia). Estos elementos **no** deben presentarse como implementados por el solo hecho de existir tareas con acuse.

**Hallazgo prioritario de semántica:** `ExecutionService.finish` permite `complete` sobre `SUSPENDED` una vez cerradas las tareas, aunque la mezcla permanezca `REVOKED` y el inventario `BLOCKED`. Esta transición es técnicamente intencional en las pruebas de Sprint 4 y puede representar **cierre de la gestión del incidente**, no producción física finalizada. Sin embargo, el estado `COMPLETED` y la etiqueta «Finalizado» pueden confundirse con montaje producido conforme. Resolver explícitamente el significado antes de ampliar reportes.

**Deuda documental:** el `README.md` y `docs/PLAN.md` todavía incluyen frases sobre la rama de Sprint 4 pendiente de integración; actualizar su estado real sin borrar registros históricos de verificación.

## 2. Objetivo y límite del Sprint 5

> Como coordinador de la simulación, necesito distinguir un montaje terminado normalmente de un expediente cerrado tras un incidente, conocer el tiempo de respuesta por área, identificar tareas vencidas y exportar evidencia para revisar el funcionamiento del procedimiento con los participantes.

**P0 / imprescindible:** contrato de estados de cierre, OLA de simulación trazables y alertas internas calculadas, indicadores operativos reproducibles, regresión y persistencia.

**P1 / según capacidad:** exportación CSV/JSON acotada y protegida de resultados/expedientes, mejoras de navegación y filtros por área/fecha/montaje. 

**Fuera de alcance:** reserva y consumo físico, seguimiento por cono/caja, ERP, maquinaria, email/ClickUp reales, órdenes reales al proveedor, login corporativo, firma electrónica, tolerancias industriales aprobadas o automatización de la decisión técnica. No comprometer todo S03/S10 dentro de este sprint; documentar su cobertura parcial y separar una entrega siguiente.

## 3. Historias y criterios de aceptación

### S5-01 — Estados de cierre inequívocos (P0)

Como Producción quiero que el resultado de una ejecución permita diferenciar **producción simulada completada normalmente** de **cierre del expediente tras una suspensión**.

- **Propuesta por validar:** `COMPLETED` solo para `IN_PROGRESS` con autorización todavía utilizable; una ejecución `SUSPENDED` podrá terminar en `CLOSED_WITH_INCIDENT` (gestión concluida, sin aseverar producción realizada) o `CANCELLED`. Ambas decisiones deben requerir todas las tareas cerradas, segregación existente, motivo y evidencia.
- Nunca reanudar `SUSPENDED` automáticamente. Una NC resuelta no restituye la autorización de mezcla.
- Preservar sin modificación las ejecuciones históricas `COMPLETED` de V5, incluso las que provinieron de `SUSPENDED`. La UI/reportes deberán distinguirlos mediante `suspended_at`, exponiendo una etiqueta heredada claramente descrita, sin reescribir auditoría.
- Cambiar tests, API y UI de manera coordinada. Migración V6 aditiva/compatible para permitir el nuevo estado; no editar V1–V5.
- Si negocio prefiere que `COMPLETED` signifique exclusivamente «expediente cerrado», tomar esa decisión explícitamente y diferenciar mediante `outcome`; nunca asumir equivalencia con producción terminada.

### S5-02 — OLA de simulación versionados (P0)

Como responsable de área quiero conocer los plazos de **acuse** y **cierre** de mis tareas y cuáles están vencidas.

- Crear configuración **ficticia y versionada** por `task_type` y `target_role`, con `ack_target` y `close_target` opcionales, sin valores «oficiales» inventados. Para un escenario demostrativo se podrán cargar umbrales claramente etiquetados `DEMO`, nunca presentados como políticas de PROCO.
- Al generar una tarea, guardar el **snapshot** de versión de regla y plazos (`ack_due_at`, `close_due_at`) o una referencia inmutable equivalente; cambiar las reglas no modifica tareas históricas.
- Calcular estados `ON_TIME`, `DUE_SOON`, `OVERDUE`, `COMPLETED_ON_TIME`, `COMPLETED_LATE` con **hora del servidor** en UTC; documentar si son minutos corridos o hábiles y no inferir calendario laboral. Si no hay OLA configurado, mostrar `NOT_CONFIGURED`, nunca incumplimiento ficticio.
- Un vencimiento **no bloquea, autoriza, rechaza, suspende ni cierra** una tarea. El acuse sigue siendo distinto del cierre y conserva actor/evidencia.
- Las alertas pueden derivarse de consultas locales; no se requiere worker ni mensajería externa inicialmente. Si se agregan eventos de alerta persistentes, aplicar deduplicación por tarea/umbral y claves de correlación.

### S5-03 — Dashboard de seguimiento (P0)

Como coordinador de la simulación quiero observar el desempeño y las excepciones del flujo.

- Contadores: ejecuciones `IN_PROGRESS`/`SUSPENDED`/`COMPLETED`/`CANCELLED`/`CLOSED_WITH_INCIDENT`, NC abiertas/cerradas/rechazadas, tareas por estado y área, tareas vencidas, autorizaciones de mezcla revocadas.
- Medidas: tiempo hasta primer acuse y hasta cierre por área y tipo de tarea; media/mediana o percentiles cuando haya suficientes datos. Documentar denominadores y excluir de tiempos de cierre las tareas abiertas.
- Filtros por intervalo UTC, área, montaje y estado; límites de rango y paginación. Las métricas se obtienen de PostgreSQL mediante consultas agregadas/batched, evitando N+1 y trayendo a la API solo información necesaria.
- Manejar correctamente listas vacías, un único dato, fechas sin registros y operaciones aún abiertas. Todas las cantidades deben identificarse como **simuladas**.
- Los roles de lectura autorizados acceden a métricas de simulación; no aceptar un rol enviado por el cliente como fuente de autorización.

### S5-04 — Exportación de evidencia (P1)

Como revisor quiero exportar un expediente trazable sin modificarlo.

- Exportar JSON estructurado de un expediente (lote → NC/bloqueos → mezcla → ejecución → tareas → eventos) y CSV tabular para métricas/tareas según filtros.
- Toda exportación incluye fecha UTC de generación, identificadores, versión de las reglas y leyenda **SOLO SIMULACIÓN**. No equiparar referencias de texto con archivos firmados.
- Validar permisos en backend, paginar/acotar cardinalidad, no exponer contraseñas, hashes ni secretos. Proteger CSV contra inyección de fórmulas (`=`, `+`, `-`, `@`, tabulaciones y caracteres de control); usar escape/quoting correcto y UTF-8.
- Pruebas de exhaustividad sin duplicaciones y de protección de cadenas hostiles.

### S5-05 — Validación y documentación (P0)

- La CI debe seguir pasando **como mínimo** las 48 pruebas backend y 17 escenarios Playwright existentes; agregar casos de transición terminal, OLA, reloj controlado, alertas, filtros, migración V5→V6, export si se desarrolla.
- Ejecutar tests con PostgreSQL y Compose **aislados**, no sobre el volumen de la demo principal. Verificar reinicio y persistencia de nuevos datos y decisiones.
- Conservar V1–V5 sin reescribir; documentar el modelo de estados final, contrato API, indicadores y su definición.
- Actualizar `README.md`, `docs/PLAN.md` y los documentos de verificación para reflejar merges previos y resultados reales con SHA/URLs de CI.

## 4. Propuesta técnica de mínima complejidad

Continuar con **monolito modular Java 21 / Spring Boot / JDBC / PostgreSQL / Flyway / React**.

- Extender `execution` para el contrato de estado terminal; centralizar guardas en políticas testables. Si el estado nuevo es aprobado para simulación, añadir V6.
- Nuevo módulo o paquete `monitoring` para políticas OLA, consultas agregadas y proyecciones, sin mover reglas de autorización al frontend.
- `GET /api/monitoring/summary?from=...&to=...`, `GET /api/monitoring/tasks?...`, `GET /api/monitoring/ola-rules` (lectura) y proyecciones de OLA en `GET /api/tasks`, preservando compatibilidad de clientes. Las rutas son **propuestas**, no contrato existente.
- Eventualmente `GET /api/reports/executions/{id}.json` y `GET /api/reports/tasks.csv` bajo P1. Las exportaciones son operaciones de lectura y no deben cambiar estado.
- Snapshot inmutable OLA por tarea al crearla, no recomputar históricos usando valores nuevos; usar reloj inyectable o reloj de DB consistentemente, con tests deterministas.
- Consultas agregadas con índices útiles por `created_at`, `status`, `target_role`; medir `EXPLAIN ANALYZE` solo cuando haya volumen significativo, no agregar índices sin motivo.
- Evitar scheduler/job automático salvo que se identifique una necesidad clara: basta con calcular el vencimiento al consultar el simulador; si se requiere notificación sin sesión abierta, dedicar una entrega a un job interno idempotente.

## 5. Riesgos y decisiones para revisión humana

| Riesgo/ambigüedad | Decisión propuesta para la simulación | Debe confirmar |
|---|---|---|
| `COMPLETED` tras suspensión sugiere producción normal | Nuevo cierre `CLOSED_WITH_INCIDENT`, sin reanudar; histórico intacto | Producción, Tejeduría, Calidad |
| Tiempo hasta acuse/cierre no definido | Valores DEMO versionados o reglas vacías, sin SLA oficial | Responsables de áreas y GEO |
| Días hábiles vs corridos | Para simulación, por defecto minutos corridos UTC, claramente etiquetados | GEO/Calidad |
| Notificación vista vs acción confirmada | «Vencida» no implica que nadie la haya visto; solo acuse auditable indica confirmación | Calidad/Almacén |
| `COMPLETED` retrospectivo recibe tareas | Exponer «montaje histórico bajo revisión» sin volver a `IN_PROGRESS` | Producción/Calidad |
| Indicador de cumplimiento agregado | Declarar denominador, censura de tareas abiertas y política de datos escasos | GEO |
| Datos en exportación | Solo datos sintéticos; escaneo/escape de CSV y permisos | Responsable del simulador |
| S03/S10 parciales | Plan de Sprint 6 específico, no declarar cumplimiento integral | Almacén/Abastecimiento/Calidad |

## 6. Escenarios de aceptación sugeridos

1. Montaje normal finaliza `COMPLETED`, sin cambiar stock ni autorización de mezcla.
2. Montaje suspendido **no** puede pasar a producción «normalmente completada»; termina en `CLOSED_WITH_INCIDENT` o `CANCELLED` con evidencias y tareas cerradas.
3. Las ejecuciones históricas V5 conservan valores y eventos; las que fueron suspendidas se muestran como «cierre heredado tras incidente».
4. Una tarea abierta sin acuse antes de su plazo aparece vencida; no se cierra ni se aprueba por vencimiento.
5. Una tarea acusada a tiempo pero cerrada tarde conserva ambas mediciones por separado.
6. Una regla OLA modificada no altera fechas objetivo ni resultados históricos de tareas anteriores.
7. `NOT_CONFIGURED` aparece cuando no existe OLA; nunca «incumplida» por defecto.
8. Varias tareas del mismo montaje se agregan por rol y estado sin duplicar casos.
9. Los reportes de intervalos/áreas vacíos devuelven 0 o N/A correcto, sin divisiones por cero.
10. Filtros, orden y paginación conservan resultados estables frente a datos simultáneos.
11. Caducidad de autorización sigue bloqueando el inicio o la finalización normal, sin afectar decisiones históricas.
12. NC concurrente y cierre de ejecución mantienen los mismos invariantes de los Sprints 3–4.
13. Restablecer/reiniciar Docker conserva reglas versionadas, tareas, timestamps y auditoría.
14. CSV con valor `=HYPERLINK(...)` no ejecuta fórmula al abrirse en una hoja de cálculo; el JSON conserva los datos originales.
15. Toda la suite previa permanece aprobada sin pruebas omitidas.

## 7. Orden de implementación recomendado

1. **Revisión corta del estado de cierre** y decisión documentada sobre las ejecuciones suspendidas; preparar ADR/plan antes de modificar DB.
2. **Entrega P0-A:** migración V6 + estado `CLOSED_WITH_INCIDENT` + UI/contrato + regresión de Sprint 4.
3. **Entrega P0-B:** políticas y snapshot OLA + proyección de vencimientos + UI bandejas.
4. **Entrega P0-C:** dashboard agregado + filtros + tests integrales de datos y reloj.
5. **Entrega P1 opcional:** exportaciones con pruebas de seguridad + guía de uso.
6. **Cierre:** CI Maven/Playwright, upgrade V5→V6, persistencia tras reinicio, capturas y PR para revisión sin merge automático.

**Go/no-go:** si no se valida la semántica de `COMPLETED`, avanzar únicamente con consultas OLA/dashboard sin reinterpretar el historial ni introducir un estado terminal ambiguo. No alterar producción real; todo sigue siendo ficción verificable.

## 8. Prompt breve para Codex

> En `MaterialReviewFlow`, revisa el `main` fusionado por PR #3, el código `execution`, `mixing` y `nonconformity`, y usa `docs/SPRINT-5-PLAN.md` como propuesta a contrastar. Crea una rama `codex/sprint-5-monitoring`. Primero documenta la decisión funcional sobre `COMPLETED` frente a cierre de incidente; no cambies históricos sin migración compatible. Implementa P0 de forma incremental: cierre inequívoco, OLA ficticios versionados por tarea, alertas internas derivadas y dashboard con métricas definidas. Reutiliza seguridad, auditoría, idempotencia, PostgreSQL y Docker Compose. No implementes integraciones ni tiempos de respuesta oficiales. Conserva V1–V5, agrega V6 solo si hace falta, prueba actualización V5→V6 y regresión completa (≥48 backend y ≥17 Playwright existentes), concurrencia y reinicio/persistencia en entorno aislado. Si hay capacidad, añade export JSON/CSV seguro como P1. Actualiza README/PLAN y crea PR sin merge automático, con evidencias verificables por SHA.
