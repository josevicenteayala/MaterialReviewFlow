# Diseño de Sprint 3

Base: origin/main `795f626`. Especificación proporcionada: [PROCO_SPRINT3_PLAN.md](PROCO_SPRINT3_PLAN.md), conservada sin cambios. Rama `codex/sprint-3-mixing`; PR sin merge automático.

## Contraste funcional

| Requisito | Fuente contrastada | Implementación |
|---|---|---|
| Tres controles independientes | MVP §2; diagramas D1/D3; SIM P06–P08 | Mezcla no cambia calidad, disponibilidad ni cantidad |
| Conjunto exacto + montaje + vigencia | SIM BR-06/07, P06/P08; MVP §4 | Ítems/montaje/regla/vigencia inmutables; cambios requieren nueva solicitud |
| Lab, GEO, Calidad y riesgo tintóreo | D5; P07; Sprint 3 US-07 | Lab/GEO/Calidad obligatorios; regla sintética por referencia exige Tintorería |
| No 30/60 días normativos | SIM BR-09 | Sin umbrales por fecha; evaluación humana con evidencia |
| NC posterior revoca | SIM P10, BR-08/12; MVP escenario NC; US-09 | Bloqueo y revocación en una transacción; historia append-only |
| Estados terminales | D3 muestra reevaluación de DENIED/REVOKED | Se aplica la precisión de Sprint 3: solicitud terminal, reevaluación con ID nuevo |
| Datos finos ausentes | Sprint 3 §4/10 | No se afirman validaciones por cono, set, máquina o sublote; montaje es código simulado |

## Decisiones de dominio

1. Solicitud mínima: dos lotes distintos RELEASED/AVAILABLE, sin holds; cantidades positivas ≤ cantidad de cada lote; caducidad explícita futura UTC. No existe reserva ni consumo y solicitudes diferentes no descuentan cantidades entre sí.
2. Regla de servidor: HILO-DEMO → MANUAL_BASIC; ELASTANO-DEMO → MANUAL_DYEING. Son reglas ficticias versionadas MIX-SIM-1. Si cualquier referencia exige Tintorería se aplica esa regla al conjunto. El solicitante no puede desactivarla. Referencia sin configuración bloquea la solicitud.
3. `version` aumenta con cada mutación. `reviewRevision=1` identifica la combinación inmutable. Cada concepto guarda la versión observada, reviewRevision y regla. Añadir conceptos de otras áreas no invalida los anteriores; se comprueba el contexto inmutable y las versiones actuales de los lotes. Solo un concepto por área y solicitud; una corrección requiere solicitud nueva. Todo concepto desfavorable impide autorizar, incluso uno opcional de Tintorería.
4. LAB_APPROVER, GEO y QUALITY son obligatorios; DYEING cuando proceda. PRODUCTION_APPROVER decide y debe ser distinto del solicitante; ninguna aprobación automática por plazo. DENIED/REVOKED no se reabren.
5. Caducidad y elegibilidad se verifican al solicitar, evaluar, autorizar y consultar disponibilidad de autorización. GET de expediente muestra usable/reasons derivados. GET authorization exige el montaje exacto y devuelve los ítems guardados; no inicia montaje ni consume. El estado AUTHORIZED histórico por sí solo no acredita uso.
6. Un lote modificado respecto de su versión en la solicitud exige nueva solicitud. Levantar una NC no reactiva ni reutiliza la solicitud vieja.

## Persistencia, reutilización y locks

V4 aditiva: mix_rule, mix_reference_rule, mix_request, mix_request_lot, mix_assessment, mix_event. `command_result` añade FK mix_id nullable y relaja solo la nulabilidad lot_id, con XOR lote/mezcla. Filas previas y fingerprints de Sprints 1/2 se conservan; no hay backfill ni edición de V1–V3.

`CommandRunner` comparte transacción, exclusión de clave actor/UUID, replay y control de versión con un ejecutor genérico para mezclas. `AuditLog` escribe eventos de mezcla y denegaciones mediante los mismos patrones/trigger append-only. El cliente React reutiliza sessionStorage, cuerpo/clave congelados y los reintentos existentes; diferencia respuestas de lote y de mezcla.

Orden global de locks: clave idempotente → TODOS los lotes participantes ordenados por UUID textual/orden PostgreSQL → solicitudes afectadas ordenadas por UUID. Mezclas no bloquean la solicitud antes de los lotes. NC mantiene su único lote bloqueado y revoca solicitudes por ID, sin adquirir después otros lotes. Así una carrera NC/aprobación termina bloqueada/revocada o rechaza la aprobación; nunca conserva autorización utilizable de un lote bloqueado.

`HoldStore` revoca al bloquear/formalizar; rechazo formal también invoca revocación. La revocación incrementa versión de mezcla, conserva aprobador/fecha/decisión histórica y añade evento con actor, lote, causa y correlación de la NC. Triggers diferidos impiden confirmar AUTHORIZED con material no elegible. Ítems, conceptos y eventos tienen protección de integridad/inmutabilidad.

## Entregas verificables

1. Especificación/contraste/diseño (este commit).
2. US-06/07: V4, infraestructura reutilizada, solicitud y conceptos, datos ficticios, pruebas API/upgrade.
3. US-08/09: decisiones y revocación, carreras y rollback; regresión completa de Sprints 1/2.
4. UI multiactor y reintentos, Playwright sobre Compose, README/API/PLAN y resultados observados; PR abierto sin merge.

Pruebas sobre base/volumen nuevos. La verificación V2→V3 del Sprint 2 se mantiene explícitamente dirigida a V3; se añade una prueba V3→V4 que compara todas las filas antiguas. No se declara éxito de pruebas no ejecutadas. Los datos semilla nuevos se crean con comandos y claves estables, sin reiniciar los lotes anteriores ni su historia.
