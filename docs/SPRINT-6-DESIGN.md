# Sprint 6 — diseño previo, decisiones y riesgos

Base main 29859588d98a52224ef5dceb475c472fb234b6d7 (PR #4 integrado). [CI exacta del merge aprobada](https://github.com/josevicenteayala/MaterialReviewFlow/actions/runs/43484650371). Se conserva PROCO_SPRINT6_PLAN.md y se copia al nombre canónico solicitado. Requisitos de dominio contrastados: SIM-0.2 S03/S10, MVP rol PROCUREMENT/decisión comercial, diagramas D4 y docs/API/PLAN/Sprints1–5. Las propuestas del plan no autorizan movimientos físicos ni comunicaciones reales.

## Entregas

1. Diseño previo y riesgos (este documento).
2. P0 S03: V7, expediente de pre-recepción, confirmación/Quality condicional, autorización comercial y aceptación transaccional reutilizando recepción; API/tests.
3. P0 S10: caso de proveedor atómico con rechazo, vínculo a tareas existentes, pasos comerciales y cierre independiente; API/tests.
4. UI multiactor y reintentos inciertos, trazabilidad mínima P1 por OC/NC/lote/caso/tareas.
5. Regresión ≥64 backend/21 navegador, upgrade V6→V7, Compose aislado y persistencia; documentación/PR sin merge.

No se incluyen métricas/OLA nuevos para casos ni exportaciones nuevas de casos: siguen disponibles los mecanismos previos de tareas/material. Sin ERP, stock físico, transporte, crédito real ni emails.

## S03 — cantidades y autoridad

- OC.expected_quantity sigue significando kg máximos registrados como lotes. Saldo = expected_quantity − SUM(lot.quantity), incluso si un lote está rechazado: un cierre comercial no repone saldo. No se modifica OC ni cantidades de lotes históricos.
- Pre-recepción excepcional registra documento y observación >=0 (máximo3 decimales), documento/ref/proveedor-lote/fecha/ubicación/evidencia. Debe existir diferencia numérica, exceso sobre saldo o hallazgo técnico explícito. Observado puede exceder saldo sin crear lote; 0 observado permite no aceptación.
- Documento/observación/datos originales y saldo al reporte son inmutables. La proyección muestra falta documental, sobrante documental, exceso sobre saldo reportado y saldo actual. No inferir NC técnica de una diferencia aritmética.
- OPEN → UNDER_REVIEW por confirm-count de WAREHOUSE, con evidencia. Capturador puede confirmar su conteo; no se exige segunda cuenta de Almacén. Correcciones de observaciones requieren cancelar y crear otro expediente.
- QUALITY emite un único concepto FAVORABLE/UNFAVORABLE solo si technical_finding=true. No es liberación técnica del lote futuro. Desfavorable impide autorización; corrección mediante nuevo caso. No se crea NC ficticia antes de existir lote.
- PROCUREMENT review autoriza propuesta >=0, <=observado y <=saldo actual, con plan explícito del remanente si observado>propuesta. Requiere conteo confirmado, concepto favorable cuando aplica, identidad diferente del reportante/confirmante/Calidad. Estado AUTHORIZED. Una propuesta autorizada puede revisarse explícitamente por PROCUREMENT antes de aceptarse; cada versión queda en auditoría. No reserva saldo.
- WAREHOUSE accept vuelve a bloquear OC y revalida saldo/duplicado/fecha bajo lock. Si saldo cambió, 422 sin lote ni resolución; Procurement puede revisar propuesta con nueva versión. Cantidad>0 crea exactamente un lote UNDER_VERIFICATION/QUARANTINE mediante ReceiptService extraído de WorkflowService.create, conservando reglas, fingerprint y contrato normal.
- Resultado: ACCEPTED_OBSERVED si acepta todo lo observado, ACCEPTED_PARTIAL si acepta parte, EXCESS_NOT_ACCEPTED si acepta0. accepted_quantity y observed−accepted distinguen cantidad ingresada del remanente no aceptado, con plan documental de segregación SOLO SIMULACIÓN. Un faltante documental no se inventa como remanente físico. No hay stock para los kilos restantes.
- Cancelación explícita: WAREHOUSE solo OPEN; PROCUREMENT UNDER_REVIEW/AUTHORIZED. CANCELLED_WITH_REASON terminal, sin lote. Estados resueltos inmutables. El lote aceptado sigue el ciclo técnico normal, no se libera ni mezcla automáticamente.

## S10 — caso comercial separado

- Nueva decisión NC REJECTED/RETURN_TO_SUPPLIER crea supplier_case único por NC dentro de la misma transacción, después de revocación/contención existentes. SCRAP no crea caso de devolución.
- Vincula todas las tareas existentes REJECTION_COORDINATION/PROCUREMENT de esa NC (puede haber varias ejecuciones). No crea tareas duplicadas. Si no hubo montaje, no existe tarea V5 con execution_id y el caso es la bandeja comercial: no se inventa una ejecución ni una tarea incompatible.
- OPEN → SUPPLIER_CONTACT_RECORDED → RESPONSE_RECORDED → DISPOSITION_DOCUMENTED → CLOSED_ADMINISTRATIVELY. Acciones PROCUREMENT con versión, motivo, referencia simulada y evidencia. Respuesta ACCEPTED/DISPUTED/NO_RESPONSE; NO_RESPONSE solo explícito. Orientación RETURN_REQUESTED/CREDIT_REQUESTED/OTHER; disputas/sin respuesta exigen tratamiento documentado.
- physical_return_confirmed=false y erp_posted=false protegidos por CHECK, incluso al cerrar. Cierre administrativo no implica devolución realizada, liberación, crédito ni recuperación de saldo.
- Tarea de coordinación y expediente son independientes: el acuse/cierre de la tarea conserva su contrato previo. Completar tarea no cierra el caso; cerrar caso exige pasos comerciales completos y todas sus tareas vinculadas CLOSED. Sin dependencia circular ni cambio de los tests previos de tareas.
- Segundo usuario ficticio abastecimiento2 (PROCUREMENT) para cierre independiente del autor de la disposición. El rechazo sigue siendo responsabilidad QUALITY independiente; Abastecimiento no toca NC/lot para gestionar proveedor.
- Históricos: V7 no crea casos por backfill. Procurement puede adoptar explícitamente una NC histórica RETURN_TO_SUPPLIER mediante POST /supplier-cases con lotId/ncId y versión de lote, bajo locks y auditoría; no modifica lote/NC. Caso único/linked tasks; no adopta SCRAP ni lote no rechazado.

## Esquema, clases y transacciones

V7 aditiva: receipt_discrepancy, supplier_case, supplier_case_task y supplier_workflow_event (ledger único con FK tipadas; prevent_audit_change). command_result añade discrepancy_id/supplier_case_id y XOR extendido. V1–V6 y filas anteriores intactas, sin migración de negocio silenciosa.

ReceiptService contiene la creación actual, MANDATORY dentro de CommandRunner. WorkflowService delega conservando Command y huella exacta. Nuevo módulo supplier: comandos validados, políticas, stores, servicios de diferencia y caso, registro atómico de caso, controllers. CommandRunner añade targets internos fijos; AuditLog registra éxitos/denegaciones SIM-6 usando patrón existente. NcService invoca registro después de su decisión y antes de confirmar lote/auditoría. ExecutionContainment no se duplica ni se cambia para fabricar tareas sin montaje.

Locks S03: clave → purchase_order → diferencia → nuevo lote. Comparte la fila OC con recepción normal; no adquiere lotes históricos. Sin saldo reservado entre pasos.

Locks S10: clave → lote → (NC serializada por lote) → mezclas/ejecuciones/tareas del rechazo existente → caso. Comandos comerciales/adopción bloquean solo su lote, tareas vinculadas ordenadas y luego caso. No adquieren OC ni otro lote después, evitando inversión con TaskService/ejecución. Caso/tarea links son inmutables; cierre lee bajo lock el estado actual de tareas. Fallo de caso/evento revierte solo la decisión nueva íntegra, conserva el hold/NC previos.

Listas acotadas (máximo100, filtros por OC/estado/lote) y detalle/timeline por UUID, consultas agrupadas sin N+1 para enlaces. Cliente usa la misma persistencia sessionStorage de clave/ruta/cuerpo y discrimina resultados de ambos agregados para no insertarlos en la bandeja de lotes.

## Riesgos y validación

- Saldo cambia entre review/accept: denegar sin pérdida de evidencia, revisión explícita de propuesta; probar contra recepción normal y contra otro accept con barreras PostgreSQL.
- Doble resolución/duplicado proveedor-lote: versión, OC lock, constraints y creación única. Probar replay idéntico y clave reutilizada incompatible.
- Rollback al crear lote/auditoría o supplier_case: inyección de fallo SQL y reintento exacto; sin datos parciales.
- Rechazos con múltiples tareas o sin ejecución: vincular todas o ninguna, nunca fabricar movimientos. Probar SCRAP, disputa, no respuesta, cierre independiente y carreras con tareas/caso.
- Upgrade sintético V6→V7 compara todas las filas históricas (incluye rechazo previo sin caso); no edita schemas previos. Regresión original se mantiene sin omitir casos.
- Persistencia tras reinicio se ejecuta solo al terminar Maven/navegador, nunca reiniciando su PostgreSQL en paralelo.

Autoridades operativas, tolerancias contractuales, custodia física y gestión contable reales requieren validación humana fuera de esta demo. Estas son decisiones explícitas del prototipo, no procedimientos ratificados.
