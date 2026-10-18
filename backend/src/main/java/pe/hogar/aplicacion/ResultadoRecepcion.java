package pe.hogar.aplicacion;

/** Resultados de la recepción definida en EN-01 (RF-101, PD-05). */
public enum ResultadoRecepcion {
    ACEPTADO,
    DUPLICADO,
    RECHAZADO_VALIDACION,
    RECHAZADO_SIN_TURNO,
    RECHAZADO_FUERA_DE_VENTANA
}
