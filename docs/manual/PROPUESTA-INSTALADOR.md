# Paquete local de demostración PROCO — propuesta

Estado: diseño para facilitadores no técnicos. No es todavía un instalador generado o probado en el portátil destino. Reutiliza la aplicación de Sprints 1–6, PostgreSQL y el manual HTML existentes; no cambia las reglas de negocio.

## Experiencia del presentador

1. Instalar PROCO una vez con acompañamiento inicial de TI cuando el portátil necesite habilitar virtualización o instalar el motor de contenedores.
2. Abrir el acceso directo **PROCO Demostración**.
3. Pulsar **Iniciar demostración**. El lanzador comprueba requisitos, inicia los componentes y espera la disponibilidad real de la aplicación; muestra un estado claro mientras arranca.
4. Se abre el navegador con PROCO y acceso al **Manual de usuario**. Cambiar entre roles desde la pantalla de acceso, según el manual.
5. Usar **Detener demostración** al finalizar. Los datos permanecen para continuar más tarde.

La operación normal no debe requerir terminal, Git, Java, Node o PostgreSQL instalados por separado. Cerrar el navegador no se interpreta como detener la aplicación.

## Componentes del paquete

- Imágenes preconstruidas y versionadas de backend Java21, frontend React/Nginx y PostgreSQL17, de la arquitectura del portátil destino. No compilar ni descargar dependencias del proyecto durante una presentación.
- Archivo Compose exclusivo del paquete con nombres propios, imagen fijada y persistencia local independiente. Interfaz solo en localhost; PostgreSQL y backend pueden mantenerse dentro de la red privada del paquete.
- Motor Docker compatible instalado previamente o verificado por el asistente inicial. No asumir que el instalador de PROCO puede activar virtualización sin permisos ni reinicios. La licencia/requisitos del motor se verifican para el equipo y la organización.
- Lanzador gráfico adaptado al sistema operativo, con accesos **Iniciar**, **Detener**, **Manual** y **Diagnóstico**. Los mensajes de diagnóstico deben ser comprensibles y permitir guardar un archivo para soporte sin exponer contraseñas.
- Manual index.html autocontenido, accesible sin conexión. Mantenerlo fuera del contenedor para abrirlo también cuando el servicio esté detenido.
- Datos sintéticos base y guion S01–S10. Usar la API existente para preparar casos cuando corresponda; no transportar la base de desarrollo/UAT ni sus UUID como si fueran maestros.
- Versión de la aplicación, arquitectura, hashes y guía breve de instalación.

## Datos y continuidad

- Una instalación utiliza su volumen local; las actualizaciones no lo eliminan y no ejecutan down -v.
- Para repetir desde cero, ofrecer una **Nueva sesión de demostración** identificada por fecha/UUID que conserva la sesión anterior. No borrar datos silenciosamente.
- No distribuir autorizaciones precreadas con una vigencia que caducará antes de la presentación. Preferir lotes iniciales y que el facilitador cree/apruebe la mezcla durante el ejercicio, o preparar su vigencia explícitamente al crear una nueva sesión.
- No depender de internet para iniciar o realizar una demostración una vez instalado el motor y cargadas todas las imágenes. La instalación completamente offline requiere entregar también los prerrequisitos del portátil; eso se valida por sistema operativo.
- No publicar servicios en la LAN automáticamente. Esta propuesta es para demostración local en un portátil.

## Construcción y pruebas propuestas

1. Confirmar sistema operativo, arquitectura (x64/ARM64), permisos y disponibilidad de internet durante instalación.
2. Fijar el SHA que se distribuirá y su CI; construir las imágenes para esa arquitectura. No reutilizar una imagen ARM64 de Mac esperando que funcione nativamente en Windows x64.
3. Exportar/importar imágenes con docker image save/load y crear un Compose de ejecución sin build. Entregar manual y metadatos junto al paquete.
4. Crear el instalador del sistema destino y los accesos directos; el motor es un prerrequisito explícito, no una instalación oculta.
5. Probar en un portátil limpio del mismo sistema: instalación, arranque por doble clic, navegación, cambio de rol, operaciones con datos sintéticos, parada y reinicio, persistencia, puertos ocupados, motor apagado y actualización conservando datos.
6. Desconectar internet y repetir el arranque, los escenarios de demostración y la consulta del manual. Documentar tiempos/tamaño medidos; no prometer cifras sin probarlas.
7. Entregar el instalador y una hoja de una página; una persona de proceso debe probar su uso sin ayuda de terminal.

## Decisiones pendientes

- Sistema operativo y arquitectura del portátil.
- Internet disponible solo para instalación o también instalación offline.
- Posibilidad de habilitar virtualización/instalar el motor y condiciones corporativas de Docker Desktop.
- Firma del instalador con certificado de la organización, si se desea evitar avisos de editor no reconocido; no eludir controles de seguridad.

## Referencias técnicas oficiales consultadas

- https://docs.docker.com/desktop/setup/install/windows-install/
- https://docs.docker.com/reference/cli/docker/image/save/
- https://docs.docker.com/reference/cli/docker/image/load/
- https://docs.docker.com/subscription-billing/desktop-license/
