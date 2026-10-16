# PWA Hogar Protegido Virgen del Rosario

Aplicación web progresiva para el seguimiento del avance funcional de los residentes de un hogar protegido. Proyecto de tesis de Yony Alex Vilca Mamani.

Los cuidadores registran desde su teléfono, incluso sin señal, y la coordinadora ve el avance de cada residente por actividad. Este repositorio contiene la **primera etapa**, definida en la Especificación de Requisitos v1.2.1 y en el Diseño del Sistema v0.7.

## Estructura

```
backend/   Servidor en Java 21. Por ahora, el dominio y el módulo de análisis.
db/        Migraciones de PostgreSQL 16 y pruebas del esquema.
```

## Estado del avance

| Parte | Qué hay | Verificación |
| --- | --- | --- |
| Modelo de datos | Esquema completo de la primera etapa (V1) | 47 pruebas SQL |
| Turno declarado | Ventana por tipo, tolerancia de 15 minutos y encadenamiento (RB-11, RF-90) | `TurnoDeclaradoTest` |
| Registro | Jerarquía con método plantilla y fábrica; oportunidad, «no quiso», precarga y anulación | `RegistroTest` |
| Análisis | Indicadores, cobertura y estado visual con precedencia D-60; tendencia y CUSUM como estrategias | `EquivalenciaPrototipoTest` (60 vectores) |

## Cómo probar

Requiere Java 21, Maven y PostgreSQL 16, o abrir el repositorio en GitHub Codespaces.

```bash
mvn -B -f backend/pom.xml verify
createdb hogar
psql -d hogar -v ON_ERROR_STOP=1 -f db/migration/V1__esquema_inicial.sql
psql -d hogar -v ON_ERROR_STOP=1 -f db/pruebas/pruebas_esquema.sql
```

Todos los datos de prueba son sintéticos.
