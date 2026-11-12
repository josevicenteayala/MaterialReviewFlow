# Verificación de la entrega 1

Ejecutada localmente el 11 de noviembre de 2026 (America/Bogota). No representa validación operativa de PROCO.

| Comprobación | Resultado |
|---|---|
| Java 21.0.9 y Maven, suite backend | 7 pruebas, 0 fallos, 0 errores, 0 omitidas |
| PostgreSQL real | PostgreSQL 17.11 en Docker; base aislada proco_test |
| Flyway | V1 esquema y V2 catálogo aplicadas en base vacía; reinicios validan y no duplican migraciones |
| Frontend | TypeScript estricto y build Vite 7.3.8 correctos |
| Dependencias npm | 0 vulnerabilidades reportadas tras actualizar Vite |
| Docker Compose | PostgreSQL healthy, backend Java 21 iniciado, frontend Nginx iniciado en 8088 |
| Chromium / Playwright | S01 completo: 1 prueba aprobada en 6,1 segundos |
| Revisión visual | Captura completa de expediente disponible sin solapamientos a 1280 px; seis eventos legibles |

## Evidencia automatizada

`WorkflowIntegrationTest` ejecuta solicitudes HTTP contra el servidor Spring real y consulta PostgreSQL para confirmar persistencia e invariantes:

1. Recepción de hilo → inspección → muestra → resistencia conforme → aprobación independiente → disponibilidad; seis eventos y cuatro identidades.
2. Ciclo de elastano con criterio propio de elongación, resultado 500 %, liberación y disponibilidad.
3. Rol indebido 403, disponibilidad anticipada 422, liberación sin ensayo 422 y versión antigua 409; eventos COMMAND_DENIED persistidos y estado/versionado sin avance indebido.
4. Hallazgo documental, retención/bloqueo, reinspección, ensayo fallido, nueva retención y nuevo ciclo favorable; conservación de ambos ensayos en eventos históricos.
5. Mismo comando y clave produce una sola recepción; clave reutilizada con contenido distinto 409; dos inspecciones concurrentes con misma versión producen 200 y 409.
6. Sin autenticación 401, cuerpo inválido 400, saldo de OC excedido 422 y SQL UPDATE/DELETE sobre auditoría rechazado por trigger.

`LaboratoryPolicyTest` comprueba límites inclusivos, valor fuera de rango y prohibición de aprobar un resultado propio.

`frontend/e2e/material.spec.ts` cambia entre cuatro identidades en el navegador, registra muestra, verifica que la aprobación sin ensayo está deshabilitada y completa el ciclo. Captura producida: `frontend/test-results/s01-available.png` (archivo local generado, excluido de git). El primer intento de esta prueba seleccionó por defecto una OC de elastano mientras esperaba el campo de resistencia de hilo; se corrigió el caso para seleccionar explícitamente OC-H-001 y la repetición pasó.

## Cobertura respecto de SIM-0.2

- P01–P05: implementados bajo los supuestos de `PLAN.md`.
- BR-01, BR-03–05, BR-10 y BR-12: controles presentes para el ciclo inicial.
- BR-02: hallazgo o ensayo fallido retiene y bloquea en la misma transacción; comunicaciones con acuse pendientes.
- BR-09 y BR-13: no se aplican umbrales temporales ni aprobaciones automáticas.
- S01 completo; S02 y S04–S05 parciales según alcance. S03 usa guarda de saldo, sin resolución comercial de diferencias. S06–S10 completos quedan pendientes.

CI publicada en `.github/workflows/verify.yml`. La primera ejecución en GitHub aprobó las 7 pruebas backend y la compilación frontend; el instalador de Chromium falló porque `ubuntu-latest` seleccionó Ubuntu 26.04, no compatible con Playwright 1.56.1. Se fijó el runner en Ubuntu 24.04 para mantener compatibilidad reproducible. No se verificaron integraciones corporativas, adjuntos, SSO, mezclas, consumo ni NC posterior porque no forman parte de la primera entrega.
