# Verificación Sprint 3

Los resultados se registran después de ejecutar las pruebas. Base main 795f626; entorno aislado `proco-sprint3`, PostgreSQL en 55434, base nueva `proco_sprint3_test`. No se usa el volumen ni los datos de la demo existente.

## Entrega de solicitud y conceptos

Ejecutado con Java 21 y PostgreSQL 17 real: `mvn -Dtest=MixIntegrationTest test`, **5 pruebas aprobadas**, cero fallos/errores. Casos: lotes distintos/elegibles/cantidades, autorización ausente sin solicitud, conceptos faltantes, roles e identidad, Tintorería requerida por servidor, concepto desfavorable opcional, denegación terminal, inmutabilidad, reintentos de solicitud/concepto/decisión y vigencia con reloj controlado. Compilación Maven correcta.

Esta ejecución parcial corresponde a la primera entrega. La regresión completa, revocación/NC, concurrencia, migración de datos previos y navegador ya se ejecutaron; sus resultados finales se detallan en las secciones siguientes.

## Entrega de revocación y regresión

Ejecutado `mvn verify` con Java 21 y PostgreSQL real: **33 pruebas aprobadas**, cero fallos/errores/omitidas. Incluye las 19 pruebas previas y 14 nuevas (12 escenarios de integración de mezcla, 1 política y 1 upgrade V3→V4). Se ejecutaron carreras NC/aprobación repetidas, dos NC concurrentes sobre mezclas compartidas, rollback forzado al escribir la revocación, ausencia de reactivación tras levantar NC, rechazo definitivo, caducidad y conceptos de contexto antiguo. El upgrade conserva todos los valores previos; la nueva columna mix_id permanece null para comandos de lote antiguos.

Docker Compose aislado construyó backend Java 21, frontend Nginx y PostgreSQL. La interfaz multiactor y los escenarios de navegador se verificaron sobre esa aplicación, con los resultados registrados en la sección siguiente.

## Interfaz y ejecución local Docker

Ejecutado `E2E_URL=http://127.0.0.1:18090 npm --prefix frontend run test:e2e`: **12 escenarios aprobados** en 36,2 s, incluidos los ocho anteriores. Los cuatro nuevos recorren solicitud/conceptos/decisión multiactor, filtros, Tintorería requerida y denegación, NC con revocación irreversible y reintento de mezcla tras perder respuesta/recargar (misma clave y cuerpo, sin contaminar la bandeja de material).

`npm run build` y TypeScript correctos. Se revisaron visualmente `sprint3-authorized.png`, `sprint3-denied.png` y `sprint3-revoked.png`. Las capturas son artefactos generados en frontend/test-results, excluidos de Git. Se ajustó presentación de fecha de fabricación y cabecera de cantidad tras inspeccionarlas.

La CI del commit `9717e2084e7d1c51208a920348cb0c535c6d55dc` finalizó correctamente: **33 pruebas backend**, cero fallos/errores/omitidas, y **12 escenarios de navegador** aprobados en 38,5 s. Evidencia: [ejecución del PR y artefactos](https://github.com/josevicenteayala/MaterialReviewFlow/actions/runs/43229782681), [PR #2](https://github.com/josevicenteayala/MaterialReviewFlow/pull/2). Estos resultados corresponden a ese commit; los cambios posteriores tienen su propia ejecución de CI. La aplicación local de Sprint 3 usa 18090/18083/55434, sin modificar el volumen de la demo anterior.

Repetición selectiva tras los ajustes visuales: `playwright test sprint3.spec.ts`, **4 escenarios aprobados** en 16,5 s sobre la interfaz reconstruida. Las pruebas previas no se omiten de CI: el workflow sigue ejecutando los 12 escenarios y todo Maven verify.

## Trazabilidad de aceptación

| Plan Sprint 3 | Evidencia automatizada ejecutada |
|---|---|
| S3-01/02 | Solicitud exige dos lotes diferentes/elegibles y cantidades válidas; sin solicitud no hay autorización; NC/rechazo impiden nueva solicitud |
| S3-03/04/05 | Conceptos obligatorios, Tintorería por servidor, desfavorables y contexto antiguo bloquean AUTHORIZE |
| S3-06/07 | Roles API e identidad independiente; autorización exige montaje exacto; definición/ítems/conceptos/eventos protegidos |
| S3-08/09 | Revocación de todas las mezclas afectadas, historia preservada, cerrar NC no reactiva; solicitud nueva sí requiere reevaluación |
| S3-10 | Carreras NC/aprobación y dos NC sobre mezclas compartidas; rollback de NC/hold/lote/mezcla si falla el evento de revocación |
| S3-11 | Replay de solicitud/concepto/decisión sin duplicados; navegador pierde respuesta, recarga y reintenta clave/cuerpo congelados |
| S3-12 | Upgrade V3→V4 compara valores de todas las tablas antiguas; V1–V3 intactas |
| S3-13 | Las 19 pruebas previas se ejecutan dentro de las 33 actuales; ocho recorridos de navegador previos dentro de los 12 |
| S3-14 | Aplicación completa local en Docker Compose, cuatro escenarios multiactor nuevos y capturas revisadas |

No se midieron tolerancias productivas, consumo/reservas ni montaje físico: siguen fuera de alcance. El chequeo de autorización es una instantánea checkedAt, sin promesa de disponibilidad futura.
