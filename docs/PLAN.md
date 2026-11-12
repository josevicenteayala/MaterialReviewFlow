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
| RETENIDO → pruebas/liberación | Calidad reinspecciona favorablemente y retira bloqueo previo; se exige muestra y nuevos ensayos si hubo fallo. No liberación directa desde RETENIDO. |
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
