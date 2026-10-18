package pe.hogar.dominio.residentes;

/** Verifica credenciales almacenadas con hash y sal (RNF-23, DD-54). */
public interface IVerificadorContrasena {
    boolean verificar(String contrasena, String hash);
}
