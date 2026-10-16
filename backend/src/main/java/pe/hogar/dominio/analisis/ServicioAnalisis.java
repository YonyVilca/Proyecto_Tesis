package pe.hogar.dominio.analisis;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import pe.hogar.dominio.catalogo.Actividad;
import pe.hogar.dominio.catalogo.BandaFrecuencia;
import pe.hogar.dominio.catalogo.Modalidad;
import pe.hogar.dominio.catalogo.Paso;
import pe.hogar.dominio.comun.Periodo;
import pe.hogar.dominio.registro.EstadoRegistro;
import pe.hogar.dominio.registro.GrupoMotivo;
import pe.hogar.dominio.registro.MotivoSinOportunidad;
import pe.hogar.dominio.registro.Oportunidad;
import pe.hogar.dominio.registro.Registro;
import pe.hogar.dominio.registro.RegistroPorPasos;
import pe.hogar.dominio.registro.TipoTurno;
import pe.hogar.dominio.residentes.ActividadResidente;
import pe.hogar.dominio.residentes.EstadoActividadResidente;

/**
 * Cálculo del análisis de avance (EM v1.1, AD-02). Las estrategias de la ampliación se inyectan o no
 * (PD-11, PD-16): sin ellas no hay pendiente ni retroceso, y el estado queda en los cuatro del núcleo.
 */
public final class ServicioAnalisis {

    private static final String NIVEL_INDEPENDIENTE = "INDEPENDIENTE";
    private static final String VENTANA_N = "analisis.ventana_n";
    private static final String MINIMO_REGISTROS = "analisis.minimo_registros";
    private static final String CRITERIO_DOMINIO = "analisis.criterio_dominio";
    private static final String UMBRAL_COBERTURA = "analisis.umbral_cobertura";
    private static final String EPSILON = "analisis.epsilon_estancamiento";
    private static final String TECHO = "analisis.techo_estancamiento";
    private static final String VENTANA_TENDENCIA = "analisis.ventana_tendencia";
    private static final String SIN_OPORTUNIDAD_MINIMO = "analisis.sin_oportunidad_minimo";
    private static final String SIN_OPORTUNIDAD_PROPORCION = "analisis.sin_oportunidad_proporcion";
    private static final String VENTANA_NEGATIVAS = "analisis.ventana_negativas_dias";

    private final Map<String, Double> parametros;
    private final IEstimadorTendencia tendencia;
    private final IDetectorRetroceso retroceso;

    /** Núcleo comprometido: sin estrategias. Los parámetros son las filas vigentes de comun.parametro. */
    public ServicioAnalisis(Map<String, Double> parametros) {
        this(parametros, null, null);
    }

    /** Con la ampliación [A6]: cada estrategia puede faltar (multiplicidad 0..1). */
    public ServicioAnalisis(Map<String, Double> parametros, IEstimadorTendencia tendencia,
                            IDetectorRetroceso retroceso) {
        this.parametros = Map.copyOf(parametros);
        this.tendencia = tendencia;
        this.retroceso = retroceso;
    }

    /** AD-02. Indicador de una actividad del residente en el mes. */
    IndicadorActividad analizarActividad(Actividad actividad, List<Registro> registros,
                                         List<ActividadResidente> plan, Periodo periodo) {
        List<Registro> serie = filtrarPuntuables(registros, periodo.fin());
        List<Registro> segmento = segmentoVigente(serie);
        List<Registro> ventana = ultimos(serie, entero(VENTANA_N));
        Cobertura cobertura = calcularCobertura(actividad, registros, plan, periodo);
        boolean suficientes = cobertura.getRegistrosEsperados() > 0
                ? cobertura.isSuficiente() && !ventana.isEmpty()
                : cobertura.isSuficiente();

        Double avance = null;
        if (actividad.getModalidad() == Modalidad.PASOS && !ventana.isEmpty()) {
            int cumplidos = 0;
            int totales = 0;
            for (Registro r : ventana) {
                cumplidos += ((RegistroPorPasos) r).getPasosCumplidos();
                totales += ((RegistroPorPasos) r).getPasosTotales();
            }
            avance = (double) cumplidos / totales;
        }
        Double autonomia = ventana.isEmpty() ? null : media(autonomias(ventana));
        boolean criticos = actividad.obtenerPlantillaVigente()
                .map(p -> p.obtenerPasos().stream().anyMatch(Paso::critico)).orElse(false);
        var indicador = new IndicadorActividad(actividad, ventana.size(), avance, autonomia, suficientes,
                contarNegativas(registros, periodo.fin()), criticos);

        Fundamento sinOportunidad = evaluarSinOportunidad(registros, periodo).orElse(null);
        Fundamento fundamentoRetroceso = null;
        if (suficientes) {
            indicador.setRegistrosConsecutivosDominio(evaluarDominio(serie));
            if (tendencia != null && segmento.size() >= entero(MINIMO_REGISTROS)) {
                indicador.setPendiente(tendencia.estimar(autonomias(segmento)));
            }
            if (retroceso != null) {
                Optional<EvaluacionRetroceso> evaluacion = retroceso.detectar(segmento, periodo);
                if (evaluacion.isPresent()) {
                    indicador.setReferenciaCusum(evaluacion.get().referencia());
                    indicador.setValorCusum(evaluacion.get().valor());
                    fundamentoRetroceso = evaluacion.get().fundamento();
                }
            }
        }
        determinarEstado(indicador, sinOportunidad, fundamentoRetroceso,
                estadoEnElMes(plan, periodo), serie, cobertura);
        return indicador;
    }

    /** RB-08, RF-49, AD-04. Cada unidad (día o semana) cuenta a lo sumo su frecuencia. */
    Cobertura calcularCobertura(Actividad actividad, List<Registro> registros, List<ActividadResidente> plan,
                                Periodo periodo) {
        int esperados = contarEsperados(actividad, plan, periodo);
        Set<LocalDate> prioritarios = new HashSet<>(diasEnEstado(plan, periodo, EstadoActividadResidente.PRIORITARIA));
        Map<TipoTurno, Integer> reparto = new EnumMap<>(TipoTurno.class);
        Map<Object, Integer> porUnidad = new HashMap<>();
        for (Registro r : filtrarPuntuables(registros, periodo.fin())) {
            LocalDate fecha = r.getFechaHora().toLocalDate();
            if (!periodo.contiene(fecha)) {
                continue;
            }
            reparto.merge(r.getTurno().getTipo(), 1, Integer::sum);
            if (esperados == 0 || prioritarios.contains(fecha)) {
                porUnidad.merge(unidad(actividad.getBanda(), fecha), 1, Integer::sum);
            }
        }
        if (esperados == 0) {
            int efectivos = porUnidad.values().stream().mapToInt(Integer::intValue).sum();
            int enVentana = Math.min(filtrarPuntuables(registros, periodo.fin()).size(), entero(VENTANA_N));
            return new Cobertura(0, efectivos, enVentana >= entero(MINIMO_REGISTROS), reparto);
        }
        int efectivos = porUnidad.values().stream()
                .mapToInt(n -> Math.min(n, actividad.getFrecuenciaEsperada())).sum();
        return new Cobertura(esperados, efectivos,
                (double) efectivos / esperados >= obtenerParametro(UMBRAL_COBERTURA), reparto);
    }

    /** RB-09. Serie puntuable hasta una fecha, ordenada en el tiempo. */
    private List<Registro> filtrarPuntuables(List<Registro> registros, LocalDate hasta) {
        return registros.stream()
                .filter(r -> !r.getFechaHora().toLocalDate().isAfter(hasta) && r.esPuntuable())
                .sorted(Comparator.comparing(Registro::getFechaHora).thenComparing(Registro::getCodigo))
                .toList();
    }

    /** EM-06. Sufijo de la serie con la versión de plantilla de su último registro. */
    private List<Registro> segmentoVigente(List<Registro> serie) {
        if (serie.isEmpty() || !(serie.get(serie.size() - 1) instanceof RegistroPorPasos ultimo)) {
            return serie;
        }
        int i = serie.size();
        while (i > 0 && ((RegistroPorPasos) serie.get(i - 1)).getVersionPlantilla() == ultimo.getVersionPlantilla()) {
            i--;
        }
        return serie.subList(i, serie.size());
    }

    /** RB-04, EM-08. Solo los días en estado prioritaria generan expectativa. */
    private int contarEsperados(Actividad actividad, List<ActividadResidente> estados, Periodo periodo) {
        List<LocalDate> diasPrioritaria = diasEnEstado(estados, periodo, EstadoActividadResidente.PRIORITARIA);
        if (diasPrioritaria.isEmpty() || actividad.getBanda() == BandaFrecuencia.ESPORADICA) {
            return 0;
        }
        int f = actividad.getFrecuenciaEsperada();
        return switch (actividad.getBanda()) {
            case DIARIA -> diasPrioritaria.size() * f;
            case SEMANAL -> (int) Math.ceil(diasPrioritaria.size() / 7.0) * f;
            default -> f;
        };
    }

    /** RB-12, D-62. Al menos el mínimo y la proporción sobre los registros del mes. */
    private Optional<Fundamento> evaluarSinOportunidad(List<Registro> registros, Periodo periodo) {
        List<Registro> delMes = registros.stream()
                .filter(r -> periodo.contiene(r.getFechaHora().toLocalDate()))
                .filter(r -> r.getEstado() != EstadoRegistro.ANULADO).toList();
        List<Registro> sin = delMes.stream().filter(r -> r.getOportunidad() == Oportunidad.NO).toList();
        if (delMes.isEmpty() || sin.size() < entero(SIN_OPORTUNIDAD_MINIMO)) {
            return Optional.empty();
        }
        double proporcion = (double) sin.size() / delMes.size();
        if (proporcion < obtenerParametro(SIN_OPORTUNIDAD_PROPORCION)) {
            return Optional.empty();
        }
        Map<GrupoMotivo, Integer> grupos = new TreeMap<>(Comparator.comparing(GrupoMotivo::name));
        for (Registro r : sin) {
            MotivoSinOportunidad motivo = r.getMotivoSinOportunidad();
            grupos.merge(motivo == null ? GrupoMotivo.OTRO : motivo.getGrupo(), 1, Integer::sum);
        }
        String causa;
        if (grupos.keySet().equals(Set.of(GrupoMotivo.HOGAR))) {
            causa = "La causa es del hogar, no del residente.";
        } else if (grupos.keySet().equals(Set.of(GrupoMotivo.RESIDENTE))) {
            causa = "La causa es del residente: no estaba en el hogar o estaba indispuesto.";
        } else if (grupos.keySet().equals(Set.of(GrupoMotivo.OTRO))) {
            causa = "Por otros motivos.";
        } else {
            List<String> partes = new ArrayList<>();
            grupos.forEach((g, n) -> partes.add(n + switch (g) {
                case HOGAR -> " del hogar";
                case RESIDENTE -> " del residente";
                case OTRO -> " por otro motivo";
            }));
            causa = "Motivos: " + String.join(", ", partes) + ".";
        }
        String texto = sin.size() + " de " + delMes.size() + " registros del mes ("
                + Formato.porcentaje(proporcion) + ") fueron sin oportunidad. " + causa;
        return Optional.of(new Fundamento("RB-12 sin oportunidad", obtenerParametro(SIN_OPORTUNIDAD_PROPORCION),
                texto, sin));
    }

    /** Marca «se negó»: negativas en los últimos días hasta la fecha dada. */
    private int contarNegativas(List<Registro> registros, LocalDate hasta) {
        LocalDate desde = hasta.minusDays(entero(VENTANA_NEGATIVAS) - 1L);
        return (int) registros.stream()
                .filter(r -> r.isNoQuiso() && r.getEstado() != EstadoRegistro.ANULADO)
                .map(r -> r.getFechaHora().toLocalDate())
                .filter(f -> !f.isBefore(desde) && !f.isAfter(hasta))
                .count();
    }

    /** RB-06. Registros finales seguidos en independiente con todos los pasos cumplidos. */
    private int evaluarDominio(List<Registro> serie) {
        int n = 0;
        for (int i = serie.size() - 1; i >= 0; i--) {
            Registro r = serie.get(i);
            boolean todos = !(r instanceof RegistroPorPasos p) || p.getPasosCumplidos() == p.getPasosTotales();
            if (!NIVEL_INDEPENDIENTE.equals(r.getNivel().codigo()) || !todos) {
                break;
            }
            n++;
        }
        return n;
    }

    /**
     * D-60. Precedencia: sin oportunidad, sin datos, domina, retrocede, estancada, en trabajo. Las dos
     * reglas de la ampliación solo pueden cumplirse si sus estrategias están inyectadas.
     */
    private Fundamento determinarEstado(IndicadorActividad ind, Fundamento sinOportunidad, Fundamento retroceso,
                                        EstadoActividadResidente estado, List<Registro> serie,
                                        Cobertura cobertura) {
        EstadoVisual visual;
        Fundamento fundamento;
        if (sinOportunidad != null) {
            visual = EstadoVisual.SIN_OPORTUNIDAD;
            fundamento = sinOportunidad;
        } else if (!ind.isDatosSuficientes()) {
            String texto = cobertura.getRegistrosEsperados() > 0
                    ? "Se registraron " + cobertura.getRegistrosEfectivos() + " de " + cobertura.getRegistrosEsperados()
                      + " esperados (" + Formato.porcentaje(cobertura.getPorcentaje()) + "), bajo el umbral de "
                      + Formato.porcentaje(obtenerParametro(UMBRAL_COBERTURA)) + "."
                    : "Hay " + ind.getRegistrosConsiderados() + " registros con oportunidad; se necesitan "
                      + entero(MINIMO_REGISTROS) + " para concluir.";
            visual = EstadoVisual.SIN_DATOS;
            fundamento = new Fundamento("RB-08 datos suficientes", obtenerParametro(UMBRAL_COBERTURA), texto, List.of());
        } else if (ind.getRegistrosConsecutivosDominio() >= entero(CRITERIO_DOMINIO)) {
            int n = ind.getRegistrosConsecutivosDominio();
            String extra = ind.isTienePasosCriticos()
                    ? " Tiene pasos críticos: no se sugiere retirar la supervisión."
                    : " Se puede evaluar retirar el apoyo.";
            visual = EstadoVisual.DOMINA;
            fundamento = new Fundamento("RB-06 domina", obtenerParametro(CRITERIO_DOMINIO),
                    n + " registros seguidos en independiente con todos los pasos cumplidos." + extra,
                    ultimos(serie, n));
        } else if (retroceso != null) {
            visual = EstadoVisual.RETROCEDE;
            fundamento = retroceso;
        } else if (estado == EstadoActividadResidente.PRIORITARIA && ind.getPendiente() != null
                   && Math.abs(ind.getPendiente()) <= obtenerParametro(EPSILON)
                   && ind.getAutonomia() < obtenerParametro(TECHO)) {
            visual = EstadoVisual.ESTANCADA;
            fundamento = new Fundamento("RB-06 estancada", obtenerParametro(EPSILON),
                    "La autonomía varió " + Formato.conSigno(ind.getPendiente(), 3) + " por registro, dentro de ±"
                    + Formato.decimales(obtenerParametro(EPSILON), 3) + ", con media "
                    + Formato.decimales(ind.getAutonomia(), 2) + ".",
                    ultimos(serie, entero(VENTANA_TENDENCIA)));
        } else {
            visual = EstadoVisual.EN_TRABAJO;
            fundamento = new Fundamento("RB-12 en trabajo", null,
                    "Autonomía media " + Formato.decimales(ind.getAutonomia(), 2) + " en los ultimos "
                    + ind.getRegistrosConsiderados() + " registros.",
                    ultimos(serie, entero(VENTANA_N)));
        }
        ind.establecerEstado(visual, fundamento);
        return fundamento;
    }

    /** RB-10. Los parámetros los ajusta la coordinadora sin tocar el código (RNF-36). */
    private double obtenerParametro(String clave) {
        Double valor = parametros.get(clave);
        if (valor == null) {
            throw new IllegalStateException("Falta el parámetro " + clave);
        }
        return valor;
    }

    private int entero(String clave) {
        return (int) obtenerParametro(clave);
    }

    private static EstadoActividadResidente estadoEnElMes(List<ActividadResidente> plan, Periodo periodo) {
        EstadoActividadResidente estado = null;
        for (ActividadResidente tramo : plan) {
            if (vigente(tramo, periodo.fin())) {
                estado = tramo.getEstado();
            }
        }
        return estado;
    }

    private static List<LocalDate> diasEnEstado(List<ActividadResidente> plan, Periodo periodo,
                                                EstadoActividadResidente estado) {
        List<LocalDate> dias = new ArrayList<>();
        for (LocalDate d = periodo.inicio(); !d.isAfter(periodo.fin()); d = d.plusDays(1)) {
            LocalDate dia = d;
            if (plan.stream().anyMatch(t -> t.getEstado() == estado && vigente(t, dia))) {
                dias.add(d);
            }
        }
        return dias;
    }

    private static boolean vigente(ActividadResidente tramo, LocalDate fecha) {
        return !fecha.isBefore(tramo.getVigenciaDesde())
                && (tramo.getVigenciaHasta() == null || !fecha.isAfter(tramo.getVigenciaHasta()));
    }

    private static Object unidad(BandaFrecuencia banda, LocalDate fecha) {
        return switch (banda) {
            case DIARIA -> fecha;
            case SEMANAL -> List.of(fecha.get(IsoFields.WEEK_BASED_YEAR), fecha.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            default -> List.of(fecha.getYear(), fecha.getMonthValue());
        };
    }

    private static List<Registro> ultimos(List<Registro> serie, int n) {
        return serie.subList(Math.max(0, serie.size() - n), serie.size());
    }

    private static List<Double> autonomias(List<Registro> registros) {
        return registros.stream().map(Registro::calcularAutonomia).toList();
    }

    private static double media(List<Double> valores) {
        return valores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }
}
