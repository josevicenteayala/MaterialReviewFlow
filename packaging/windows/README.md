# Instalador PROCO Demostración — Windows 11 x64

Paquete separado de la aplicación de negocio: lanzador gráfico .NET10 autocontenido, instalador Inno Setup, imágenes Linux/amd64 preconstruidas y manual HTML offline. No instala runtimes Java/Node/PostgreSQL en Windows ni cambia reglas de PROCO.

## Para el presentador

1. Instalar el archivo `PROCO-Demostracion-Windows11-x64-1.0.0.exe` del artefacto verificado, con su SHA-256. Esta edición no dispone todavía de firma corporativa: TI debe verificar el origen, no desactivar controles de seguridad.
2. Una sola vez, preparar **Docker Desktop en modo Linux / WSL2** desde su instalador oficial. Se necesita internet para esa preparación, aceptación de sus condiciones y, si lo solicita, apoyo de TI/virtualización/reinicio. El instalador de PROCO no eleva permisos ni instala Docker silenciosamente. Ver LEEME.html.
3. Abrir el acceso directo **PROCO Demostración** y pulsar **Iniciar demostración**. Comprueba que el motor esté listo y carga las imágenes incluidas, sin descargar imágenes ni compilar. Cuando responde la API autenticada, abre el navegador en el puerto local asignado.
4. Usar **Manual** cuando se necesite ayuda. Los usuarios sintéticos mantienen la contraseña `Simulacion123!`. La primera sesión tiene las cinco semillas de la aplicación, no los 14 lotes de un UAT anterior.
5. **Detener** conserva el volumen. **Nueva demostración** detiene la seleccionada y crea otro identificador/puerto/volumen; las sesiones anteriores siguen seleccionables. Para crear montajes con vigencia actual, seguir el manual: no se distribuyen permisos preaprobados que luego caduquen.

## Diseño e invariantes del paquete

- Windows11 Intel/AMD64bits. No admite Windows ARM en este paquete; el instalador y el ejecutable lo rechazan expresamente. .NET10 está incluido en el ejecutable. PostgreSQL/backend permanecen dentro de la red del proyecto; solo frontend se publica en `127.0.0.1`.
- Core puro: modelos validados, SessionStore y escritura atómica; renderer Compose sin build/pull, proyectos únicos, red interna de datos y red de presentación solo para frontend; orquestación con procesos sin shell, argumentos separados y timeouts; readiness con JSON de identidad esperado.
- Launcher WinForms: comprobación de Docker Desktop local por contexto desktop-linux y named pipe, inicio del motor si está instalado, espera, apertura de navegador, manual local, nueva sesión y diagnóstico. Mutex por usuario impide dos paneles simultáneos.
- Cada sesión guarda `session.json` y `compose.yml` en `%LOCALAPPDATA%\PROCO\Demostracion\sessions\<id>`. Los volúmenes pertenecen a Docker Desktop, no a esa carpeta. La contraseña DB es aleatoria por sesión; la contraseña de usuarios de simulación es otra y permanece como en la aplicación.
- Seleccionar una sesión anterior no la modifica. El botón Detener afecta solo a la seleccionada; si se cambiaron sesiones sin detener otra, detenerla al terminar seleccionándola también. Cerrar panel/navegador no detiene contenedores.
- El manifiesto identifica SHA de fuente, arquitectura, ID de cada imagen y SHA-256 del archivo de imágenes. Antes de importar se comprueba el hash; después se comprueban los IDs. Los hashes verifican integridad del paquete recibido, no sustituyen firma/verificación del origen.
- Se usa exclusivamente el contexto local Docker Desktop; nunca un contexto remoto heredado. Se eliminan DOCKER_HOST y variables Compose heredadas al crear los procesos.
- El diagnóstico no exporta el archivo Compose ni el JSON de sesión y redacta la clave de DB de la salida. Puede contener datos sintéticos/logs; compartir solo con soporte.
- Desinstalar el panel **no elimina Docker ni volúmenes/sesiones**; no se ejecuta down, prune ni down -v. Eliminar Docker/volúmenes por otros medios sí puede perder los datos.
- No se actualizan sesiones antiguas de otra versión automáticamente. Conservar el instalador correspondiente para retomarlas; crear una sesión nueva para otra versión. Esto evita migrar silenciosamente decisiones históricas o iniciar una imagen distinta sobre sus datos.

## Construcción reproducible (equipo técnico)

El workflow `.github/workflows/windows-demo.yml` se ejecuta al publicar la rama y manualmente:

1. Ubuntu: build de backend/frontend y PostgreSQL con `linux/amd64`; archivo images.tar.gz y manifest.json con IDs/hashes. No monta volúmenes existentes.
2. Core genera exactamente el Compose del lanzador para un proyecto de verificación único. Se prueba arranque, cinco semillas, siete migraciones, recepción y replay idempotente, parada/arranque y snapshot idéntico de lotes/comandos/auditoría, con red interna de datos, red de presentación para el frontend y sin pull/build.
3. Windows2025 de CI con SDK10: 32 comprobaciones Core, publish WinForms win-x64 autocontenido; self-test nativo de formulario y captura PNG; compilación Inno Setup.
4. Instalación silenciosa a una carpeta temporal, self-test del ejecutable instalado, comprobación de archivos, desinstalación y retención de un archivo de datos de usuario. Se publica el EXE, su hash, captura y evidencia del payload como artefacto. No se publica release ni se hace merge automático.

Para compilar el lanzador con un SDK10 (el usuario final no lo necesita):

```sh
dotnet run --project packaging/windows/Proco.Demo.Tests
dotnet publish packaging/windows/Proco.Demo.Launcher -c Release -r win-x64 -o /tmp/proco-windows-launcher
```

Para el payload, desde un host Docker con soporte Linux/amd64, ejecutar `packaging/windows/build-images.sh <directorio-nuevo>`. El paso Inno requiere Windows. La versión del payload se deriva del SHA exacto de la rama; las imágenes Java/React son el código actual, sin modificaciones de negocio.

## Límites de la evidencia

Compilar un EXE o ejecutar WinForms en un runner no demuestra que Docker Desktop/WSL funcione en el portátil destino. Antes de la entrega final de uso, un responsable debe ejecutar **la prueba en el portátil Windows11**: instalación inicial con internet, licencia/prerrequisitos, Iniciar → cambiar roles → usar datos propios → Detener → apagar internet → Iniciar → continuar datos → abrir Manual → Nueva demostración → volver a seleccionar la anterior → Diagnóstico. Comprobar también reinicio de Windows y puertos ocupados. No declarar estas pruebas aprobadas hasta realizarlas.

El payload no descarga dependencias y backend y PostgreSQL se comunican por una red interna; solo frontend tiene además la red de presentación para publicar su puerto local; la instalación inicial de Docker sí requiere completar sus propios componentes. No se promete funcionamiento en un equipo corporativo que prohíba virtualización/contendedores. La aceptación funcional por PROCO sigue siendo una actividad de las áreas.

Referencias oficiales: [Docker Desktop Windows](https://docs.docker.com/desktop/setup/install/windows-install/), [licencia](https://docs.docker.com/subscription-billing/desktop-license/), [.NET self-contained](https://learn.microsoft.com/en-us/dotnet/core/deploying/single-file/).
