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

No se repiten retenciones ni reinspecciones básicas. Se amplía el bloqueo posterior sobre inventario sin borrar la liberación histórica. El rechazo cambia el estado técnico actual a REJECTED y conserva todos los eventos anteriores. Continúan pendientes sublotes, devolución/baja física, evidencia binaria, mezclas y contención de montajes.

## Sprint 3 — mezclas por montaje

Implementación guiada por [PROCO_SPRINT3_PLAN.md](PROCO_SPRINT3_PLAN.md) y contraste previo en [SPRINT-3-DESIGN.md](SPRINT-3-DESIGN.md), base main 795f626. Numeración US-06–09 del plan de Sprint 3.

- US-06: solicitud por montaje, dos o más lotes distintos, cantidades/versiones/snapshots y vigencia explícita; combinación inmutable.
- US-07: conceptos de Lab, GEO y Calidad, más Tintorería según regla ficticia del servidor; sin umbrales normativos por fechas.
- US-08: autorización o denegación por Producción independiente, revalidación transaccional, sin reserva/consumo ni cambios de material.
- US-09: NC/bloqueo/rechazo revocan autorizaciones afectadas en la misma transacción. Levantar NC no reactiva una autorización anterior.

Entregas: diseño 7187547; solicitud/conceptos 4dcb2f5; revocación/concurrencia 2fd52d5; interfaz, regresión multiactor y documentación en la entrega de cierre. Resultados observados y alcance de pruebas en [SPRINT-3-VERIFICATION.md](SPRINT-3-VERIFICATION.md). Se prepara un PR de codex/sprint-3-mixing sin merge automático.

Siguen pendientes autoridades operativas definitivas, tolerancias reales, trazabilidad por cono/caja/set/máquina, sublotes, adjuntos binarios, reserva/consumo, montaje físico y ERP. Las reglas nuevas son hipótesis de simulación; no alteran el procedimiento aprobado.
