package pe.hogar.dominio.analisis;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Redondeo de los textos de los fundamentos: medio al par sobre el valor exacto, como el prototipo. */
final class Formato {

    private Formato() {
    }

    static String decimales(double valor, int cifras) {
        return new BigDecimal(valor).setScale(cifras, RoundingMode.HALF_EVEN).toPlainString();
    }

    static String conSigno(double valor, int cifras) {
        boolean negativo = valor < 0 || (valor == 0 && 1 / valor < 0);
        return (negativo ? "-" : "+") + decimales(Math.abs(valor), cifras);
    }

    static String porcentaje(double proporcion) {
        return decimales(proporcion * 100, 0) + "%";
    }
}
