package pe.hogar.dominio.comun;

import java.time.LocalDate;
import java.time.YearMonth;

/** Mes calendario que se analiza o se informa (tipo compuesto Periodo). */
public record Periodo(int anio, int mes) {

    public Periodo {
        YearMonth.of(anio, mes);
    }

    public LocalDate inicio() {
        return LocalDate.of(anio, mes, 1);
    }

    public LocalDate fin() {
        return YearMonth.of(anio, mes).atEndOfMonth();
    }

    public Periodo anterior() {
        YearMonth previo = YearMonth.of(anio, mes).minusMonths(1);
        return new Periodo(previo.getYear(), previo.getMonthValue());
    }

    public boolean contiene(LocalDate fecha) {
        return !fecha.isBefore(inicio()) && !fecha.isAfter(fin());
    }
}
