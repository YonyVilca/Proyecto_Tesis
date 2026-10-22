package pe.hogar.aplicacion;

import java.util.Optional;
import java.util.UUID;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.catalogo.NivelApoyo;
import pe.hogar.dominio.residentes.Expediente;

/** Lectura de catálogo y expedientes para validar la recepción (DC-01, DC-02, PD-03). */
public interface RepositorioCatalogoRegistro {
    Optional<Expediente> buscarExpedienteActivo(UUID codigo);
    Optional<Actividad> buscarActividad(String codigo);
    Optional<NivelApoyo> buscarNivel(String codigo);
}
