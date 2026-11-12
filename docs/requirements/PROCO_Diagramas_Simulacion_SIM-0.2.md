# PROCO — Diagramas de simulación SIM-0.2

**Borrador para simulación — no proceso operativo aprobado.**
Todos los flujos incluyen reglas [P] propuestas. Los esquemas expresan decisiones de negocio; Mermaid no es un motor de ejecución de BPMN. Los estados de material, inventario y mezcla deben persistirse por separado.

## D1. Proceso multiactor de extremo a extremo

```mermaid
flowchart TD
 A[Almacén: recibir y registrar] --> B[Inventario en cuarentena]
 B --> C[Calidad: validar etiquetas y trazabilidad]
 C --> D{¿Datos críticos correctos?}
 D -- No --> H[Calidad: retener y solicitar subsanación]
 H --> C
 D -- Sí --> E[Laboratorio: ensayar y revisar]
 E --> F{¿Cumple requisitos?}
 F -- No --> N[Calidad: confirmar retención o rechazo]
 F -- Sí --> G[Laboratorio: liberar técnicamente]
 G --> I[Almacén: habilitar disponibilidad si no hay bloqueo]
 I --> J{¿Habrá mezcla?}
 J -- No --> K[Uso individual autorizado]
 J -- Sí --> L[Producción: solicitud con lotes y montaje]
 L --> M[GEO, Calidad, Laboratorio y Tintorería si aplica: evaluar]
 M --> O{¿Aprobación de Producción?}
 O -- Sí --> P[Tejeduría: montaje autorizado]
 O -- No --> Q[Mezcla denegada: uso individual si elegible]
```

## D2. Estados técnicos del material

```mermaid
stateDiagram-v2
 [*] --> RECIBIDO
 RECIBIDO --> EN_VERIFICACION: Registro de Almacén
 EN_VERIFICACION --> RETENIDO: Falta información
 EN_VERIFICACION --> EN_PRUEBAS: Calidad conforme
 EN_PRUEBAS --> RETENIDO: Prueba pendiente/duda
 EN_PRUEBAS --> LIBERADO: Aprobador Lab + evidencia
 EN_PRUEBAS --> RECHAZADO: Calidad + dictamen
 RETENIDO --> EN_PRUEBAS: Subsanación/reevaluación
 RETENIDO --> RECHAZADO: Calidad formaliza
 LIBERADO --> LIBERADO: Nueva NC bloquea inventario, no borra historia
 RECHAZADO --> [*]
```

## D3. Estados independientes de disponibilidad y mezcla

```mermaid
stateDiagram-v2
 state Inventario {
  [*] --> CUARENTENA
  CUARENTENA --> DISPONIBLE: Material liberado y sin retenciones
  CUARENTENA --> BLOQUEADO: Hallazgo preventivo
  DISPONIBLE --> BLOQUEADO: NC posterior
  BLOQUEADO --> DISPONIBLE: Calidad cierra NC + liberación válida
  BLOQUEADO --> BAJA: Rechazo / devolución
 }
 state SolicitudMezcla {
  [*] --> NO_SOLICITADA
  NO_SOLICITADA --> EN_EVALUACION: Solicitud
  EN_EVALUACION --> AUTORIZADA: Lotes elegibles + conceptos + aprobación
  EN_EVALUACION --> DENEGADA: Evidencia insuficiente/incompatibilidad
  AUTORIZADA --> REVOCADA: Nueva NC o cambio de combinación
  DENEGADA --> EN_EVALUACION: Nueva solicitud / reevaluación
  REVOCADA --> EN_EVALUACION: Nueva validación
 }
```

## D4. Matriz de autoridad como flujo de bloqueo y rechazo

```mermaid
flowchart TD
 A[Cualquier área detecta anomalía] --> B[Bloqueo preventivo inmediato + evento]
 B --> C[Almacén confirma segregación física simulada]
 B --> D[Calidad ratifica alcance de retención]
 D --> E[Laboratorio/GEO/Producción evalúan según naturaleza]
 E --> F{¿Se subsana?}
 F -- Sí --> G[Laboratorio revisa cumplimiento; Calidad cierra NC]
 G --> H[Almacén restablece disponibilidad si liberación vigente]
 F -- No --> I[Calidad formaliza rechazo]
 I --> J[Abastecimiento gestiona proveedor ficticio]
 I --> K[Almacén registra baja/devolución simulada]
```

## D5. Secuencia de autorización de mezcla

```mermaid
sequenceDiagram
 actor T as Tejeduría
 participant S as Simulador
 actor L as Laboratorio
 actor G as GEO
 actor TI as Tintorería
 actor P as Producción (aprobador)
 T->>S: Solicita mezcla(lotes, cantidades, montaje)
 S->>S: Verifica calidad, disponibilidad, trazabilidad
 alt Algún lote no elegible
   S-->>T: 422: mezcla no solicitada; motivo
 else Lotes elegibles
   S->>L: Solicita concepto / ensayos
   S->>G: Solicita concepto operacional
   opt Riesgo tintóreo
     S->>TI: Solicita concepto tintóreo
   end
   L-->>S: Concepto técnico
   G-->>S: Concepto operacional
   TI-->>S: Concepto tintóreo cuando corresponda
   S->>P: Tarea de aprobación
   P->>S: Autoriza o deniega con motivo
   S-->>T: Resultado y evidencia, ligado a montaje
 end
```

## D6. AS-IS documental frente al simulador TO-BE

```mermaid
flowchart LR
 subgraph ASIS[Base documental v01]
 A1[Recepción y muestras] --> A2[Validaciones y pruebas] --> A3[Informe, liberación y/o NC] --> A4[Compatibilidad en producción]
 end
 subgraph SIM[Simulación SIM-0.2]
 B1[ID y cuarentena] --> B2[Tareas + pruebas + evidencia] --> B3[Decisión técnica versionada] --> B4[Disponibilidad independiente] --> B5[Mezcla por montaje] --> B6[Alertas, NC y auditoría]
 end
 A4 -. Diseño propuesto, no proceso aprobado .-> B1
```

## D7. Notificaciones y datos de trazabilidad

```mermaid
flowchart TD
 A[Evento de negocio] --> B[(Bitácora de auditoría: quién / qué / cuándo / por qué)]
 A --> C[(Estado de flujo + tarea)]
 C --> D[Bandeja de área responsable]
 C --> E[Alertas internas + acuses]
 B --> F[Expediente del material o mezcla]
 E --> F
 F --> G[Reporte de simulación]
```
