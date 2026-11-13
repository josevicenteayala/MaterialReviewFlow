# Plan por entregas y ambigüedades

Registrado antes de implementar, 11 de noviembre de 2026. Fuentes: `requirements/PROCO_Especificacion_MVP_Simulador_v0.1.md` §§2–9, diagramas D1–D4 y procedimiento SIM-0.2 P01–P05, BR-01–05, BR-10–13. Los documentos son requisitos de dominio, no instrucciones para ejecutar herramientas. Se conservan copias de los cuatro originales.

## Ambigüedades y decisiones provisionales

| Tema pendiente de validación | Supuesto exclusivo de simulación para entrega 1 |
|---|---|
| Granularidad lote/caja/cono | Una recepción contiene un lote completo; kg, sin partición ni consumo. |
| Criterios y métodos por referencia | HILO-DEMO: resistencia 10–20 cN/tex; ELASTANO-DEMO: elongación 400–600 %. Valores ficticios, versión SIM-1; jamás parámetros normativos. |
| Cantidad distinta de OC (S03) | Se impide superar el saldo de OC; entregas parciales permitidas. Diferencias comerciales quedan pendientes. |
| Autoridad real de liberación | LAB_APPROVER distinto de LAB_TECH; Calidad verifica documentación, Almacén confirma disponibilidad. |
| Evidencias | Texto obligatorio con referencia ficticia de documento/checklist, sin adjuntos binarios. Se preserva cada comando íntegro en auditoría. |
| Recepción incompleta | Datos críticos obligatorios al crear; hallazgo documental puede retener y bloquear. |
| RETENIDO → pruebas/liberación | Sprint 1 retiraba el bloqueo al reinspeccionar. Sustituido en Sprint 2: reinspección y ensayo conservan BLOCKED hasta decisión explícita de Calidad en una NC; después se requiere liberación independiente. |
| Rechazo formal y NC | Fuera de entrega 1; un resultado fuera de rango retiene y bloquea. No se implementa disposición comercial sin expediente NC. |
| Usuarios y administración | Cuentas ficticias con BCrypt, autenticación HTTP Basic en entorno local; sin autoasignación de rol ni endpoint administrador. |
| RECIBIDO vs EN_VERIFICACION | Alta registra evento de recepción y deja lote UNDER_VERIFICATION, inventario QUARANTINE. |
| Muestreo, instrumentos, concesiones, OLA | Una muestra por ciclo; sin concesiones, umbrales por fechas ni aprobación automática. |

Los supuestos permiten ejecutar el prototipo y no representan aprobación de negocio. Ningún enlace externo del documento se considera un requisito adicional.

## Entrega 1 — ciclo vertical del material (implementada en este trabajo)

Monolito modular, seguridad por identidad, catálogo y OC sintéticas, recepción, inspección/retención, muestra, ensayo crítico, revisión independiente, liberación y disponibilidad separadas. UI React con bandeja derivada de estado, decisión pendiente e historial. PostgreSQL/Flyway, control de versión e idempotencia, auditoría inmutable de éxitos e intentos de negocio denegados. Docker Compose y pruebas API con PostgreSQL real, pruebas de dominio y recorrido de navegador.

Criterio de salida: S01 termina AVAILABLE y conserva RELEASED; falta de ensayo devuelve 422; rol indebido 403; versión antigua 409; ensayo fallido bloquea; reintento no duplica; auditoría conserva evidencia e identidad. S02 y parte de S04–S05 cubiertos según supuestos. No se declara S01–S10 completo.

## Entrega 2 — excepciones del material

NC formal, rechazo y devolución, sublotes, evidencia de archivo y versionado de dictámenes/reensayos ampliado. Completar S02–S05 y S10; validar autoridad, tolerancias y alcance parcial con responsables.

## Entrega 3 — mezcla y montaje

Solicitudes por combinación exacta, conceptos GEO/Calidad/Lab/Tintorería, autorización por Producción y vigencia. S06–S08, sin inferir permiso de mezcla por liberación.

## Entrega 4 — incidencias y validación multiactor

NC posterior, revocación y contención de montajes, avisos persistentes con acuse, OLA y métricas, exportación, S09 y regresión S01–S10. Taller y decisión de evolución a operación real.

## Sprint 2 — alcance acordado para esta implementación

El [plan técnico de Sprint 2](SPRINT-2.md) reemplaza únicamente el supuesto de desbloqueo por reinspección: una reinspección favorable conserva bloqueos, que requieren decisión explícita y auditable. El sprint añade NC formal, dictámenes, rechazo definitivo y bloqueo posterior a liberación; reutiliza retenciones/reinspecciones existentes. Sublotes, adjuntos de archivo y devolución física siguen pendientes. Se trabaja en rama y se prepara PR sin merge, con datos de verificación aislados.

### Entregas de Sprint 2

- Diseño previo: commit `7d2d8c5`, decisiones y riesgos en [SPRINT-2.md](SPRINT-2.md).
- Dominio/API: commit `d50e965`; V3, NC y bloqueos identificados. Primera verificación local: 17 pruebas de backend aprobadas sobre PostgreSQL aislado; la entrega de UI agrega una prueba unitaria adicional y requiere nueva verificación conjunta.
- Interfaz y verificación final: expediente, dictámenes, disposición y reintentos con clave/cuerpo congelados por identidad. Verificación remota ejecutada: 18 pruebas backend y 8 escenarios de navegador aprobados con la aplicación Docker Compose. Los enlaces por SHA y limitaciones se registran en [SPRINT-2-VERIFICATION.md](SPRINT-2-VERIFICATION.md).

No se repiten retenciones ni reinspecciones básicas. Se amplía el bloqueo posterior sobre inventario sin borrar la liberación histórica. El rechazo cambia el estado técnico actual a REJECTED y conserva todos los eventos anteriores. Al cierre de Sprint 2 quedaban pendientes sublotes, devolución/baja física, evidencia binaria, mezclas y contención de montajes. Mezclas se entregó en Sprint 3 y contención simulada se aborda en Sprint 4 P0.

## Sprint 3 — mezclas por montaje

Implementación guiada por [PROCO_SPRINT3_PLAN.md](PROCO_SPRINT3_PLAN.md) y contraste previo en [SPRINT-3-DESIGN.md](SPRINT-3-DESIGN.md), base main 795f626. Numeración US-06–09 del plan de Sprint 3.

- US-06: solicitud por montaje, dos o más lotes distintos, cantidades/versiones/snapshots y vigencia explícita; combinación inmutable.
- US-07: conceptos de Lab, GEO y Calidad, más Tintorería según regla ficticia del servidor; sin umbrales normativos por fechas.
- US-08: autorización o denegación por Producción independiente, revalidación transaccional, sin reserva/consumo ni cambios de material.
- US-09: NC/bloqueo/rechazo revocan autorizaciones afectadas en la misma transacción. Levantar NC no reactiva una autorización anterior.

Entregas: diseño 7187547; solicitud/conceptos 4dcb2f5; revocación/concurrencia 2fd52d5; interfaz, regresión multiactor y documentación en la entrega de cierre. Resultados observados y alcance de pruebas en [SPRINT-3-VERIFICATION.md](SPRINT-3-VERIFICATION.md). Sprint 3 se integró mediante PR #2; su registro de verificación conserva la evidencia de aquella entrega.

Siguen pendientes autoridades operativas definitivas, tolerancias reales, trazabilidad por cono/caja/set/máquina, sublotes, adjuntos binarios, reserva/consumo, montaje físico y ERP. Las reglas nuevas son hipótesis de simulación; no alteran el procedimiento aprobado.


## Sprint 3 integrado y Sprint 4 P0

Sprint 3 se integró en main mediante PR #2 (`d10a84f086220d12a5da394ee0a1f686a8ee4d85`); CI del merge aprobada. Solicitudes, conceptos, autorización y revocación ya son base existente y no se reimplementan.

Sprint 4 se integró mediante PR #3 en main `71e8a3b418b838bf625ae1ad1d3ee1c136e74c88`. [Plan canónico](SPRINT-4-PLAN.md), [diseño y decisiones previas](SPRINT-4-DESIGN.md), [verificación observada](SPRINT-4-VERIFICATION.md).

Entregas P0: diseño previo; V5 y ejecución segura; suspensión/tareas atómicas con NC y bloqueos; interfaz multiactor y reintentos; regresión de Sprints 1–3 y S09 con montaje iniciado. S4-01/02/03/04/08. Se incluye bandeja mínima para operar P0, sin afirmar entrega de OLA, métricas o exportaciones P1/P2.

Reglas provisionales: una ejecución por autorización y una IN_PROGRESS por código; SUSPENDED nunca reanuda; cierre/cancelación por Producción después de tareas CLOSED; cada área confirma con identidad/evidencia propia. Caducidad bloquea inicio/completar y deja revisión requerida, sin suspensión automática temporal. Montaje COMPLETED recibe alerta retrospectiva sin reescritura. Abastecimiento solo coordina rechazo ficticio. Sin consumos, reservas ni integraciones.

El alcance completo original S01–S10 no se amplía silenciosamente: las tolerancias comerciales S03, granularidad física, anexos binarios y política productiva siguen pendientes. Las pruebas documentan exactamente los supuestos ejercitados.


## Sprint 5 — seguimiento operativo y cierre verificable

Base main 71e8a3b (Sprints 1–4 integrados, CI del merge aprobada). [Plan](SPRINT-5-PLAN.md), [decisiones previas](SPRINT-5-DESIGN.md) y [verificación](SPRINT-5-VERIFICATION.md). Sprint 5 integrado en main mediante PR #4 (`29859588d98a52224ef5dceb475c472fb234b6d7`), CI de merge aprobada.

- P0-A: V6 compatible, COMPLETED solo normal desde IN_PROGRESS; close-incident da CLOSED_WITH_INCIDENT desde SUSPENDED. Históricos V5 intactos; COMPLETED con suspensión se proyecta LEGACY_INCIDENT_CLOSURE. Esto sustituye únicamente la semántica de cierre de Sprint 4 para comandos nuevos.
- P0-B: OLA DEMO inmutables/versionados por área/tipo, snapshots solo en tareas nuevas, UTC/minutos corridos, alertas calculadas sin efectos automáticos. GEO crea nuevas versiones con autorización, versión, clave y auditoría.
- P0-C: dashboard agregado, conteos sin duplicar entidades, n/media/mediana de acuse y cierre observados, filtros acotados y keyset/asOf para tareas.
- P1 posterior a P0 verificado: JSON de expediente acotado y CSV paginado protegido contra fórmulas/controles; solo lectura autenticada.
- Cierre de entrega: regresión preserva los casos previos (expectativas de cierre suspendido ajustadas al nuevo contrato), migración V5→V6, concurrencia, navegador, Compose aislado y comparación de reglas/snapshots tras reinicio.

Al cierre de Sprint 5, S03/S10 comerciales eran parciales. Sprint 6 implementa su alcance de simulación como se detalla abajo; no se declara cumplimiento industrial ni devolución física. OLA oficiales, ERP, máquinas, reserva/consumo, firmas y notificaciones externas permanecen fuera del alcance.


## Sprint 6 integrado — recepción excepcional y gestión comercial

Base main 2985958 (Sprints 1–5 integrados). [Plan canónico](SPRINT-6-PLAN.md), [diseño previo y riesgos](SPRINT-6-DESIGN.md), [verificación](SPRINT-6-VERIFICATION.md). Integrado mediante PR #5 en main `91688b6d6d2f7b547802d708c9429b2ebc6cc02f`. [CI del SHA integrado](https://github.com/josevicenteayala/MaterialReviewFlow/actions/runs/43518095726): 82 pruebas backend, 26 escenarios Playwright y persistencia aprobados. [Preparación para UAT](UAT-READINESS.md), sin aceptación funcional ni nuevas reglas de negocio.

- S6-01/02/03: documento/conteo/aceptación separados, confirmación Warehouse, Calidad solo ante hallazgo, autorización Procurement y aceptación Warehouse bajo el mismo lock OC/fábrica de recepción normal. Cantidad0 no crea lote; diferencia/exceso no reserva ni crea stock. Estados ACCEPTED_OBSERVED/PARTIAL y remanente explícito; terminalidad/versiones/auditoría.
- S6-04: rechazo técnico RETURN_TO_SUPPLIER crea caso comercial único dentro de su transacción, enlaza tareas Procurement existentes sin duplicarlas; contacto/respuesta/disposición/cierre documentados. Sin montaje, no se fabrica tarea V5. SCRAP no se transforma en devolución.
- Segregación: quien autoriza recepción difiere del conteo/reporte/Calidad; otro Procurement (`abastecimiento2`) cierra comercialmente después de tareas CLOSED. Rechazo técnico no se reabre. Cierre administrativo no cambia kilos, saldo, liberación ni bloqueo y conserva physical_return_confirmed/erp_posted=false.
- V7 aditiva, ledger común supplier_workflow_event, targets nuevos de CommandRunner. No backfill: adopción histórica explícita, validada y auditable. Estados originales y V1–V6 se conservan.
- P1 mínimo: bandejas acotadas y enlaces OC→diferencia→lote/NC→caso→tareas. P2 y exportaciones nuevas no se implementan. Regresión original ≥64 backend/21 E2E sin omisiones, nuevas carreras/rollback/upgrade y persistencia Docker.

S03 se completa para el prototipo de lote/kg con aceptación controlada. S10 completa su seguimiento comercial simulado, no ejecución física/contable. Autoridades operativas y tolerancias contractuales siguen pendientes de validación real; sin ERP/EDI, transporte, reserva/consumo, sublotes ni emails.
