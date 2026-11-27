package pe.hogar.infraestructura;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.hogar.aplicacion.RepositorioParametrosAnalisis;

/** Lee el último valor vigente de cada clave sin modificar la parametrización (RB-10). */
@Repository
public class RepositorioParametrosAnalisisJdbc implements RepositorioParametrosAnalisis {
    private final JdbcTemplate jdbc;

    public RepositorioParametrosAnalisisJdbc(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Map<String, Double> obtenerVigentes() {
        return jdbc.query("SELECT DISTINCT ON (clave) clave,valor FROM comun.parametro "
                        + "WHERE vigencia_desde<=now() ORDER BY clave,vigencia_desde DESC",
                rs -> {
                    Map<String, Double> valores = new LinkedHashMap<>();
                    while (rs.next()) valores.put(rs.getString("clave"), rs.getBigDecimal("valor").doubleValue());
                    return Map.copyOf(valores);
                });
    }
}
