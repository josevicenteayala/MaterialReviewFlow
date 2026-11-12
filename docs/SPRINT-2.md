# Sprint 2 Gestión formal de no conformidades

Plan previo a implementación. Base: main, commit 8c98096. Rama: `codex/sprint-2-nonconformities`. No se hará merge. Se inspeccionaron WorkflowService, InspectionPolicy, LaboratoryPolicy, InventoryPolicy, V1__schema.sql, WorkflowIntegrationTest y docs/PLAN.md antes de diseñar.

## Diagnóstico

Ya existen retención y reinspección básica. El defecto a corregir es el desbloqueo implícito: `inspect(conforms=true)` y un ensayo conforme escriben QUARANTINE. El inventario no tiene aún bloqueos con identidad/cierre. La UI genera una nueva Idempotency-Key en cada envío, incluso tras perder una respuesta.

## Entregas y verificación prevista

1. **Contrato y diseño**: este documento y actualización de PLAN. Commit independiente antes de modificar código.
2. **Dominio, persistencia y API**: V3 aditiva, módulo de NC y guardas de inventario; reutilizar inspección, muestra y reensayo actuales. Pruebas HTTP con PostgreSQL aislado, concurrencia y actualización de una base V2 sintética con comparación íntegra de filas existentes.
3. **UI, reintentos y revisión**: expediente NC integrado; cola de comando incierto por identidad conservando URL, cuerpo exacto y clave en sessionStorage (sin contraseña). Pruebas Playwright de pérdida de respuesta y de rechazo/bloqueo, build, Docker Compose aislado, documentación y PR abierto para revisión.

No se declarará ninguna prueba aprobada hasta ejecutarla. No se usarán los volúmenes ni bases de la demo existente para pruebas.

## Clases y tablas

| Archivo/módulo | Cambio previsto |
|---|---|
| V1 y V2 | Sin modificaciones ni cambios de checksum |
| V3__nonconformities_and_holds.sql | Tablas nuevas `non_conformity`, `nc_opinion`, `inventory_hold`; admitir REJECTED en lot; check rechazo→BLOCKED y triggers diferidos de integridad entre bloqueos e inventario |
| nonconformity | DTO validado, consulta de expediente, políticas y servicio/controller para abrir, dictaminar, levantar o rechazar |
| inventory/HoldStore | Crear bloqueos identificados, asociarlos al expediente y cerrarlos exclusivamente mediante decisión explícita |
| workflow/CommandRunner | Extraer transacción, serialización de clave, reintento y auditoría denegada para compartirlos con NC, preservando fingerprints antiguos |
| WorkflowService | Conservar BLOCKED al reinspeccionar o ensayar; registrar bloqueo en fallos, comprobar ausencia de bloqueos al liberar/disponer |
| InspectionPolicy | Reutilizar sin duplicar; no admite REJECTED ni reinspección de liberados |
| LaboratoryPolicy / InventoryPolicy | Comprobar también cantidad de bloqueos activos en la vista del lote |
| Lot / LotStore | Proyección adicional activeHolds; consulta consistente desde una sentencia SQL |
| AuditLog | Eventos nuevos SIM-2; conservar todos los SIM-1 y trigger append-only |
| SimulationData | Añadir únicamente cuenta ficticia calidad2 para revisión independiente; no actualizar usuarios/lotes existentes |
| WorkflowIntegrationTest y nuevas pruebas | Regresión adaptada a levantamiento explícito, fallos, múltiples bloqueos, roles, responsables, segregación, cierre/rechazo terminal, carreras, idempotencia y migración V2→V3 |
| frontend/src | Cliente de comandos reutilizable con reintento de cuerpo/clave congelados; panel de NC, bloqueos y decisión pendiente |
| Compose / CI / docs | Puertos parametrizables para entorno separado; ejecutar CI sobre rama/PR; API, plan y verificación actualizados |

`command_result` conserva su esquema y semántica: una clave repetida devuelve la vista actual del lote sin duplicar efectos. Comandos de NC incluyen acción e identificador de NC en su fingerprint. Cada mutación de NC también incrementa la versión del lote, que es la única precondición de concurrencia de todo su expediente.

## Decisiones de dominio

- Unidad de control: lote completo. No se añaden sublotes, montajes, consumo, adjuntos binarios ni devolución física en este sprint.
- NC: UUID, lote, origen DOCUMENTARY/LABORATORY/POST_RELEASE/OTHER, estado OPEN/CLOSED/REJECTED, responsable QUALITY, reportante, motivo/evidencia originales, dictámenes append-only y disposición final. No se permite editar dictámenes ni reabrir una NC cerrada; una nueva incidencia tiene otro ID.
- Cualquier usuario de los cuatro roles actuales puede reportar una NC; el responsable debe existir, ser QUALITY y ser distinto del reportante; una asignación que impediría la revisión independiente se rechaza al abrir. La apertura bloquea preventivamente en la misma transacción, incluso RELEASED/AVAILABLE. El estado técnico y evidencia de la liberación histórica se conservan al reportar.
- Una inspección o ensayo fallido crea un bloqueo activo sin expediente; abrir NC formaliza los bloqueos sin expediente del lote o crea uno nuevo si no existen. Bloqueos nuevos quedan independientes hasta formalizarse. Se pueden tener varias NC y bloqueos simultáneos.
- NC abiertas antes de una reinspección conservan su bloqueo; inspección y reensayo favorables nunca lo levantan. Muestras y ensayos pueden completarse mientras está BLOCKED.
- LAB_APPROVER añade dictamen CONFORMING/NONCONFORMING con motivo y evidencia; el dictamen es válido solo para la versión del lote al emitirlo. Si el expediente cambia después (otra NC, reinspección o ensayo), se exige dictamen actualizado antes de decidir. Dictámenes no cambian inventario.
- Solo el QUALITY responsable decide. Debe ser distinto del reportante y del autor del dictamen. Para reportantes QUALITY existe calidad2. El dictamen más reciente debe ser conforme para levantar y no conforme para rechazar.
- Levantar exige resultado/muestra conformes vigentes, estado UNDER_TEST o RELEASED y dictamen actual favorable. Cierra explícitamente los bloqueos de esa NC. Si quedan otros bloqueos activos, conserva BLOCKED; al cerrar el último vuelve a QUARANTINE. No se vuelve directamente a AVAILABLE: requiere Almacén. Un lote UNDER_TEST todavía requiere liberación independiente.
- Rechazo: nuevo estado técnico REJECTED terminal e inventario BLOCKED, sin alterar cantidad ni eliminar liberaciones anteriores. Disposición explícita RETURN_TO_SUPPLIER o SCRAP, únicamente intención simulada. No se ejecuta salida física. Los bloqueos permanecen; otras NC abiertas conservan sus expedientes, pero ya no admiten mutaciones sobre un lote terminal.
- Bloqueo heredado V2: no se backfillea ni se cambian filas. Un BLOCKED sin filas inventory_hold sigue bloqueado por su estado. Se formaliza abriendo NC y solo se levanta mediante su decisión explícita. La migración no interpreta ni sustituye decisiones históricas.

## Compatibilidad y riesgos

- Cambio deliberado de comportamiento: clientes Sprint 1 que reinspeccionan y liberan sin levantar bloqueos recibirán 422. La UI nueva muestra el expediente pendiente; no simula éxito silencioso.
- El DTO Lot añade activeHolds, sin quitar campos. V1/V2 y el fingerprint del Command original permanecen iguales; los comandos semilla siguen siendo idempotentes.
- Dictamen ligado a la versión evita aprobar evidencia invalidada por una carrera. Bloqueo por lote serializa acciones NC y workflow; todos usan el mismo orden: clave de comando → fila del lote → filas NC/bloqueos.
- No se migra inventario existente a otro estado. Los triggers nuevos se validan ante escrituras; una prueba de upgrade compara lotes, usuarios, catálogo, command_result y auditoría antes/después.
- Respuesta incierta (red, 5xx o JSON ilegible tras 2xx): conservar exactamente comando y clave, bloquear nuevos envíos de esa identidad y permitir reintentar. Persistir en sesión permite recargar. Un 4xx definitivo permite corregir y emitir un nuevo comando. Confirmar un POST y fallar al refrescar consultas no debe repetir el POST.
- La cola conserva textos de evidencia dentro de la pestaña, no credenciales. Al cambiar identidad no se ejecutan comandos de otro usuario. No se permite descartar un comando incierto desde la UI y emitir otro accidentalmente.
- Autoridades y disposiciones son hipótesis de simulación; sin umbrales normativos nuevos ni integración corporativa.
