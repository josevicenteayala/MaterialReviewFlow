# Sprint 4 P0 — verificación observada

Fecha: 13-11-2026 (America/Bogota). Base `main` d10a84f086220d12a5da394ee0a1f686a8ee4d85, cuyo [CI de merge](https://github.com/josevicenteayala/MaterialReviewFlow/actions/runs/43290438001) terminó SUCCESS antes de iniciar el sprint. Diseño previo: af55e78; backend probado: b7b6101; interfaz probada: d077c94. La evidencia remota del SHA final se publica en el PR; los resultados locales siguientes no se atribuyen anticipadamente a CI.

## Entorno y comandos ejecutados

Java 21, Maven, PostgreSQL 17 real. Proyecto Compose nuevo `proco-sprint4`: DB 55436, backend 18085, frontend 18092. Base de regresión nueva `proco_sprint4_regression`, distinta de la demo `proco`. Ningún volumen anterior se utilizó ni eliminó. V1–V4 sin cambios.

```sh
TEST_DB_URL=jdbc:postgresql://localhost:55436/proco_sprint4_regression \
 JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
 mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/proco-m2 verify -q
npm --prefix frontend run build
DB_PORT=55436 BACKEND_PORT=18085 FRONTEND_PORT=18092 docker compose -p proco-sprint4 up -d --build
E2E_URL=http://127.0.0.1:18092 PLAYWRIGHT_BROWSERS_PATH=/tmp/proco-playwright npm --prefix frontend run test:e2e
python3 scripts/verify-sprint4-restart.py --project proco-sprint4 --url http://127.0.0.1:18092
```

| Verificación | Resultado ejecutado |
|---|---|
| Maven verify | **48 pruebas, 0 fallos, 0 errores, 0 omitidas**. 33 anteriores + 14 ExecutionIntegrationTest + 1 ExecutionMigrationUpgradeTest |
| TypeScript/Vite | Compilación correcta de la interfaz final |
| Docker Compose | Construcción y arranque correctos de backend/frontend/PostgreSQL |
| Playwright completo | **16 aprobados en 57,9 s**: 12 anteriores + 4 Sprint 4 |
| Reinicio de Compose | **PASS**: filas completas idénticas en diez tablas; 12 ejecuciones, 42 tareas, 109 eventos SIM-4 conservados en la demo usada |
| Revisión visual | Captura `frontend/test-results/s04-s09-containment.png` inspeccionada; referencia residual del pie actualizada a Sprint 4 y eventos con etiquetas legibles |
| diff check | `git diff --check` sin errores |

Los conteos de reinicio corresponden a esta demo local con varias ejecuciones de fixtures; no son cantidades iniciales garantizadas. El script calcula SHA-256 sobre todas las filas ordenadas de montage_execution, containment_task, execution_event, lot, mix_request, non_conformity, inventory_hold, audit_event, mix_event y command_result. Ejemplo observado: ejecución `bc9e584741daf4e5dd3b362baaf26896aaa0482de696d35018d64341a565dcf5`, tareas `47f95bf564b197782aaf9e2df496fee93ae6a2249a73a9c9d67436ebdb59473c`; igualdad antes/después, no solo conteos. La CI ejecuta el mismo script después de sus propios fixtures y publica su salida como artefacto.

Incidencias de verificación resueltas: el primer acceso de Maven a red local fue denegado por sandbox y se ejecutó con acceso autorizado; la creación de DB se repitió cuando PostgreSQL estuvo listo. TypeScript detectó un tipo incompleto de replay de tarea, corregido antes de la compilación final. Un selector exacto de login y otro que contaba la alerta además de las tareas retrospectivas se corrigieron en tests. Esos intentos no se cuentan como aprobados; los 48/16 corresponden a las ejecuciones completas posteriores.

## Evidencia P0 y S09

| Criterio | Prueba ejecutada |
|---|---|
| S4-01 / servidor revalida POST start | startRevalidatesRoleVersionMontageAndAuthorization: no autorización/rol/montaje/versión incorrectos; inicio sin cambiar stock; una ejecución por solicitud |
| Unicidad/concurrencia | concurrentStartsSameAuthorizationCreateOneExecution; concurrentStartsSameMontageAcrossDifferentAuthorizations (lotes disjuntos, arbitraje del índice SQL) |
| S4-02 / S09 | s09NcAfterStartSuspendsCreatesCorrelatedTasksAndNeverResumes: montaje IN_PROGRESS real en el simulador antes de NC; BLOCKED + REVOKED + SUSPENDED + cinco tareas con lote/mix/NC; levantar NC no reactiva |
| Atomicidad | taskOrAuditFailureRollsBackEntireMaterialCommand: error inyectado por separado en tarea/evento; rollback de NC/hold/lote/mix/ejecución/command_result; misma clave puede confirmar tras retirar la falla |
| Carreras NC/inicio | concurrentStartAndNcNeverLeaveRunningRevokedExecution (cuatro repeticiones con barrera); no ejecución IN_PROGRESS con autorización revocada |
| Causas compartidas | concurrentNcOnSharedExecutionAndStartReplayDoNotDuplicateTasks; taskCompletionAndNewNcSerializeBeforeExecutionDisposition |
| Holds existentes | directHoldAndItsLaterNcFormalizationBothCoordinateWithoutDuplicateReplay: nc_id nulo explícito en hold previo; posterior formalización añade tareas correlacionadas sin otro hold ni replay duplicado |
| S4-03/04 / roles | taskAuthorizationAcuseEvidenceVersionAndReplay; concurrentAcuseAssignsSingleIdentityAndOnlyAssigneeCompletes; bandeja por rol; acuse distinto de cierre; evidencia obligatoria; versión y asignación |
| Disposición y estados terminales | S09 termina solo con tareas CLOSED; completo/cancelado no reabre. Trigger rechaza reanudación de SUSPENDED y borrado de tareas/eventos |
| Caducidad | expirationBeforeStartAndDuringExecutionBlocksAdvanceAndRecordsReview: inicio/completar denegados, REVIEW_REQUIRED preservado, cancelación documentada permitida |
| Retrospectiva / alcance | completedExecutionGetsRetrospectiveAlertAndUnrelatedLotDoesNotSuspend: lote ajeno no afecta; COMPLETED conserva estado y agrega cuatro tareas retrospectivas |
| Rechazo | rejectAddsProcurementTaskEvenWhenAlreadyRevoked: tarea de coordinación con NC para Abastecimiento; devolución física no implementada |
| Upgrade V4→V5 | ExecutionMigrationUpgradeTest compara cada fila de todas las tablas V4, incluida mezcla AUTHORIZED e idempotencia; columnas nuevas nulas en comandos anteriores |
| UI / respuestas inciertas | Playwright prueba inicio perdido y acuse perdido tras commit/recarga, misma clave/cuerpo, un objetivo y sin contaminar lotes |

El E2E S09 inicia como Tejeduría una mezcla que requiere Tintorería, reporta NC, comprueba suspensión/cinco tareas, bloquea disposición anticipada, cambia entre Almacén/Calidad/Tejeduría/GEO/Tintorería para acusar y completar con evidencia, y finaliza con disposición de Producción. La mezcla sigue REVOKED y el lote BLOCKED después de finalizar la ejecución. No se representa consumo ni operación física.

## Regresión frente a escenarios funcionales S01–S10

Los nombres y expectativas se contrastaron con la tabla de escenarios del procedimiento SIM-0.2 en docs/requirements, el MVP, docs/PLAN.md y docs/API.md. Se ejecutan todas las pruebas previas sin omitirlas; esta tabla distingue cobertura automatizada de las decisiones funcionales aún pendientes.

| Escenario funcional | Evidencia ejecutada / límite |
|---|---|
| S01 recepción/liberación/uso individual | WorkflowIntegrationTest.happyPathAndAudit, elastanoUsesItsOwnCriticalRange; E2E material.spec.ts. RELEASED/AVAILABLE no implica autorización de mezcla |
| S02 etiqueta sin lote/fecha crítica | Validación API de campos requeridos y retención documental/NC/reinspección. Granularidad de etiqueta física y coordinación comercial de Abastecimiento siguen fuera del modelo |
| S03 diferencia cantidad/OC | Guarda de saldo y cantidades de recepción existente. Registro/resolución comercial de diferencias no implementado; **no se declara escenario completo** |
| S04 ensayo no conforme | Retención/bloqueo, NC, dictamen y rechazo/reevaluación en WorkflowIntegrationTest y E2E Sprint 2 |
| S05 reensayo favorable | retentionAndRetestPreserveHistory, levantamiento explícito de holds y decisión independiente; no desbloquea por ensayo favorable solo |
| S06 lotes liberados sin autorización | Pruebas de mezcla/no permiso y startRevalidatesRoleVersionMontageAndAuthorization; inicio denegado sin autorización |
| S07 mezcla fechas/lotes distintos | Suite MixIntegrationTest y E2E Sprint 3: conceptos, Tintorería por regla, autorización exacta, caducidad; no inventa tolerancias 30/60 días |
| S08 diferencia de tono/Tintorería | Concepto desfavorable deniega; NC reportada por Tintorería revoca/bloquea. Sin medición industrial de tono |
| S09 NC tras comenzar montaje | Nueva integración + E2E descritos arriba: ejecución, suspensión, tareas y disposición verificadas |
| S10 rechazo/devolución | Rechazo terminal, BLOCKED y expediente con disposición; nueva coordinación PROCUREMENT. Solo devolución ficticia, sin movimiento físico/ERP |

## Límites de la entrega

P0 S4-01/02/03/04/08 y bandeja mínima para usarlos. No se incluye OLA, indicadores, CSV/JSON, cancelación administrativa de tareas, reanudación, consumo/reserva, máquinas, ERP ni notificaciones externas. REVIEW_REQUIRED por caducidad se registra cuando se intenta completar, sin proceso temporal externo. Las autorizaciones por montaje y autoridades operativas siguen siendo hipótesis explícitas de simulación.

## Ajuste de revisión — filtros de Mis tareas

El comentario del PR #3 sobre el estado vacío aplica: la lista se filtraba por estado pero el mensaje usaba la longitud total. Se comparte `visibleTasks` entre renderizado y condición de vacío, con mensajes distintos para área sin tareas y filtro sin coincidencias.

La nueva prueba de navegador reprodujo la ausencia del mensaje con la versión anterior una vez listo el backend. Tras el ajuste, `npm --prefix frontend run build` y `git diff --check` terminaron correctamente; `E2E_URL=http://127.0.0.1:18092 PLAYWRIGHT_BROWSERS_PATH=/tmp/proco-playwright npm --prefix frontend run test:e2e -- sprint4.spec.ts` aprobó **5 escenarios en 26,2 s**. El nuevo caso valida área vacía, tarea OPEN existente, filtros ACKNOWLEDGED/CLOSED sin resultados y retorno a OPEN/ALL con mensaje retirado. Solo se simula la respuesta GET de tareas para aislar esta condición visual; los otros cuatro casos conservan la integración real. Los resultados de 48/16 anteriores permanecen asociados a su entrega; el nuevo caso aumenta a 17 el conjunto de navegador ejecutado por CI, cuyo resultado por SHA se enlaza en el PR.
