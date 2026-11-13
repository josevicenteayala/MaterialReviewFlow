# Sprint 6 — evidencia verificable de S03/S10

Fecha 13-11-2026 (America/Bogota). Base main `29859588d98a52224ef5dceb475c472fb234b6d7`, PR #4 integrado; [CI exacta del merge SUCCESS](https://github.com/josevicenteayala/MaterialReviewFlow/actions/runs/43484650371). Diseño previo 968e065, backend 696d8d1, interfaz 34413b5. Evidencia remota del SHA final se enlaza en el PR; no se atribuyen a CI resultados locales anticipadamente.

## Entorno y resultados observados

Compose independiente `proco-sprint6`: DB55440, backend18089, frontend18096. PostgreSQL17 real; Java21; servidores HTTP de tests hasta18480. Regresión final en base nueva `proco_sprint6_final`, distinta de la demo `proco`. V1–V6 intactas; solo V7 nueva. Ningún volumen de demos previas se utilizó/eliminó.

| Verificación ejecutada | Resultado |
|---|---|
| Maven verify final | **81 pruebas, 0 fallos, 0 errores, 0 omitidas** |
| Cobertura preservada | Las64 pruebas backend anteriores se ejecutan; 16 SupplierIntegrationTest + 1 SupplierMigrationUpgradeTest nuevas |
| TypeScript/Vite | Compilación correcta de la interfaz final |
| Docker Compose | Construcción y arranque correctos, aplicación autónoma local |
| Playwright completo | **26 escenarios aprobados (1,5 min)**, incluidos los21 anteriores y cinco S6 |
| Reinicio/persistencia | **PASS**, igualdad de todas las filas en16 tablas, incluidas OC y agregados nuevos |
| QA visual | Capturas `s06-discrepancy-accepted.png` y `s06-supplier-case-closed.png` inspeccionadas: cantidades, falta/remanente, roles, tareas, rechazo bloqueado y NO físico/ERP claros |
| diff check | `git diff --check` sin errores |

Comandos ejecutados (seleccionar JDK21; caché local de Maven/Chromium en /tmp):

```sh
DB_PORT=55440 BACKEND_PORT=18089 FRONTEND_PORT=18096 docker compose -p proco-sprint6 up -d --build
TEST_DB_URL=jdbc:postgresql://localhost:55440/proco_sprint6_final \
 JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
 mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/proco-m2 verify -q
npm --prefix frontend run build
E2E_URL=http://127.0.0.1:18096 PLAYWRIGHT_BROWSERS_PATH=/tmp/proco-playwright npm --prefix frontend run test:e2e
# Después de terminar Maven y navegador:
python3 scripts/verify-sprint4-restart.py --project proco-sprint6 --url http://127.0.0.1:18096
```

El primer recorrido S6 tuvo4 aprobados y un timeout de selector exacto del test al volver a editar un textarea con valor tras un422. Se corrigió ese selector, sin cambiar la regla de saldo; la repetición S6 pasó5/5 en21,5s y luego la suite completa26/26. Ese intento fallido no se cuenta como aprobado. Después se añadió el caso backend de múltiples tareas vinculadas y se repitió todo Maven en proco_sprint6_final:81/0/0/0. No se ejecutó un reinicio sobre PostgreSQL mientras Maven estaba activo.

## Trazabilidad de aceptación

| Plan / escenario | Pruebas ejecutadas / evidencia |
|---|---|
| S6-01/02, S03 faltante/sobrante | s03ShortageAndDocumentSurplusPreserveFactsAndOnlyCreateAcceptedQuarantine: OC100, doc100/obs90 y doc90/obs100; conserva cantidades originales, OC inalterada y crea solo cantidad aceptada en cuarentena |
| S6-03/07/09, exceso y cero | excessAndZeroAcceptanceDoNotInventStockOrSpendDocumentedQuantity: OC30/obs40, propuesta40 rechazada, aceptación30/remanente10; observado0/aceptado0 no crea lote |
| S6-08, autoridad y Calidad | rolesQualityConditionAndCancellationAreEnforced: Warehouse cuenta, Procurement autoriza, Quality solo con hallazgo; desfavorable bloquea; cancelar conserva evidencia; favorable no libera lote automáticamente |
| Saldo cambia entre pasos | changedBalanceRequiresExplicitRevisedAuthorization: recepción20 tras autorizar90 en OC100, accept falla, propuesta revisada80 y total100 |
| S6-04/05, concurrencia | normalReceiptAndDiscrepancyAcceptShareOrderLock: recepción normal20 vs aceptación90, solo una confirma; twoDifferentAcceptancesCannotExceedPurchaseOrder: dos aceptaciones70 en OC100, 200/422 y total70; acceptanceIdempotencyAndConcurrentResolutionCreateOneLot: mismo expediente200/409, un lote |
| S6-06, rollback S03 | acceptanceRollsBackLotAuditAndCaseOnInjectedFailureAndReplaysSafely: falla inyectada por separado en INSERT de lote/evento; no quedan lote, resolución, auditoría de recepción ni command_result parciales; misma clave confirma después y replay no duplica |
| S6-10, S10 sin montaje | supplierCaseFollowsRejectedMaterialWithoutChangingTechnicalStateOrBalance: caso automático RETURN_TO_SUPPLIER sin tarea ficticia; disputa, disposición y cierre independiente; conserva versión/REJECTED/BLOCKED y flags físicos/ERP false |
| S6-10, tareas existentes | linkedProcurementTasksAreReusedAndGateAdministrativeClose: enlaza la tarea existente, no la duplica, close bloqueado hasta CLOSED; ejecución sigue SUSPENDED y mezcla REVOKED |
| Varias ejecuciones | oneRejectionLinksEveryExistingProcurementTaskAcrossExecutions: caso único enlaza dos tareas existentes, cierre falla con cualquier pendiente y luego confirma; ambos montajes siguen suspendidos |
| S6-11, SCRAP | scrapNeverCreatesSupplierReturnCase: sin caso de retorno; adopción explícita también rechazada |
| S6-12/13, comunicación ficticia | commercialCommandsAreVersionedIdempotentAndCannotSkipStagesOrClaimPhysicalReturn y casos anteriores: referencias/actores/UTC/evidencia, disputa/NO_RESPONSE explícitos, tratamiento obligatorio, etapas no saltables, cierre por otro Procurement, true físico rechazado |
| S6-14, rollback S10 | failedSupplierRegistrationRollsBackNewTechnicalRejectionAndProcurementTask: falla INSERT de caso; revierte rechazo nuevo/NC/Procurement task/command_result, conserva hold/NC previos, replay exacto crea un caso/tarea |
| Carrera con tarea | supplierCaseClosureAndTaskCompletionSerializeWithoutDeadlock: close comercial vs tarea.complete, 200 o422 controlados, sin deadlock; después cierra con tarea completa |
| Históricos | legacyRejectionRequiresExplicitAdoptionAndNeverRewritesLotOrNc: adopción auditable idempotente, lote/NC idénticos y sin duplicado por otro actor |
| S6-16, upgrade | SupplierMigrationUpgradeTest crea esquema V6 con NC RETURN_TO_SUPPLIER, hold, tareas OLA, ejecución cerrada, eventos e idempotencia; compara todas las filas tras V7; no backfill de casos/diferencias ni cambio de OC/kilos |
| S6-15 y UI | Cinco E2E descritos abajo, misma clave/cuerpo tras perder respuesta y recargar; un lote/expediente/evento; reinicio compara filas completas |

## Escenarios de navegador nuevos

1. S03: captura documento100/observado90 sin lote; confirmación Almacén; se intenta autorizar antes de Calidad y se deniega; concepto favorable; autorización Procurement; aceptación Warehouse crea90 UNDER_VERIFICATION/QUARANTINE; vínculo al lote correcto.
2. S03 excedente: observado/documentado20000 sobre saldo de OC demo10000; propuesta20000 denegada, propuesta30/remanente19970 explícitos; aceptación parcial sin modificar observación.
3. S10: montaje iniciado, NC/dictamen/rechazo RETURN; caso enlaza tarea Procurement existente; contacto/respuesta DISPUTED/disposición CREDIT_REQUESTED ficticios; acuse/cierre de tarea vía ejecución; abastecimiento2 cierra caso; versiones/REJECTED/BLOCKED/SUSPENDED y cantidad se conservan, flags false.
4. Respuesta perdida de accept tras commit: recarga/login y reintento con clave/cuerpo exactos, un lote/evento de aceptación; el UUID del expediente no se inserta en la bandeja de lotes.
5. Respuesta perdida de contact: vuelve al caso correcto tras recarga/retry; una evidencia de contacto, sin confundir caso y lote.

## Persistencia y CI

El script compatible con Sprints4/5 añade purchase_order, receipt_discrepancy, supplier_case, supplier_case_task y supplier_workflow_event cuando existe V7. Compara SHA-256 de filas completas ordenadas antes/después de restart; no solo conteos. En la demo verificada conservó2 OC,9 diferencias,7 casos,4 enlaces y65 eventos comerciales, además de material/NC/OLA/auditoría/idempotencia. Conteos reflejan fixtures repetidos, no datos iniciales garantizados.

Hashes observados: purchase_order `4a5dbb427c92ff86fb2d7e893c269bd86391cc1d144910c03efb3e7d2ec45136`; receipt_discrepancy `2b2d4078856f656c04c86b509d27583957cb50efa9ede926e31e6b0f5f9e659c`; supplier_case `b0453e3cb618894f3860936a2b7a8eb93bc1736678ddd28f418d38f9097a4e50`.

CI ejecuta Maven completo y todos los Playwright, después el mismo script de reinicio, y publica Surefire/capturas/logs Compose/hashes como artefactos. El PR vincula los resultados del SHA final; no se declara CI verde antes de observarla.

## Alcance y límites

S03 completo para simulación kg/lote: registro, confirmación, autorización, aceptación/no aceptación, remanente documentado y saldo protegido. No incluye tolerancias contractuales aprobadas ni custodia física. S10 cubre gestión comercial administrativa ficticia tras rechazo técnico: no transporte, crédito, devolución física ni contabilidad. No reposición automática de saldo ni alteración de lote/NC por cerrar el caso. OLA/métricas/exportaciones de estos agregados son posteriores; los mecanismos previos permanecen. Sin ERP, emails, reservas/consumo, sublotes ni movimientos reales.
