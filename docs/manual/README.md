# Manual de usuario PROCO SIM-0.2

Abrir `index.html` en un navegador. El HTML es autocontenido: incluye estilos, JavaScript e imágenes y funciona sin internet. No necesita servidor ni instalación. La aplicación se usa por separado en http://localhost:18098 cuando el entorno UAT está activo.

Funciones del manual: índice por fase, búsqueda de capítulos, enlaces entre roles, diagramas de flujo, ampliación de capturas (Esc para cerrar) y vista de impresión/PDF. En móvil, el botón Índice despliega el menú. Una copia HTML aislada conserva todas las imágenes.

Base funcional: commit 91688b6, Sprints 1–6. Las capturas de `assets/` son fuentes de imágenes reales obtenidas en un entorno didáctico separado `proco-manual-91688b6` con datos ficticios. No se modificaron los datos del ambiente UAT para ilustrarlas. Las capturas muestran áreas de pantalla con desplazamiento; no se modificó su contenido. El ejemplo de respuesta perdida se recuperó con el mismo comando.

`assets/capturas.json` registra las capturas. `VERIFICACION.md` describe los controles realizados sobre el documento. Para regenerar el HTML desde el contenido fuente y las capturas, ejecutar `python3 generar.py` (solo biblioteca estándar). No constituye validación funcional de PROCO ni aprobación de reglas reales.
