package pe.hogar.dominio.analisis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.catalogo.BandaFrecuencia;
import pe.hogar.dominio.catalogo.Modalidad;
import pe.hogar.dominio.catalogo.NivelApoyo;
import pe.hogar.dominio.catalogo.Paso;
import pe.hogar.dominio.catalogo.PlantillaPasos;
import pe.hogar.dominio.comun.Periodo;
import pe.hogar.dominio.registro.FabricaRegistro;
import pe.hogar.dominio.registro.MotivoSinOportunidad;
import pe.hogar.dominio.registro.Oportunidad;
import pe.hogar.dominio.registro.Registro;
import pe.hogar.dominio.registro.RegistroPorPasos;
import pe.hogar.dominio.registro.TipoTurno;
import pe.hogar.dominio.registro.TurnoDeclarado;
import pe.hogar.dominio.residentes.ActividadResidente;
import pe.hogar.dominio.residentes.CuentaAcceso;
import pe.hogar.dominio.residentes.EstadoActividadResidente;
import pe.hogar.dominio.residentes.Expediente;
import pe.hogar.dominio.residentes.Rol;

/**
 * Prueba de equivalencia con el prototipo de la EM v1.1: los mismos registros sintéticos deben dar el
 * mismo indicador, estado, fundamento y cobertura (RNF-02). Una diferencia es un error de traducción.
 * nucleo/ se calcula sin estrategias; completo/, con las dos de la ampliación [A6].
 */
class EquivalenciaPrototipoTest {

    private static final double TOLERANCIA = 1e-6;
    private static final ObjectMapper JSON = new ObjectMapper();

    static Stream<Path> vectores() throws IOException, URISyntaxException {
        Path raiz = Path.of(EquivalenciaPrototipoTest.class.getResource("/vectores").toURI());
        try (Stream<Path> archivos = Files.walk(raiz)) {
            return archivos.filter(p -> p.toString().endsWith(".json")).sorted().toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectores")
    void reproduceElPrototipo(Path archivo) throws IOException {
        JsonNode v;
        try (InputStream in = Files.newInputStream(archivo)) {
            v = JSON.readTree(in);
        }
        JsonNode p = v.get("parametros");
        Map<String, Double> parametros = new HashMap<>();
        p.fields().forEachRemaining(e -> {
            if (e.getValue().isNumber()) {
                String clave = e.getKey().equals("epsilon") ? "epsilon_estancamiento" : e.getKey();
                parametros.put("analisis." + clave, e.getValue().asDouble());
            }
        });
        ServicioAnalisis servicio = p.get("con_ampliacion").asBoolean()
                ? new ServicioAnalisis(parametros, new TendenciaMinimosCuadrados(p.get("ventana_tendencia").asInt()),
                        new DetectorCusum(p.get("cusum_k").asDouble(), p.get("cusum_h").asDouble(),
                                p.get("minimo_registros").asInt()))
                : new ServicioAnalisis(parametros);

        Actividad actividad = actividad(v.get("actividad"));
        List<ActividadResidente> plan = new ArrayList<>();
        for (JsonNode t : v.get("plan")) {
            plan.add(new ActividadResidente(UUID.randomUUID(), actividad,
                    EstadoActividadResidente.valueOf(t.get("estado").asText()), LocalDate.parse(t.get("desde").asText()),
                    t.get("hasta").isNull() ? null : LocalDate.parse(t.get("hasta").asText())));
        }
        Map<String, NivelApoyo> niveles = new HashMap<>();
        p.get("pesos").fields().forEachRemaining(e ->
                niveles.put(e.getKey(), new NivelApoyo(e.getKey(), e.getKey(), e.getValue().asDouble(), 0)));
        Map<UUID, String> codigos = new HashMap<>();
        List<Registro> registros = registros(v.get("registros"), actividad, niveles, codigos);
        String[] mes = v.get("periodo").asText().split("-");
        Periodo periodo = new Periodo(Integer.parseInt(mes[0]), Integer.parseInt(mes[1]));

        IndicadorActividad ind = servicio.analizarActividad(actividad, registros, plan, periodo);
        Cobertura cob = servicio.calcularCobertura(actividad, registros, plan, periodo);

        JsonNode e = v.get("esperado").get("indicador");
        assertEquals(e.get("estadoVisual").asText(), ind.getEstadoVisual().name());
        assertEquals(e.get("registrosConsiderados").asInt(), ind.getRegistrosConsiderados());
        assertCercano(e.get("avance"), ind.getAvance());
        assertCercano(e.get("autonomia"), ind.getAutonomia());
        assertCercano(e.get("necesidadApoyo"), ind.getNecesidadApoyo());
        assertEquals(e.get("datosSuficientes").asBoolean(), ind.isDatosSuficientes());
        assertEquals(e.get("negativas").asInt(), ind.getNegativas());
        assertCercano(e.get("pendiente"), ind.getPendiente());
        assertCercano(e.get("referenciaCusum"), ind.getReferenciaCusum());
        assertCercano(e.get("valorCusum"), ind.getValorCusum());
        assertEquals(e.get("registrosConsecutivosDominio").asInt(), ind.getRegistrosConsecutivosDominio());
        assertEquals(e.get("admiteRetiroApoyo").asBoolean(), ind.admiteRetiroApoyo());

        JsonNode f = v.get("esperado").get("fundamento");
        Fundamento fundamento = ind.obtenerFundamento();
        assertEquals(f.get("regla").asText(), fundamento.getRegla());
        assertCercano(f.get("umbral"), fundamento.getUmbral());
        assertEquals(f.get("texto").asText(), fundamento.getTexto());
        List<String> origen = new ArrayList<>();
        f.get("registrosOrigen").forEach(c -> origen.add(c.asText()));
        assertEquals(origen, fundamento.obtenerRegistrosOrigen().stream().map(r -> codigos.get(r.getCodigo())).toList());

        JsonNode c = v.get("esperado").get("cobertura");
        assertEquals(c.get("registrosEsperados").asInt(), cob.getRegistrosEsperados());
        assertEquals(c.get("registrosEfectivos").asInt(), cob.getRegistrosEfectivos());
        assertCercano(c.get("porcentaje"), cob.getPorcentaje());
        assertEquals(c.get("suficiente").asBoolean(), cob.isSuficiente());
        Map<String, Integer> reparto = new HashMap<>();
        c.get("repartoPorTurno").fields().forEachRemaining(x -> reparto.put(x.getKey(), x.getValue().asInt()));
        Map<String, Integer> obtenido = new HashMap<>();
        cob.getRepartoPorTurno().forEach((t, n) -> obtenido.put(t.name(), n));
        assertEquals(reparto, obtenido);
    }

    private static Actividad actividad(JsonNode a) {
        List<Integer> criticos = new ArrayList<>();
        a.get("pasosCriticos").forEach(n -> criticos.add(n.asInt()));
        List<Paso> pasos = new ArrayList<>();
        for (int orden = 1; orden <= a.get("totalPasos").asInt(); orden++) {
            pasos.add(new Paso(orden, "Paso " + orden, criticos.contains(orden)));
        }
        return new Actividad(a.get("codigo").asText(), a.get("codigo").asText(), a.get("eje").asText(),
                Modalidad.valueOf(a.get("modalidad").asText()), BandaFrecuencia.valueOf(a.get("banda").asText()),
                a.get("frecuenciaEsperada").asInt(), false,
                List.of(new PlantillaPasos(1, LocalDate.of(2000, 1, 1), null, pasos)));
    }

    /** Reconstruye cada registro con los mismos gestos que hace el cuidador en el teléfono. */
    private static List<Registro> registros(JsonNode lista, Actividad actividad, Map<String, NivelApoyo> niveles,
                                            Map<UUID, String> codigos) {
        var fabrica = new FabricaRegistro();
        var expediente = new Expediente(UUID.randomUUID(), "XX");
        var coordinadora = new CuentaAcceso(UUID.randomUUID(), "coordinadora", Rol.COORDINADORA);
        Map<String, CuentaAcceso> cuentas = new HashMap<>();
        List<Registro> registros = new ArrayList<>();
        for (JsonNode n : lista) {
            LocalDateTime momento = LocalDateTime.parse(n.get("fechaHora").asText());
            CuentaAcceso cuenta = cuentas.computeIfAbsent(n.get("cuenta").asText(),
                    u -> new CuentaAcceso(UUID.randomUUID(), u, Rol.CUIDADOR));
            var turno = new TurnoDeclarado(UUID.randomUUID(), TipoTurno.valueOf(n.get("tipoTurno").asText()),
                    cuenta, momento);
            Registro r = fabrica.crear(actividad, expediente, turno, momento);
            if ("NO".equals(n.get("oportunidad").asText())) {
                r.indicarOportunidad(Oportunidad.NO, MotivoSinOportunidad.valueOf(n.get("motivo").asText()), null);
            }
            if (n.get("noQuiso").asBoolean()) {
                r.marcarNoQuiso(null);
            }
            if (!n.get("pasos").isNull()) {
                for (int i = 0; i < n.get("pasos").size(); i++) {
                    ((RegistroPorPasos) r).marcarPaso(i + 1, n.get("pasos").get(i).asBoolean());
                }
            }
            r.confirmar(n.get("nivel").isNull() ? null : niveles.get(n.get("nivel").asText()), null, momento);
            if (n.get("anulado").asBoolean()) {
                r.anular("Anulado en el escenario", coordinadora);
            }
            codigos.put(r.getCodigo(), n.get("codigo").asText());
            registros.add(r);
        }
        return registros;
    }

    private static void assertCercano(JsonNode esperado, Double obtenido) {
        if (esperado.isNull()) {
            assertNull(obtenido);
        } else {
            assertEquals(esperado.asDouble(), obtenido, TOLERANCIA);
        }
    }
}
