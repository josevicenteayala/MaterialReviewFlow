# ADR 001 Monolito modular para el primer ciclo

Estado: adoptado para simulación. Fecha: 2026-11-11.

El ciclo necesita consistencia entre estado técnico, inventario, comandos y auditoría. Se usa un solo proceso Spring Boot con módulos de dominio y una transacción PostgreSQL por comando. `workflow` coordina políticas puras de recepción/calidad/laboratorio/inventario; `audit` y `security` concentran sus responsabilidades. La persistencia JDBC permite revisar los bloqueos y guardas sin proxies ORM. El registro de auditoría conserva cada muestra, inspección, resultado y aprobación como comandos inmutables; la fila del lote sirve como vista actual del agregado.

Se usa precondición de versión para detectar vistas antiguas y bloqueo de fila para serializar escrituras del agregado. Una exclusión transaccional por actor y clave permite reintentos incluso antes de existir un lote. La fila de OC serializa recepciones concurrentes y protege saldo. Los eventos denegados se escriben después del rollback en otra transacción.

La evidencia textual y la bandeja calculada son límites deliberados de la entrega. En entregas posteriores, muestras/ensayos/decisiones podrán tener tablas de consulta propias, conservando los eventos históricos. No hay motor BPMN, mensajería externa ni despliegue productivo. Java 21 es la versión de compilación y ejecución Docker; Spring Boot 4.0 es compatible con ella según los [requisitos oficiales](https://docs.spring.io/spring-boot/4.0/system-requirements.html).
