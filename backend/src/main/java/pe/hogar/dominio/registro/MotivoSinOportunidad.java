package pe.hogar.dominio.registro;

/** Motivos de «no hubo oportunidad», agrupados por su causa (RB-03, D-61). */
public enum MotivoSinOportunidad {
    NORMA_DEL_HOGAR(GrupoMotivo.HOGAR),
    FALTAN_RECURSOS(GrupoMotivo.HOGAR),
    CUIDADOR_CON_OTRO_RESIDENTE(GrupoMotivo.HOGAR),
    NO_ESTABA_EN_EL_HOGAR(GrupoMotivo.RESIDENTE),
    ENFERMO_O_INDISPUESTO(GrupoMotivo.RESIDENTE),
    OTRO(GrupoMotivo.OTRO);

    private final GrupoMotivo grupo;

    MotivoSinOportunidad(GrupoMotivo grupo) {
        this.grupo = grupo;
    }

    public GrupoMotivo getGrupo() {
        return grupo;
    }
}
