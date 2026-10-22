package pe.hogar.aplicacion;

import java.util.UUID;
import java.util.Set;
import pe.hogar.dominio.registro.CampoPrecarga;
import pe.hogar.dominio.registro.Registro;

/** Persistencia transaccional de registros y sus componentes (DC-03, PD-03). */
public interface RepositorioRegistros {
    boolean existe(UUID codigo);
    void guardar(Registro registro, Set<CampoPrecarga> camposModificados);
}
