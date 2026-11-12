# PROCO · Especificación inicial del MVP del simulador

**Versión:** MVP-0.1 · **Fecha:** 2026-11-11  
**Estado:** Borrador técnico para prototipo. No corresponde a una aplicación operativa ni altera el procedimiento aprobado.  
**Fuente principal:** «Procedimiento para la recepción, inspección, aprobación y liberación de hilos y elastanos PROCO INC», versión 01.  
**Reglas funcionales propuestas:** «PROCO_Procedimiento_Simulacion_SIM-0.2».

## 1. Problema y resultado esperado

Una recepción pasa por Almacén, Calidad, Laboratorio y, eventualmente, Producción/Tejeduría, GEO y Tintorería. No hay que confundir la liberación técnica de un lote con su disponibilidad actual ni con la autorización de mezclarlo con otro lote. El MVP permite que usuarios ficticios de cada rol ejecuten tareas y decisiones en un único historial verificable.

**Demostración de éxito (escenario feliz):** crear recepción → comprobar etiquetas → registrar muestras y pruebas → autorizar liberación → habilitar disponibilidad → solicitar mezcla de dos lotes → obtener conceptos de compatibilidad → autorizar montaje → consultar línea de tiempo y evidencias. Debe ser igualmente sencillo demostrar un rechazo y el bloqueo por una NC sobrevenida.

### En alcance para MVP

- Usuarios de prueba y control de acceso por rol, bandeja de tareas y auditoría.
- Registro de proveedores ficticios, referencias, órdenes de compra y recepciones con lotes/sublotes.
- Inspección inicial, muestras y resultados de Laboratorio con criterios configurables por referencia.
- Decisiones de retención, liberación, rechazo; disponibilidad y bloqueos independientes.
- Solicitudes de mezcla por combinación exacta de lotes y montaje; conceptos de otras áreas y aprobación de Producción/Tejeduría.
- Casos de NC, contención posterior al montaje, avisos internos y dashboard básico.
- Diez escenarios de pruebas end-to-end (S01–S10), con datos sintéticos y eventos trazables.

### Fuera de alcance inicial

ERP/inventario real; envío a proveedores reales; lectura de máquinas o ensayos automatizados; integración real con ClickUp/email; autenticación corporativa SSO; optimización del muestreo; compatibilidad predictiva por IA; aprobaciones legales o certificaciones.

## 2. Diseño de dominio: tres controles desacoplados

| Control | Entidad propietaria | Estados de simulación | Guarda principal |
|---|---|---|---|
| Calidad técnica | `MaterialLot` / `QualityDecision` | `RECEIVED`, `UNDER_VERIFICATION`, `UNDER_TEST`, `HELD`, `RELEASED`, `REJECTED` | Dictamen técnico, trazabilidad y aprobación autorizada |
| Disponibilidad | `InventoryBalance` + `InventoryHold` | `QUARANTINE`, `AVAILABLE`, `BLOCKED`, `REMOVED` | Sin bloqueos, material liberado, cantidad positiva |
| Autorización de mezcla | `MixRequest` | `NOT_REQUESTED`, `UNDER_REVIEW`, `AUTHORIZED`, `DENIED`, `REVOKED` | Cada lote liberado y disponible, conceptos favorables, montaje y vigencia |

**Nota de diseño:** tras una NC posterior, el sistema conserva la decisión histórica de liberación (no la borra) pero marca `BLOCKED` y suspende/revoca las autorizaciones de mezcla relacionadas. La decisión técnica puede ser reemplazada por una nueva decisión versionada tras reevaluación. Nunca se reescribe el historial.

## 3. Roles y permisos de negocio

| Rol (RBAC) | Puede crear o editar | Puede aprobar | No puede |
|---|---|---|---|
| `WAREHOUSE` | Recepciones, ubicación, comprobación física y bloqueo ejecutado | Confirmación de disponibilidad condicionada | Liberación técnica o mezcla |
| `QUALITY` | Inspección documental, NC, retención, propuestas de disposición | Retención formal y rechazo formal | Sustituir aprobación técnica de Laboratorio |
| `LAB_TECH` | Muestras, ensayos y resultados | No se autoaprueba ensayos críticos | Liberación final sin revisión |
| `LAB_APPROVER` | Dictamen sobre pruebas | Liberación técnica | Autorizar mezcla |
| `PRODUCTION_REQUESTER` | Solicitud de mezcla/montaje | No la propia aprobación si rige segregación | Alterar evidencia de Laboratorio |
| `PRODUCTION_APPROVER` | Evaluación final de montaje | Autorización/denegación de mezcla | Liberar material ni remover bloqueo de Calidad |
| `GEO` | Conceptos, seguimiento e incidentes | Concepto técnico, no decisión formal de rechazo | Borrar NC |
| `DYEING` | Conceptos de afinidad tintórea/tono | Aval técnico obligatorio cuando aplique | Autorizar mezcla final |
| `PROCUREMENT` | Gestión de NC con proveedor | Decisión comercial de devolución después de rechazo | Liberación técnica |
| `SIM_ADMIN` | Datos semilla, parámetros y configuración del entorno | No debe saltar controles de dominio | Aprobar decisiones por ser administrador |

**Precisión:** esta tabla define permisos propuestos para el ejercicio, no cargos asignados oficialmente por PROCO. Para el prototipo, un solo participante puede asumir distintos perfiles ficticios de usuarios distintos, pero la API debe registrar qué usuario simulado ejecutó cada acción y aplicar las reglas por identidad.

## 4. Modelo lógico inicial

- `supplier(id, code, name)`
- `material_specification(id, reference, type, composition, version, critical_test_rules_json, active)`
- `purchase_order(id, supplier_id, reference, expected_quantity)`
- `receipt(id, purchase_order_id, received_at, received_by, status, version)`
- `material_lot(id, receipt_id, supplier_lot, production_date, set_code, machine_code, quantity, unit, quality_state, version)`
- `lot_unit(id, material_lot_id, unit_type, label_code, quantity)`; utilizable para sublotes o conos cuando aplique.
- `inspection(id, material_lot_id, performed_by, performed_at, outcome, checklist_json)`
- `sample(id, material_lot_id, sample_code, received_at, traceability_ok)`
- `lab_test(id, sample_id, test_type, parameter, result_value, unit, rule_version, performed_by, result_status)`
- `quality_decision(id, lot_id, type, outcome, approved_by, approved_at, reason, evidence_refs, previous_decision_id)`
- `inventory_balance(id, lot_unit_id, quantity_available, status, version)`
- `inventory_hold(id, lot_unit_id, reason, triggered_by, opened_at, ratified_by, closed_at, status)`
- `mix_request(id, mount_id, status, requested_by, decided_by, rule_version, valid_until, version)`
- `mix_item(id, mix_request_id, lot_unit_id, quantity)`
- `mix_assessment(id, mix_request_id, area, assessor, outcome, explanation, evidence_refs)`
- `production_mount(id, mount_code, machine, status, started_at)`
- `non_conformity(id, scope_type, scope_id, severity, status, opened_by, disposition_by, resolution)`
- `evidence(id, kind, object_uri_or_storage_key, checksum, submitted_by, created_at)`
- `workflow_task(id, workflow_id, assigned_role, assigned_user, state, due_at, completed_at)`
- `notification(id, event_id, intended_role, status, acknowledged_by, acknowledged_at)`
- `audit_event(id, aggregate_type, aggregate_id, event_type, actor_id, actor_role, occurred_at, from_state, to_state, reason, correlation_id, payload_json)`

**Integridad mínima:** IDs estables, claves foráneas, control de versiones optimista, suma de cantidades de sublotes ≤ cantidad recibida, cantidad de mezcla ≤ cantidad disponible; no se autoriza mezcla cuando algún lote está `BLOCKED`, `HELD` o `REJECTED`; no doble aprobación de mismo evento con versión anterior. La granularidad definitiva (lote/caja/cono) debe validar el negocio antes de implementar la base.

## 5. API candidata

Las rutas son una propuesta de contrato, no un OpenAPI final.

| Acción | Método y ruta | Roles principales |
|---|---|---|
| Alta de recepción | `POST /api/receipts` | WAREHOUSE |
| Listar/filtrar recepciones | `GET /api/receipts` | Usuarios autorizados |
| Registrar inspección | `POST /api/lots/{id}/inspections` | QUALITY |
| Enviar muestra | `POST /api/lots/{id}/samples` | WAREHOUSE / QUALITY |
| Reportar resultados | `POST /api/samples/{id}/test-results` | LAB_TECH |
| Decidir liberación | `POST /api/lots/{id}/release-decisions` | LAB_APPROVER |
| Iniciar retención | `POST /api/lots/{id}/holds` | Roles de detección autorizados |
| Ratificar/cerrar retención | `POST /api/holds/{id}/decisions` | QUALITY |
| Formalizar rechazo | `POST /api/lots/{id}/rejections` | QUALITY |
| Confirmar disponibilidad | `POST /api/lots/{id}/availability` | WAREHOUSE |
| Solicitar mezcla | `POST /api/mix-requests` | PRODUCTION_REQUESTER |
| Concepto de mezcla | `POST /api/mix-requests/{id}/assessments` | LAB_APPROVER / GEO / DYEING / QUALITY |
| Decidir mezcla | `POST /api/mix-requests/{id}/decision` | PRODUCTION_APPROVER |
| Abrir NC | `POST /api/nonconformities` | Roles de detección autorizados |
| Consultar historial | `GET /api/lots/{id}/timeline` | Roles autorizados |
| Bandeja por rol | `GET /api/tasks?role=...` | Según usuario |

**Contrato de error:** `403` por rol insuficiente; `409` por versión o estado incompatibles; `422` por reglas de negocio incumplidas; toda operación sensible debe aceptar `Idempotency-Key` o identificador de comando para evitar duplicidad por reintentos.

## 6. Pantallas (orden de construcción)

1. **Tablero de simulación:** selector de caso, progreso por área, cola de tareas, alertas y leyenda «SOLO SIMULACIÓN».
2. **Recepción:** orden, proveedor, cantidad, lotes, etiquetas y checklist de Almacén.
3. **Calidad:** validación documental, hallazgos, retención, NC y disposición.
4. **Laboratorio:** bandeja de muestras, catálogo de ensayos, ingreso de resultados y aprobación independiente.
5. **Almacén:** cuarentena/disponibilidad/bloqueo y confirmación.
6. **Mezclas:** selección de varios lotes, montaje, validadores (GEO/Tintorería/Lab) y decisión de Producción.
7. **Auditoría y métricas:** vista de línea de tiempo, evidencia, evento, notificación, actor y resultados simulados.

Cada pantalla debe mostrar la **decisión pendiente**, responsable, por qué no puede avanzar y evidencia faltante; evitar mostrar únicamente un «estado» sin explicación.

## 7. Historias de usuario (orden recomendado)

| ID | Historia | Definición de terminado |
|---|---|---|
| US-01 | Como Almacén registro una recepción y sus lotes. | IDs persistentes, validación OC/cantidad, cuarentena, evento, prueba S01 inicial. |
| US-02 | Como Calidad reviso identidad y registro hallazgos. | Bloqueo por falta de datos; evidencia y tareas; S02–S03. |
| US-03 | Como Laboratorio capturo resultados y un aprobador decide. | Segregación, criterios por referencia y liberación impedida con pruebas faltantes; S04–S05. |
| US-04 | Como Almacén habilito disponibilidad solo cuando corresponde. | Estado independiente y restricciones transaccionales. |
| US-05 | Como Tejeduría solicito mezcla por montaje y lotes específicos. | No mezcla automática por liberación; S06. |
| US-06 | Como Producción evalúo conceptos y autorizo/deniego mezclas. | GEO y Tintorería consultados según material, S07–S08. |
| US-07 | Como Calidad gestiono una NC posterior y bloqueo el alcance. | Lotes, disponibilidades y mezclas vinculadas bloqueados, S09–S10. |
| US-08 | Como auditor consulto historia y evidencias de cada decisión. | Imposible modificar eventos pasados; exportación de prueba. |
| US-09 | Como coordinador consulto vencimientos y KPIs. | OLA de prueba configurables y nunca aprobación automática por plazo. |

### Criterios de aceptación imprescindibles

```gherkin
Scenario: No liberar sin todas las pruebas
  Given un lote está EN_PRUEBAS y falta un ensayo crítico
  When LAB_APPROVER intenta liberarlo
  Then la API responde 422 y el lote no cambia de estado
  And queda registro de intento rechazado con su razón

Scenario: Liberación no autoriza mezclas
  Given dos lotes técnicamente LIBERADOS y disponibles
  And no existe una autorización para mezclarlos en el montaje M-100
  When PRODUCTION_REQUESTER intenta iniciar M-100 usando ambos lotes
  Then el montaje queda bloqueado hasta aprobación de mezcla

Scenario: NC posterior revoca el uso
  Given los lotes A y B están incluidos en una mezcla autorizada
  When DYEING reporta una NC crítica del lote B
  Then B queda BLOQUEADO en el inventario simulado
  And la autorización de mezcla asociada queda REVOCADA
  And se crean tareas de contención para Calidad, Tejeduría y Almacén

Scenario: Segregación de aprobador
  Given un LAB_TECH ingresó los resultados críticos de un lote
  When ese mismo usuario intenta aprobar la liberación
  Then la API responde 403 o 422 según la política de autorización
  And no existe decisión favorable emitida
```

## 8. Arquitectura y stack de arranque

**Recomendación:** monolito modular Java + Spring Boot, frontend React/TypeScript y PostgreSQL. Justificación: el problema es principalmente un workflow transaccional con consistencia fuerte y auditoría. Microservicios, Kafka, motor BPMN e integraciones externas agregarían complejidad sin valor probado para la primera simulación.

```text
proco-simulator/
  backend/
    src/main/java/.../
      reception/       (application, domain, infrastructure, web)
      quality/
      laboratory/
      inventory/
      mixing/
      nonconformity/
      workflow/
      audit/
      security/
    src/main/resources/db/migration/
  frontend/
    src/features/{dashboard,reception,quality,laboratory,mixing,nonconformity}/
  docs/{process,adr,api,scenarios}/
  docker-compose.yml
  README.md
```

- Java 21 LTS como base conservadora, o una versión LTS posterior adoptada por el equipo; elegir versión de Spring Boot que esté soportada al iniciar.
- Spring Web, Validation, Security, Data JPA/JDBC, Flyway, Actuator; OpenAPI; Maven o Gradle.
- PostgreSQL + transacciones/locking optimista; Testcontainers + JUnit 5; Playwright para UI.
- Autenticación de demostración con usuarios de prueba y credenciales locales, contraseñas protegidas; Keycloak/SSO cuando sea necesario.
- Auditoría y notificaciones locales persistentes; eventos internos en una transacción única. Si más adelante se agrega mensajería externa, usar patrón outbox.
- Datos ficticios en ambiente aislado. No desplegar con rutas a infraestructura productiva.

**Trade-off:** un motor BPMN como Camunda/Flowable podría ayudar si hay múltiples excepciones y modelado de procesos por usuarios de negocio, pero no conviene introducirlo sin demostrar esa necesidad con el piloto.

## 9. Hoja de ruta de inicio

1. **Validación funcional:** taller corto con responsables: confirmar autoridad sobre rechazo y bloqueo, criterios técnicos por referencia, papel de Tintorería y alcance de sublotes. Registrar decisiones pendientes.
2. **Mapa de casos:** seleccionar un caso feliz, uno con retención, uno con rechazo y uno con mezcla. Preparar datos sintéticos reproducibles.
3. **Bootstrap:** repositorio, arquitectura modular, Postgres, migración inicial, CI y un ambiente local con Docker Compose.
4. **Vertical slice 1:** `CreateReceipt` → `Inspect` → `LabTest` → `Release` → `MakeAvailable`, con UI mínima y audit log.
5. **Vertical slice 2:** `RequestMix` → conceptos → `DecideMix` → autorización ligada a montaje.
6. **Vertical slice 3:** `ReportNC` → bloqueo inmediato → invalidación de mezclas → revisión y cierre.
7. **Validación multiactor:** S01–S10, pruebas de permisos y concurrencia, tiempos y evidencia; ajuste de reglas.
8. **Go/no-go:** solo después de validación del taller decidir si se evoluciona a sistema real, con integraciones y cumplimiento de requisitos corporativos.

## 10. Decisiones que bloquean un futuro sistema operativo (no bloquean la simulación)

- Quién tiene poder real de rechazo final y quién ejecuta la disposición comercial.
- Si la unidad de control es un lote completo, sublote, caja o cono.
- Tolerancias, muestreo, criticidad y métodos de ensayo por referencia (incluido elastano).
- Referencias que requieren concepto obligatorio de Tintorería.
- Matriz de concesiones, cadena de escalamiento, esquema de evidencia y conservación.
- Fuente maestra de inventario y mecanismo transaccional de bloqueo para producción.

## 11. Referencias

1. PROCO INC., Procedimiento original versión 01, especialmente pp. 1, 3, 5, 7 y 8.
2. ISO, orientación sobre información documentada de ISO 9001:2015: https://www.iso.org/files/live/sites/isoorg/files/standards/docs/en/iso_9001_2015_guidance_documented_information.pdf
3. GS1, principios CTE/KDE: https://support.gs1.org/support/solutions/articles/43000734535-what-is-the-gs1-traceability-standard-
4. OWASP, Access Controls: https://devguide.owasp.org/en/04-design/02-web-app-checklist/07-access-controls/
5. OWASP ASVS: https://asvs.owasp.org/
