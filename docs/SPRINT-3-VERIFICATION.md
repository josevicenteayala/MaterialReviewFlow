# Verificación Sprint 3

Los resultados se registran después de ejecutar las pruebas. Base main 795f626; entorno aislado `proco-sprint3`, PostgreSQL en 55434, base nueva `proco_sprint3_test`. No se usa el volumen ni los datos de la demo existente.

## Entrega de solicitud y conceptos

Ejecutado con Java 21 y PostgreSQL 17 real: `mvn -Dtest=MixIntegrationTest test`, **5 pruebas aprobadas**, cero fallos/errores. Casos: lotes distintos/elegibles/cantidades, autorización ausente sin solicitud, conceptos faltantes, roles e identidad, Tintorería requerida por servidor, concepto desfavorable opcional, denegación terminal, inmutabilidad, reintentos de solicitud/concepto/decisión y vigencia con reloj controlado. Compilación Maven correcta.

La regresión completa, revocación/NC, concurrencia, migración de datos previos y navegador se registrarán en las siguientes entregas cuando hayan sido ejecutados.

## Entrega de revocación y regresión

Ejecutado `mvn verify` con Java 21 y PostgreSQL real: **33 pruebas aprobadas**, cero fallos/errores/omitidas. Incluye las 19 pruebas previas y 14 nuevas (12 escenarios de integración de mezcla, 1 política y 1 upgrade V3→V4). Se ejecutaron carreras NC/aprobación repetidas, dos NC concurrentes sobre mezclas compartidas, rollback forzado al escribir la revocación, ausencia de reactivación tras levantar NC, rechazo definitivo, caducidad y conceptos de contexto antiguo. El upgrade conserva todos los valores previos; la nueva columna mix_id permanece null para comandos de lote antiguos.

Docker Compose aislado construyó backend Java 21, frontend Nginx y PostgreSQL. La interfaz multiactor y los escenarios de navegador están pendientes de su ejecución específica.
