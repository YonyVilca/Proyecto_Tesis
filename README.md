# PWA Hogar Protegido Virgen del Rosario

Aplicación web progresiva para el seguimiento del avance funcional de los residentes de un hogar protegido. Proyecto de tesis de Yony Alex Vilca Mamani.

Los cuidadores registran desde su teléfono, incluso sin señal, y la coordinadora ve el avance de cada residente por actividad. Este repositorio contiene la **primera etapa**, definida en la Especificación de Requisitos v1.2.1 y en el Diseño del Sistema v0.7.

## Estructura

```
backend/   Servidor Spring Boot 3 en Java 21; dominio y módulo de análisis.
db/        Migraciones de PostgreSQL 16 y pruebas del esquema.
db/desarrollo/  Datos sintéticos, solo para desarrollo y pruebas.
```

## Estado del avance

| Parte | Qué hay | Verificación |
| --- | --- | --- |
| Modelo de datos | Esquema completo de la primera etapa (V1) | 47 pruebas SQL |
| Turno declarado | Ventana por tipo, tolerancia de 15 minutos y encadenamiento (RB-11, RF-90) | `TurnoDeclaradoTest` |
| Registro | Jerarquía con método plantilla y fábrica; oportunidad, «no quiso», precarga y anulación | `RegistroTest` |
| Análisis | Indicadores, cobertura y estado visual con precedencia D-60; tendencia y CUSUM como estrategias | `EquivalenciaPrototipoTest` (60 vectores) |

## Cómo probar

Requiere Java 21, Maven y Docker. `docker compose up -d` inicia PostgreSQL 16 local
(base `hogar`, usuario `hogar`, contraseña `hogar_desarrollo`).

```bash
mvn -B -f backend/pom.xml verify
```

Para iniciar el servidor en desarrollo, desde el directorio `backend/` ejecuta:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

El perfil `dev` carga migraciones del classpath y los datos sintéticos desde
`../db/desarrollo`; por eso este comando debe ejecutarse desde `backend/`.
Las migraciones se empaquetan en el jar. Los datos de `db/desarrollo` se copian
solo al classpath de pruebas y no se incluyen en el artefacto de producción.

