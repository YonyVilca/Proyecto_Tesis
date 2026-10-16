-- V1. Modelo fisico de la primera etapa. PWA Hogar Protegido Virgen del Rosario.
-- Diseno del Sistema v0.7. PostgreSQL 15 o superior (DD-14).
-- Ejecutar como rol_propietario. Las marcas [E n] y [A n] indican la parte de la etapa.

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE SCHEMA comun;
CREATE SCHEMA catalogo;
CREATE SCHEMA identidad;
CREATE SCHEMA residentes;
CREATE SCHEMA registro;
CREATE SCHEMA analisis;
CREATE SCHEMA informe;

-- ---------------------------------------------------------------- enumeraciones (EN-01)
CREATE TYPE comun.modalidad AS ENUM ('PASOS', 'EVENTO', 'VALORACION');
CREATE TYPE comun.banda_frecuencia AS ENUM ('DIARIA', 'SEMANAL', 'MENSUAL', 'ESPORADICA');
CREATE TYPE comun.rol AS ENUM ('CUIDADOR', 'COORDINADORA');
CREATE TYPE comun.sexo AS ENUM ('FEMENINO', 'MASCULINO');
CREATE TYPE comun.estado_civil AS ENUM ('SOLTERO', 'CASADO', 'CONVIVIENTE', 'DIVORCIADO', 'VIUDO');
CREATE TYPE comun.estado_expediente AS ENUM ('ACTIVO', 'ARCHIVADO');
CREATE TYPE comun.tipo_tutoria AS ENUM ('TITULAR', 'COBERTURA');
CREATE TYPE comun.motivo_cobertura AS ENUM ('EMERGENCIA', 'VACACIONES');
CREATE TYPE comun.estado_actividad_residente AS ENUM ('PRIORITARIA', 'MANTENIMIENTO', 'NO_CORRESPONDE');
CREATE TYPE comun.motivo_no_corresponde AS ENUM ('POR_SEXO', 'LIMITACION_FISICA_PERMANENTE',
  'CONDICION_DE_SALUD_PERMANENTE', 'OTRA_CONDICION_PERMANENTE');
CREATE TYPE comun.tipo_turno AS ENUM ('M', 'T', 'MT', 'GC', 'GD', 'N', 'GN', 'D');
CREATE TYPE comun.estado_registro AS ENUM ('A_MEDIAS', 'CONFIRMADO', 'ANULADO');
CREATE TYPE comun.origen_registro AS ENUM ('TACTIL', 'VOZ');
CREATE TYPE comun.oportunidad AS ENUM ('SI', 'NO');
CREATE TYPE comun.motivo_sin_oportunidad AS ENUM ('NORMA_DEL_HOGAR', 'FALTAN_RECURSOS',
  'CUIDADOR_CON_OTRO_RESIDENTE', 'NO_ESTABA_EN_EL_HOGAR', 'ENFERMO_O_INDISPUESTO', 'OTRO');
CREATE TYPE comun.grupo_motivo AS ENUM ('HOGAR', 'RESIDENTE', 'OTRO');
CREATE TYPE comun.campo_precarga AS ENUM ('PASOS', 'SECUENCIA', 'OPORTUNIDAD');
CREATE TYPE comun.dominio_nota AS ENUM ('ESTADO_DE_ANIMO', 'SALUD_FISICA', 'SUENO', 'ALIMENTACION',
  'CONDUCTA', 'CONVIVENCIA', 'FAMILIA_Y_VISITAS', 'SEGURIDAD_O_INCIDENTE', 'OTRO');
CREATE TYPE comun.estado_visual AS ENUM ('SIN_OPORTUNIDAD', 'SIN_DATOS', 'DOMINA', 'RETROCEDE',
  'ESTANCADA', 'EN_TRABAJO');
CREATE TYPE comun.formato_descarga AS ENUM ('DOCX', 'XLSX', 'PDF');

-- ---------------------------------------------------------------- catalogo (DC-01, ER-01)
CREATE TABLE catalogo.eje (
  id      BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_eje PRIMARY KEY,
  codigo  VARCHAR(2)   NOT NULL CONSTRAINT uq_eje_codigo UNIQUE,
  nombre  VARCHAR(80)  NOT NULL
);
COMMENT ON TABLE catalogo.eje IS 'Clasificación de las actividades: A ABVD, B AIVD, C AAVD';
COMMENT ON COLUMN catalogo.eje.codigo IS 'Letra del eje: A, B o C';
COMMENT ON COLUMN catalogo.eje.nombre IS 'Nombre del eje';

CREATE TABLE catalogo.subgrupo (
  id      BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_subgrupo PRIMARY KEY,
  eje_id  BIGINT       NOT NULL CONSTRAINT fk_subgrupo_eje REFERENCES catalogo.eje (id) ON DELETE CASCADE,
  codigo  VARCHAR(6)   NOT NULL CONSTRAINT uq_subgrupo_codigo UNIQUE,
  nombre  VARCHAR(80)  NOT NULL,
  orden   SMALLINT     NOT NULL DEFAULT 1 CONSTRAINT ck_subgrupo_orden CHECK (orden > 0)
);
COMMENT ON TABLE catalogo.subgrupo IS 'Agrupación de actividades afines dentro de un eje';
COMMENT ON COLUMN catalogo.subgrupo.eje_id IS 'Eje que compone el subgrupo';
COMMENT ON COLUMN catalogo.subgrupo.codigo IS 'Código del subgrupo';
COMMENT ON COLUMN catalogo.subgrupo.nombre IS 'Nombre del subgrupo';
COMMENT ON COLUMN catalogo.subgrupo.orden IS 'Posición dentro del eje';

CREATE TABLE catalogo.actividad (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_actividad PRIMARY KEY,
  subgrupo_id          BIGINT                  NOT NULL CONSTRAINT fk_actividad_subgrupo REFERENCES catalogo.subgrupo (id) ON DELETE RESTRICT,
  codigo               VARCHAR(6)              NOT NULL CONSTRAINT uq_actividad_codigo UNIQUE,
  nombre               VARCHAR(120)            NOT NULL,
  modalidad            comun.modalidad         NOT NULL,
  banda                comun.banda_frecuencia  NOT NULL,
  frecuencia_esperada  SMALLINT                NOT NULL DEFAULT 1 CONSTRAINT ck_actividad_frecuencia CHECK (frecuencia_esperada > 0),
  secuencia_relevante  BOOLEAN                 NOT NULL DEFAULT FALSE,
  aplicabilidad_sexo   comun.sexo,
  vigente              BOOLEAN                 NOT NULL DEFAULT TRUE,
  CONSTRAINT uq_actividad_id_modalidad UNIQUE (id, modalidad)
);
COMMENT ON TABLE catalogo.actividad IS 'Tarea observable del catálogo, unidad sobre la que se registra y se calcula';
COMMENT ON COLUMN catalogo.actividad.subgrupo_id IS 'Subgrupo que la clasifica; el eje se deriva de el';
COMMENT ON COLUMN catalogo.actividad.codigo IS 'Código del Anexo A, por ejemplo A-02';
COMMENT ON COLUMN catalogo.actividad.nombre IS 'Nombre de la actividad';
COMMENT ON COLUMN catalogo.actividad.modalidad IS 'PASOS, EVENTO o VALORACION; no cambia una vez que hay registros (DD-15)';
COMMENT ON COLUMN catalogo.actividad.banda IS 'Banda de frecuencia esperada (RB-04)';
COMMENT ON COLUMN catalogo.actividad.frecuencia_esperada IS 'Registros esperados por unidad de la banda';
COMMENT ON COLUMN catalogo.actividad.secuencia_relevante IS 'Si se registra la secuencia correcta (RF-76)';
COMMENT ON COLUMN catalogo.actividad.aplicabilidad_sexo IS 'Nulo: corresponde a todos; con valor: solo a ese sexo (D-45)';
COMMENT ON COLUMN catalogo.actividad.vigente IS 'Falso si la actividad dejó de usarse';

CREATE TABLE catalogo.actividad_referencia (
  actividad_id  BIGINT       NOT NULL CONSTRAINT fk_actividad_referencia_actividad REFERENCES catalogo.actividad (id) ON DELETE CASCADE,
  orden         SMALLINT     NOT NULL,
  referencia    VARCHAR(200) NOT NULL,
  CONSTRAINT pk_actividad_referencia PRIMARY KEY (actividad_id, orden),
  CONSTRAINT uq_actividad_referencia UNIQUE (actividad_id, referencia)
);
COMMENT ON TABLE catalogo.actividad_referencia IS 'Referencias normativas o técnicas de una actividad (atributo multivaluado)';
COMMENT ON COLUMN catalogo.actividad_referencia.actividad_id IS 'Actividad referida';
COMMENT ON COLUMN catalogo.actividad_referencia.orden IS 'Orden de la referencia';
COMMENT ON COLUMN catalogo.actividad_referencia.referencia IS 'Texto de la referencia';

CREATE TABLE catalogo.plantilla_pasos (
  id              BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_plantilla_pasos PRIMARY KEY,
  actividad_id    BIGINT          NOT NULL,
  modalidad       comun.modalidad NOT NULL DEFAULT 'PASOS' CONSTRAINT ck_plantilla_modalidad CHECK (modalidad = 'PASOS'),
  version         SMALLINT        NOT NULL CONSTRAINT ck_plantilla_version CHECK (version > 0),
  vigencia_desde  DATE            NOT NULL,
  vigencia_hasta  DATE,
  CONSTRAINT fk_plantilla_actividad FOREIGN KEY (actividad_id, modalidad)
    REFERENCES catalogo.actividad (id, modalidad) ON DELETE CASCADE,
  CONSTRAINT uq_plantilla_version UNIQUE (actividad_id, version),
  CONSTRAINT uq_plantilla_id_actividad UNIQUE (id, actividad_id),
  CONSTRAINT ck_plantilla_vigencia CHECK (vigencia_hasta IS NULL OR vigencia_hasta > vigencia_desde),
  CONSTRAINT ex_plantilla_vigencia EXCLUDE USING gist
    (actividad_id WITH =, daterange(vigencia_desde, vigencia_hasta, '[)') WITH &&)
);
COMMENT ON TABLE catalogo.plantilla_pasos IS 'Versión de los pasos de una actividad por pasos (DD-04)';
COMMENT ON COLUMN catalogo.plantilla_pasos.actividad_id IS 'Actividad, que debe ser de modalidad PASOS';
COMMENT ON COLUMN catalogo.plantilla_pasos.modalidad IS 'Columna de control para la clave compuesta (DD-20); siempre PASOS';
COMMENT ON COLUMN catalogo.plantilla_pasos.version IS 'Número de versión, creciente por actividad';
COMMENT ON COLUMN catalogo.plantilla_pasos.vigencia_desde IS 'Inicio de vigencia';
COMMENT ON COLUMN catalogo.plantilla_pasos.vigencia_hasta IS 'Fin de vigencia, exclusivo; nulo si es la vigente';

CREATE TABLE catalogo.paso (
  plantilla_id  BIGINT        NOT NULL CONSTRAINT fk_paso_plantilla REFERENCES catalogo.plantilla_pasos (id) ON DELETE CASCADE,
  orden         SMALLINT      NOT NULL CONSTRAINT ck_paso_orden CHECK (orden > 0),
  descripcion   VARCHAR(200)  NOT NULL,
  critico       BOOLEAN       NOT NULL DEFAULT FALSE,
  CONSTRAINT pk_paso PRIMARY KEY (plantilla_id, orden)
);
COMMENT ON TABLE catalogo.paso IS 'Paso de una plantilla';
COMMENT ON COLUMN catalogo.paso.plantilla_id IS 'Plantilla que contiene el paso';
COMMENT ON COLUMN catalogo.paso.orden IS 'Posición del paso';
COMMENT ON COLUMN catalogo.paso.descripcion IS 'Lo que el residente debe hacer';
COMMENT ON COLUMN catalogo.paso.critico IS 'Si su omisión compromete la seguridad (RB-07)';

CREATE TABLE catalogo.escala_apoyo (
  id              BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_escala_apoyo PRIMARY KEY,
  version         SMALLINT     NOT NULL CONSTRAINT uq_escala_version UNIQUE,
  nombre          VARCHAR(80)  NOT NULL,
  vigencia_desde  DATE         NOT NULL,
  vigente         BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_escala_vigente ON catalogo.escala_apoyo (vigente) WHERE vigente;
COMMENT ON TABLE catalogo.escala_apoyo IS 'Versión de la escala de apoyo (DD-03)';
COMMENT ON COLUMN catalogo.escala_apoyo.version IS 'Número de versión';
COMMENT ON COLUMN catalogo.escala_apoyo.nombre IS 'Nombre de la escala';
COMMENT ON COLUMN catalogo.escala_apoyo.vigencia_desde IS 'Inicio de vigencia';
COMMENT ON COLUMN catalogo.escala_apoyo.vigente IS 'Solo una escala vigente a la vez (ux_escala_vigente)';

CREATE TABLE catalogo.nivel_apoyo (
  id                      BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_nivel_apoyo PRIMARY KEY,
  escala_id               BIGINT        NOT NULL CONSTRAINT fk_nivel_escala REFERENCES catalogo.escala_apoyo (id) ON DELETE CASCADE,
  codigo                  VARCHAR(20)   NOT NULL,
  nombre                  VARCHAR(40)   NOT NULL,
  descripcion_observable  TEXT          NOT NULL,
  peso                    NUMERIC(3,2)  NOT NULL CONSTRAINT ck_nivel_peso CHECK (peso BETWEEN 0 AND 1),
  orden                   SMALLINT      NOT NULL,
  CONSTRAINT uq_nivel_escala_codigo UNIQUE (escala_id, codigo),
  CONSTRAINT uq_nivel_escala_orden UNIQUE (escala_id, orden)
);
COMMENT ON TABLE catalogo.nivel_apoyo IS 'Nivel de una escala con su peso (RB-01)';
COMMENT ON COLUMN catalogo.nivel_apoyo.escala_id IS 'Escala que define el nivel';
COMMENT ON COLUMN catalogo.nivel_apoyo.codigo IS 'Código único dentro de su escala: INDEPENDIENTE, VERBAL, PARCIAL, TOTAL';
COMMENT ON COLUMN catalogo.nivel_apoyo.nombre IS 'Nombre que ve el cuidador';
COMMENT ON COLUMN catalogo.nivel_apoyo.descripcion_observable IS 'Lo que se observa en ese nivel; ayuda a un toque (RF-97) [E10]';
COMMENT ON COLUMN catalogo.nivel_apoyo.peso IS 'Peso en la autonomía, entre 0 y 1; no se modifica (DD-22)';
COMMENT ON COLUMN catalogo.nivel_apoyo.orden IS 'Posición en la escala';

-- ---------------------------------------------------------------- residentes e identidad (DC-02, ER-02)
CREATE TABLE residentes.cuenta_acceso (
  id               BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_cuenta_acceso PRIMARY KEY,
  codigo           VARCHAR(10)   NOT NULL CONSTRAINT uq_cuenta_codigo UNIQUE,
  nombre_completo  VARCHAR(120)  NOT NULL,
  usuario          VARCHAR(40)   NOT NULL CONSTRAINT uq_cuenta_usuario UNIQUE,
  hash_contrasena  VARCHAR(100)  NOT NULL,
  rol              comun.rol     NOT NULL DEFAULT 'CUIDADOR',
  activa           BOOLEAN       NOT NULL DEFAULT TRUE
);
COMMENT ON TABLE residentes.cuenta_acceso IS 'Credencial y rol de cada usuario (RF-01, RF-03)';
COMMENT ON COLUMN residentes.cuenta_acceso.codigo IS 'Código seudónimo del usuario';
COMMENT ON COLUMN residentes.cuenta_acceso.nombre_completo IS 'Nombre del cuidador o de la coordinadora';
COMMENT ON COLUMN residentes.cuenta_acceso.usuario IS 'Nombre de usuario para iniciar sesión';
COMMENT ON COLUMN residentes.cuenta_acceso.hash_contrasena IS 'Hash con sal de la contraseña (RNF-23)';
COMMENT ON COLUMN residentes.cuenta_acceso.rol IS 'CUIDADOR o COORDINADORA';
COMMENT ON COLUMN residentes.cuenta_acceso.activa IS 'Falso al desactivar; los registros se conservan (RF-04)';

CREATE TABLE identidad.persona (
  id                BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_persona PRIMARY KEY,
  dni               CHAR(8)             NOT NULL CONSTRAINT uq_persona_dni UNIQUE
                                        CONSTRAINT ck_persona_dni CHECK (dni ~ '^[0-9]{8}$'),
  nombres           VARCHAR(80)         NOT NULL,
  primer_apellido   VARCHAR(60)         NOT NULL,
  segundo_apellido  VARCHAR(60),
  fecha_nacimiento  DATE                NOT NULL,
  sexo              comun.sexo          NOT NULL,
  estado_civil      comun.estado_civil  NOT NULL,
  diagnostico       TEXT,
  numero_conadis    VARCHAR(20)
);
COMMENT ON TABLE identidad.persona IS 'Datos de identidad de un residente; acceso restringido (RNF-25, DD-35)';
COMMENT ON COLUMN identidad.persona.dni IS 'DNI de ocho dígitos, obligatorio y único (D-65)';
COMMENT ON COLUMN identidad.persona.nombres IS 'Nombres';
COMMENT ON COLUMN identidad.persona.primer_apellido IS 'Primer apellido; con los nombres forma el nombre visible (D-64)';
COMMENT ON COLUMN identidad.persona.segundo_apellido IS 'Segundo apellido, si lo tiene';
COMMENT ON COLUMN identidad.persona.fecha_nacimiento IS 'Fecha de nacimiento; la edad se calcula (D-50)';
COMMENT ON COLUMN identidad.persona.sexo IS 'Sexo; resuelve la aplicabilidad por sexo al cargar las actividades';
COMMENT ON COLUMN identidad.persona.estado_civil IS 'Estado civil';
COMMENT ON COLUMN identidad.persona.diagnostico IS 'Diagnóstico; solo lo ve la coordinadora (RF-91)';
COMMENT ON COLUMN identidad.persona.numero_conadis IS 'Número del carné CONADIS, opcional';

CREATE TABLE identidad.contacto_emergencia (
  id                BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_contacto_emergencia PRIMARY KEY,
  persona_id        BIGINT        NOT NULL CONSTRAINT fk_contacto_persona REFERENCES identidad.persona (id) ON DELETE CASCADE,
  parentesco        VARCHAR(40)   NOT NULL,
  nombres           VARCHAR(80)   NOT NULL,
  apellidos         VARCHAR(120)  NOT NULL,
  sexo              comun.sexo    NOT NULL,
  dni               CHAR(8)       NOT NULL CONSTRAINT ck_contacto_dni CHECK (dni ~ '^[0-9]{8}$'),
  fecha_nacimiento  DATE          NOT NULL,
  telefono          VARCHAR(15)   NOT NULL
);
COMMENT ON TABLE identidad.contacto_emergencia IS 'Contacto de emergencia de una persona (D-50)';
COMMENT ON COLUMN identidad.contacto_emergencia.persona_id IS 'Persona a la que pertenece';
COMMENT ON COLUMN identidad.contacto_emergencia.parentesco IS 'Relación con la persona';
COMMENT ON COLUMN identidad.contacto_emergencia.nombres IS 'Nombres del contacto';
COMMENT ON COLUMN identidad.contacto_emergencia.apellidos IS 'Apellidos del contacto';
COMMENT ON COLUMN identidad.contacto_emergencia.sexo IS 'Sexo del contacto';
COMMENT ON COLUMN identidad.contacto_emergencia.dni IS 'DNI del contacto';
COMMENT ON COLUMN identidad.contacto_emergencia.fecha_nacimiento IS 'Fecha de nacimiento del contacto';
COMMENT ON COLUMN identidad.contacto_emergencia.telefono IS 'Teléfono';

CREATE TABLE residentes.expediente (
  id            UUID                     CONSTRAINT pk_expediente PRIMARY KEY,
  persona_id    BIGINT                   NOT NULL CONSTRAINT fk_expediente_persona REFERENCES identidad.persona (id) ON DELETE RESTRICT,
  fecha_ingreso DATE                     NOT NULL,
  fecha_alta    DATE,
  estado        comun.estado_expediente  NOT NULL DEFAULT 'ACTIVO',
  iniciales     VARCHAR(4)               NOT NULL,
  color         CHAR(7)                  NOT NULL CONSTRAINT ck_expediente_color CHECK (color ~ '^#[0-9A-F]{6}$'),
  CONSTRAINT ck_expediente_alta CHECK (fecha_alta IS NULL OR fecha_alta >= fecha_ingreso),
  CONSTRAINT ck_expediente_estado CHECK ((estado = 'ARCHIVADO') = (fecha_alta IS NOT NULL))
);
CREATE UNIQUE INDEX ux_expediente_activo ON residentes.expediente (persona_id) WHERE estado = 'ACTIVO';
CREATE UNIQUE INDEX ux_expediente_color_activo ON residentes.expediente (color) WHERE estado = 'ACTIVO';
COMMENT ON TABLE residentes.expediente IS 'Un ingreso de una persona; todo el seguimiento se asocia aqui (DD-35)';
COMMENT ON COLUMN residentes.expediente.id IS 'UUID del expediente';
COMMENT ON COLUMN residentes.expediente.persona_id IS 'Persona que ingresa; un solo expediente activo por persona';
COMMENT ON COLUMN residentes.expediente.fecha_ingreso IS 'Fecha de ingreso al hogar';
COMMENT ON COLUMN residentes.expediente.fecha_alta IS 'Fecha de alta; el alta es de la tesis final';
COMMENT ON COLUMN residentes.expediente.estado IS 'ACTIVO o ARCHIVADO';
COMMENT ON COLUMN residentes.expediente.iniciales IS 'Iniciales que se muestran con el color (RF-96) [E9]';
COMMENT ON COLUMN residentes.expediente.color IS 'Color fijo en hexadecimal, distinto entre expedientes activos';

CREATE TABLE residentes.tutoria (
  id                BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_tutoria PRIMARY KEY,
  expediente_id     UUID                    NOT NULL CONSTRAINT fk_tutoria_expediente REFERENCES residentes.expediente (id) ON DELETE CASCADE,
  cuenta_id         BIGINT                  NOT NULL CONSTRAINT fk_tutoria_cuenta REFERENCES residentes.cuenta_acceso (id) ON DELETE RESTRICT,
  tipo              comun.tipo_tutoria      NOT NULL DEFAULT 'TITULAR',
  motivo_cobertura  comun.motivo_cobertura,
  fecha_inicio      DATE                    NOT NULL,
  fecha_fin         DATE,
  CONSTRAINT ck_tutoria_tipo CHECK (
    (tipo = 'TITULAR' AND motivo_cobertura IS NULL) OR
    (tipo = 'COBERTURA' AND motivo_cobertura IS NOT NULL AND fecha_fin IS NOT NULL)),
  CONSTRAINT ck_tutoria_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
  CONSTRAINT ex_tutoria_titular EXCLUDE USING gist
    (expediente_id WITH =, daterange(fecha_inicio, fecha_fin, '[]') WITH &&) WHERE (tipo = 'TITULAR'),
  CONSTRAINT ex_tutoria_cobertura EXCLUDE USING gist
    (expediente_id WITH =, daterange(fecha_inicio, fecha_fin, '[]') WITH &&) WHERE (tipo = 'COBERTURA')
);
COMMENT ON TABLE residentes.tutoria IS 'Clase-asociación entre expediente y cuenta (D-51)';
COMMENT ON COLUMN residentes.tutoria.expediente_id IS 'Expediente tutelado';
COMMENT ON COLUMN residentes.tutoria.cuenta_id IS 'Cuidador tutor';
COMMENT ON COLUMN residentes.tutoria.tipo IS 'TITULAR, sin fecha de fin mientras dure; COBERTURA, con inicio y fin';
COMMENT ON COLUMN residentes.tutoria.motivo_cobertura IS 'EMERGENCIA o VACACIONES; solo en coberturas';
COMMENT ON COLUMN residentes.tutoria.fecha_inicio IS 'Inicio, inclusivo';
COMMENT ON COLUMN residentes.tutoria.fecha_fin IS 'Fin, inclusivo; nulo en un titular vigente';

CREATE TABLE residentes.actividad_residente (
  id                     UUID                              CONSTRAINT pk_actividad_residente PRIMARY KEY,
  expediente_id          UUID                              NOT NULL CONSTRAINT fk_actres_expediente REFERENCES residentes.expediente (id) ON DELETE CASCADE,
  actividad_id           BIGINT                            NOT NULL CONSTRAINT fk_actres_actividad REFERENCES catalogo.actividad (id) ON DELETE RESTRICT,
  estado                 comun.estado_actividad_residente  NOT NULL DEFAULT 'MANTENIMIENTO',
  motivo_no_corresponde  comun.motivo_no_corresponde,
  vigencia_desde         DATE                              NOT NULL,
  vigencia_hasta         DATE,
  CONSTRAINT ck_actres_motivo CHECK ((estado = 'NO_CORRESPONDE') = (motivo_no_corresponde IS NOT NULL)),
  CONSTRAINT ck_actres_vigencia CHECK (vigencia_hasta IS NULL OR vigencia_hasta > vigencia_desde),
  CONSTRAINT ex_actres_vigencia EXCLUDE USING gist
    (expediente_id WITH =, actividad_id WITH =, daterange(vigencia_desde, vigencia_hasta, '[)') WITH &&)
);
COMMENT ON TABLE residentes.actividad_residente IS 'Estado de cada actividad para un residente, con vigencia (D-45)';
COMMENT ON COLUMN residentes.actividad_residente.id IS 'UUID; es Auditable';
COMMENT ON COLUMN residentes.actividad_residente.expediente_id IS 'Expediente';
COMMENT ON COLUMN residentes.actividad_residente.actividad_id IS 'Actividad del catálogo';
COMMENT ON COLUMN residentes.actividad_residente.estado IS 'PRIORITARIA, MANTENIMIENTO o NO_CORRESPONDE';
COMMENT ON COLUMN residentes.actividad_residente.motivo_no_corresponde IS 'Obligatorio con NO_CORRESPONDE; lista cerrada (RF-69)';
COMMENT ON COLUMN residentes.actividad_residente.vigencia_desde IS 'Inicio, inclusivo';
COMMENT ON COLUMN residentes.actividad_residente.vigencia_hasta IS 'Fin, exclusivo; nulo si es la vigente';

-- ---------------------------------------------------------------- comun (DC-04)
CREATE TABLE comun.parametro (
  id              UUID           CONSTRAINT pk_parametro PRIMARY KEY,
  clave           VARCHAR(60)    NOT NULL CONSTRAINT uq_parametro_clave UNIQUE,
  valor           NUMERIC(10,4)  NOT NULL,
  descripcion     TEXT           NOT NULL,
  vigencia_desde  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
COMMENT ON TABLE comun.parametro IS 'Valor editable de las reglas; se cambia por script con asiento en bitacora (RB-10)';
COMMENT ON COLUMN comun.parametro.id IS 'UUID; es Auditable';
COMMENT ON COLUMN comun.parametro.clave IS 'Clave, por ejemplo analisis.cusum_h';
COMMENT ON COLUMN comun.parametro.valor IS 'Valor vigente';
COMMENT ON COLUMN comun.parametro.descripcion IS 'Uso del parámetro';
COMMENT ON COLUMN comun.parametro.vigencia_desde IS 'Desde cuando rige el valor';

CREATE TABLE comun.bitacora (
  id                     UUID          CONSTRAINT pk_bitacora PRIMARY KEY,
  tipo_entidad           VARCHAR(40)   NOT NULL,
  identificador_entidad  UUID          NOT NULL,
  campo                  VARCHAR(60)   NOT NULL,
  valor_anterior         TEXT,
  valor_nuevo            TEXT,
  motivo                 TEXT,
  autor_id               BIGINT        NOT NULL CONSTRAINT fk_bitacora_autor REFERENCES residentes.cuenta_acceso (id) ON DELETE RESTRICT,
  fecha_hora             TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_bitacora_entidad ON comun.bitacora (tipo_entidad, identificador_entidad);
COMMENT ON TABLE comun.bitacora IS 'Asiento de toda corrección y anulación; no modificable (RNF-24, DD-07)';
COMMENT ON COLUMN comun.bitacora.tipo_entidad IS 'Clase de la entidad: Registro, ActividadResidente, Parámetro';
COMMENT ON COLUMN comun.bitacora.identificador_entidad IS 'UUID de la entidad';
COMMENT ON COLUMN comun.bitacora.campo IS 'Atributo cambiado';
COMMENT ON COLUMN comun.bitacora.valor_anterior IS 'Valor antes del cambio';
COMMENT ON COLUMN comun.bitacora.valor_nuevo IS 'Valor después del cambio';
COMMENT ON COLUMN comun.bitacora.motivo IS 'Obligatorio en la anulación; nulo en la corrección dentro de la ventana (D-43)';
COMMENT ON COLUMN comun.bitacora.autor_id IS 'Cuenta que hizo el cambio';
COMMENT ON COLUMN comun.bitacora.fecha_hora IS 'Momento del asiento';

-- ---------------------------------------------------------------- registro (DC-03, ER-03)
CREATE TABLE registro.turno_declarado (
  id              UUID              CONSTRAINT pk_turno_declarado PRIMARY KEY,
  cuenta_id       BIGINT            NOT NULL CONSTRAINT fk_turno_cuenta REFERENCES residentes.cuenta_acceso (id) ON DELETE RESTRICT,
  tipo            comun.tipo_turno  NOT NULL CONSTRAINT ck_turno_tipo CHECK (tipo <> 'D'),
  declarado_en    TIMESTAMPTZ       NOT NULL,
  inicio_ventana  TIMESTAMPTZ       NOT NULL,
  fin_ventana     TIMESTAMPTZ       NOT NULL,
  recibido_en     TIMESTAMPTZ       NOT NULL DEFAULT now(),
  CONSTRAINT uq_turno_id_cuenta UNIQUE (id, cuenta_id),
  CONSTRAINT ck_turno_ventana CHECK (fin_ventana > inicio_ventana
    AND fin_ventana - inicio_ventana <= interval '36 hours'),
  CONSTRAINT ex_turno_solapado EXCLUDE USING gist
    (cuenta_id WITH =, tstzrange(inicio_ventana, fin_ventana, '[)') WITH &&)
);
COMMENT ON TABLE registro.turno_declarado IS 'Turno declarado y su ventana (D-42, RB-11, DD-34)';
COMMENT ON COLUMN registro.turno_declarado.id IS 'UUID generado en el teléfono';
COMMENT ON COLUMN registro.turno_declarado.cuenta_id IS 'Cuidador que lo declaro';
COMMENT ON COLUMN registro.turno_declarado.tipo IS 'Primer tipo declarado; D no se guarda porque no habilita registro';
COMMENT ON COLUMN registro.turno_declarado.declarado_en IS 'Momento de la declaración, en el teléfono';
COMMENT ON COLUMN registro.turno_declarado.inicio_ventana IS 'Inicio de la ventana según el tipo';
COMMENT ON COLUMN registro.turno_declarado.fin_ventana IS 'Fin de la ventana; solo crece al encadenar (RF-90)';
COMMENT ON COLUMN registro.turno_declarado.recibido_en IS 'Momento de llegada al servidor';

CREATE TABLE registro.turno_encadenado (
  turno_id  UUID              NOT NULL CONSTRAINT fk_encadenado_turno REFERENCES registro.turno_declarado (id) ON DELETE CASCADE,
  orden     SMALLINT          NOT NULL CONSTRAINT ck_encadenado_orden CHECK (orden > 0),
  tipo      comun.tipo_turno  NOT NULL CONSTRAINT ck_encadenado_tipo CHECK (tipo <> 'D'),
  CONSTRAINT pk_turno_encadenado PRIMARY KEY (turno_id, orden)
);
COMMENT ON TABLE registro.turno_encadenado IS 'Tipos encadenados a un turno (atributo multivaluado encadenados)';
COMMENT ON COLUMN registro.turno_encadenado.turno_id IS 'Turno ampliado';
COMMENT ON COLUMN registro.turno_encadenado.orden IS 'Orden del encadenamiento';
COMMENT ON COLUMN registro.turno_encadenado.tipo IS 'Tipo encadenado';

CREATE TABLE registro.evidencia (
  id                   UUID         CONSTRAINT pk_evidencia PRIMARY KEY,
  grupal               BOOLEAN      NOT NULL DEFAULT FALSE,
  fecha_captura        TIMESTAMPTZ  NOT NULL,
  tamano_bytes         INTEGER      NOT NULL CONSTRAINT ck_evidencia_tamano CHECK (tamano_bytes > 0),
  ruta_almacenamiento  TEXT
);
COMMENT ON TABLE registro.evidencia IS 'Foto individual o grupal; la comparten los registros que la muestran (DD-38)';
COMMENT ON COLUMN registro.evidencia.id IS 'UUID generado en el teléfono';
COMMENT ON COLUMN registro.evidencia.grupal IS 'Si muestra a varios residentes (D-47)';
COMMENT ON COLUMN registro.evidencia.fecha_captura IS 'Momento de la foto';
COMMENT ON COLUMN registro.evidencia.tamano_bytes IS 'Tamaño del archivo comprimido';
COMMENT ON COLUMN registro.evidencia.ruta_almacenamiento IS 'Ruta del archivo cifrado; nula hasta que llega el archivo';

CREATE TABLE registro.registro (
  id                      UUID                          CONSTRAINT pk_registro PRIMARY KEY,
  modalidad               comun.modalidad               NOT NULL CONSTRAINT ck_registro_modalidad CHECK (modalidad IN ('PASOS', 'EVENTO')),
  expediente_id           UUID                          NOT NULL CONSTRAINT fk_registro_expediente REFERENCES residentes.expediente (id) ON DELETE RESTRICT,
  actividad_id            BIGINT                        NOT NULL,
  plantilla_id            BIGINT,
  cuenta_id               BIGINT                        NOT NULL,
  turno_id                UUID                          NOT NULL,
  nivel_apoyo_id          BIGINT                        CONSTRAINT fk_registro_nivel REFERENCES catalogo.nivel_apoyo (id) ON DELETE RESTRICT,
  evidencia_id            UUID                          CONSTRAINT fk_registro_evidencia REFERENCES registro.evidencia (id) ON DELETE RESTRICT,
  fecha_hora              TIMESTAMPTZ                   NOT NULL,
  inicio_captura          TIMESTAMPTZ                   NOT NULL,
  fin_captura             TIMESTAMPTZ                   NOT NULL,
  estado                  comun.estado_registro         NOT NULL DEFAULT 'CONFIRMADO',
  origen                  comun.origen_registro         NOT NULL DEFAULT 'TACTIL',
  oportunidad             comun.oportunidad             NOT NULL DEFAULT 'SI',
  motivo_sin_oportunidad  comun.motivo_sin_oportunidad,
  motivo_otro             VARCHAR(200),
  no_quiso                BOOLEAN                       NOT NULL DEFAULT FALSE,
  nota                    TEXT,
  secuencia_correcta      BOOLEAN,
  participo               BOOLEAN,
  diferido                BOOLEAN                       NOT NULL DEFAULT FALSE,
  motivo_diferido         VARCHAR(200),
  motivo_anulacion        TEXT,
  recibido_en             TIMESTAMPTZ                   NOT NULL DEFAULT now(),
  CONSTRAINT fk_registro_actividad_modalidad FOREIGN KEY (actividad_id, modalidad)
    REFERENCES catalogo.actividad (id, modalidad) ON DELETE RESTRICT,
  CONSTRAINT fk_registro_plantilla FOREIGN KEY (plantilla_id, actividad_id)
    REFERENCES catalogo.plantilla_pasos (id, actividad_id) ON DELETE RESTRICT,
  CONSTRAINT fk_registro_turno_autor FOREIGN KEY (turno_id, cuenta_id)
    REFERENCES registro.turno_declarado (id, cuenta_id) ON DELETE RESTRICT,
  CONSTRAINT uq_registro_id_plantilla UNIQUE (id, plantilla_id),
  CONSTRAINT ck_registro_estado CHECK (estado <> 'A_MEDIAS'),
  CONSTRAINT ck_registro_pasos CHECK ((modalidad = 'PASOS') = (plantilla_id IS NOT NULL)),
  CONSTRAINT ck_registro_secuencia CHECK (modalidad = 'PASOS' OR secuencia_correcta IS NULL),
  CONSTRAINT ck_registro_evento CHECK ((modalidad = 'EVENTO') = (participo IS NOT NULL)),
  CONSTRAINT ck_registro_oportunidad CHECK ((oportunidad = 'NO') = (motivo_sin_oportunidad IS NOT NULL)),
  CONSTRAINT ck_registro_motivo_otro CHECK ((motivo_sin_oportunidad IS NOT DISTINCT FROM 'OTRO') = (motivo_otro IS NOT NULL)),
  CONSTRAINT ck_registro_no_quiso CHECK (NOT (no_quiso AND oportunidad = 'NO')),
  CONSTRAINT ck_registro_nivel CHECK (
    (oportunidad = 'SI' AND NOT no_quiso AND participo IS NOT FALSE) = (nivel_apoyo_id IS NOT NULL)),
  CONSTRAINT ck_registro_anulacion CHECK ((estado = 'ANULADO') = (motivo_anulacion IS NOT NULL)),
  CONSTRAINT ck_registro_diferido CHECK (diferido = (motivo_diferido IS NOT NULL)),
  CONSTRAINT ck_registro_captura CHECK (fin_captura >= inicio_captura AND fecha_hora <= fin_captura)
);
CREATE INDEX ix_registro_expediente_fecha ON registro.registro (expediente_id, actividad_id, fecha_hora);
CREATE INDEX ix_registro_turno ON registro.registro (turno_id);
COMMENT ON TABLE registro.registro IS 'Tabla por jerarquía de Registro: PASOS [C] y EVENTO [A1] (DD-15)';
COMMENT ON COLUMN registro.registro.id IS 'UUID generado en el teléfono; hace idempotente el envio (RF-32)';
COMMENT ON COLUMN registro.registro.modalidad IS 'Discriminador, atado a la actividad por clave compuesta';
COMMENT ON COLUMN registro.registro.expediente_id IS 'Residente observado';
COMMENT ON COLUMN registro.registro.actividad_id IS 'Actividad observada; cambiarla es anular y crear otro (DD-41)';
COMMENT ON COLUMN registro.registro.plantilla_id IS 'Versión de plantilla con que se capturo; solo en PASOS (RF-65)';
COMMENT ON COLUMN registro.registro.cuenta_id IS 'Autor; debe ser el del turno (fk_registro_turno_autor)';
COMMENT ON COLUMN registro.registro.turno_id IS 'Turno declarado en cuya ventana se creó';
COMMENT ON COLUMN registro.registro.nivel_apoyo_id IS 'Nivel elegido; obligatorio si hubo oportunidad, no hubo negativa y participo';
COMMENT ON COLUMN registro.registro.evidencia_id IS 'Foto que lo muestra, opcional y compartible';
COMMENT ON COLUMN registro.registro.fecha_hora IS 'Hora real de lo observado; fija el día y el mes (D-66)';
COMMENT ON COLUMN registro.registro.inicio_captura IS 'Elección del residente; base de la ventana y de RF-98';
COMMENT ON COLUMN registro.registro.fin_captura IS 'Confirmación del registro (RF-98)';
COMMENT ON COLUMN registro.registro.estado IS 'CONFIRMADO o ANULADO; A_MEDIAS nunca llega al servidor (DD-40)';
COMMENT ON COLUMN registro.registro.origen IS 'TACTIL o VOZ [A11] (RF-98)';
COMMENT ON COLUMN registro.registro.oportunidad IS 'SI o NO (RB-03)';
COMMENT ON COLUMN registro.registro.motivo_sin_oportunidad IS 'Obligatorio con oportunidad NO (D-61)';
COMMENT ON COLUMN registro.registro.motivo_otro IS 'Texto del motivo OTRO';
COMMENT ON COLUMN registro.registro.no_quiso IS 'Podia hacerla y se negó (D-61)';
COMMENT ON COLUMN registro.registro.nota IS 'Nota opcional, por ejemplo con no quiso o en un evento';
COMMENT ON COLUMN registro.registro.secuencia_correcta IS 'Solo en PASOS cuando la plantilla la marca relevante';
COMMENT ON COLUMN registro.registro.participo IS 'Solo en EVENTO [A1]';
COMMENT ON COLUMN registro.registro.diferido IS 'Registrado con hora anterior dentro de la ventana [A9]';
COMMENT ON COLUMN registro.registro.motivo_diferido IS 'Obligatorio si diferido [A9]';
COMMENT ON COLUMN registro.registro.motivo_anulacion IS 'Obligatorio si ANULADO (RF-94)';
COMMENT ON COLUMN registro.registro.recibido_en IS 'Momento de llegada al servidor';

CREATE TABLE registro.registro_paso (
  registro_id   UUID      NOT NULL,
  plantilla_id  BIGINT    NOT NULL,
  orden         SMALLINT  NOT NULL,
  cumplido      BOOLEAN   NOT NULL DEFAULT FALSE,
  CONSTRAINT pk_registro_paso PRIMARY KEY (registro_id, orden),
  CONSTRAINT fk_registro_paso_registro FOREIGN KEY (registro_id, plantilla_id)
    REFERENCES registro.registro (id, plantilla_id) ON DELETE CASCADE,
  CONSTRAINT fk_registro_paso_paso FOREIGN KEY (plantilla_id, orden)
    REFERENCES catalogo.paso (plantilla_id, orden) ON DELETE RESTRICT
);
COMMENT ON TABLE registro.registro_paso IS 'Paso marcado en un registro por pasos (composicion)';
COMMENT ON COLUMN registro.registro_paso.registro_id IS 'Registro';
COMMENT ON COLUMN registro.registro_paso.plantilla_id IS 'Plantilla del registro; garantiza que el paso sea de su versión';
COMMENT ON COLUMN registro.registro_paso.orden IS 'Paso de la plantilla';
COMMENT ON COLUMN registro.registro_paso.cumplido IS 'Si el residente lo completo, con o sin ayuda (RB-02)';

CREATE TABLE registro.registro_campo_modificado (
  registro_id  UUID                  NOT NULL CONSTRAINT fk_campo_registro REFERENCES registro.registro (id) ON DELETE CASCADE,
  campo        comun.campo_precarga  NOT NULL,
  CONSTRAINT pk_registro_campo_modificado PRIMARY KEY (registro_id, campo)
);
COMMENT ON TABLE registro.registro_campo_modificado IS 'Campos de la precarga que el cuidador cambio (D-59, RF-98)';
COMMENT ON COLUMN registro.registro_campo_modificado.registro_id IS 'Registro';
COMMENT ON COLUMN registro.registro_campo_modificado.campo IS 'PASOS, SECUENCIA u OPORTUNIDAD';

CREATE TABLE registro.nota_turno (
  id             UUID                CONSTRAINT pk_nota_turno PRIMARY KEY,
  expediente_id  UUID                NOT NULL CONSTRAINT fk_nota_expediente REFERENCES residentes.expediente (id) ON DELETE RESTRICT,
  cuenta_id      BIGINT              NOT NULL,
  turno_id       UUID                NOT NULL,
  fecha_hora     TIMESTAMPTZ         NOT NULL,
  dominio        comun.dominio_nota,
  texto          TEXT                NOT NULL CONSTRAINT ck_nota_texto CHECK (length(trim(texto)) > 0),
  CONSTRAINT fk_nota_turno_autor FOREIGN KEY (turno_id, cuenta_id)
    REFERENCES registro.turno_declarado (id, cuenta_id) ON DELETE RESTRICT
);
COMMENT ON TABLE registro.nota_turno IS 'Nota de turno sobre un residente, sin actividad [A2] (RF-72)';
COMMENT ON COLUMN registro.nota_turno.expediente_id IS 'Residente';
COMMENT ON COLUMN registro.nota_turno.cuenta_id IS 'Autor';
COMMENT ON COLUMN registro.nota_turno.turno_id IS 'Turno del autor';
COMMENT ON COLUMN registro.nota_turno.fecha_hora IS 'Momento de la nota';
COMMENT ON COLUMN registro.nota_turno.dominio IS 'Etiqueta opcional de nueve valores';
COMMENT ON COLUMN registro.nota_turno.texto IS 'Texto de la nota';

-- Ventana del turno (DD-46, RB-11, D-66)
CREATE FUNCTION registro.verificar_ventana() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE t registro.turno_declarado;
BEGIN
  SELECT * INTO t FROM registro.turno_declarado WHERE id = NEW.turno_id;
  IF NEW.inicio_captura < t.inicio_ventana - interval '15 minutes'
     OR NEW.inicio_captura > t.fin_ventana + interval '15 minutes' THEN
    RAISE EXCEPTION 'registro % fuera de la ventana del turno %', NEW.id, NEW.turno_id
      USING ERRCODE = 'check_violation';
  END IF;
  IF NEW.inicio_captura > now() OR NEW.fecha_hora > now() THEN
    RAISE EXCEPTION 'registro % con hora futura', NEW.id USING ERRCODE = 'check_violation';
  END IF;
  IF NEW.fecha_hora < t.inicio_ventana - interval '15 minutes' THEN
    RAISE EXCEPTION 'registro % con hora anterior a la ventana', NEW.id USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_registro_ventana BEFORE INSERT ON registro.registro
  FOR EACH ROW EXECUTE FUNCTION registro.verificar_ventana();

-- Sin pasos marcados cuando no hay oportunidad o hubo negativa (RF-70)
CREATE FUNCTION registro.verificar_paso_puntuable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM registro.registro r WHERE r.id = NEW.registro_id
             AND (r.oportunidad = 'NO' OR r.no_quiso)) THEN
    RAISE EXCEPTION 'el registro % no admite pasos (RF-70)', NEW.registro_id USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_registro_paso_puntuable BEFORE INSERT ON registro.registro_paso
  FOR EACH ROW EXECUTE FUNCTION registro.verificar_paso_puntuable();

CREATE FUNCTION registro.retirar_pasos() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.oportunidad = 'NO' OR NEW.no_quiso THEN
    DELETE FROM registro.registro_paso WHERE registro_id = NEW.id;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_registro_retirar_pasos AFTER UPDATE OF oportunidad, no_quiso ON registro.registro
  FOR EACH ROW EXECUTE FUNCTION registro.retirar_pasos();

-- Un registro por pasos con oportunidad y sin negativa trae todos los pasos de su plantilla (RF-20).
-- Se verifica al confirmar la transaccion, porque registro y pasos llegan juntos.
CREATE FUNCTION registro.verificar_pasos_completos() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.modalidad = 'PASOS' AND NEW.oportunidad = 'SI' AND NOT NEW.no_quiso
     AND (SELECT count(*) FROM registro.registro_paso WHERE registro_id = NEW.id)
      <> (SELECT count(*) FROM catalogo.paso WHERE plantilla_id = NEW.plantilla_id) THEN
    RAISE EXCEPTION 'el registro % no trae todos los pasos de su plantilla', NEW.id
      USING ERRCODE = 'check_violation';
  END IF;
  RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_registro_pasos_completos AFTER INSERT ON registro.registro
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION registro.verificar_pasos_completos();

-- Una actividad no cambia de modalidad si tiene plantillas o registros: lo impiden las claves compuestas.

-- ---------------------------------------------------------------- informe [E1] (DC-05)
CREATE TABLE informe.informe_mes (
  id                    UUID                    CONSTRAINT pk_informe_mes PRIMARY KEY,
  expediente_id         UUID                    NOT NULL CONSTRAINT fk_informe_expediente REFERENCES residentes.expediente (id) ON DELETE RESTRICT,
  cuenta_id             BIGINT                  NOT NULL CONSTRAINT fk_informe_cuenta REFERENCES residentes.cuenta_acceso (id) ON DELETE RESTRICT,
  periodo_anio          SMALLINT                NOT NULL CONSTRAINT ck_informe_anio CHECK (periodo_anio >= 2026),
  periodo_mes           SMALLINT                NOT NULL CONSTRAINT ck_informe_mes CHECK (periodo_mes BETWEEN 1 AND 12),
  formato               comun.formato_descarga  NOT NULL,
  fecha_descarga        TIMESTAMPTZ             NOT NULL DEFAULT now(),
  parametros_aplicados  TEXT                    NOT NULL
);
CREATE INDEX ix_informe_expediente_periodo ON informe.informe_mes (expediente_id, periodo_anio, periodo_mes);
COMMENT ON TABLE informe.informe_mes IS 'Una fila por descarga del informe del mes, con su instantánea [E1] (DD-39)';
COMMENT ON COLUMN informe.informe_mes.expediente_id IS 'Residente';
COMMENT ON COLUMN informe.informe_mes.cuenta_id IS 'Tutor que lo descargo';
COMMENT ON COLUMN informe.informe_mes.periodo_anio IS 'Año del periodo';
COMMENT ON COLUMN informe.informe_mes.periodo_mes IS 'Mes del periodo';
COMMENT ON COLUMN informe.informe_mes.formato IS 'DOCX, XLSX o PDF';
COMMENT ON COLUMN informe.informe_mes.fecha_descarga IS 'Momento de la descarga';
COMMENT ON COLUMN informe.informe_mes.parametros_aplicados IS 'Claves y valores de los parámetros usados, en texto';

CREATE TABLE informe.fila_informe (
  informe_id                 UUID                 NOT NULL CONSTRAINT fk_fila_informe REFERENCES informe.informe_mes (id) ON DELETE CASCADE,
  orden                      SMALLINT             NOT NULL,
  actividad_id               BIGINT               NOT NULL CONSTRAINT fk_fila_actividad REFERENCES catalogo.actividad (id) ON DELETE RESTRICT,
  avance                     NUMERIC(5,4),
  autonomia                  NUMERIC(5,4),
  porcentaje_cobertura       NUMERIC(5,4),
  estado_visual              comun.estado_visual  NOT NULL,
  registros_sin_oportunidad  SMALLINT             NOT NULL DEFAULT 0,
  registros_no_quiso         SMALLINT             NOT NULL DEFAULT 0,
  en_reforzamiento           BOOLEAN              NOT NULL DEFAULT FALSE,
  orden_prioridad            SMALLINT,
  CONSTRAINT pk_fila_informe PRIMARY KEY (informe_id, orden),
  CONSTRAINT uq_fila_actividad UNIQUE (informe_id, actividad_id)
);
COMMENT ON TABLE informe.fila_informe IS 'Fila del cuadro de avance tal como se descargo (instantánea)';
COMMENT ON COLUMN informe.fila_informe.informe_id IS 'Descarga';
COMMENT ON COLUMN informe.fila_informe.orden IS 'Posición en el cuadro, agrupado por eje';
COMMENT ON COLUMN informe.fila_informe.actividad_id IS 'Actividad';
COMMENT ON COLUMN informe.fila_informe.avance IS 'Pasos cumplidos sobre pasos totales en la ventana';
COMMENT ON COLUMN informe.fila_informe.autonomia IS 'Autonomía media en la ventana';
COMMENT ON COLUMN informe.fila_informe.porcentaje_cobertura IS 'Efectivos sobre esperados; nulo sin esperados';
COMMENT ON COLUMN informe.fila_informe.estado_visual IS 'Estado al descargar';
COMMENT ON COLUMN informe.fila_informe.registros_sin_oportunidad IS 'Registros sin oportunidad del mes';
COMMENT ON COLUMN informe.fila_informe.registros_no_quiso IS 'Registros no quiso del mes';
COMMENT ON COLUMN informe.fila_informe.en_reforzamiento IS 'Si va al cuadro de reforzamiento (RF-47)';
COMMENT ON COLUMN informe.fila_informe.orden_prioridad IS 'Posición en el ranking; nulo si quedo fuera';

CREATE TABLE informe.tutor_informe (
  informe_id  UUID                NOT NULL CONSTRAINT fk_tutor_informe REFERENCES informe.informe_mes (id) ON DELETE CASCADE,
  cuenta_id   BIGINT              NOT NULL CONSTRAINT fk_tutor_cuenta REFERENCES residentes.cuenta_acceso (id) ON DELETE RESTRICT,
  tipo        comun.tipo_tutoria  NOT NULL,
  desde       DATE                NOT NULL,
  hasta       DATE                NOT NULL,
  dias        SMALLINT            NOT NULL CONSTRAINT ck_tutor_dias CHECK (dias BETWEEN 1 AND 31),
  CONSTRAINT pk_tutor_informe PRIMARY KEY (informe_id, cuenta_id, desde),
  CONSTRAINT ck_tutor_fechas CHECK (hasta >= desde)
);
COMMENT ON TABLE informe.tutor_informe IS 'Tutores del mes y días a cargo de cada uno (D-68)';
COMMENT ON COLUMN informe.tutor_informe.informe_id IS 'Descarga';
COMMENT ON COLUMN informe.tutor_informe.cuenta_id IS 'Tutor';
COMMENT ON COLUMN informe.tutor_informe.tipo IS 'TITULAR o COBERTURA';
COMMENT ON COLUMN informe.tutor_informe.desde IS 'Primer día a cargo en el mes';
COMMENT ON COLUMN informe.tutor_informe.hasta IS 'Último día a cargo en el mes';
COMMENT ON COLUMN informe.tutor_informe.dias IS 'Días a cargo';

-- ---------------------------------------------------------------- vistas
CREATE VIEW residentes.v_expediente_visible AS
SELECT e.id, e.estado, e.fecha_ingreso, e.iniciales, e.color,
       p.nombres || ' ' || p.primer_apellido AS nombre_visible
FROM residentes.expediente e
JOIN identidad.persona p ON p.id = e.persona_id;
COMMENT ON VIEW residentes.v_expediente_visible IS 'Lo que ven los cuidadores: nombre y un apellido, iniciales y color (D-64, DD-36)';

CREATE FUNCTION analisis.grupo_de(m comun.motivo_sin_oportunidad) RETURNS comun.grupo_motivo
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE
    WHEN m IN ('NORMA_DEL_HOGAR', 'FALTAN_RECURSOS', 'CUIDADOR_CON_OTRO_RESIDENTE') THEN 'HOGAR'
    WHEN m IN ('NO_ESTABA_EN_EL_HOGAR', 'ENFERMO_O_INDISPUESTO') THEN 'RESIDENTE'
    WHEN m = 'OTRO' THEN 'OTRO'
  END::comun.grupo_motivo $$;

CREATE VIEW analisis.v_registro_puntuacion AS
SELECT r.id, r.expediente_id, r.actividad_id, r.modalidad, r.fecha_hora,
       (r.fecha_hora AT TIME ZONE 'America/Lima')::date AS dia,
       date_trunc('month', r.fecha_hora AT TIME ZONE 'America/Lima')::date AS periodo,
       t.tipo AS tipo_turno, r.estado, r.oportunidad, r.motivo_sin_oportunidad,
       analisis.grupo_de(r.motivo_sin_oportunidad) AS grupo_motivo, r.no_quiso,
       b.puntuable, p.cumplidos AS pasos_cumplidos, p.total AS pasos_totales,
       CASE WHEN NOT b.puntuable THEN NULL
            WHEN r.modalidad = 'PASOS' THEN (p.cumplidos::numeric / NULLIF(p.total, 0)) * n.peso
            ELSE n.peso END AS autonomia
FROM registro.registro r
JOIN registro.turno_declarado t ON t.id = r.turno_id
CROSS JOIN LATERAL (SELECT r.estado = 'CONFIRMADO' AND r.oportunidad = 'SI' AND NOT r.no_quiso
                           AND r.participo IS NOT FALSE AND r.nivel_apoyo_id IS NOT NULL AS puntuable) b
LEFT JOIN catalogo.nivel_apoyo n ON n.id = r.nivel_apoyo_id
LEFT JOIN LATERAL (SELECT count(*) AS total, count(*) FILTER (WHERE rp.cumplido) AS cumplidos
                   FROM registro.registro_paso rp WHERE rp.registro_id = r.id) p ON r.modalidad = 'PASOS';
COMMENT ON VIEW analisis.v_registro_puntuacion IS 'Puntuabilidad y autonomía de cada registro (DD-19, EM v1.1)';

CREATE VIEW registro.v_medicion_captura AS
SELECT r.id, a.codigo AS actividad, r.modalidad, t.tipo AS tipo_turno, r.origen,
       r.inicio_captura, r.fin_captura,
       extract(epoch FROM r.fin_captura - r.inicio_captura) AS segundos,
       r.oportunidad, r.no_quiso,
       coalesce(array_agg(c.campo ORDER BY c.campo) FILTER (WHERE c.campo IS NOT NULL), '{}') AS campos_modificados,
       count(c.campo) = 0 AS sin_cambios
FROM registro.registro r
JOIN catalogo.actividad a ON a.id = r.actividad_id
JOIN registro.turno_declarado t ON t.id = r.turno_id
LEFT JOIN registro.registro_campo_modificado c ON c.registro_id = r.id
WHERE r.estado = 'CONFIRMADO'
GROUP BY r.id, a.codigo, t.tipo;
COMMENT ON VIEW registro.v_medicion_captura IS 'Exportación de RF-98 sin datos de identidad: ni expediente ni autor (RNF-38)';

-- ---------------------------------------------------------------- roles (DD-18, DD-22, DD-36)
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rol_aplicacion') THEN CREATE ROLE rol_aplicacion; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rol_identidad') THEN CREATE ROLE rol_identidad; END IF;
END $$;

GRANT USAGE ON SCHEMA comun, catalogo, residentes, registro, analisis, informe TO rol_aplicacion;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO rol_aplicacion;
GRANT SELECT ON residentes.cuenta_acceso, residentes.expediente, residentes.tutoria,
  residentes.actividad_residente, residentes.v_expediente_visible TO rol_aplicacion;
GRANT SELECT ON comun.parametro TO rol_aplicacion;
GRANT SELECT, INSERT ON comun.bitacora TO rol_aplicacion;
GRANT SELECT, INSERT ON registro.turno_declarado, registro.turno_encadenado, registro.evidencia,
  registro.registro, registro.registro_paso, registro.registro_campo_modificado, registro.nota_turno
  TO rol_aplicacion;
GRANT UPDATE (fin_ventana) ON registro.turno_declarado TO rol_aplicacion;
GRANT UPDATE (ruta_almacenamiento) ON registro.evidencia TO rol_aplicacion;
GRANT UPDATE (expediente_id, nivel_apoyo_id, evidencia_id, fecha_hora, estado, oportunidad,
  motivo_sin_oportunidad, motivo_otro, no_quiso, nota, secuencia_correcta, participo,
  motivo_anulacion) ON registro.registro TO rol_aplicacion;
GRANT UPDATE (cumplido), DELETE ON registro.registro_paso TO rol_aplicacion;
GRANT SELECT ON analisis.v_registro_puntuacion, registro.v_medicion_captura TO rol_aplicacion;
GRANT EXECUTE ON FUNCTION analisis.grupo_de(comun.motivo_sin_oportunidad) TO rol_aplicacion;
GRANT SELECT, INSERT ON informe.informe_mes, informe.fila_informe, informe.tutor_informe TO rol_aplicacion;

GRANT USAGE ON SCHEMA comun, identidad, residentes TO rol_identidad;
GRANT SELECT, INSERT, UPDATE ON identidad.persona, identidad.contacto_emergencia TO rol_identidad;
GRANT SELECT ON residentes.expediente TO rol_identidad;
