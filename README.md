# Simulador PROCO SIM-0.2

Sprints 1–3 integrados en `main`; Sprint 4 P0 en revisión: aplicación web local para recorrer **Almacén → Calidad → Laboratorio → liberación técnica → disponibilidad**, con expediente formal de NC, bloqueos persistentes, rechazo definitivo, autorización de mezclas y ejecución simulada con contención por áreas. Java 21, Spring Boot 4.0.0, PostgreSQL 17, React 19/TypeScript y Docker Compose. Datos y tolerancias ficticios; únicamente simulación.

Antes de implementar se registraron [ambigüedades, supuestos y plan de cuatro entregas](docs/PLAN.md). Las fuentes originales están en [docs/requirements](docs/requirements). Sprints 1–3 están en main (Sprint 3: merge PR #2, `d10a84f`). Sprint 4 se prepara en `codex/sprint-4-containment`, sin modificar main ni hacer merge automático. El alcance técnico de mezclas y sus riesgos están en [docs/SPRINT-3-DESIGN.md](docs/SPRINT-3-DESIGN.md). El montaje físico, sublotes, devolución física, reserva/consumo, notificaciones externas y OLA siguen pendientes; el acuse de tareas de contención ya es persistente.

## Repositorio y continuidad

Repositorio compartido: [josevicenteayala/MaterialReviewFlow](https://github.com/josevicenteayala/MaterialReviewFlow). La rama `main` contiene Sprints 1–3. Sprint 4 se propone en un PR desde `codex/sprint-4-containment`, sin merge automático. Su especificación es [docs/PROCO_SPRINT3_PLAN.md](docs/PROCO_SPRINT3_PLAN.md). El plan general conserva como pendientes sublotes, devolución física y evidencias de archivo. Los supuestos pendientes siguen documentados y requieren validación funcional antes de una adopción operativa.

```sh
git clone --branch codex/sprint-4-containment https://github.com/josevicenteayala/MaterialReviewFlow.git
cd MaterialReviewFlow
docker compose up -d --build
```

Los cambios siguientes deben conservar la auditoría y mantener verdes las pruebas del ciclo inicial. GitHub Actions ejecuta la verificación al publicar cambios o abrir un pull request.

## Ejecutar

Requisitos: Docker Engine con Compose; puertos 5432, 8080 y 8088 libres. No requiere Java ni Node instalados para usar los contenedores.

```sh
docker compose up -d --build
```

Abrir [http://localhost:8088](http://localhost:8088). La primera construcción descarga dependencias. Esperar a que el backend termine de arrancar antes de entrar. Base de datos persistida en volumen `proco-data`; `docker compose down` detiene sin borrar datos.

| Usuario | Rol |
|---|---|
| `almacen` | WAREHOUSE |
| `calidad` | QUALITY |
| `calidad2` | QUALITY (responsable independiente) |
| `tecnico` | LAB_TECH |
| `aprobador` | LAB_APPROVER |
| `tejeduria` | PRODUCTION_REQUESTER |
| `produccion` | PRODUCTION_APPROVER |
| `geo` | GEO |
| `tintoreria` | DYEING |
| `abastecimiento` | PROCUREMENT (coordinación ficticia de rechazo) |

Contraseña de los diez: **`Simulacion123!`**. Cuentas ficticias persistidas con BCrypt; credenciales enviadas con HTTP Basic explícito, sin cookies ni almacenamiento local. Al salir se elimina la credencial de la memoria de la interfaz. El selector elige identidad, no concede permisos. API y PostgreSQL solo se publican en loopback.

Se generan dos referencias, dos proveedores/OC y dos lotes iniciales (hilo y elastano), en cuarentena, con eventos auditables. Los comandos semilla tienen claves fijas y se reejecutan idempotentemente al arrancar: no reinician el avance del material.

## Demostración S01

1. Entrar como `almacen`; seleccionar HILO-SIM-001 o crear una recepción con OC-H-001. La recepción requiere lote proveedor, fecha de fabricación no futura, cantidad positiva en kg, ubicación, motivo y evidencia.
2. Salir y entrar como `calidad`; seleccionar el lote, completar motivo/evidencia y confirmar inspección conforme. Registrar muestra trazable (también permitido a Almacén).
3. Entrar como `tecnico`; registrar resistencia **15 cN/tex** para hilo. Para elastano, elongación **500 %**. Solo se admite un resultado por ciclo; otro ensayo exige reinspección y nueva muestra.
4. Entrar como `aprobador`; revisar y aprobar liberación técnica. El inventario permanece en cuarentena.
5. Entrar como `almacen`; confirmar segregación y disponibilidad. Resultado: calidad RELEASED e inventario AVAILABLE; historial con seis eventos e identidades distintas.

Todo comando requiere motivo y evidencia. Para ensayar retención, Calidad puede registrar un hallazgo o el técnico ingresar resistencia **5**: calidad HELD e inventario BLOCKED. Calidad reinspecciona con evidencia de subsanación, se inicia un nuevo ciclo y se requiere nueva muestra y nuevo resultado. El bloqueo permanece hasta formalizar la NC, obtener dictamen vigente y levantarla explícitamente. Los resultados anteriores quedan en auditoría; nunca se sobreescriben sus eventos. Un lote liberado ya no admite edición o nuevos ensayos en esta entrega.

## Reglas y persistencia

- Calidad técnica y disponibilidad son campos independientes con restricciones SQL. Liberar no habilita automáticamente inventario ni autoriza mezclas.
- Cada acción valida rol, estado, evidencia y versión en servidor. Un técnico no puede liberar. La política también compara identidad de capturador/aprobador, aunque se ampliaran roles en el futuro.
- Todas las escrituras de un comando, su auditoría y resultado idempotente se confirman en una transacción. Los intentos de negocio denegados se registran en una transacción independiente después del rollback. Fallos de autenticación/JSON/validación de transporte no generan un evento de negocio.
- `version` es la precondición optimista; además se bloquea la fila durante el comando. Saldo de OC protegido con bloqueo de la orden al recibir; no se acepta una cantidad acumulada superior a la OC. Lote proveedor único por OC.
- `Idempotency-Key` UUID obligatorio; se serializan reintentos por actor/clave. Misma clave y comando devuelve el lote actual sin repetir la acción. La reutilización con otro contenido devuelve 409. La UI conserva clave, URL y cuerpo original en sessionStorage por identidad antes de enviar. Tras red/5xx/respuesta incompleta ofrece reintentar exactamente el comando pendiente, incluso tras recargar; impide nuevos comandos de esa identidad. Un 4xx definitivo permite corregir con clave nueva. Una consulta fallida después de confirmar el POST nunca repite la mutación.
- Cada evento conserva actor/rol, fecha UTC, estado anterior/nuevo, motivo, evidencia, criterio SIM-1, comando y correlación. Trigger PostgreSQL impide UPDATE/DELETE. Un administrador de base de datos puede alterar la infraestructura; no se pretende protección criptográfica frente a DBA.
- Ensayos críticos ficticios por referencia almacenados en catálogo versionado. No hay API administrativa para editarlos, ni concesiones ni liberaciones automáticas.

## Arquitectura

Monolito modular con composición desde `workflow`; sin motor BPMN ni servicios distribuidos.

| Paquete backend | Responsabilidad |
|---|---|
| `reception` | Modelo de lote y consultas de expediente |
| `quality` | Condiciones de inspección/reinspección |
| `laboratory` | Criterios críticos y segregación de aprobación |
| `inventory` | Bloqueos identificados y condiciones de disponibilidad |
| `execution` | Inicio/finalización de montajes simulados, suspensión y tareas por área |
| `mixing` | Solicitud/conceptos/decisiones, elegibilidad y revocación transaccional por montaje |
| `nonconformity` | Expediente, dictámenes versionados, responsables, levantamiento y rechazo |
| `workflow` | Comandos, autorización de negocio, transacción, concurrencia y API |
| `audit` | Registro append-only, intentos denegados y consultas |
| `security` | Autenticación, BCrypt y autorización de acceso autenticado |
| `shared` | Errores de dominio sin dependencias de infraestructura |
| `simulation` | Inicialización idempotente de datos sintéticos |

Las políticas de Calidad/Laboratorio/Inventario no dependen de Spring ni ejecutan SQL. `workflow` coordina el agregado material; esta entrega usa JDBC para hacer explícitas las transacciones y bloqueos. Flyway administra esquema y catálogo. La UI organiza creación, bandeja y expediente; no replica la autoridad del servidor.

## API de la entrega

Contrato detallado y ejemplos en [docs/API.md](docs/API.md). Todas las consultas requieren autenticación.

| Método y ruta | Rol |
|---|---|
| GET `/api/me`, `/api/catalog`, `/api/lots`, `/api/lots/{id}`, `/api/lots/{id}/timeline` | Cualquier usuario de simulación |
| POST `/api/receipts` | WAREHOUSE |
| POST `/api/lots/{id}/inspect` | QUALITY |
| POST `/api/lots/{id}/sample` | WAREHOUSE, QUALITY |
| POST `/api/lots/{id}/test` | LAB_TECH |
| POST `/api/lots/{id}/release` | LAB_APPROVER |
| POST `/api/lots/{id}/available` | WAREHOUSE |
| POST `/api/lots/{id}/nonconformities` | Roles operativos de Sprints 1–3; excluye PROCUREMENT |
| POST `/api/lots/{id}/nonconformities/{nc}/opinion` | LAB_APPROVER |
| POST `/api/lots/{id}/nonconformities/{nc}/resolve` o `/reject` | QUALITY responsable independiente |

Errores: 400 formato/validación de transporte; 401 credenciales; 403 rol; 404 lote/acción inexistente; 409 estado/versión/clave incompatible; 422 guarda de negocio. Los errores de negocio devuelven `{status,message}`.

## Desarrollo y pruebas

Requisitos locales: JDK **21**, Maven 3.9+, Node 22.12+ y Docker. El proyecto fija bytecode Java 21. En macOS con varios JDK, seleccionar `JAVA_HOME=$(/usr/libexec/java_home -v 21)` antes de ejecutar Maven.

```sh
docker compose up -d db
docker compose exec -T db psql -U proco -d postgres -c 'CREATE DATABASE proco_test'
mvn -f backend/pom.xml test
npm --prefix frontend ci
npm --prefix frontend run build
```

Crear `proco_test` solo una vez. La suite arranca un servidor HTTP real en 18080 y usa PostgreSQL real en `proco_test`, separado de la demo `proco`. Puede cambiarse la URL con `TEST_DB_URL`; usuario/contraseña con `DB_USER`/`DB_PASSWORD`. Los tests insertan lotes únicos y conservan auditoría, sin borrar datos. Si se ejecutan miles de veces sobre la misma base, usar una base de prueba nueva por el saldo finito de OC.

Pruebas: política de límites y segregación; S01 por API; retención/reinspección/reensayo; roles, ensayo faltante y disponibilidad anticipada; concurrencia 200/409; idempotencia; validación/autenticación; trigger de inmutabilidad.

Con Compose completo activo, ejecutar recorrido de navegador:

```sh
cd frontend
npx playwright install chromium --only-shell
npm run test:e2e
```

La prueba cambia entre cuatro identidades, comprueba bloqueo de aprobación sin ensayo y termina en AVAILABLE con seis eventos; genera captura en `frontend/test-results/s01-available.png`. `E2E_URL` permite cambiar el host. CI define PostgreSQL aislado para pruebas, JDK 21, compilación y recorrido Playwright contra la aplicación completa levantada con Docker Compose.

Para desarrollo interactivo: `mvn -f backend/pom.xml spring-boot:run` y `npm --prefix frontend run dev`; Vite enruta `/api` al puerto 8080. Detener primero el backend de Compose si ocupa ese puerto.

## Límites de esta entrega

Simulación local sin SSO, HTTPS, recuperación de contraseña, gestión de usuarios, aislamiento por organización ni adjuntos binarios. Todos los usuarios autorizados pueden consultar todos los lotes ficticios. La evidencia es una referencia textual auditable, no un archivo firmado. La bandeja se calcula desde el estado persistido; las tareas de contención tienen acuse/cierre, sin plazos OLA automáticos. No se declara conformidad normativa ni validación de los diez escenarios completos. Ver [plan](docs/PLAN.md) y [registro de verificación](docs/VERIFICATION.md).

## Revisar Sprint 2 sin modificar la demo existente

```sh
git switch codex/sprint-2-nonconformities
DB_PORT=55432 BACKEND_PORT=18081 FRONTEND_PORT=18088 docker compose -p proco-sprint2 up -d --build
```

Abrir [http://localhost:18088](http://localhost:18088). El proyecto Compose separado usa otro volumen; no ejecutar `down -v` sobre la demo existente. V3 solo añade esquema y restricciones, no reescribe lotes, usuarios ni auditoría; V1/V2 permanecen intactas. Un BLOCKED heredado conserva su bloqueo y debe formalizarse como NC. La prueba de upgrade compara todas las filas V2 antes/después en un esquema sintético.

Recorrido de NC: un usuario operativo (excepto Abastecimiento) reporta, elige responsable QUALITY distinto del reportante y adjunta motivo/evidencia textual. El inventario se bloquea aunque estuviera RELEASED/AVAILABLE. `aprobador` registra dictamen CONFORMING o NONCONFORMING para la versión actual. Solo el responsable decide: LIFT_HOLD exige ensayo/muestra conformes y devuelve a cuarentena si era el último bloqueo; RETURN_TO_SUPPLIER/SCRAP rechaza definitivamente y mantiene bloqueado. No se mueve cantidad ni se ejecuta devolución física. `calidad2` permite una decisión independiente cuando reporta `calidad`.

Para pruebas aisladas, crear una base nueva en ese proyecto y usar `TEST_DB_URL=jdbc:postgresql://localhost:55432/proco_sprint2_test` al ejecutar Maven. Para Playwright: `E2E_URL=http://127.0.0.1:18088 npm --prefix frontend run test:e2e`. El proxy Vite admite `PROCO_API_TARGET` si se necesita desarrollo local con puertos separados.

El [registro de Sprint 2](docs/SPRINT-2-VERIFICATION.md) distingue pruebas realmente ejecutadas y limitaciones ambientales. El rechazo no es reversible; nuevas incidencias se registran con nuevos expedientes. Las NC adicionales de un lote rechazado conservan su estado e historial, pero el lote terminal no admite mutaciones posteriores.

## Sprint 3 Mezclas por montaje

[Especificación proporcionada](docs/PROCO_SPRINT3_PLAN.md), [diseño y contraste funcional](docs/SPRINT-3-DESIGN.md) y [verificación](docs/SPRINT-3-VERIFICATION.md).

```sh
git switch main
DB_PORT=55434 BACKEND_PORT=18083 FRONTEND_PORT=18090 docker compose -p proco-sprint3 up -d --build
```

Abrir [http://localhost:18090](http://localhost:18090). El proyecto separado usa un volumen nuevo. La ejecución estándar `docker compose up -d --build` sigue disponible en los puertos originales si se desea actualizar ese entorno. V4 añade esquema; no reescribe datos existentes ni modifica V1–V3. Se añaden usuarios y tres lotes ficticios nuevos mediante comandos de claves estables: HILO-MIX-001, HILO-MIX-002 y ELASTANO-MIX-001, inicialmente liberados/disponibles. Los reinicios no deshacen su avance ni recrean decisiones.

1. Entrar como `tejeduria`, abrir **Mezclas por montaje** y crear solicitud con dos o más lotes elegibles, cantidades, montaje, vigencia y evidencia. No se reservan ni consumen existencias.
2. `aprobador`, `geo` y `calidad` emiten cada uno un concepto favorable con evidencia. Si participa ELASTANO-DEMO, la regla ficticia obliga además a `tintoreria`. No existen tolerancias automáticas por diferencia de fechas.
3. `produccion` revisa los impedimentos y autoriza o deniega el conjunto exacto. Un concepto desfavorable impide autorizar. El solicitante no aprueba su propia solicitud.
4. Desde el expediente se puede consultar la autorización para ese montaje, con revalidación de lotes/vigencia; la consulta no inicia montaje físico.
5. Abrir una NC sobre un lote desde **Ver lote** bloquea inventario y revoca las mezclas autorizadas afectadas en una transacción. El historial conserva la autorización original. Levantar la NC no reactiva la mezcla: se requiere nueva solicitud.

La pantalla incluye bandeja filtrable por área, estado y montaje, conceptos, evidencia, decisiones y revocaciones. Los comandos inciertos de mezcla reutilizan el mismo cliente de sessionStorage y la misma clave/cuerpo al reintentar, incluso tras recargar; no se confunden con comandos de material.

Regla ficticia del servidor: HILO-DEMO → MANUAL_BASIC; ELASTANO-DEMO → MANUAL_DYEING; versión MIX-SIM-1. Los criterios no son productivos. No se afirma validación por cono/caja, set, puesto o máquina. La unidad sigue siendo lote completo. Ver [contrato API](docs/API.md).

Para regresión aislada, crear `proco_sprint3_test` una sola vez con psql dentro de ese proyecto y ejecutar:

```sh
TEST_DB_URL=jdbc:postgresql://localhost:55434/proco_sprint3_test mvn -f backend/pom.xml verify
npm --prefix frontend run build
E2E_URL=http://127.0.0.1:18090 npm --prefix frontend run test:e2e
```

Java local debe ser 21; Chromium se instala según las instrucciones de Playwright anteriores. Las pruebas de upgrade V2→V3 y V3→V4 usan esquemas sintéticos propios y comparan valores previos de material, NC, holds, auditoría e idempotencia. El CI ejecuta toda la suite contra PostgreSQL real y la interfaz contra Docker Compose desechable. No hay montaje físico, reserva, consumo ni integración corporativa en este sprint.

## Sprint 4 P0 — montaje simulado y contención

[Especificación](docs/SPRINT-4-PLAN.md), [diseño previo](docs/SPRINT-4-DESIGN.md), [resultados observados](docs/SPRINT-4-VERIFICATION.md). V5 es aditiva y conserva V1–V4. Cuenta ficticia adicional `abastecimiento` (PROCUREMENT), misma contraseña `Simulacion123!`; no puede liberar material ni autorizar mezclas.

```sh
git switch codex/sprint-4-containment
DB_PORT=55436 BACKEND_PORT=18085 FRONTEND_PORT=18092 docker compose -p proco-sprint4 up -d --build
```

Abrir http://localhost:18092. Volumen independiente de las demos previas; no ejecutar `down -v`. El arranque conserva datos y no reinicia decisiones. Se reutilizan los tres lotes demo disponibles y las identidades de Sprint 3; los tests generan fixtures adicionales con identificadores únicos mediante la API.

1. Crear y autorizar una mezcla como en Sprint 3. Como `tejeduria`, entrar en **Montajes y tareas**, seleccionar autorización, registrar motivo/evidencia e iniciar. El servidor revalida dentro del POST. Una ejecución por autorización; máximo una IN_PROGRESS por código de montaje.
2. Reportar NC en un lote participante, por ejemplo como `tintoreria`, asignando `calidad`. En el mismo commit: inventario BLOCKED, mezcla REVOKED, montaje SUSPENDED y tareas OPEN para Almacén, Calidad, Tejeduría y GEO; Tintorería participa según regla.
3. Entrar con cada identidad. En **Mis tareas**, seleccionar el montaje, aportar motivo/evidencia y acusar. Después registrar evidencia de la acción y completarla. El acuse no cierra la tarea. Solo la identidad que acusó puede completarla.
4. `produccion` decide finalizar o cancelar con disposición documentada, después de cerrar todas las tareas. Cerrar NC no reactiva mezcla ni ejecución. No hay endpoint de reanudación.
5. Una NC posterior a un montaje COMPLETED genera tareas de evaluación retrospectiva, conserva COMPLETED y registra alerta. Un rechazo agrega coordinación ficticia para `abastecimiento` aunque la mezcla ya estuviera revocada; no ejecuta devolución física.

Una autorización vencida impide nuevos inicios. Durante ejecución impide completar y registra REVIEW_REQUIRED; admite cancelación documentada tras cerrar tareas. No hay vigilancia externa del reloj ni política industrial de caducidad aprobada.

Regresión aislada (crear la base una sola vez):

```sh
docker compose -p proco-sprint4 exec -T db psql -U proco -d postgres -c 'CREATE DATABASE proco_sprint4_test'
TEST_DB_URL=jdbc:postgresql://localhost:55436/proco_sprint4_test mvn -f backend/pom.xml verify
npm --prefix frontend run build
E2E_URL=http://127.0.0.1:18092 npm --prefix frontend run test:e2e
```

Para detener y conservar datos: `docker compose -p proco-sprint4 stop`; para volver a levantar los mismos contenedores: `docker compose -p proco-sprint4 start`. Esta entrega no implementa OLA, indicadores, exportaciones, inventario real, ERP, máquinas ni notificaciones externas.

Después de ejecutar los E2E de Sprint 4, verificar reinicio y persistencia con `python3 scripts/verify-sprint4-restart.py --project proco-sprint4 --url http://127.0.0.1:18092`. Compara filas completas de diez tablas antes/después y reinicia solo ese proyecto, sin borrar volúmenes.
