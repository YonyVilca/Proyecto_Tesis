# Instrucciones para agentes de código

Rigen para cualquier asistente de IA que trabaje en este repositorio (Claude, OpenCode, Cline u otro).

## Fuentes de verdad

El código implementa documentos que no viven en el repositorio:

- Especificación de Requisitos v1.2.1 (congelada): requisitos `RF-`, `RNF-`, reglas `RB-` y decisiones `D-`.
- Diseño del Sistema v0.7.x: clases `DC-`, secuencias `SD-`, actividad `AD-`, estados `ST-`, patrones `PD-`, decisiones `DD-`.
- Especificación del Módulo de Análisis v1.1: fórmulas `EM-` del análisis.

Si una tarea no indica qué requisito y qué diagrama implementa, se pregunta antes de escribir código.

## Reglas

1. **El diseño manda.** Clases, operaciones, firmas y nombres siguen el contrato de clases del diseño. Si el código necesita otra firma u operación, primero se actualiza el diseño y luego el código.
2. **Los identificadores no se reutilizan** (`RF-`, `D-`, `PD-`, etc.). Se citan en el Javadoc de lo que implementa cada regla.
3. **Solo datos sintéticos** (D-34). Nombres ficticios (RNF-28). Nunca datos reales de residentes, ni en pruebas ni en ejemplos (Ley N.° 29733).
4. **El dominio no depende de ningún framework** (`backend/src/main/java/pe/hogar/dominio`). Spring, la base y la red van en otras capas (PD-01).
5. **Ningún archivo de más.** Cada archivo nuevo responde a una historia del backlog.
6. **Las migraciones aplicadas no se editan.** Un cambio de esquema es una migración nueva `V<n>__descripcion.sql`.
7. **Una historia, un cambio.** Commits con el requisito: `feat(RF-17): ...`, `test(RB-06): ...`, `fix(RB-11): ...`.
8. **El agente no publica.** Implementa, verifica que pasen las pruebas
   (mvn -B -f backend/pom.xml verify) y crea los commits en la rama local.
   Nunca ejecuta git push ni crea el Pull Request; eso lo hace el tesista.

## Comandos

```bash
mvn -B -f backend/pom.xml verify                     # pruebas del dominio y del análisis
psql -v ON_ERROR_STOP=1 -f db/migration/V1__esquema_inicial.sql
psql -v ON_ERROR_STOP=1 -f db/pruebas/pruebas_esquema.sql
cd frontend && npm ci
cd frontend && npm test
cd frontend && npm run build
```

## Pruebas

- Cada regla de negocio tiene su prueba unitaria, nombrada por lo que verifica.
- El análisis se verifica contra los vectores del prototipo (`backend/src/test/resources/vectores`). Si un vector falla, el error está en la traducción, no en el vector.
- Quien implementa no aprueba su propio cambio: la revisión la hace otra persona u otro agente.
