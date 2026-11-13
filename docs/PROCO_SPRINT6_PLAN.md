# PROCO SIM-0.2 — Sprint 6: diferencias de recepción y disposición comercial simulada

**Estado:** propuesta de implementación; **SOLO SIMULACIÓN**.  
**Repositorio:** `josevicenteayala/MaterialReviewFlow` · rama base `main` · commit revisado `29859588d98a52224ef5dceb475c472fb234b6d7` (PR #4, Sprint 5).  
**Documento:** `docs/SPRINT-6-PLAN.md` (ubicación sugerida al incorporarlo a Git).  
**Fecha de preparación:** 2026-11-13 (America/Bogota).

> Los roles, transiciones y acuerdos comerciales aquí descritos son **hipótesis de simulación**, no reglas ratificadas por PROCO ni procedimientos legales, comerciales o contables. No se realizan comunicaciones reales con proveedores, cambios en ERP ni movimientos físicos de mercancía.

## 1. Diagnóstico fundamentado en el código de main

El sistema ya implementa: recepción en `WorkflowService.create` (valida OC y saldo antes de crear `lot`), inspección/laboratorio y liberación, NC y bloqueo persistente, rechazo con destino previsto `RETURN_TO_SUPPLIER`/`SCRAP`, tarea `REJECTION_COORDINATION` para PROCUREMENT, mezclas por montaje, contención, auditoría append-only, idempotencia, estados terminales y seguimiento OLA/reportes. El Sprint 5 introdujo V6, `CLOSED_WITH_INCIDENT`, reglas DEMO, dashboard y exportaciones. La suite de la rama del PR #4 tuvo 64 pruebas backend y 21 E2E aprobados; **verificar separadamente CI del commit exacto de merge antes del Sprint 6**.

Brechas concretas:

1. **S03:** `purchase_order` solo tiene `expected_quantity` y `lot` tiene `quantity`. La recepción actual **rechaza sin expediente** una cantidad superior al saldo de OC; las entregas parciales se admiten, pero no se documentan diferencias entre documento del proveedor, conteo físico simulado y cantidad aceptada. No existe una resolución por Almacén/Abastecimiento.
2. **S10:** rechazar un lote con `RETURN_TO_SUPPLIER` produce decisión técnica terminal y tarea de coordinación para Abastecimiento, **no** un expediente comercial de proveedor con comunicaciones, respuestas, gestión de retorno y cierre. No se ejecuta devolución física.
3. La auditoría del lote y las tareas existentes deben **reutilizarse**, sin reinterpretar `REJECTED` como mercancía físicamente despachada ni reescribir lotes, cantidades, OC, NC, dictámenes o eventos históricos.

**Sprint Goal:** simular una discrepancia de recepción y su resolución documentada y completar la **trazabilidad de gestión comercial** posterior al rechazo, con garantías de saldo, roles, evidencia e idempotencia. **No** implementar ERP/EDI, contabilidad, transporte, baja física, reserva ni consumo.

## 2. Alcance y prioridades

| ID | Prioridad | Historia | Resultado verificable |
|---|---|---|---|
| S6-01 | P0 | Registrar diferencia en recepción sin crear lote válido por accidente | Almacén captura OC, lote de proveedor, cantidad informada/documentada, cantidad contada, ubicación, fecha, motivo y evidencia en un expediente de pre-recepción. |
| S6-02 | P0 | Investigar y decidir diferencia | Abastecimiento documenta concepto comercial; Calidad participa **solo si** existe un hallazgo documental/técnico; Almacén confirma el conteo y la resolución propuesta. Ninguna decisión admite superar saldo de OC. |
| S6-03 | P0 | Aceptación parcial controlada o tratamiento del excedente | Solo cantidad aceptada y autorizada puede convertirse en lote `UNDER_VERIFICATION / QUARANTINE`; el faltante/exceso se registra pero **no** se convierte en inventario disponible. Reutilizar validación/creación de recepción sin duplicar reglas. |
| S6-04 | P0 | Expediente comercial posterior a rechazo | Para una NC finalizada con `RETURN_TO_SUPPLIER`, abrir o vincular un caso de Abastecimiento en la misma transacción de la decisión; registrar eventos de contacto **simulado**, respuesta del proveedor, plan de disposición y cierre administrativo. |
| S6-05 | P0 | Auditoría, seguridad, regresión e integridad | Migración V7 aditiva, RBAC backend, segregación, versionado, idempotencia, bloqueo/orden de locks, pruebas de carrera y rollback, UI de usuarios ficticios, CI y Compose autónomo. |
| S6-06 | P1 | Bandeja de seguimiento y enlaces de expediente | Mostrar casos por área/estado con vínculo OC → diferencia → lote aceptado y NC → rechazo → caso de proveedor; exportaciones solo si la capacidad lo permite. |
| S6-07 | P2 | Métricas y OLA para estos nuevos casos | Solo después de completar P0/P1; no confundir con los OLA DEMO actuales de tareas de contención. |

**Prioridad del desarrollo:** S6-01 → S6-02 → S6-03; en paralelo de diseño (no de transacción), S6-04. Si la capacidad es insuficiente, dividir en Sprint 6A (S03) y 6B (S10), sin declarar S10 completa hasta cubrir su gestión administrativa.

## 3. Modelo funcional propuesto

### 3.1 Pre-recepción y diferencia S03

El actual `POST /api/receipts` permanece compatible. Se añade un **expediente de diferencia** solo para la pre-recepción excepcional, que permite conservar también los intentos que la validación actual no convertiría en lote.

Datos mínimos: `id`, `purchaseOrderCode`, `supplierLot`, `documentedQuantityKg`, `observedQuantityKg`, `proposedAcceptedQuantityKg` (nullable hasta resolución), `supplierDocumentRef`, `reason`, `evidence`, `reportedBy`, `reportedAt`, `status`, `version`, `decisionBy`, `decisionEvidence`, `acceptedLotId` (nullable), `resolutionReason`, `resolutionAt`. Debe poder distinguir **falta** (documentada > observada), **sobrante** (observada > documentada) y **exceso sobre saldo OC**; no inferir que todos son una NC técnica.

Estados iniciales propuestos:

```text
OPEN -> UNDER_REVIEW -> ACCEPTED_PARTIAL
                    \-> EXCESS_NOT_ACCEPTED
                    \-> CANCELLED_WITH_REASON
```

`ACCEPTED_PARTIAL` significa aceptación **contable simulada** de una cantidad registrada en el nuevo lote en cuarentena, no liberación técnica ni autorización de mezcla. `EXCESS_NOT_ACCEPTED` no crea lote para el excedente; la gestión del material físicamente segregado y su destino real siguen fuera de alcance. No aceptar ajustes silenciosos de OC ni eliminar el expediente. Si el caso exige dos destinos (aceptar parcial y dejar excedente pendiente), **no sobrecargar un único estado**: registrar resultado aceptado + saldo pendiente y eventos específicos, o modelar ítems de decisión. Documentarlo antes de escribir SQL.

Para cualquier conversión a lote, exigir:
- Cantidad aceptada > 0, ≤ cantidad observada y ≤ saldo OC calculado **bajo bloqueo de la fila `purchase_order`**.
- Identidad de lote proveedor no duplicada en esa OC; fecha válida, referencia y ubicación.
- Evidencia/roles requeridos y versión vigente del expediente; sin sobrepasar cantidad ni liberar automáticamente.
- Si la cantidad aceptada es 0, no crear lote y conservar expediente con disposición del excedente/no aceptación.
- Los kilos documentados u observados se conservan como **datos históricos**, no se reutilizan como `lot.quantity` ni como stock disponible.

**Segregación propuesta:** WAREHOUSE captura/valida el conteo; PROCUREMENT registra y decide el tratamiento comercial. El responsable de autorización de una excepción de aceptación **debe ser distinto del reportante** (para la demo se necesitarían dos cuentas si la misma área solicita y aprueba). QUALITY emite concepto solo cuando la diferencia involucra identidad, etiqueta o criterio técnico, no en toda diferencia aritmética. Autoridades reales pendientes de taller.

### 3.2 Gestión de proveedor tras rechazo S10

El rechazo existente en `NcService` sigue siendo una decisión **técnica y terminal** para el lote. Crear un agregado comercial separado, p.ej. `supplier_case`, enlazado a `non_conformity`, `lot`, OC y disposición. No reabrir el lote `REJECTED` para gestionar el proveedor.

Estados propuestos para `RETURN_TO_SUPPLIER`:

```text
OPEN -> SUPPLIER_CONTACT_RECORDED -> RESPONSE_RECORDED
                                    -> DISPOSITION_DOCUMENTED -> CLOSED_ADMINISTRATIVELY
```

Cada paso exige fecha UTC, actor PROCUREMENT, motivo, referencia de comunicación simulada y evidencia textual. La respuesta del proveedor puede ser `ACCEPTED`, `DISPUTED` o `NO_RESPONSE` **solo como dato de simulación**; `NO_RESPONSE` no se genera automáticamente por reloj. Registrar orientación `RETURN_REQUESTED`, `CREDIT_REQUESTED` u `OTHER` como dato comercial ficticio, sin crear crédito real. El cierre administrativo debe indicar explícitamente `physicalReturnConfirmed=false` y `erpPosted=false`, a menos que en una versión futura existan eventos de ejecución verificables.

**Integración con tarea existente:** el caso comercial referencia la tarea PROCUREMENT de tipo `REJECTION_COORDINATION` ya creada en Sprint 4. **No duplicar tareas** por el mismo evento. El acuse y cierre de la tarea y los estados del caso comercial son conceptos distintos; documentar su relación (p. ej. cerrar tarea solo cuando el caso cumpla un mínimo de evidencia), sin acoplarlos circularmente.

Para `SCRAP`, no abrir ficticiamente una devolución al proveedor. Se puede mostrar un caso de disposición interna independiente **solo si** se aprueba explícitamente su alcance; de lo contrario conservar NC/rechazo y coordinarla por los mecanismos existentes.

## 4. Persistencia y arquitectura

Conservar monolito modular Java 21 + Spring Boot + JDBC + PostgreSQL + React/TypeScript. Nueva migración `V7__receiving_discrepancies_and_supplier_cases.sql` **aditiva**; V1–V6 y sus checksums intactos. Posibles tablas:

- `receipt_discrepancy` (un expediente por intento documentado; estado/version/actor/evidencia; FK OC, FK lote aceptado nullable).
- `receipt_discrepancy_event` (append-only; trazabilidad de decisiones/resultado de conteo y recepción final).
- `supplier_case` (FK única a NC relevante, FK lote/OC, gestión comercial, estado/version, vínculo a tarea de Abastecimiento cuando exista).
- `supplier_case_event` (append-only para comunicación/respuesta/cierre; sin sustituir auditoría original).

Antes de implementarlas, comprobar si es preferible una tabla única `case_event` con tipo y FK explicitadas para no duplicar infra de auditoría. No construir un segundo motor genérico de workflow: reutilizar `CommandRunner`, `RuleException`, `AuditLog`, lectores de lotes/NC y política de acceso existente.

**Control de concurrencia:** clave idempotente → `purchase_order FOR UPDATE` → expediente de diferencia → creación de lote, con saldo calculado bajo el lock; para rechazo: lote → NC → mezclas/ejecuciones/tareas según orden actual → caso proveedor, sin adquirir después otra OC si eso invierte locks. Si se necesita leer OC, hacerlo fuera de lock o diseñar una fase compatible. Garantizar transacción única para rechazo + creación del caso comercial, sin duplicarlo en retries. Una falla de auditoría/caso comercial debe revertir la **decisión nueva**; nunca modificar rechazos históricos para backfill sin una migración justificada.

En datos de Sprint 5 no existe granularidad de sublote/caja/cono ni contabilidad de stock separado. La decisión de aceptar parte del material no habilita producción: requiere inspección y liberación normales y mantiene `QUARANTINE`. No introducir consumo ni reservation ledger en V7.

## 5. API candidata (contratos propuestos, no existentes)

| Operación | Propuesta | Rol |
|---|---|---|
| Abrir diferencia | `POST /api/receipt-discrepancies` | WAREHOUSE |
| Listar/consultar | `GET /api/receipt-discrepancies`, `GET /api/receipt-discrepancies/{id}` | Lectura autenticada del simulador |
| Registrar revisión/comentario | `POST /api/receipt-discrepancies/{id}/review` | PROCUREMENT; QUALITY solo concepto técnico cuando corresponda |
| Resolver y, si aplica, crear lote | `POST /api/receipt-discrepancies/{id}/resolve` | PROCUREMENT autoriza; WAREHOUSE ejecuta aceptación en paso controlado o única transacción según diseño definido |
| Casos de proveedor | `GET /api/supplier-cases`, `GET /api/supplier-cases/{id}` | Lectura autorizada |
| Registrar comunicación | `POST /api/supplier-cases/{id}/contact` | PROCUREMENT |
| Registrar respuesta | `POST /api/supplier-cases/{id}/response` | PROCUREMENT |
| Registrar disposición | `POST /api/supplier-cases/{id}/disposition` | PROCUREMENT |
| Cerrar caso administrativo | `POST /api/supplier-cases/{id}/close` | PROCUREMENT autorizado |
| Eventos | `GET /api/receipt-discrepancies/{id}/timeline`, `GET /api/supplier-cases/{id}/timeline` | Lectura autenticada |

Todas las mutaciones: `Idempotency-Key`, versión, datos y evidencia validados en servidor; 403 rol; 409 versión/conflicto/clave; 422 regla; eventos auditables. Devolver estado/versión y links estables. El frontend reutiliza `sessionStorage` para reintento de comando incierto, sin duplicar efectos.

## 6. Casos de aceptación y regresión

| ID | Escenario | Resultado mínimo |
|---|---|---|
| S6-01 | OC 100 kg, documento 100 kg y conteo 90 kg | Registrar falta de 10 kg; no crear lote de 100 ni ajustar OC automáticamente |
| S6-02 | OC 100 kg, documento 90 kg y conteo 100 kg | Registrar sobrante documental, trazabilidad y resolución explícita |
| S6-03 | Saldo OC 30 kg y conteo 40 kg | Registrar expediente por 40; impedir crear lote por 40; resolución puede aceptar ≤30, excedente no disponible |
| S6-04 | OC 100 kg, ya 90 recibidos y otra recepción de 20 | Concurrencia impide que suma de lotes supere 100 |
| S6-05 | Dos decisiones concurrentes de la misma discrepancia | Solo una confirmada; otra 409; no duplicar lote |
| S6-06 | Fallo artificial al escribir auditoría o lote | Rollback integral, incluyendo resolución y resultado idempotente; reintento seguro |
| S6-07 | Un intento sin cantidad aceptada | No crear lote ni habilitar disponibilidad |
| S6-08 | Cuenta distinta intenta modificar resolución o saltar aprobaciones | 403/422 y sin cambios de negocio |
| S6-09 | Recepción excepcional aceptada | Lote en `UNDER_VERIFICATION / QUARANTINE`, sin liberación automática |
| S6-10 | NC REJECTED con RETURN_TO_SUPPLIER | Se crea caso comercial vinculado (una vez), lote sigue REJECTED/BLOCKED y tarea Procurement existente no se duplica |
| S6-11 | NC REJECTED con SCRAP | No se crea falsa devolución al proveedor |
| S6-12 | Contacto/respuesta/disposición/cierre simulados | Actor, UTC, motivos/evidencias, eventos y estado comercial visibles; sin email/ERP/retorno real |
| S6-13 | El proveedor disputa el reclamo | Registrar respuesta negativa, evitar declarar devolución física/cierre satisfactorio sin tratamiento documentado |
| S6-14 | Fallo al crear caso proveedor dentro de rechazo | Revierte rechazo nuevo, NC, auditoría e idempotencia; conservar lote/hold previos |
| S6-15 | Reintento tras respuesta HTTP perdida y reinicio Docker | Misma clave y cuerpo, un solo expediente y un solo evento de decisión; persistencia verificada |
| S6-16 | Migración V6→V7 y regresión Sprints 1–5 | Preservar filas legacy, ≥64 backend y ≥21 Playwright previos sin omitir; añadir pruebas nuevas |

Las cifras son **umbral mínimo de regresión** del PR #4, no promesa de cuántas pruebas nuevas se escribirán. No atribuir éxitos sin ejecución en CI del commit correspondiente.

## 7. Riesgos, criterios y decisiones a validar con negocio

1. **Qué significa la cantidad de OC** (kg, número de conos/cajas, tolerancias contractuales), quién puede decidir una aceptación parcial, qué registros debe conservar Almacén.
2. **Documento vs conteo**: evidencia mínima, procedimiento si no hay documento del proveedor, diferencias dentro/fuera de tolerancia; no inventar umbral automático.
3. **Autoridad de Abastecimiento:** negociación, aceptación comercial, devolución, crédito y destrucción; cada una separada de Calidad y de la liberación de Laboratorio.
4. **Disposición logística:** material no aceptado o rechazado no equivale a material físicamente enviado fuera de planta. Definir si necesita unidad de control/lote segregado y firma de entrega antes de representarlo.
5. **Trazabilidad actual**: la compra y la recepción del sistema son simplificaciones; para una futura aplicación operativa se requieren documentos, cantidades por unidad logística, proveedores reales y prueba material de custodia.
6. **Seguridad:** autenticación HTTP Basic, credenciales demo compartidas y visibilidad amplia son aceptables únicamente en la demo local; no exponer la aplicación a red externa como si fuera productiva.
7. **Control de alcance:** no ampliar este sprint a sublotes, reservas, inventario ERP, calendario laboral, emails, OLA reales o integraciones de proveedores; abrir historias separadas después de validar S03 y S10.

## 8. Orden de implementación y criterio de salida

1. **Preflight:** `git pull --ff-only` desde `main`, verificar CI del SHA base y revisar `WorkflowService.create`, `NcService.reject`, `ExecutionContainment` y `ReportStore`. Actualizar README/PLAN que aún hablen de PR #4 pendiente.
2. Documentar decisión de dominio: diferencia comercial vs NC técnica; aceptación parcial; qué se representa como retorno *simulado* y qué no.
3. Crear rama `codex/sprint-6-supplier-workflows`, diseño y contrato revisado, V7 aditiva con prueba de upgrade aislada.
4. Implementar S6-01/02/03 con backend, UI y pruebas sobre órdenes/lotes simulados. Mantener compatibilidad con `/api/receipts` y con sus claves idempotentes.
5. Implementar S6-04: caso de Abastecimiento vinculado al rechazo técnico, reutilizar tarea ya existente, probar atomicidad/duplicados y trazabilidad.
6. Completar UI multiactor y flujos de demostración; integrar donde aporte valor con dashboard/exportación **sin ampliar el P0**.
7. Ejecutar la regresión completa (Maven/PostgreSQL, Playwright, Compose separado y reinicio), actualizar documentación y preparar PR para revisión, **sin hacer merge automático**.

**Definition of Done:** una diferencia puede documentarse y resolverse sin violar el saldo OC ni inventar stock; un rechazo puede seguirse comercialmente sin alterar el estado técnico ni afirmar retornos físicos; los casos quedan enlazados, auditados y protegidos frente a carreras, con pruebas verificables. S03 puede declararse **completo para simulación** solo si registro, decisión y aceptación/segregación quedan ejecutables; S10 sigue siendo *gestión comercial simulada*, no devolución física.

## 9. Prompt listo para Codex

> Implementa Sprint 6 en `MaterialReviewFlow` tomando `main` fusionado por PR #4 como base y este documento `docs/SPRINT-6-PLAN.md` como **propuesta** a contrastar con el código y las fuentes funcionales. Crea `codex/sprint-6-supplier-workflows`. Primero documenta modelo/roles/estados y riesgos; no cambies código sin inspeccionar la recepción actual y el rechazo/Procurement existentes. Implementa P0: expediente de discrepancia con documento/conteo/saldo OC, decisión de aceptación parcial sin sobrepasar OC, y expediente comercial de proveedor tras NC `RETURN_TO_SUPPLIER` vinculado a la tarea Procurement de Sprint 4. **No** modificar OC o lotes históricos, no emitir retornos físicos/ERP, no interpretar `SCRAP` como devolución. Agrega V7 aditiva, RBAC, idempotencia y auditoría, sincronización de saldo de OC, pruebas de concurrencia/rollback/upgrade V6→V7, React multiactor y E2E. Conserva/regresa al menos 64 tests backend y 21 Playwright existentes, CI en verde, reinicio Docker con persistencia verificada y reportes por SHA. Actualiza README/PLAN/API y prepara PR sin merge.
