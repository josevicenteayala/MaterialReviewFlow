# Sprint 5 — resultados comprobados

13-11-2026, America/Bogota. Base main `71e8a3b418b838bf625ae1ad1d3ee1c136e74c88`, PR #3 integrado; [CI exacta del merge SUCCESS](https://github.com/josevicenteayala/MaterialReviewFlow/actions/runs/43445491175). Diseño previo b3ac12b, cierre 603172f, P0 OLA/dashboard 8cb5ed1, exportaciones y estabilización de filtros 99970cc. Los resultados remotos por SHA final se enlazan en el PR; esta evidencia local no anticipa el resultado de CI.

## Entorno y resultados locales

Java 21, PostgreSQL 17, JDBC real y HTTP real; Compose aislado `proco-sprint5`, puertos DB55438/backend18087/frontend18094. Demo `proco`, pruebas finales en base nueva `proco_sprint5_final`; ninguna demo previa ni su volumen se alteró. Solo migración V6 nueva, V1–V5 intactas.

| Comprobación ejecutada | Resultado observado |
|---|---|
| Maven verify final en base nueva | **63 pruebas, 0 fallos, 0 errores, 0 omitidas** |
| Regresión previa preservada | 48 casos backend de Sprints 1–4 ejecutados; solo se ajusta el resultado/acción de cierre suspendido al contrato explícito nuevo |
| Incremento backend | 1 caso de cierre, 11 MonitoringIntegrationTest, 1 OlaPolicyTest, 1 CsvPolicyTest y 1 upgrade V5→V6 |
| TypeScript/Vite | Build correcto; compilación de contenedores también correcta |
| Playwright completo | **21 aprobados (1,2 min)**, incluidos los 17 anteriores y 4 nuevos |
| Navegador tras el ajuste final de filtro asOf | **4 casos Sprint 5 aprobados (13,5 s)** sobre backend e interfaz reconstruidos |
| Reinicio/persistencia | **PASS**, igualdad de todas las filas de once tablas; reglas, snapshots, decisiones y auditoría conservados |
| Revisión visual | `s05-dashboard-active.png` inspeccionada: métricas con n y N/A, plazos UTC/DEMO, filtros y descarga por página; se separaron los botones de descarga/paginación |
| Integridad de diff | `git diff --check` sin errores |

Comandos ejecutados (Maven local con JDK21 y caché /tmp, Chromium instalado en /tmp/proco-playwright):

```sh
DB_PORT=55438 BACKEND_PORT=18087 FRONTEND_PORT=18094 docker compose -p proco-sprint5 up -d --build
TEST_DB_URL=jdbc:postgresql://localhost:55438/proco_sprint5_final \
 JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
 mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/proco-m2 verify -q
npm --prefix frontend run build
E2E_URL=http://127.0.0.1:18094 PLAYWRIGHT_BROWSERS_PATH=/tmp/proco-playwright npm --prefix frontend run test:e2e
E2E_URL=http://127.0.0.1:18094 PLAYWRIGHT_BROWSERS_PATH=/tmp/proco-playwright npm --prefix frontend run test:e2e -- sprint5.spec.ts
python3 scripts/verify-sprint4-restart.py --project proco-sprint5 --url http://127.0.0.1:18094
```

El reinicio conservó en la demo verificada 17 ejecuciones, 52 tareas, 127 eventos de ejecución, 13 versiones OLA y las otras siete tablas. Se comparó SHA-256 de filas completas ordenadas, no solo conteos: reglas `531534497440963b2c004581b0c546495ab0fc43dba6d70bbef4f3a783ff6e9b`, tareas `7f11da24efd29877859f50d13a2a5ebeefa1180e9bb2471b49af99156d06d22c`. Son conteos de una demo con fixtures repetidos, no datos iniciales garantizados. El mismo script de CI detecta ola_rule y la incluye; plazos/snapshot forman parte de las filas completas de containment_task.

## Semántica y compatibilidad

- productionCompletionIsDistinctFromIncidentClosure prueba que SUSPENDED no puede completar producción; close-incident produce CLOSED_WITH_INCIDENT, replay no duplica y el estado terminal no se reescribe. Los casos anteriores de S09/carrera NC-cierre siguen ejecutándose con la acción nueva.
- MonitoringMigrationUpgradeTest crea un histórico V5 completo con ejecución COMPLETED tras suspensión, tareas acusadas/cerradas, NC/hold, auditoría y comando idempotente. Compara todas las filas anteriores después de V6 (columnas nuevas null) y mantiene status/eventos/fingerprint originales. La proyección cuenta LEGACY_INCIDENT_CLOSURE=1 y COMPLETED normal=0.
- V6 no backfill de plazos. El histórico continúa NOT_CONFIGURED aunque existan reglas nuevas. Upgrade anteriores se fijan a V3, V4 y V5 para conservar su alcance y se añade V5→V6.

## OLA, métricas y concurrencia

| Caso automatizado ejecutado | Qué verifica |
|---|---|
| OlaPolicyTest | Sin configuración; antes/dentro de ventana próxima; igualdad al plazo; vencimiento posterior; acción completada a tiempo/tarde |
| snapshotSurvivesNewRuleAndReadsDoNotMutateProcess | Snapshot/versiones anteriores intactos, tareas futuras usan nueva regla, vencimiento solo lectura, sin eventos ni cambios automáticos |
| onTimeAcknowledgmentAndLateClosureKeepIndependentDurationsAndDenominators | Acuse20min, cierre70min; n separados, media/mediana exactas, tarea abierta excluida de cierre, alertas pendientes retiradas tras acción |
| missingAndExplicitlyDisabledOlaAreNeverMarkedOverdue | Objetivos null o regla ausente → NOT_CONFIGURED, ninguna alerta ficticia |
| rulesAreAuthorizedAuditedIdempotentAndConcurrentVersionsConflict | Solo GEO; replay; auditoría; trigger inmutable; dos versiones concurrentes con igual expectativa → 200/409 |
| snapshotCreationCanRaceNewRuleWithoutTornDeadlines | NC y cambio de regla simultáneos; ambos plazos corresponden a una sola versión inmutable |
| emptyRangesInvalidFiltersAndAuthenticationAreExplicit | Ceros/N/A, rango acotado, área/estado/cursor/límite inválidos, autenticación |
| multipleCausesCountCasesOnceAndMedianExcludesOpenClosures | Dos NC y ocho tareas no duplican ejecución/mezcla; n2, media/mediana180s; un cierre observado600s |
| keysetAsOfPreventsDuplicatesAndFreezesStateAcrossConcurrentChanges | Insert nuevo y acuse entre páginas no duplican/incluyen tareas posteriores; total/asOf estables |
| paginatedExecutionFilterKeepsItsAsOfWhenParentIncidentCloses | Cerrar el incidente entre páginas no elimina resultados del filtro SUSPENDED fijado al asOf anterior; estado/versión de tarea también históricos |
| Regresión Sprints 3–4 | Carreras NC/aprobación/inicio/cierre, doble inicio y acuse, rollback integral si falla tarea/evento, roles, idempotencia y no reactivación |

Indicadores: cohortes [from,to) por creación de tarea/mezcla, inicio de ejecución y apertura NC; área/montaje/estado de ejecución mediante EXISTS para no duplicar entidades. Estado de tarea afecta solo tareas/tiempos. Media y mediana de acuses/cierres observados con n explícito; null si n0. Tareas abiertas no se cuentan como cierres. Summary es una lectura REPEATABLE_READ con hora UTC; estados de entidades agregadas son actuales, no reconstrucción histórica global. La bandeja/CSV fija a asOf tareas y estado de ejecución para paginar sin desplazamientos por acciones posteriores.

## Exportación y navegador

P1 se implementó después de P0 comprobado (58 backend y casos de cierre/dashboard/UI). JSON y CSV son GET autenticados, sin escrituras.

- jsonExportIsCompleteBoundedAndReadOnlyWithoutSecrets comprueba IDs, dos lotes, NC/hold, cuatro tareas, reglas y eventos; no duplicación, no secretos, sin nuevos eventos; 401/404 y 422 si una sección supera2000 filas.
- csvPagesAreExhaustiveAndNeutralizeFormulasWhileJsonKeepsOriginalEvidence recorre dos páginas sin duplicar/omitir IDs, comprueba leyenda/UTF-8/cursores y errores; CSV neutraliza fórmulas y comillas/controles, JSON conserva exactamente los originales.
- CsvPolicyTest incluye =,+,-,@, tabulaciones/CRLF/NUL, BOM/formato invisible, espacios Unicode, comillas, comas, acentos y null.
- Playwright nuevo: dashboard filtrado por montaje/área y rango vacío; OLA guardado con respuesta perdida, recarga y misma clave/cuerpo; producción normal y presentación heredada; descargas JSON y CSV de la página filtrada con contenido leído y verificado. La presentación heredada se aísla en UI con GET simulado; la persistencia real V5→V6 la verifica la prueba de upgrade PostgreSQL.
- Las alertas vencidas se verifican con Clock controlado en backend; el navegador comprueba próximos vencimientos y acuse a tiempo con hora real, sin endpoint para manipular el reloj productivo.

## Incidencias de verificación resueltas

Un selector exacto del área OLA del test no encontraba su label con opciones; se corrigió y los escenarios posteriores pasaron. En un intento de Maven, la comprobación de reinicio se lanzó mientras otra base del mismo contenedor PostgreSQL seguía en pruebas: produjo `FATAL: terminating connection due to administrator command` y un HTTP500. Ese intento no se cuenta como aprobado. Se repitió toda la suite sobre proco_sprint5_final sin tareas de infraestructura concurrentes: 63/0/0/0. El script de reinicio usa la demo y CI lo ejecuta después de terminar Maven y navegador.

## Límites

Todo es DEMO y local. Sin OLA oficial, calendario laboral, worker, emails, ERP, máquinas, consumo/reserva ni notificaciones fuera de la UI. No se afirma cobertura completa S03 comercial o devolución física S10; se separan para una entrega siguiente. CSV es una página acotada, no exportación implícita de toda la cohorte. El JSON acota secciones y no sustituye documentos firmados. Sin reanudación ni reinterpretación de históricos.

## Ajuste de revisión — contrato público de tareas

Aplica la observación del PR #4: `OlaPolicy.project` copiaba los auxiliares SQL `execution_outcome_at` y `state_at` al payload público. Ahora consume el primero para `execution_status` y retira ambos de la copia de salida, sin modificar el mapa de origen ni el filtrado SQL. `status`, `execution_status` y la paginación a asOf conservan sus valores.

La prueba nueva reprodujo la fuga con la versión anterior. Después del ajuste, `TEST_DB_URL=jdbc:postgresql://localhost:55438/proco_sprint5_final JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/proco-m2 -Dtest=OlaPolicyTest,MonitoringIntegrationTest test -q` aprobó **13 pruebas, cero fallos/errores/omitidas**. Incluye contrato público, fuente inmutable y respuesta real del endpoint cuando se cierra el montaje entre páginas. `git diff --check` correcto. La nueva prueba incrementa la suite completa a64; sus resultados por SHA se enlazan en el PR y no se confunden con las63 de la entrega inicial.
