---
name: diseno-programacion
description: Cómo escribir código en este proyecto según su diseño. Usar antes de crear o modificar clases del dominio, servicios, adaptadores o pruebas de la PWA Hogar.
---

# Diseño de programación del proyecto

## Antes de escribir

1. Ubica la historia y sus requisitos (`RF-`, `RB-`).
2. Ubica en el diseño la clase (DC-01 a DC-06), la secuencia (SD-) o la actividad (AD-) que la describen y la tabla del modelo de datos.
3. Si la operación que necesitas no está en el contrato de clases, detente y propón el cambio de diseño.

## Capas y paquetes (PD-01, PQ-01)

| Paquete | Contiene | Puede depender de |
| --- | --- | --- |
| `pe.hogar.dominio.comun` | Periodo, Auditable, Bitacora | nada |
| `pe.hogar.dominio.catalogo` | Actividad, PlantillaPasos, Paso, NivelApoyo | comun |
| `pe.hogar.dominio.residentes` | CuentaAcceso, Expediente, ActividadResidente | comun, catalogo |
| `pe.hogar.dominio.registro` | TurnoDeclarado, Registro y especializaciones, FabricaRegistro | comun, catalogo, residentes |
| `pe.hogar.dominio.analisis` | ServicioAnalisis, estrategias, indicadores | registro, catalogo, comun |
| `pe.hogar.aplicacion` | Servicios de control, transacciones | dominio |
| `pe.hogar.infraestructura` | Persistencia, archivos, documentos | dominio |

El dominio no importa Spring, JDBC ni HTTP.

## Patrones y dónde se aplican

| Patrón | Regla de código |
| --- | --- |
| Método plantilla (PD-08) | Lo común va en `Registro`; cada especialización implementa `calcularAutonomia` y `esPuntuable`. |
| Método fábrica (PD-12) | Los constructores de `Registro` son de paquete; solo `FabricaRegistro.crear` los usa. |
| Estrategia (PD-11) | Tendencia y retroceso se inyectan por interfaz; sin ellas el núcleo no calcula ESTANCADA ni RETROCEDE. Nunca un `if` por parte de la etapa. |
| Objeto valor (PD-07) | Sin identidad propia: `record` inmutable (Periodo, NivelApoyo, Paso). |
| Registro de auditoría (PD-14) | Toda anulación o corrección devuelve un asiento de `Bitacora`. |
| Borrado lógico (PD-15) | Nada se elimina: un registro anulado cambia de estado. |
| Receptor idempotente (PD-05) | El UUID lo genera el teléfono; un reenvío responde DUPLICADO. |
| Inyección de dependencias (PD-16) | Las dependencias entran por el constructor y son interfaces. |

## Reglas de estilo

- Nombres del contrato, en español, sin traducir ni abreviar.
- Las invariantes se validan en el constructor o en la operación que las cambia; un objeto inválido no existe.
- Las operaciones verifican el estado del registro (ST-01) antes de cambiarlo.
- Javadoc corto que cite la regla que implementa (`RB-05`, `D-41`).

## Pruebas

- Una prueba por regla, nombrada en español por su comportamiento: `conOportunidadElNivelEsObligatorio`.
- El análisis debe reproducir los vectores del prototipo dentro de 1e-6.
- Datos sintéticos siempre (D-34).
