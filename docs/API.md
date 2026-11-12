# Contrato HTTP de la primera entrega

Autenticación HTTP Basic en todas las rutas. JSON con fechas ISO, cantidades decimales en kg. GET devuelve datos y no cambia estado. POST devuelve el expediente actual con HTTP 200. `Idempotency-Key` UUID y `version` son obligatorios. Cambiar de clave es un comando nuevo; una misma clave nunca admite otro contenido. Una repetición idéntica devuelve la vista actual del lote, no un snapshot antiguo.

## Crear recepción

```sh
curl -u 'almacen:Simulacion123!' http://localhost:8080/api/receipts \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 33333333-3333-4333-8333-333333333333' \
  -d '{"version":0,"orderCode":"OC-H-001","supplierLot":"DEMO-CURL-001","productionDate":"2026-01-01","quantity":25,"location":"Q-01","reason":"Recepción de prueba","evidence":"OC ficticia y checklist EV-01"}'
```

Conservar el `id` de la respuesta para los siguientes comandos. Cada paso usa una clave nueva y la `version` más reciente. Campos comunes a todos: `version` entero ≥ 0, `reason` y `evidence` textos no vacíos hasta 2000 caracteres.

| Acción POST | Campos adicionales | Guarda y efecto |
|---|---|---|
| `/api/receipts` | orderCode, supplierLot, productionDate, quantity, location | WAREHOUSE; OC existente y saldo suficiente. UNDER_VERIFICATION / QUARANTINE, versión 0 |
| `/api/lots/{id}/inspect` | conforms: boolean | QUALITY; en verificación o retenido. Conforme inicia ciclo, borra vista actual de muestra/resultado y pasa UNDER_TEST / QUARANTINE; fallo HELD / BLOCKED |
| `/api/lots/{id}/sample` | sampleCode: texto | QUALITY o WAREHOUSE; UNDER_TEST sin muestra; registra muestra única del ciclo |
| `/api/lots/{id}/test` | resultValue: decimal | LAB_TECH; UNDER_TEST con muestra, sin resultado. Guarda resultado; si fuera de rango pasa HELD / BLOCKED |
| `/api/lots/{id}/release` | — | LAB_APPROVER distinto de capturador; UNDER_TEST con muestra y resultado conforme, sin bloqueo. RELEASED / QUARANTINE |
| `/api/lots/{id}/available` | — | WAREHOUSE; RELEASED / QUARANTINE y cantidad positiva. RELEASED / AVAILABLE |

Cada comando exitoso sobre lote existente incrementa `version` en uno. La reinspección aumenta `cycle` en uno; la evidencia de ciclos anteriores queda íntegra en auditoría. El lote no dispone de acciones genéricas para editar estado.

## Consultar

- `/api/me`: username y role autenticados.
- `/api/catalog`: OC, proveedor, referencia, tipo, nombre de ensayo, unidad, mínimo, máximo y versión del criterio. Las claves del catálogo SQL usan snake_case.
- `/api/lots`: todos los lotes, más recientes primero. Claves camelCase: id, orderCode, supplierLot, productionDate, quantity, location, qualityState, inventoryState, version, cycle, sampleCode, resultValue, testedBy, createdAt, reference, supplier, testName, unit, minimum, maximum, ruleVersion.
- `/api/lots/{id}`: expediente actual.
- `/api/lots/{id}/timeline`: eventos ordenados por instante e id, claves snake_case. Incluyen event_type, actor, role, occurred_at UTC, from_state/to_state, reason, evidence, rule_version, correlation_id y payload textual del comando original.

Las bandejas se derivan de estos datos; no existe `/api/tasks` persistido en esta entrega. No hay rutas de mezcla, edición de evidencia ni borrado de auditoría.

## Ejemplo de inspección

```json
{"version":0,"reason":"Identificación y empaque conformes","evidence":"Checklist firmado ficticio EV-02","conforms":true}
```

Estados de error: 400 formato o validación, 401 autenticación, 403 rol, 404 recurso/acción, 409 transición/versión/idempotencia, 422 regla incumplida. Ejemplo: `{"status":422,"message":"Falta muestra o ensayo crítico"}`. Los intentos rechazados por reglas se auditan sin cambiar el estado ni la versión.
