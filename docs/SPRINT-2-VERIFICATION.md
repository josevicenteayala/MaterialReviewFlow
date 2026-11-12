# Verificación de Sprint 2

Rama `codex/sprint-2-nonconformities`, sin merge. Registro iniciado el 12 de noviembre de 2026, America/Bogota. No se ha usado la base ni el volumen de la demo existente para escrituras de prueba.

## Ejecuciones locales

| Comprobación ejecutada | Resultado observado |
|---|---|
| Suite de backend de la entrega d50e965, JDK 21 y PostgreSQL 17 real | 17 pruebas, 0 fallos/errores/omitidas |
| Upgrade sintético V2→V3 | Filas de app_user, specification, purchase_order, lot, audit_event y command_result idénticas antes/después; sin backfill de holds/NC |
| Políticas unitarias de la entrega posterior | LaboratoryPolicyTest y NcPolicyTest ejecutadas, ambas aprobadas |
| Build frontend | TypeScript estricto y Vite 7.3.8 completados sin errores |
| Instalación npm con tipos Node | 0 vulnerabilidades reportadas |

Base de las pruebas iniciales: `jdbc:postgresql://localhost:55432/proco_sprint2_test`, creada en proyecto Compose `proco-sprint2`, volumen `proco-sprint2_proco-data`. La prueba de upgrade usa y elimina exclusivamente un esquema sintético `upgrade_<UUID>` creado por ella. V1/V2 no se editaron.

Tras esas pruebas, el socket de OrbStack dejó de responder a `_ping` y PostgreSQL aceptaba TCP pero agotaba el protocolo. La construcción Compose final local no llegó a completarse; el arranque Java alternativo tampoco pudo obtener conexión a esa base. Se detuvieron únicamente los CLI/Vite de diagnóstico propios. No se reinició el motor ni se detuvieron/eliminaron contenedores o volúmenes de la demo existente.

## Verificación final remota

Primera ejecución remota [43162081872](https://github.com/josevicenteayala/MaterialReviewFlow/actions/runs/43162081872): 18 pruebas backend aprobadas, build frontend y arranque Docker Compose correctos. Playwright aprobó 6 de 8 casos; dos agotaron tiempo al buscar el selector de disposición con getByLabel exacto. El snapshot mostraba el combobox accesible; se ajustó el selector a getByRole(combobox, name). Repetición final pendiente al registrar este cambio. El workflow usa Ubuntu 24.04, PostgreSQL desechable para Maven y **otro** PostgreSQL de Docker Compose para la aplicación web, con puertos separados. Construye e inicia la aplicación completa antes de ejecutar Playwright; adjunta reportes y logs de construcción/servicios. No se considera aprobado hasta observar el resultado de esa ejecución.

La suite final prevista contiene 18 pruebas backend y 8 escenarios de navegador. Los números se confirmarán desde los reportes realmente ejecutados, no por inferencia a partir de los archivos fuente.

## Casos incluidos

- Regresión del ciclo hilo/elastano; retención/reinspección reutilizadas con levantamiento explícito.
- RELEASED/AVAILABLE → RELEASED/BLOCKED sin borrar la liberación; dictamen, levantamiento a cuarentena y disponibilidad posterior por Almacén.
- NC formal con origen/responsable, evidencia inicial, dictámenes inmutables y disposición final.
- Responsable QUALITY distinto del reportante y del autor de dictamen; permisos por acción y por lote/expediente.
- Rechazo terminal con destino previsto y bloqueo de cualquier transición ulterior; cantidades intactas.
- Múltiples NC, cierre parcial conserva otro bloqueo y dictamen invalidado por cambio de versión.
- Legacy BLOCKED sin holds: formalización y decisión explícita; migración conserva filas antiguas.
- Reintentos simultáneos con misma clave no duplican NC/hold/evento; rechazos concurrentes 200/409; carrera disponibilidad/bloqueo.
- Fallo forzado al insertar auditoría revierte NC, hold, versión y command_result. Triggers protegen auditoría/dictámenes y guardan inventario con holds activos.
- Navegador: respuesta perdida después de commit, recarga/cambio de identidad, 5xx, corrección de 422 y 2xx incompleto; comparación de clave/cuerpo y conteo real de efectos.
- Consulta fallida después de POST confirmado nunca genera reintento de mutación.

No se verifican montajes, mezclas, sublotes, ejecución de devolución/baja, adjuntos binarios ni sistemas corporativos; continúan fuera de alcance.
