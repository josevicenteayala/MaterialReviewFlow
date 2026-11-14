# Verificación del manual v1.0

Base funcional: MaterialReviewFlow 91688b6, Sprints 1–6. Manual didáctico en español; no es aceptación del procedimiento por PROCO.

## Controles ejecutados

- 24 capturas reales de Chromium, resolución1440×1000, con datos sintéticos de un proyecto Docker independiente `proco-manual-91688b6` (puertos55444/18101/18102).
- Capturas y ejemplos de operaciones generados exclusivamente en ese proyecto. El entorno UAT del usuario `proco-uat-91688b6` se mantuvo activo y no se utilizó para mutaciones o capturas.
- Contraste de nombres/opciones/permisos con main.tsx, NonConformities, Mixing, Executions, SupplierWorkflows y Monitoring; políticas de liberación/NC, API y requisitos SIM-0.2/UAT.
- Navegador abierto directamente mediante file://: 18 capítulos y 24 imágenes completas, sin imágenes rotas, anclas rotas, errores JavaScript ni peticiones HTTP externas al cargar/navegar el manual.
- Búsqueda de «reensayo», búsqueda sin resultados y restablecimiento correctos; enlaces internos, ampliación de imagen y cierre con Esc correctos.
- Vista390×844 sin desbordamiento horizontal de la página; menú móvil se abre y se cierra al navegar. Las tablas/diagramas tienen desplazamiento local.
- Render de impresión A4 mediante Chromium generado sin error; no se entrega ese PDF como una edición maquetada independiente.
- Revisión visual de portada en escritorio/móvil y de capturas de liberación, caso comercial y cierre de incidente; las capturas14/20 se reencuadraron para mostrar los botones de decisión. No se retocaron sus contenidos.
- Imágenes, estilos y JavaScript integrados en index.html; el ZIP contiene también capturas fuente y generar.py para mantenerlo.

HTML SHA-256: `4a378a35d9d0be00a5171d04aaea07b0c0def6499a7c8a003e2c2f1fa9421ff7`.

El proyecto exclusivo de capturas se detiene al finalizar conservando su volumen. UAT permanece activo para el usuario. No se cambiaron backend, frontend, migraciones ni reglas de negocio. Los ejemplos del manual no se declaran validación funcional de PROCO.
