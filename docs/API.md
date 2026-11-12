# Contrato HTTP del simulador PROCO Sprint 2

Autenticación HTTP Basic. Todas las rutas requieren identidad; datos sintéticos. GET no cambia estado. POST devuelve HTTP 200 con el lote actual, también para mutaciones de NC. `Idempotency-Key` UUID y `version` entero ≥ 0 obligatorios en todos los comandos. `reason` y `evidence` no vacíos, máximo 2000 caracteres. Errores: 400 formato, 401 autenticación, 403 autorización, 404 recurso, 409 versión/estado/clave incompatible, 422 regla incumplida, cuerpo `{status,message}` para errores de negocio.

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

No existe `/api/tasks` persistido ni rutas de mezcla/consumo/devolución física. Evidencia textual, sin archivos binarios. No se debe volver a desplegar el código Sprint 1 sobre V3 esperando que pueda resolver bloqueos nuevos; una reversión requiere código compatible, no reescribir ni eliminar auditoría.
