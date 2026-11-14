# Evidencia del paquete Windows

## Controles locales ejecutados

- SDK .NET10 en directorio temporal, sin modificar el ambiente UAT.
- **32 comprobaciones Core aprobadas**, incluyendo espera de semillas tras iniciar HTTP, sesiones/nombres/orígenes de navegador distintos, persistencia de selección/credenciales, validación de rutas/puertos, prohibición de pull/build, operación sobre el proyecto seleccionado, parada no destructiva, rechazo de Docker remoto/modo Windows, fallo de arranque/ready, integridad del archivo y diagnóstico sin la clave DB.
- `dotnet publish -c Release -r win-x64`: compilación autocontenida correcta. Esto por sí solo no se interpreta como ejecución Windows.
- Manual HTML: 18 capítulos/24 capturas; navegación, búsqueda, ampliación, móvil, impresión y ausencia de dependencias externas verificados. Acceso del presentador por el panel; comandos solo para el UAT técnico.

## Ejecuciones remotas comprobadas durante el desarrollo

- [43536961974](https://github.com/josevicenteayala/MaterialReviewFlow/actions/runs/43536961974), fuente af4c6dd: **82 backend**, sin fallos/errores/omitidas, **26 Playwright**, persistencia PASS.
- [43538063255](https://github.com/josevicenteayala/MaterialReviewFlow/actions/runs/43538063255), fuente ddc73d3: payload Linux/amd64 con arranque, recepción/replay y reinicio conservando snapshot; panel nativo Windows2025; compilación del instalador; instalación, ejecutable instalado y desinstalación conservando archivo de usuario: **SUCCESS**. Esta iteración precede a la espera de semillas y al ajuste de nombres/orígenes; no se atribuye su resultado a cambios posteriores.
- Las comprobaciones de la fuente posterior y los hashes/artefacto de entrega se enlazan en el PR #7, por SHA exacto. La CI no declara aceptación funcional ni sustituye la prueba en el portátil.

## Fallos observados y corregidos

- Red inicialmente interna para todos los servicios: la presentación no era accesible desde el host. Se separó red de datos interna y red de presentación solo del frontend; DB/backend no publican puertos.
- Carrera entre HTTP disponible y cinco semillas: una repetición comprobó menos lotes justo después de responder /me. Se añadió espera de las identidades de las cinco semillas y dos pruebas específicas.
- El runner Windows2022 no cumplía el mínimo de versión del instalador Windows11. Se usa Windows2025 y se descubren compiladores Inno6/7 disponibles. No se alteró el mínimo del instalador para ocultar la diferencia.

## Pendiente en portátil Windows11 del presentador

No se ha ejecutado Docker Desktop/WSL en el portátil físico desde este equipo macOS. Debe completarse con internet la preparación inicial del motor/licencia/virtualización y luego probar **sin internet**: Iniciar, cambiar roles, registrar un dato ficticio, Detener, Iniciar y conservar el dato, Manual, Nueva demostración, volver a sesión anterior, reiniciar Windows y recuperar sesión. El instalador no lleva firma corporativa; TI debe verificar origen/hash y sus políticas. No se desactivan protecciones.

El entorno UAT existente permaneció activo. No se modificaron servicios de negocio ni migraciones V1–V7; no se borraron volúmenes/datos. No hay merge automático.
