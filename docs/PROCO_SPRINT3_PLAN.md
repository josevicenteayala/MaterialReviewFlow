# PROCO — Sprint 3: autorización de mezclas por montaje

**Estado:** propuesta de implementación para simulación, pendiente de aprobación funcional.  
**Base inspeccionada:** `MaterialReviewFlow/main`, commit `795f6267273b294bc2dd0794a07a87a562f96fdc` (12 de noviembre de 2026).  
**No representa un procedimiento operativo aprobado por PROCO.**

## 1. Objetivo

Simular la solicitud, evaluación, autorización, denegación y revocación de mezclas de hilos o elastanos para **un montaje específico**. La liberación técnica del lote, su disponibilidad en inventario y la autorización de mezcla continuarán siendo controles independientes. Los criterios de compatibilidad serán **configuración ficticia/versionada o evaluación humana**, nunca tolerancias productivas inventadas.

## 2. Punto de partida comprobado

El Sprint 2, integrado en `main`, incorporó expedientes de no conformidad, dictámenes, bloqueos persistentes, levantamiento formal, rechazo terminal y auditoría. La ejecución CI de `main` 43219437177 terminó exitosamente. El control de un lote utiliza `qualityState`, `inventoryState`, `activeHolds` y `version`.

Fuentes de requisitos: `docs/PLAN.md`, `docs/requirements/PROCO_Especificacion_MVP_Simulador_v0.1.md` (sobre todo S06–S08 y escenario de bloqueo S09), `docs/requirements/PROCO_Diagramas_Simulacion_SIM-0.2.md` (D1, D3, D5), `docs/SPRINT-2.md`.

## 3. Historias priorizadas

### US-06 — Solicitar mezcla (P0)
- Actor: `PRODUCTION_REQUESTER` (Tejeduría).
- Registrar identificador de montaje, **dos o más lotes diferentes** con cantidades positivas y evidencia documental.
- Guardas: cada lote `RELEASED`, `AVAILABLE`, cero holds activos, sin rechazo. No exceder cantidad registrada de cada lote.
- Conservar lista exacta, cantidades, versiones verificadas, motivo, evidencia y reglas de simulación aplicables.
- Cambiar lotes, cantidades o montaje exige nueva solicitud; no editar silenciosamente una combinación pendiente/autorizada.

### US-07 — Conceptos técnicos por área (P0)
- Roles: `LAB_APPROVER`, `GEO`, `QUALITY` y `DYEING` cuando la regla sintética explícita lo requiera.
- Para cada concepto: resultado favorable/desfavorable, actor, motivo, evidencia, tiempo, versión de solicitud y versión/regla de compatibilidad.
- Política candidata (a validar): Lab + GEO + Calidad obligatorios; Tintorería solo si una bandera de riesgo tintóreo exige su participación.
- **No aplicar automáticamente** los rangos propuestos de diferencia de fechas de 30/60 días como tolerancias aprobadas.
- Cada área debe poder emitir una evaluación independiente; toda evaluación desfavorable impide la autorización.

### US-08 — Autorizar o denegar una mezcla (P0)
- Actor: `PRODUCTION_APPROVER`, identidad distinta de la del solicitante.
- Revalidar los lotes y todos los conceptos obligatorios **en la transacción de la decisión**.
- `AUTHORIZED` solo para el montaje y la combinación exacta; no equivale a consumo o reserva de inventario.
- Si falta un concepto, su versión es antigua o un lote ya no es elegible: denegar la transición con razón verificable.
- `DENIED` terminal para la solicitud; una revisión posterior requiere una nueva solicitud.

### US-09 — Revocación por bloqueo o rechazo (P0)
- Al abrir una NC, crear un bloqueo o rechazar un lote utilizado en una mezcla autorizada, invalidar/revocar las autorizaciones afectadas en **la misma transacción**.
- Conservar decisión histórica, auditoría, actor, causa y timestamp; nunca borrar la autorización anterior.
- Al consultar o usar una autorización, comprobar de nuevo que los lotes están elegibles; un estado histórico `AUTHORIZED` no basta para permitir uso.
- Levantar posteriormente la NC **no reactiva automáticamente** una mezcla revocada: se requiere nueva solicitud y reevaluación.
- El consumo real, reserva y control de montaje físico quedan fuera de este Sprint 3.

## 4. Modelo de dominio candidato

- `mix_request`: id, montage_code, status (`UNDER_REVIEW`, `AUTHORIZED`, `DENIED`, `REVOKED`), requester, approver, razón, evidencia, criterio/regla de simulación, version, created_at, decided_at, revoked_at.
- `mix_request_lot`: request_id, lot_id, quantity, lot_version_at_request, snapshot de referencia/fecha/lote/set si está disponible, orden estable.
- `mix_assessment`: id, request_id, role, author, outcome, reason, evidence, based_on_mix_version, created_at. Append-only.
- `mix_event`: auditoría append-only: estado previo/nuevo, rol, actor, motivo, evidencia, correlación y timestamp.
- Catálogo de reglas de simulación: marca explícita de necesidad de dictamen de Tintorería y versión de criterio.

**Precisión importante:** los campos de lote disponibles en el MVP no incluyen información por cono/caja, máquina, puesto o set. Documentar su ausencia y no fingir validación de afinidad intra-lote en el Sprint 3. La granularidad fina permanece pendiente.

## 5. Invariantes transaccionales

1. No se autoriza una mezcla si algún lote no está liberado/disponible o tiene bloqueos activos.
2. La autorización está ligada al conjunto **exacto** de lotes, cantidades y montaje.
3. El solicitante no puede ser el aprobador final.
4. Todos los conceptos obligatorios deben existir, ser favorables y estar vigentes.
5. Ni la solicitud ni la autorización cambian cantidades; no hay reserva o consumo de inventario.
6. Una NC o bloqueo sobre cualquiera de los lotes revoca cualquier autorización todavía vigente que dependa de él.
7. La revocación es irreversible para la autorización anterior; crear una nueva solicitud tras resolver el incidente.
8. Versionado optimista, idempotencia con `Idempotency-Key`, auditoría de mutaciones e intentos de negocio rechazados.
9. Evitar deadlocks: bloquear las filas de los lotes en **orden determinista**, y definir un orden común de adquisición de locks entre los flujos de NC, disponibilidad y mezcla. Probar carreras NC vs aprobación.
10. Migración Flyway `V4` **aditiva**; no reescribir V1–V3 ni alterar datos existentes.

## 6. API candidata (ajustar después del diseño)

| Método | Ruta | Uso |
|---|---|---|
| POST | `/api/mix-requests` | Crear solicitud por montaje y lotes |
| GET | `/api/mix-requests` | Bandeja/listado |
| GET | `/api/mix-requests/{id}` | Expediente |
| GET | `/api/mix-requests/{id}/timeline` | Eventos y evidencias |
| POST | `/api/mix-requests/{id}/assessments` | Concepto técnico independiente |
| POST | `/api/mix-requests/{id}/decision` | Autorizar o denegar |

Todas las mutaciones deben ejecutar guardas RBAC en backend, validar versión y usar idempotencia. No confiar exclusivamente en los controles de frontend.

## 7. Pantallas

- Bandeja de mezclas con filtros por área, estado y montaje.
- Solicitud con selector de lotes elegibles, cantidades, motivación y evidencia.
- Revisión por Laboratorio, GEO, Calidad y Tintorería cuando aplique.
- Decisión de Producción, con explicación de conceptos pendientes o desfavorables.
- Historial integral y visualización de autorizaciones revocadas por NC.
- Aviso visible: **«SOLO SIMULACIÓN — una autorización no reserva ni consume existencias»**.

## 8. Pruebas de aceptación y riesgos

| ID | Prueba | Resultado esperado |
|---|---|---|
| S3-01 | Dos lotes liberados y disponibles sin solicitud aprobada | No existe autorización de mezcla |
| S3-02 | Solicitud con un lote bloqueado, retenido o rechazado | Rechazada por regla de negocio |
| S3-03 | Falta dictamen GEO/Lab/Calidad requerido | No autoriza |
| S3-04 | Escenario que requiere concepto DYEING; no existe | No autoriza |
| S3-05 | Concepto desfavorable | No autoriza |
| S3-06 | Usuario solicitante intenta aprobar | 403/422, sin cambios |
| S3-07 | Aprobación correcta con conceptos vigentes | `AUTHORIZED` solo para montaje exacto |
| S3-08 | NC sobre lote de mezcla autorizada | Bloqueo y `REVOKED` atómicos, con historial |
| S3-09 | Levantar NC después de revocar | Sigue `REVOKED`; requiere nueva solicitud |
| S3-10 | Aprobación concurrente con apertura de NC | No persiste autorización utilizable del lote bloqueado |
| S3-11 | Reintento con misma clave y cuerpo | No duplica solicitud, concepto o decisión |
| S3-12 | Upgrade V3 → V4 | Datos y auditoría previos intactos |
| S3-13 | Regresión completa Sprint 1 y 2 | Sin fallos |
| S3-14 | Navegador multiactor con Docker Compose | Escenarios demostrables de extremo a extremo |

**Riesgo principal:** inconsistencia entre estado de mezcla y bloqueo posterior. Mitigar mediante orden global de locks, transacción atómica de revocación y revalidación de elegibilidad en cada acción de uso.

## 9. Entregas de implementación

1. Diseño de dominio, roles, esquema V4, diagramas y contrato API (sin modificar producción).
2. Backend para solicitar y evaluar mezclas, con unit/integration tests sobre PostgreSQL aislado.
3. Aprobación/denegación, seguridad, concurrencia e idempotencia.
4. Revocación integrada al módulo NC; regresión de Sprint 2.
5. UI multiactor, datos sintéticos, Playwright, CI y documentación.

**Definition of Done:** S06–S08 reproducibles, revocación automática y atómica ante NC/bloqueo, identidad y auditoría verificables, CI verde, Compose local, migraciones sin pérdida de datos y pull request abierto para revisión. No efectuar merge automático.

## 10. Restricciones y decisiones pendientes

- Confirmar autoridad real de Producción/Tejeduría para aprobar mezclas y segregación de solicitante/aprobador.
- Confirmar cuándo Tintorería debe participar y quién valida sus criterios.
- Confirmar tolerancias por material, fechas de fabricación, proveedor y compatibilidad de tintura.
- Confirmar si la unidad de control real es lote/caja/cono y los datos faltantes para trazabilidad.
- No introducir inventario reservado, consumo, integración ERP, emails corporativos, sublotes ni montajes físicos en este sprint.
- Mantener marcadas todas las reglas aquí propuestas como **hipótesis de simulación**.
