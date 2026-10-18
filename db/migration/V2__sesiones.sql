CREATE TABLE residentes.sesion (
  id          UUID        NOT NULL DEFAULT gen_random_uuid() CONSTRAINT pk_sesion PRIMARY KEY,
  cuenta_id   BIGINT      NOT NULL CONSTRAINT fk_sesion_cuenta REFERENCES residentes.cuenta_acceso (id) ON DELETE CASCADE,
  token_hash  CHAR(64)    NOT NULL CONSTRAINT uq_sesion_token_hash UNIQUE,
  creada_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
  ultimo_uso  TIMESTAMPTZ,
  revocada_en TIMESTAMPTZ
);
COMMENT ON TABLE residentes.sesion IS 'Sesiones persistentes con token revocable (RF-02, DD-53)';
COMMENT ON COLUMN residentes.sesion.id IS 'Identificador único de la sesión';
COMMENT ON COLUMN residentes.sesion.cuenta_id IS 'Cuenta propietaria de la sesión';
COMMENT ON COLUMN residentes.sesion.token_hash IS 'SHA-256 hexadecimal del token opaco; el token no se almacena';
COMMENT ON COLUMN residentes.sesion.creada_en IS 'Momento de creación de la sesión';
COMMENT ON COLUMN residentes.sesion.ultimo_uso IS 'Última actividad autenticada';
COMMENT ON COLUMN residentes.sesion.revocada_en IS 'Momento en que se cerró la sesión';
CREATE INDEX ix_sesion_cuenta_id ON residentes.sesion (cuenta_id);
GRANT SELECT, INSERT ON residentes.sesion TO rol_aplicacion;
GRANT UPDATE (ultimo_uso, revocada_en) ON residentes.sesion TO rol_aplicacion;
