# Contrato HTTP del simulador PROCO Sprints 1 a 3

Autenticación HTTP Basic. Todas las rutas requieren identidad; datos sintéticos. GET no cambia estado. Los comandos de material y NC devuelven HTTP 200 con el lote actual; los de mezcla devuelven su propio expediente. `Idempotency-Key` UUID y `version` entero ≥ 0 obligatorios en todos los comandos. `reason` y `evidence` no vacíos, máximo 2000 caracteres. Errores: 400 formato, 401 autenticación, 403 autorización, 404 recurso, 409 versión/estado/clave incompatible, 422 regla incumplida, cuerpo `{status,message}` para errores de negocio.

## Idempotencia, versión y transacciones

Una clave identifica un solo comando de un usuario. Un reintento debe conservar **URL, cuerpo exacto y clave**. Misma clave con otro contenido devuelve 409; repetición idéntica devuelve la vista actual del lote, sin repetir efectos. No se devuelve un snapshot antiguo. La versión del lote protege todo el expediente: cada inspección, ensayo, NC, dictamen y decisión la incrementa. Abrir recepción crea versión 0. El cuerpo del Command Sprint 1 y su fingerprint no cambian.

Todas las escrituras, auditoría y registro idempotente se confirman en la misma transacción. Comandos NC y workflow bloquean la misma fila de lote; dos comandos distintos con igual versión compiten: uno confirma y otro recibe 409. Los intentos denegados por reglas se auditan después del rollback sin cambiar versión/estado. Fallos de infraestructura/serialización/autenticación no se presentan como decisiones de negocio.

La UI guarda el comando pendiente en sessionStorage por usuario antes de enviarlo, sin almacenar credenciales. Red, 5xx, 408/425/429, 401 incierto y respuesta 2xx ilegible/incompleta conservan clave y cuerpo para confirmar mediante reintento. Recargar o cambiar de identidad no los elimina. No se permite descartar el pendiente y emitir accidentalmente otro. Un 4xx definitivo permite corregir con una clave nueva. Un GET fallido después de confirmar el POST no repite la mutación.

## Recepción y ciclo ya existente

```sh
curl -u 'almacen:Simulacion123!' http://localhost:8080/api/receipts \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 33333333-3333-4333-8333-333333333333' \
  -d '{"version":0,"orderCode":"OC-H-001","supplierLot":"DEMO-CURL-001","productionDate":"2026-01-01","quantity":25,"location":"Q-01","reason":"Recepción de prueba","evidence":"OC ficticia y checklist EV-01"}'
```

Conservar `id` y versión de respuesta. Cada acción nueva usa clave nueva; solamente un reintento conserva la clave anterior. Fechas ISO, cantidades decimales en kg.

| Acción POST | Rol / campos adicionales | Guarda y efecto |
|---|---|---|
| `/api/receipts` | WAREHOUSE; orderCode, supplierLot, productionDate, quantity, location | OC existente, saldo suficiente; UNDER_VERIFICATION / QUARANTINE |
| `/api/lots/{id}/inspect` | QUALITY; conforms boolean | Verificación o retenido. Conforme inicia ciclo y vacía muestra/resultado actuales; **conserva el inventario bloqueado**. Fallo HELD y nuevo bloqueo activo |
| `/api/lots/{id}/sample` | WAREHOUSE o QUALITY; sampleCode | UNDER_TEST sin muestra; registra muestra del ciclo, incluso si inventario sigue bloqueado |
| `/api/lots/{id}/test` | LAB_TECH; resultValue decimal | Muestra obligatoria, resultado único por ciclo. Conforme conserva inventario actual; fallo HELD y nuevo bloqueo activo |
| `/api/lots/{id}/release` | LAB_APPROVER | UNDER_TEST, muestra y resultado conforme de otra identidad, QUARANTINE y cero bloqueos activos; pasa RELEASED |
| `/api/lots/{id}/available` | WAREHOUSE | RELEASED, QUARANTINE, cantidad positiva y cero bloqueos; pasa AVAILABLE |

REJECTED no admite ninguna transición posterior. El rechazo no altera la cantidad recibida ni borra liberaciones anteriores.

## Expediente de no conformidad

| Acción POST | Rol | Campos adicionales / efecto |
|---|---|---|
| `/api/lots/{lot}/nonconformities` | WAREHOUSE, QUALITY, LAB_TECH, LAB_APPROVER | `origin`, `responsible`; crea NC OPEN y bloquea preventivamente en una transacción |
| `/api/lots/{lot}/nonconformities/{nc}/opinion` | LAB_APPROVER | `outcome`; añade dictamen inmutable, ligado a la versión resultante del lote |
| `/api/lots/{lot}/nonconformities/{nc}/resolve` | QUALITY responsable | `disposition: "LIFT_HOLD"`; exige dictamen conforme vigente, muestra/ensayo conforme y estado UNDER_TEST o RELEASED; levanta explícitamente los bloqueos de esta NC |
| `/api/lots/{lot}/nonconformities/{nc}/reject` | QUALITY responsable | `disposition: "RETURN_TO_SUPPLIER"` o `"SCRAP"`; exige dictamen no conforme vigente; NC REJECTED y lote REJECTED / BLOCKED terminal |

- `origin`: DOCUMENTARY, LABORATORY, POST_RELEASE u OTHER. POST_RELEASE solo admite lote ya RELEASED.
- `responsible`: username existente con rol QUALITY y distinto del reportante. `calidad2` es una cuenta de simulación adicional para independencia. La decisión debe ser distinta del autor del dictamen y del reportante.
- `outcome`: CONFORMING o NONCONFORMING. Los dictámenes previos se conservan. Cualquier mutación posterior del lote invalida su vigencia: se exige uno actualizado antes de decidir.
- Todo comando conserva motivo y evidencia propios; el expediente conserva también motivo/evidencia originales y de disposición. No hay edición ni borrado de dictámenes.
- Hay múltiples NC por lote. Abrir una asocia bloqueos activos todavía sin expediente; si no existen crea uno nuevo. Un fallo posterior puede generar otro bloqueo que deberá formalizarse, sin levantar los anteriores.
- Cerrar una NC solo levanta sus bloqueos. Si quedan otros, permanece BLOCKED. Al levantar el último pasa a QUARANTINE; requiere confirmación posterior de Almacén, y liberación de Laboratorio si todavía UNDER_TEST.
- Un lote RELEASED/AVAILABLE puede bloquearse sin cambiar calidad, muestra, resultado ni decisión histórica. Solo un rechazo definitivo posterior cambia su calidad actual.
- Una NC cerrada no se reabre. Tras rechazo del lote, otras NC previas conservan el expediente, pero no admiten nuevas mutaciones.
- La disposición de rechazo es un destino previsto: no ejecuta baja física ni devolución al proveedor.

Ejemplo de apertura (sustituir versión por la actual):

```json
{"version":5,"reason":"Diferencia detectada después de liberar","evidence":"EV-NC-101","origin":"POST_RELEASE","responsible":"calidad"}
```

La respuesta es el lote, ahora BLOCKED con versión incrementada y `activeHolds`. Consultar la colección de NC para obtener el UUID generado. Ejemplo de dictamen:

```json
{"version":6,"reason":"Evaluación técnica independiente","evidence":"EV-DICT-101","outcome":"CONFORMING"}
```

Tras esa respuesta, la versión es 7 y el dictamen soporta precisamente esa versión. Ejemplo de decisión explícita:

```json
{"version":7,"reason":"Riesgo resuelto; levantar el bloqueo identificado","evidence":"EV-DEC-101","disposition":"LIFT_HOLD"}
```

## Consultas

- GET `/api/me`: username y role.
- GET `/api/quality-users`: usernames elegibles para responsabilidad QUALITY, sin contraseñas.
- GET `/api/catalog`: OC, proveedor, referencia y criterio ficticio; claves snake_case.
- GET `/api/lots`, `/api/lots/{id}`: expediente camelCase; se conserva el contrato previo y se añade `activeHolds`. Un BLOCKED heredado puede tener cero filas de bloqueo y sigue siendo no disponible.
- GET `/api/lots/{id}/timeline`: eventos append-only con actor/rol, UTC, estados, motivo/evidencia, correlación y comando. Nuevos eventos NC_OPEN, NC_OPINION, NC_RESOLVE, NC_REJECT usan SIM-2; la tolerancia de ensayo sigue SIM-1. Los eventos históricos permanecen intactos.
- GET `/api/lots/{id}/nonconformities`: colección camelCase con id, lotId, origin, status, responsible, openedBy, reason, evidence, openedAt, disposition, decidedBy/At, decisionReason/Evidence y `opinions`. La lectura de expediente/dictámenes usa una instantánea consistente.
- Cada dictamen usa claves SQL snake_case: id, nc_id, author, outcome, reason, evidence, based_on_version, created_at. `based_on_version` coincide con la versión del lote después de emitirlo.
- GET `/api/lots/{id}/holds`: historial snake_case con id, lot_id, nc_id (nullable), status ACTIVE/LIFTED, apertura, motivo/evidencia, correlation_id y cierre explícito (closed_by/at, close_reason/evidence).

## Compatibilidad

V3 no actualiza filas existentes ni modifica V1/V2. BLOCKED de V2 conserva su estado: abrir NC lo formaliza y una decisión explícita puede levantarlo. No hay desbloqueo automático por migración o reinspección. Clientes Sprint 1 que pretendan liberar tras reinspeccionar sin resolver los bloqueos recibirán 422.

No existe `/api/tasks` persistido ni rutas de consumo/devolución física. Las rutas de mezcla se incorporan en la sección de Sprint 3. Evidencia textual, sin archivos binarios. No se debe volver a desplegar el código Sprint 1 sobre V3 esperando que pueda resolver bloqueos nuevos; una reversión requiere código compatible, no reescribir ni eliminar auditoría.

# Sprint 3 Autorización de mezclas

La especificación de Sprint 3 usa US-06 (solicitud), US-07 (conceptos), US-08 (decisión) y US-09 (revocación); esa numeración rige esta entrega. Reglas y autoridades son hipótesis de simulación, contrastadas en `SPRINT-3-DESIGN.md` con P06–P10 y BR-06–09/12 del procedimiento.

## Contrato de comandos

Todas las mutaciones usan el mismo control por identidad, `Idempotency-Key` UUID y `version` optimista. POST devuelve el expediente **de mezcla** con id/version; la repetición idéntica devuelve su vista actual, sin repetir eventos. No se puede reutilizar una clave de un comando de lote para uno de mezcla. El cliente conserva cuerpo/clave por identidad y reconoce qué agregado debe recargar después del reintento.

Campos comunes: version ≥ 0; reason/evidence no vacíos, máximo 2000 caracteres. Errores 403 rol, 404 recurso, 409 versión/estado/clave, 422 guarda de dominio; 400 para validación de transporte. Intentos de negocio rechazados se auditan en mix_event después del rollback; los eventos son append-only.

| Método y ruta | Rol | Contrato |
|---|---|---|
| POST `/api/mix-requests` | PRODUCTION_REQUESTER | version=0, montageCode (≤80 caracteres), validUntil UTC futuro e items (2–50 lotes diferentes con lotId y quantity positiva, hasta 3 decimales) |
| POST `/api/mix-requests/{id}/assessments` | LAB_APPROVER, GEO, QUALITY, DYEING | version actual, outcome FAVORABLE/UNFAVORABLE, motivo/evidencia. Un concepto por área; corrección requiere nueva solicitud |
| POST `/api/mix-requests/{id}/decision` | PRODUCTION_APPROVER distinto de solicitante | version actual, decision AUTHORIZE/DENY, motivo/evidencia. AUTHORIZE exige todos los conceptos, contexto, vigencia y lotes elegibles; DENY registra decisión terminal aunque falten conceptos |
| GET `/api/mix-requests` | Autenticados | Bandeja con expediente completo y elegibilidad derivada, sin consultas por cada ítem/área |
| GET `/api/mix-requests/rules` | Autenticados | Mapa referencia → regla ficticia/versionada, incluida dyeingRequired |
| GET `/api/mix-requests/{id}` | Autenticados | Expediente con ítems exactos, snapshots y estados actuales, conceptos e impedimentos |
| GET `/api/mix-requests/{id}/timeline` | Autenticados | Eventos de creación, conceptos, autorización/denegación/revocación e intentos rechazados |
| GET `/api/mix-requests/{id}/authorization?montageCode=…` | Autenticados | Revalida vigencia, regla y cada lote; exige montaje exacto. 422 si no utilizable. Devuelve la combinación guardada; no inicia montaje, no reserva ni consume |

Ejemplo de solicitud (reemplazar lotes y caducidad con datos actuales):

```json
{
  "version": 0,
  "montageCode": "M-SIM-100",
  "validUntil": "2026-12-01T23:00:00Z",
  "reason": "Evaluación humana de compatibilidad",
  "evidence": "EV-MIX-100",
  "items": [
    {"lotId": "UUID-LOTE-A", "quantity": 0.4},
    {"lotId": "UUID-LOTE-B", "quantity": 0.5}
  ]
}
```

Concepto: `{"version":0,"reason":"Concepto favorable del área","evidence":"EV-C-100","outcome":"FAVORABLE"}`. El primer concepto devuelve versión 1; el siguiente usa esa versión, sin invalidar el concepto anterior. Decisión: `{"version":3,"reason":"Aprobación independiente","evidence":"EV-A-100","decision":"AUTHORIZE"}` para tres conceptos en un caso sin Tintorería.

## Estados, conceptos y elegibilidad

- `UNDER_REVIEW → AUTHORIZED/DENIED`; `AUTHORIZED → REVOKED` por bloqueo/NC/rechazo. DENIED y REVOKED son terminales. No hay edición de montaje, lista, cantidades, vigencia o criterio; los cambios requieren nuevo ID.
- Los lotes deben estar RELEASED/AVAILABLE, sin holds, con cantidad positiva suficiente y con la misma versión de la solicitud. No se modifica cantidad ni versión de lote al crear/evaluar/autorizar una mezcla. Solicitudes independientes no son reservas acumulativas.
- Catálogo ficticio: HILO-DEMO usa MANUAL_BASIC/MIX-SIM-1 (Lab/GEO/Calidad); ELASTANO-DEMO exige además Tintorería por MANUAL_DYEING/MIX-SIM-1. Si cualquiera lo requiere, toda la combinación lo requiere. El cliente no puede desactivar esa bandera. No se aplican umbrales por fecha de 30/60 días.
- Cada concepto guarda actor/rol, motivo/evidencia, instante UTC, based_on_mix_version observado, review_revision y rule_version. La revisión de combinación permanece 1 porque es inmutable. La versión operativa aumenta al añadir cada concepto; no se exige que todos provengan de la última versión operativa, sino del mismo contexto inmutable. Cambiar lotes/reglas o introducir un contexto antiguo invalida la autorización. Cualquier concepto desfavorable, incluido uno opcional de Tintorería, bloquea AUTHORIZE.
- El expediente expone `requiredRoles`, `materialBlockers`, `blockers`, `usable` y `checkedAt`. `materialBlockers` excluye conceptos faltantes para permitir evaluaciones independientes; `blockers` añade faltantes/desfavorables/desactualizados. `usable` exige AUTHORIZED y ningún impedimento. El estado AUTHORIZED histórico puede conservarse al vencer el tiempo, pero nunca basta para utilizarlo.
- GET authorization es una comprobación en la instantánea checkedAt: no garantiza elegibilidad futura ni crea reserva/consumo. El sistema no proporciona un endpoint de montaje físico.
- Ítems y conceptos conservan claves snake_case; raíz del expediente camelCase. Ítems incluyen lot_id, quantity, ordinal, reference, supplier_lot, production_date, lot_version_at_request, current_lot_version, quality_state, inventory_state y active_holds. No se afirma trazabilidad por set, cono/caja o máquina.

## Revocación transaccional y auditoría

Abrir NC por Almacén, Calidad, Laboratorio, Producción, GEO o Tintorería utiliza el módulo existente. La inserción/formalización del hold y cualquier rechazo formal revocan las mezclas AUTHORIZED afectadas en la misma transacción. Se conserva approver, decidedAt, decisionReason/Evidence y se agrega revokedAt/revocationReason y un evento REVOKED con lote, causa y correlación de la NC. No se reactivan al cerrar una NC.

Orden común de locks: clave idempotente → lotes ordenados por UUID → solicitudes ordenadas por UUID. NC mantiene su lote y solo adquiere después solicitudes; nunca toma otros lotes. La aprobación vuelve a comprobar material y conceptos después de adquirir todos sus locks. Los triggers diferidos impiden confirmar AUTHORIZED con lotes bloqueados, rechazados, de versión distinta o cantidades insuficientes. Si falla la revocación o su auditoría, se revierte también la NC/hold/lote y su registro de comando.

V4 es aditiva: tablas nuevas y columna mix_id en command_result, con XOR lote/mezcla. Los comandos antiguos conservan lot_id y fingerprint. No se reescriben V1–V3 ni decisiones anteriores. Auditoría de mezclas y conceptos son append-only; ítems/montaje quedan sellados e inmutables.
