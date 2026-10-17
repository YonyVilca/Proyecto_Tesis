-- D-34, RNF-28, RNF-23; HU-50, HU-52; RF-04, RF-06, RF-07, RF-64, RF-65, RF-67.
-- DC-01, DC-02; ER-01 a ER-03 (DD-14). Semilla sintética para desarrollo y pruebas.

INSERT INTO catalogo.eje (codigo, nombre)
VALUES ('A', 'Actividades básicas de la vida diaria')
ON CONFLICT DO NOTHING;

INSERT INTO catalogo.subgrupo (eje_id, codigo, nombre)
SELECT e.id, 'A1', 'Higiene personal'
FROM catalogo.eje e WHERE e.codigo = 'A'
ON CONFLICT DO NOTHING;

INSERT INTO catalogo.actividad
  (subgrupo_id, codigo, nombre, modalidad, banda, frecuencia_esperada, secuencia_relevante)
SELECT s.id, 'A-02', 'Ducharse', 'PASOS', 'DIARIA', 1, TRUE
FROM catalogo.subgrupo s WHERE s.codigo = 'A1'
ON CONFLICT DO NOTHING;

INSERT INTO catalogo.plantilla_pasos (actividad_id, version, vigencia_desde, vigencia_hasta)
SELECT a.id, 1, DATE '2026-01-01', NULL
FROM catalogo.actividad a WHERE a.codigo = 'A-02'
ON CONFLICT DO NOTHING;

INSERT INTO catalogo.paso (plantilla_id, orden, descripcion, critico)
SELECT p.id, v.orden, v.descripcion, v.critico
FROM catalogo.plantilla_pasos p
JOIN catalogo.actividad a ON a.id = p.actividad_id
CROSS JOIN (VALUES
  (1::SMALLINT, 'Prepara la ropa y la toalla', FALSE),
  (2::SMALLINT, 'Abre la ducha y regula el agua', TRUE),
  (3::SMALLINT, 'Se moja el cuerpo', FALSE),
  (4::SMALLINT, 'Se enjabona', FALSE),
  (5::SMALLINT, 'Se enjuaga', FALSE),
  (6::SMALLINT, 'Se seca y se viste', FALSE)
) AS v(orden, descripcion, critico)
WHERE a.codigo = 'A-02' AND p.version = 1
ON CONFLICT DO NOTHING;

INSERT INTO catalogo.escala_apoyo (version, nombre, vigencia_desde)
VALUES (1, 'Escala de apoyo de cuatro niveles', DATE '2026-01-01')
ON CONFLICT DO NOTHING;

INSERT INTO catalogo.nivel_apoyo
  (escala_id, codigo, nombre, descripcion_observable, peso, orden)
SELECT e.id, v.codigo, v.nombre, v.descripcion, v.peso, v.orden
FROM catalogo.escala_apoyo e
CROSS JOIN (VALUES
  ('INDEPENDIENTE', 'Independiente', 'Completa la actividad sin ayuda de otra persona.', 1.00::NUMERIC(3,2), 1::SMALLINT),
  ('VERBAL', 'Apoyo verbal', 'Completa la actividad después de recibir indicaciones verbales.', 0.67::NUMERIC(3,2), 2::SMALLINT),
  ('PARCIAL', 'Apoyo físico parcial', 'Completa parte de la actividad con ayuda física en algunos pasos.', 0.33::NUMERIC(3,2), 3::SMALLINT),
  ('TOTAL', 'Apoyo total', 'Completa la actividad con ayuda física durante todos los pasos.', 0.00::NUMERIC(3,2), 4::SMALLINT)
) AS v(codigo, nombre, descripcion, peso, orden)
WHERE e.version = 1
ON CONFLICT DO NOTHING;

INSERT INTO residentes.cuenta_acceso
  (codigo, nombre_completo, usuario, hash_contrasena, rol)
VALUES
  ('CUI-01', 'Cuidador Uno', 'cuidador1', '$2b$10$xj.WMN3Fqen9Fqx8bW1ucecQHhjPDg.3aJ6d19fDfkb9ft/wONwdS', 'CUIDADOR'),
  ('CUI-02', 'Cuidador Dos', 'cuidador2', '$2b$10$xj.WMN3Fqen9Fqx8bW1ucecQHhjPDg.3aJ6d19fDfkb9ft/wONwdS', 'CUIDADOR'),
  ('COO-01', 'Coordinadora Uno', 'coordinadora', '$2b$10$xj.WMN3Fqen9Fqx8bW1ucecQHhjPDg.3aJ6d19fDfkb9ft/wONwdS', 'COORDINADORA')
ON CONFLICT DO NOTHING;

INSERT INTO identidad.persona
  (dni, nombres, primer_apellido, segundo_apellido, fecha_nacimiento, sexo, estado_civil, diagnostico)
VALUES
  ('00000001', 'Ernesto', 'Quispe', 'Luna', DATE '1970-04-12', 'MASCULINO', 'SOLTERO', NULL),
  ('00000002', 'Juan', 'Rojas', 'Paredes', DATE '1965-09-23', 'MASCULINO', 'SOLTERO', NULL),
  ('00000003', 'Marta', 'Salazar', 'Cárdenas', DATE '1978-02-05', 'FEMENINO', 'SOLTERO', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO residentes.expediente (id, persona_id, fecha_ingreso, iniciales, color)
SELECT v.expediente_id::UUID, p.id, DATE '2025-06-01', v.iniciales, v.color
FROM (VALUES
  ('00000000-0000-0000-0000-000000000001', '00000001', 'EP', '#1F77B4'),
  ('00000000-0000-0000-0000-000000000002', '00000002', 'JQ', '#2CA02C'),
  ('00000000-0000-0000-0000-000000000003', '00000003', 'MR', '#D62728')
) AS v(expediente_id, dni, iniciales, color)
JOIN identidad.persona p ON p.dni = v.dni
ON CONFLICT DO NOTHING;

INSERT INTO residentes.tutoria (expediente_id, cuenta_id, tipo, fecha_inicio)
SELECT e.id, c.id, 'TITULAR', DATE '2026-01-01'
FROM (VALUES
  ('00000000-0000-0000-0000-000000000001', 'CUI-01'),
  ('00000000-0000-0000-0000-000000000002', 'CUI-01'),
  ('00000000-0000-0000-0000-000000000003', 'CUI-02')
) AS v(expediente_id, codigo_cuenta)
JOIN residentes.expediente e ON e.id = v.expediente_id::UUID
JOIN residentes.cuenta_acceso c ON c.codigo = v.codigo_cuenta
ON CONFLICT DO NOTHING;

INSERT INTO residentes.actividad_residente
  (id, expediente_id, actividad_id, estado, vigencia_desde)
SELECT v.actividad_residente_id::UUID, e.id, a.id, 'PRIORITARIA', DATE '2026-01-01'
FROM (VALUES
  ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001'),
  ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000002'),
  ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000003')
) AS v(actividad_residente_id, expediente_id)
JOIN residentes.expediente e ON e.id = v.expediente_id::UUID
JOIN catalogo.actividad a ON a.codigo = 'A-02'
ON CONFLICT DO NOTHING;
