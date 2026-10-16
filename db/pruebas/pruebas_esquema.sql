-- Pruebas del modelo fisico v0.7. Datos sinteticos (D-34); nombres ficticios (RNF-28).
\set ON_ERROR_STOP 1
CREATE TEMP TABLE resultado (n serial, prueba text, esperado text, obtenido text);

CREATE FUNCTION pg_temp.debe_fallar(prueba text, sentencia text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE s text;
BEGIN
  BEGIN
    FOREACH s IN ARRAY string_to_array(sentencia, '; ') LOOP EXECUTE s; END LOOP;
    EXECUTE 'RESET ROLE';
    INSERT INTO resultado (prueba, esperado, obtenido) VALUES (prueba, 'rechazo', 'ACEPTADO');
  EXCEPTION WHEN OTHERS THEN
    INSERT INTO resultado (prueba, esperado, obtenido) VALUES (prueba, 'rechazo', 'rechazo: ' || SQLERRM);
  END;
END $$;
CREATE FUNCTION pg_temp.debe_pasar(prueba text, sentencia text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE s text;
BEGIN
  BEGIN
    FOREACH s IN ARRAY string_to_array(sentencia, '; ') LOOP EXECUTE s; END LOOP;
    EXECUTE 'RESET ROLE';
    INSERT INTO resultado (prueba, esperado, obtenido) VALUES (prueba, 'acepta', 'acepta');
  EXCEPTION WHEN OTHERS THEN
    INSERT INTO resultado (prueba, esperado, obtenido) VALUES (prueba, 'acepta', 'FALLA: ' || SQLERRM);
  END;
END $$;

-- Datos base (carga por script, como en el nucleo)
INSERT INTO catalogo.eje (codigo, nombre) VALUES ('A', 'Actividades basicas de la vida diaria'), ('B', 'Actividades instrumentales');
INSERT INTO catalogo.subgrupo (eje_id, codigo, nombre) VALUES (1, 'A1', 'Higiene'), (2, 'B1', 'Salidas');
INSERT INTO catalogo.actividad (subgrupo_id, codigo, nombre, modalidad, banda) VALUES
  (1, 'A-02', 'Ducha', 'PASOS', 'DIARIA'),
  (2, 'B-08', 'Salida a la comunidad', 'EVENTO', 'ESPORADICA'),
  (1, 'A-12', 'Higiene menstrual', 'PASOS', 'DIARIA');
UPDATE catalogo.actividad SET aplicabilidad_sexo = 'FEMENINO' WHERE codigo = 'A-12';
INSERT INTO catalogo.plantilla_pasos (actividad_id, version, vigencia_desde) VALUES (1, 1, '2026-01-01');
INSERT INTO catalogo.paso (plantilla_id, orden, descripcion) VALUES (1, 1, 'Prepara la ropa'), (1, 2, 'Abre la ducha'), (1, 3, 'Se enjabona');
INSERT INTO catalogo.escala_apoyo (version, nombre, vigencia_desde) VALUES (1, 'Escala de cuatro niveles', '2026-01-01');
INSERT INTO catalogo.nivel_apoyo (escala_id, codigo, nombre, descripcion_observable, peso, orden) VALUES
  (1, 'INDEPENDIENTE', 'Independiente', 'Lo hace sin ayuda', 1.00, 1),
  (1, 'VERBAL', 'Verbal', 'Necesita indicaciones', 0.67, 2),
  (1, 'PARCIAL', 'Parcial', 'Necesita ayuda fisica en parte', 0.33, 3),
  (1, 'TOTAL', 'Total', 'Lo hace el cuidador', 0.00, 4);
INSERT INTO residentes.cuenta_acceso (codigo, nombre_completo, usuario, hash_contrasena, rol) VALUES
  ('C01', 'Cuidadora Ficticia Uno', 'cuid1', 'x', 'CUIDADOR'),
  ('C02', 'Cuidador Ficticio Dos', 'cuid2', 'x', 'CUIDADOR'),
  ('K01', 'Coordinadora Ficticia', 'coord', 'x', 'COORDINADORA');
INSERT INTO identidad.persona (dni, nombres, primer_apellido, fecha_nacimiento, sexo, estado_civil, diagnostico)
  VALUES ('10000001', 'Ernesto', 'Quispe', '1990-05-01', 'MASCULINO', 'SOLTERO', 'dato restringido');
INSERT INTO residentes.expediente (id, persona_id, fecha_ingreso, iniciales, color)
  VALUES ('00000000-0000-0000-0000-0000000000e1', 1, '2025-01-10', 'EQ', '#1F5F8B');
INSERT INTO residentes.actividad_residente (id, expediente_id, actividad_id, estado, vigencia_desde) VALUES
  (gen_random_uuid(), '00000000-0000-0000-0000-0000000000e1', 1, 'PRIORITARIA', '2026-01-01');
INSERT INTO residentes.tutoria (expediente_id, cuenta_id, tipo, fecha_inicio) VALUES ('00000000-0000-0000-0000-0000000000e1', 1, 'TITULAR', '2025-01-10');
INSERT INTO registro.turno_declarado (id, cuenta_id, tipo, declarado_en, inicio_ventana, fin_ventana) VALUES
  ('00000000-0000-0000-0000-0000000000a1', 1, 'M', now() - interval '3 hours', now() - interval '3 hours', now() + interval '3 hours');

-- Identidad (D-65, D-64)
SELECT pg_temp.debe_fallar('DNI duplicado', $q$INSERT INTO identidad.persona (dni, nombres, primer_apellido, fecha_nacimiento, sexo, estado_civil) VALUES ('10000001','Otro','Nombre','1980-01-01','MASCULINO','SOLTERO')$q$);
SELECT pg_temp.debe_fallar('DNI con letras', $q$INSERT INTO identidad.persona (dni, nombres, primer_apellido, fecha_nacimiento, sexo, estado_civil) VALUES ('1234567A','Otro','Nombre','1980-01-01','MASCULINO','SOLTERO')$q$);
SELECT pg_temp.debe_fallar('Dos expedientes activos de la misma persona', $q$INSERT INTO residentes.expediente (id, persona_id, fecha_ingreso, iniciales, color) VALUES (gen_random_uuid(), 1, '2026-02-01', 'EQ', '#AA0000')$q$);
SELECT pg_temp.debe_fallar('Dos titulares a la vez', $q$INSERT INTO residentes.tutoria (expediente_id, cuenta_id, tipo, fecha_inicio) VALUES ('00000000-0000-0000-0000-0000000000e1', 2, 'TITULAR', '2026-03-01')$q$);
SELECT pg_temp.debe_fallar('Cobertura sin fin', $q$INSERT INTO residentes.tutoria (expediente_id, cuenta_id, tipo, motivo_cobertura, fecha_inicio) VALUES ('00000000-0000-0000-0000-0000000000e1', 2, 'COBERTURA', 'VACACIONES', '2026-03-01')$q$);
SELECT pg_temp.debe_pasar('Cobertura con inicio, fin y motivo', $q$INSERT INTO residentes.tutoria (expediente_id, cuenta_id, tipo, motivo_cobertura, fecha_inicio, fecha_fin) VALUES ('00000000-0000-0000-0000-0000000000e1', 2, 'COBERTURA', 'VACACIONES', '2026-03-01', '2026-03-10')$q$);
SELECT pg_temp.debe_fallar('No corresponde sin motivo', $q$INSERT INTO residentes.actividad_residente (id, expediente_id, actividad_id, estado, vigencia_desde) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000e1', 3, 'NO_CORRESPONDE', '2026-01-01')$q$);
SELECT pg_temp.debe_fallar('Dos estados vigentes de la misma actividad', $q$INSERT INTO residentes.actividad_residente (id, expediente_id, actividad_id, estado, vigencia_desde) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000e1', 1, 'MANTENIMIENTO', '2026-02-01')$q$);

-- Turno (RB-11, RF-90)
SELECT pg_temp.debe_fallar('Turno de descanso', $q$INSERT INTO registro.turno_declarado (id, cuenta_id, tipo, declarado_en, inicio_ventana, fin_ventana) VALUES (gen_random_uuid(), 2, 'D', now(), now(), now() + interval '6 hours')$q$);
SELECT pg_temp.debe_fallar('Turnos solapados del mismo cuidador', $q$INSERT INTO registro.turno_declarado (id, cuenta_id, tipo, declarado_en, inicio_ventana, fin_ventana) VALUES (gen_random_uuid(), 1, 'T', now(), now(), now() + interval '6 hours')$q$);
SELECT pg_temp.debe_pasar('Encadenar amplia la ventana', $q$UPDATE registro.turno_declarado SET fin_ventana = fin_ventana + interval '6 hours' WHERE id = '00000000-0000-0000-0000-0000000000a1'$q$);

-- Registro

-- Registro, pasos, anulacion, evidencia y privilegios
SELECT pg_temp.debe_pasar('Registro por pasos valido', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000b1', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', 2, 'SI', NULL, false, NULL, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds'); INSERT INTO registro.registro_paso (registro_id, plantilla_id, orden, cumplido) VALUES ('00000000-0000-0000-0000-0000000000b1', 1, 1, true); INSERT INTO registro.registro_paso (registro_id, plantilla_id, orden, cumplido) VALUES ('00000000-0000-0000-0000-0000000000b1', 1, 2, true); INSERT INTO registro.registro_paso (registro_id, plantilla_id, orden, cumplido) VALUES ('00000000-0000-0000-0000-0000000000b1', 1, 3, false)$q$);
SELECT pg_temp.debe_fallar('Reenvio con el mismo UUID (la API responde DUPLICADO)', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000b1', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', 2, 'SI', NULL, false, NULL, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_fallar('Registro con hora de captura fuera de la ventana', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000b2', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', 2, 'SI', NULL, false, NULL, now() - interval '5 hours', now() - interval '5 hours', now() - interval '5 hours' + interval '40 seconds')$q$);
SELECT pg_temp.debe_pasar('Registro dentro de la tolerancia de 15 minutos', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000b3', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', 2, 'SI', NULL, false, NULL, now() - interval '3 hours 10 minutes', now() - interval '3 hours 10 minutes', now() - interval '3 hours 10 minutes' + interval '40 seconds'); INSERT INTO registro.registro_paso (registro_id, plantilla_id, orden, cumplido) VALUES ('00000000-0000-0000-0000-0000000000b3', 1, 1, true); INSERT INTO registro.registro_paso (registro_id, plantilla_id, orden, cumplido) VALUES ('00000000-0000-0000-0000-0000000000b3', 1, 2, true); INSERT INTO registro.registro_paso (registro_id, plantilla_id, orden, cumplido) VALUES ('00000000-0000-0000-0000-0000000000b3', 1, 3, false)$q$);
SELECT pg_temp.debe_fallar('Registro con hora futura', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000b4', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', 2, 'SI', NULL, false, NULL, now() + interval '10 minutes', now() + interval '10 minutes', now() + interval '10 minutes' + interval '40 seconds')$q$);
SELECT pg_temp.debe_fallar('Registro de otro autor en el turno ajeno', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000b5', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 2, '00000000-0000-0000-0000-0000000000a1', 2, 'SI', NULL, false, NULL, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_fallar('Modalidad distinta de la de la actividad', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000b6', 'EVENTO', '00000000-0000-0000-0000-0000000000e1', 1, NULL, 1, '00000000-0000-0000-0000-0000000000a1', 2, 'SI', NULL, false, true, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_fallar('Por pasos sin plantilla', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000b7', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, NULL, 1, '00000000-0000-0000-0000-0000000000a1', 2, 'SI', NULL, false, NULL, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_fallar('Plantilla de otra actividad', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000b8', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 3, 1, 1, '00000000-0000-0000-0000-0000000000a1', 2, 'SI', NULL, false, NULL, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_fallar('Sin oportunidad sin motivo', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000b9', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', NULL, 'NO', NULL, false, NULL, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_pasar('Sin oportunidad con motivo y sin nivel', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000ba', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', NULL, 'NO', 'NORMA_DEL_HOGAR', false, NULL, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_fallar('Sin oportunidad con nivel', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000bb', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', 2, 'NO', 'FALTAN_RECURSOS', false, NULL, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_fallar('Motivo OTRO sin texto', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000bc', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', NULL, 'NO', 'OTRO', false, NULL, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_pasar('No quiso sin nivel', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000bd', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', NULL, 'SI', NULL, true, NULL, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_fallar('No quiso con nivel', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000be', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', 2, 'SI', NULL, true, NULL, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_fallar('Con oportunidad y sin nivel', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000bf', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', NULL, 'SI', NULL, false, NULL, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_pasar('Evento valido [A1]', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000c1', 'EVENTO', '00000000-0000-0000-0000-0000000000e1', 2, NULL, 1, '00000000-0000-0000-0000-0000000000a1', 2, 'SI', NULL, false, true, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_pasar('Evento sin participacion y sin nivel [A1]', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000c2', 'EVENTO', '00000000-0000-0000-0000-0000000000e1', 2, NULL, 1, '00000000-0000-0000-0000-0000000000a1', NULL, 'SI', NULL, false, false, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_fallar('Registro A_MEDIAS en el servidor (DD-40)', $q$INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, estado, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000c3', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', 2, 'SI', NULL, false, NULL, 'A_MEDIAS', now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_pasar('Corregir un paso marcado', $q$UPDATE registro.registro_paso SET cumplido = false WHERE registro_id = '00000000-0000-0000-0000-0000000000b1' AND orden = 1$q$);
SELECT pg_temp.debe_fallar('Por pasos con oportunidad sin sus pasos (RF-20)', $q$SET CONSTRAINTS registro.trg_registro_pasos_completos IMMEDIATE; INSERT INTO registro.registro (id, modalidad, expediente_id, actividad_id, plantilla_id, cuenta_id, turno_id, nivel_apoyo_id, oportunidad, motivo_sin_oportunidad, no_quiso, participo, fecha_hora, inicio_captura, fin_captura) VALUES ('00000000-0000-0000-0000-0000000000c4', 'PASOS', '00000000-0000-0000-0000-0000000000e1', 1, 1, 1, '00000000-0000-0000-0000-0000000000a1', 2, 'SI', NULL, false, NULL, now() - interval '1 hour', now() - interval '1 hour', now() - interval '1 hour' + interval '40 seconds')$q$);
SELECT pg_temp.debe_fallar('Paso inexistente en la plantilla', $q$INSERT INTO registro.registro_paso (registro_id, plantilla_id, orden, cumplido) VALUES ('00000000-0000-0000-0000-0000000000b1', 1, 9, true)$q$);
SELECT pg_temp.debe_fallar('Paso en un registro sin oportunidad (RF-70)', $q$INSERT INTO registro.registro_paso (registro_id, plantilla_id, orden, cumplido) VALUES ('00000000-0000-0000-0000-0000000000ba', 1, 1, true)$q$);
SELECT pg_temp.debe_pasar('Campo modificado de la precarga', $q$INSERT INTO registro.registro_campo_modificado VALUES ('00000000-0000-0000-0000-0000000000b1', 'PASOS')$q$);
SELECT pg_temp.debe_fallar('Anular sin motivo', $q$UPDATE registro.registro SET estado = 'ANULADO' WHERE id = '00000000-0000-0000-0000-0000000000b1'$q$);
SELECT pg_temp.debe_pasar('Anular con motivo', $q$UPDATE registro.registro SET estado = 'ANULADO', motivo_anulacion = 'registro de prueba' WHERE id = '00000000-0000-0000-0000-0000000000b1'$q$);
SELECT pg_temp.debe_pasar('Foto grupal compartida por dos registros', $q$INSERT INTO registro.evidencia (id, grupal, fecha_captura, tamano_bytes) VALUES ('00000000-0000-0000-0000-0000000000f1', true, now(), 120000); UPDATE registro.registro SET evidencia_id = '00000000-0000-0000-0000-0000000000f1' WHERE id IN ('00000000-0000-0000-0000-0000000000b3', '00000000-0000-0000-0000-0000000000bd')$q$);
SELECT pg_temp.debe_fallar('La aplicacion no lee la identidad (DD-36)', $q$SET LOCAL ROLE rol_aplicacion; SELECT 1 FROM identidad.persona$q$);
SELECT pg_temp.debe_pasar('La aplicacion lee el nombre visible', $q$SET LOCAL ROLE rol_aplicacion; SELECT nombre_visible FROM residentes.v_expediente_visible$q$);
SELECT pg_temp.debe_fallar('La aplicacion no cambia la actividad de un registro', $q$SET LOCAL ROLE rol_aplicacion; UPDATE registro.registro SET actividad_id = 3$q$);
SELECT pg_temp.debe_fallar('La aplicacion no cambia el inicio de la captura', $q$SET LOCAL ROLE rol_aplicacion; UPDATE registro.registro SET inicio_captura = now()$q$);
SELECT pg_temp.debe_fallar('La aplicacion no modifica la bitacora', $q$SET LOCAL ROLE rol_aplicacion; UPDATE comun.bitacora SET motivo = 'x'$q$);
SELECT pg_temp.debe_fallar('La aplicacion no borra registros', $q$SET LOCAL ROLE rol_aplicacion; DELETE FROM registro.registro$q$);
SELECT pg_temp.debe_fallar('La aplicacion no cambia el inicio de la ventana', $q$SET LOCAL ROLE rol_aplicacion; UPDATE registro.turno_declarado SET inicio_ventana = now()$q$);

-- Vistas
SELECT pg_temp.debe_pasar('Vista de puntuacion', $q$SELECT 1/(count(*) FILTER (WHERE puntuable))::int FROM analisis.v_registro_puntuacion$q$);
SELECT pg_temp.debe_pasar('Vista de medicion sin identidad', $q$SELECT 1/count(*)::int FROM registro.v_medicion_captura WHERE sin_cambios IS NOT NULL$q$);

\pset format aligned
SELECT n, prueba, esperado, obtenido FROM resultado ORDER BY n;
SELECT count(*) AS pruebas,
       count(*) FILTER (WHERE (esperado = 'acepta' AND obtenido = 'acepta') OR (esperado = 'rechazo' AND obtenido LIKE 'rechazo%')) AS correctas
FROM resultado;
SELECT id, puntuable, round(autonomia, 4) AS autonomia, grupo_motivo, tipo_turno FROM analisis.v_registro_puntuacion ORDER BY id;

-- Falla con código de salida distinto de cero si alguna prueba no dio lo esperado (CI).
DO $$
DECLARE fallidas int;
BEGIN
  SELECT count(*) INTO fallidas FROM resultado
  WHERE NOT ((esperado = 'acepta' AND obtenido = 'acepta') OR (esperado = 'rechazo' AND obtenido LIKE 'rechazo%'));
  IF fallidas > 0 THEN
    RAISE EXCEPTION '% pruebas del esquema fallaron', fallidas;
  END IF;
END $$;
