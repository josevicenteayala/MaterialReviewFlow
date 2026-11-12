# Verificación Sprint 3

Los resultados se registran después de ejecutar las pruebas. Base main 795f626; entorno aislado `proco-sprint3`, PostgreSQL en 55434, base nueva `proco_sprint3_test`. No se usa el volumen ni los datos de la demo existente.

## Entrega de solicitud y conceptos

Ejecutado con Java 21 y PostgreSQL 17 real: `mvn -Dtest=MixIntegrationTest test`, **5 pruebas aprobadas**, cero fallos/errores. Casos: lotes distintos/elegibles/cantidades, autorización ausente sin solicitud, conceptos faltantes, roles e identidad, Tintorería requerida por servidor, concepto desfavorable opcional, denegación terminal, inmutabilidad, reintentos de solicitud/concepto/decisión y vigencia con reloj controlado. Compilación Maven correcta.

La regresión completa, revocación/NC, concurrencia, migración de datos previos y navegador se registrarán en las siguientes entregas cuando hayan sido ejecutados.
