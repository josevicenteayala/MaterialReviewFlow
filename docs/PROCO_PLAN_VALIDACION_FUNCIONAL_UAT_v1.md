# PROCO SIM-0.2 — Plan de validación funcional con las áreas (UAT v1.0)

**Estado:** propuesta para taller de validación; **no** es aprobación operativa de PROCO.  
**Proyecto:** MaterialReviewFlow — https://github.com/josevicenteayala/MaterialReviewFlow  
**Base revisada:** rama `main`, commit `91688b6d6d2f7b547802d708c9429b2ebc6cc02f` (PR #5, Sprint 6), 13-11-2026.  
**Documentos fuente:** `docs/requirements/PROCO_Especificacion_MVP_Simulador_v0.1.md`, `docs/requirements/PROCO_Procedimiento_Simulacion_SIM-0.2.pdf`, `docs/SPRINT-2..6-*`, `docs/API.md`, `README.md`.  
**Advertencia:** en esta versión, cantidades, criterios de ensayos, OLA y autorizaciones son parámetros o decisiones ficticios; el software no mueve material físicamente, no emite comunicaciones a proveedores ni opera ERP.

## A. Propósito y criterio de éxito

Validar con representantes de **Almacén, Calidad, Laboratorio Hilos, Tejeduría, Producción, GEO, Tintorería y Abastecimiento** que los flujos y decisiones que representa el simulador son entendibles, coherentes y adecuados como punto de partida para un procedimiento de PROCO. La revisión debe establecer **qué reproduce correctamente el procedimiento**, **qué es hipótesis de simulación**, **qué requiere aprobación de negocio** y **qué debe cambiar**.

**No confundir dos puertas de decisión:**
1. **UAT del simulador:** las áreas pueden utilizarlo para analizar el procedimiento y demostrar S01–S10 con datos ficticios.
2. **Go-live operativo:** **fuera de alcance** por ahora; requiere definición y aprobación de reglas, identidad/seguridad, evidencias, datos, operación física e integraciones.

## B. Sesiones y responsabilidades propuestas

| Sesión | Duración sugerida | Participantes | Objetivos |
|---|---:|---|---|
| **Taller 1: Recepción, calidad y decisión técnica** | 90 min | Almacén, Calidad, Laboratorio Hilos, Abastecimiento, facilitador | S01–S05, S03, criterios técnicos, retenciones, rechazo y discrepancias |
| **Taller 2: Mezclas, montajes e incidencias** | 90 min | Tejeduría, Producción, GEO, Tintorería, Calidad, Laboratorio, Abastecimiento | S06–S10, aprobación, revocación, tareas, OLA, comunicaciones y cierre |
| **Cierre de decisiones** | 45–60 min | Responsables designados de todas las áreas | Ratificar o rechazar reglas provisionales, clasificar hallazgos y acordar siguiente fase |

**Facilitador:** controla tiempo, muestra escenario y registra discrepancias sin decidir por los procesos.  
**Responsable de negocio por escenario:** declara si el flujo es adecuado, sugiere corrección y firma el resultado.  
**Equipo técnico:** muestra trazas, captura evidencias, distingue bug de regla no ratificada; no altera los datos de demostración para ocultar fallas.  
**Calidad:** custodia la matriz de decisiones y trazabilidad; no sustituye la autoridad de Abastecimiento, Laboratorio o Producción.

### Preparación antes del taller

- Tomar SHA exacto de `main`, confirmar CI verde de ese mismo SHA, versiones V1–V7, puertos e integridad de los volúmenes.
- Crear **ambiente aislado** para UAT (datos sintéticos) y conservar la demo de desarrollo original. Identificar el proyecto por el SHA probado. El entorno `proco-uat-91688b6` ya está preparado: para retomarlo, seguir el comando `start` de [UAT-READINESS.md §3](UAT-READINESS.md#3-entorno-preparado-y-aislamiento). El siguiente ejemplo documenta su creación original desde una copia limpia del repositorio; usarlo **solo si el proyecto/volumen todavía no existen y los puertos están libres**:

  ```bash
  git switch --detach 91688b6d6d2f7b547802d708c9429b2ebc6cc02f
  DB_PORT=55442 BACKEND_PORT=18097 FRONTEND_PORT=18098 docker compose -p proco-uat-91688b6 up -d --build
  ```

  Abrir http://localhost:18098. El proyecto `proco-uat-91688b6` usa el volumen `proco-uat-91688b6_proco-data`, distinto de las demos anteriores. **Para otro taller o repetición desde cero sobre el mismo SHA**, elegir un nombre aún no utilizado, por ejemplo `proco-uat-91688b6-taller-02`, y puertos libres; comprobar antes que tampoco exista su volumen. Para otro commit, cambiar también el SHA del nombre y fijar ese commit. El nombre con SHA no evita reutilización entre dos talleres del mismo commit. `start` conserva los datos del entorno existente; `up` sobre el mismo nombre también puede reutilizarlos, no crea una base limpia. No ejecutar `docker compose down -v` ni borrar volúmenes existentes. Si se repiten escenarios que consumen saldo de OC, usar **un entorno UAT nuevo** o identificadores/lotes y saldo comprobados; no borrar datos para simular reversibilidad. Un proyecto nuevo solo incorpora las cinco semillas estándar: los fixtures preparados adicionales se crean por UI/API según el reporte.
- Validar los usuarios sintéticos de `SimulationData`: `almacen`, `calidad`, `calidad2`, `tecnico`, `aprobador`, `tejeduria`, `produccion`, `geo`, `tintoreria`, `abastecimiento`, `abastecimiento2`. Una persona puede representar varias áreas cambiando de usuario, pero se deben mantener las identidades distintas donde aplique segregación.
- Confirmar que la aplicación es accesible solo en `localhost` (Docker Compose expone servicios en `127.0.0.1`). Para varios participantes, realizar una demostración facilitada o diseñar **aparte** una configuración segura multiusuario; no publicar puertos directamente en la LAN.
- Preparar evidencias con formato `UAT-<ID>-<fecha>-<rol>`, registros de lotes/mezclas/NC/casos y capturas anonimizadas/sintéticas. No incorporar información real de proveedores, trabajadores o producción.

## C. Matriz de aceptación funcional S01–S10

**Resultado obligatorio por fila:** `APROBADO / APROBADO CON OBSERVACIONES / RECHAZADO / NO EJECUTADO`; responsable, evidencia, defecto o decisión vinculada. El PASS de un test de CI **no** reemplaza la aceptación del área.

| ID | Demostración / actor conductor | Comportamiento observable y evidencia requerida | Área que valida |
|---|---|---|---|
| **S01** | Recepción normal: `almacen` → `calidad` → `tecnico` → `aprobador` → `almacen` | Nuevo lote `UNDER_VERIFICATION/QUARANTINE`; muestra y ensayo; autorización de Lab distinta del técnico; `RELEASED/AVAILABLE` solo tras Almacén; línea de tiempo completa | Almacén, Calidad, Laboratorio |
| **S02** | Etiqueta o documentación incompleta: `calidad` / `almacen` | Retención, inventario `BLOCKED`, motivo y evidencia; no liberación automática; tratamiento y reevaluación explícitos. **Nota:** la simulación no registra fotografía real de etiqueta ni cono | Calidad, Almacén |
| **S03** | Diferencia OC–documento–conteo: `almacen` → `calidad` si aplica → `abastecimiento` → `almacen` | Los valores documental, observado y aceptado se distinguen; autorización comercial independiente; cero/saldo insuficiente/excedente; un lote solo por cantidad aceptada en `QUARANTINE`; se conserva remanente simulado | Almacén, Abastecimiento, Calidad |
| **S04** | Ensayo fuera de especificación: `tecnico` → `calidad` | Resultado ficticio no conforme retiene y bloquea, crea/gestiona NC; no hay liberación ni disponibilidad mientras exista bloqueo | Laboratorio, Calidad |
| **S05** | Reensayo favorable después de NC: `calidad` → `tecnico` → `aprobador` → `calidad` / `almacen` | Evidencia y resultado nuevo sin borrar el anterior; bloqueo persiste hasta levantamiento formal; liberación/disponibilidad separadas | Laboratorio, Calidad, Almacén |
| **S06** | Dos lotes liberados sin permiso de mezcla: `tejeduria` | Sistema impide iniciar montaje sin autorización específica; una liberación técnica no equivale a permiso de mezcla | Tejeduría, Producción |
| **S07** | Mezcla de lotes con fechas/referencias distintas: `tejeduria` → `aprobador` (Lab) → `geo` → `calidad` → `produccion` | Conceptos obligatorios y aprobación por montaje; evidencia, vigencia y lotes exactos; **no** aplicar 30/60 días como tolerancias reales | Producción, Laboratorio, GEO, Calidad |
| **S08** | Riesgo tintóreo: `tintoreria` | Para referencia DEMO marcada, Tintorería es obligatoria; concepto desfavorable bloquea aprobación; registrar criterios de afinidad reales que faltarían | Tintorería, Laboratorio, Producción |
| **S09** | NC después de iniciar montaje: `tejeduria` → `tintoreria` / `calidad` → todas las áreas con tareas → `produccion` | NC deja lote `BLOCKED`, mezcla `REVOKED`, ejecución `SUSPENDED`; tareas separadas por área con acuse/cierre y evidencia; cierre posterior como `CLOSED_WITH_INCIDENT`, no `COMPLETED` normal; no reactivación automática | Producción, Tejeduría, GEO, Calidad, Almacén, Tintorería |
| **S10** | Rechazo técnico con disposición RETURN_TO_SUPPLIER: `calidad` → `abastecimiento` → `abastecimiento2` | Lote `REJECTED/BLOCKED`, caso comercial por NC, contacto/respuesta/disposición y cierre administrativo independiente; tareas Procurement vinculadas si existen; **no se afirma devolución física ni ERP** | Calidad, Abastecimiento, Almacén |

### Pruebas negativas transversales durante los talleres

1. Usar una identidad sin permiso para liberar, autorizar mezcla, modificar un OLA o cerrar el expediente de un proveedor. Debe denegarse y no alterar estado.
2. Intentar resolver un bloqueo con el mismo usuario que originó una decisión que requiere segregación.
3. Forzar cambios concurrentes de saldo de OC o de versión; no puede quedar un lote con cantidad no autorizada ni doble aceptación.
4. Intentar ejecutar mezcla revocada, reabrir rechazo, completar un montaje suspendido como producción normal, o aceptar una tarea desde otra área.
5. Desconectar antes de confirmar respuesta a un POST; el reintento debe reutilizar cuerpo y clave idempotente, sin duplicados.
6. Consultar dashboard/exportación y verificar que no modifican expedientes, no presentan OLA DEMO como aprobados ni exponen contraseñas.

## D. Decisiones de negocio que el software **no** debe presumir aprobadas

| Código | Decisión abierta | Situación de simulación | Quién debe validarla | Criticidad |
|---|---|---|---|---|
| DEC-01 | Autoridad formal de liberación técnica, retención, rechazo y retiro de bloqueo | Lab / Calidad / Almacén separados, según flujo propuesto | Calidad, Laboratorio, Almacén | Alta |
| DEC-02 | Diferencia de cantidad: ¿en qué casos Calidad debe emitir concepto? | Solo si el expediente tiene `technicalFinding=true`; diferencias aritméticas solas no lo exigen | Calidad, Almacén, Abastecimiento | Alta |
| DEC-03 | Regla contractual de aceptación parcial, exceso y tolerancia contra OC | Sin tolerancias; aceptado ≤ observado y ≤ saldo | Almacén, Abastecimiento | Alta |
| DEC-04 | Granularidad de trazabilidad y gestión de cantidad | Lote completo/kg; no sublote, cono, caja, set o máquina | Calidad, Tejeduría, Almacén | Alta |
| DEC-05 | Métodos, límites y métodos de ensayo por referencia | Rangos ficticios SIM-1 | Laboratorio, Calidad | Alta |
| DEC-06 | Compatibilidad de lotes y obligatoriedad del concepto de Tintorería | Reglas MIX-SIM-1 manuales y por referencia DEMO | Tejeduría, Producción, GEO, Tintorería, Laboratorio | Alta |
| DEC-07 | Autoridad de suspensión y destino de montaje afectado | Suspensión automática lógica; no reinicio; `CLOSED_WITH_INCIDENT` documental | Producción, Tejeduría, GEO, Calidad | Alta |
| DEC-08 | OLA/SLA y calendario | Valores DEMO en minutos corridos UTC, no calendario laboral | GEO, Calidad, responsables de área | Media |
| DEC-09 | Rechazo y proveedor: ¿puede cerrarse administrativamente si respuesta es `NO_RESPONSE` o disputa? | Se exige plan/tratamiento documentado; no movimiento físico | Abastecimiento, Calidad | Alta |
| DEC-10 | ¿Qué representa aceptación/rechazo de proveedor y evidencia exigible? | Referencia de comunicación textual, sin email/envío ni anexo binario | Abastecimiento, Calidad | Media |
| DEC-11 | Separación identidad/roles y acceso a expedientes | Usuarios ficticios HTTP Basic; todos los usuarios consultan datos sintéticos | Seguridad, responsables de áreas | Alta para uso real |
| DEC-12 | Posibilidad de integrar sistema con ERP, inventario físico, archivo y SSO | No implementado | Operaciones, TI, Calidad | Alta para uso real |

**Registro de decisión por cada DEC:** decisión propuesta, decisión del taller, respaldo/regla, nombre y cargo del aprobador, fecha, alcance y versión de procedimiento; si no existe acuerdo usar **PENDIENTE**, no asumir aprobación.

## E. Ficha para cada ejecución UAT

```text
UAT ID:
Escenario Sxx / objetivo:
Fecha-hora y zona horaria:
SHA de main y versión de base (Flyway):
Ambiente Compose / URL:
Facilitador:
Participante / área / usuario simulado:
Datos iniciales (OC, lote, mezcla, montaje, NC, UUID):
Pasos realmente ejecutados:
Resultado esperado:
Resultado observado:
Estado: APROBADO | APROBADO CON OBSERVACIONES | RECHAZADO | NO EJECUTADO
Evidencias: captura/IDs/timeline/JSON/CSV (datos sintéticos):
Defecto técnico (BUG-__) o decisión no ratificada (DEC-__):
Severidad y efecto operativo:
Responsable de seguimiento y fecha:
Validación/aceptación del área:
```

**Criterio para clasificar:** si contradice una regla **ya acordada**, es defecto; si la regla no está ratificada, es decisión funcional pendiente; si está expresamente fuera de alcance, es brecha para evolución y no fallo del MVP.

## F. Agenda concreta de facilitación

**Taller 1 (90 min):** 0–10 contexto y alcance ficticio; 10–30 S01/S02; 30–55 S03 (faltante, exceso, aceptación parcial y revisión de saldo); 55–75 S04/S05; 75–90 registrar DEC-01..05 y priorizar.

**Taller 2 (90 min):** 0–10 recap de decisiones; 10–30 S06/S07; 30–45 S08; 45–70 S09 (NC, revocación, suspensión y acuses); 70–80 S10 comercial; 80–90 DEC-06..10 y riesgos.

**Cierre (45–60 min):** revisar matriz S01–S10, decisiones DEC-01..12, clasificación de defectos, riesgo de confundir evidencia textual con prueba física y recomendación. Si hay demasiados pendientes críticos, programar taller focalizado por área.

## G. Criterios de salida y decisión go/no-go

### Go para **validar el simulador como herramienta de análisis**

- Los 10 escenarios S01–S10 se ejecutaron o se documentó claramente por qué no, con responsables y evidencias.
- Cero defectos **bloqueantes o críticos** abiertos en transiciones, permisos, trazabilidad o integridad; si se descubre alguno, corregir y repetir el escenario afectado.
- Las decisiones críticas DEC-01..07 y DEC-09 están **ratificadas** o se indica explícitamente que el uso sigue limitado a simulación sin representar el proceso aprobado.
- CI del SHA probado está verde (Maven, TypeScript/React, Playwright y persistencia); los resultados manuales no se sustituyen por CI.
- Se entrega un acta de aceptación **del simulador**, con observaciones y restricciones.

### No-go para **sistema productivo** hasta resolver adicionalmente

- Granularidad real de material, criterios técnicos y tolerancias; definición completa de roles, gobernanza y OLA aprobados.
- Autenticación corporativa, autorización, manejo de datos reales, seguridad y continuidad operacional.
- Movimientos de inventario físico, integración ERP/proveedores, evidencias documentales reales, y manejo transaccional de consumo/reservas si aplica.
- Procedimientos de contingencia, soporte, QA de operación, cumplimiento y validación formal por las áreas y TI.

## H. Próximo entregable tras los talleres

**Acta de validación PROCO SIM-0.2**: resumen ejecutivo, matriz completada S01–S10, decisiones ratificadas/no ratificadas, defectos observados (con prioridad), recomendaciones y un backlog de **correcciones acotadas**. Decidir después si se requiere **Sprint 7 de remediación/UAT**. No lanzar por defecto una nueva fase técnica de sublotes, ERP o integración sin demostrar su necesidad y aprobación.

## I. Verificación técnica básica para preparar el taller (Codex)

Encargar a Codex solo un **pre-flight de lectura**, sin modificar `main`:

> Revisa main de MaterialReviewFlow (SHA 91688b6) y este plan UAT. Verifica que GitHub Actions del SHA esté verde; confirma las migraciones V1–V7, rutas API, usuarios sintéticos, acceso local y que S01–S10 tengan fixtures/recorridos disponibles. Propón un entorno Docker Compose aislado para taller sin tocar el volumen existente. Produce una matriz de mapeo escenario → usuario → ruta/UI → dato demo → evidencia, e identifica decisiones de negocio pendientes. No inventes datos reales, no cambies código ni borres volúmenes. Si hace falta una mejora indispensable para reproducir escenarios, proponla en un PR separado y explícito, no en main.
