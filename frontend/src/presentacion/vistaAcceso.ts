import { cerrarSesion, declararTurno, ErrorApi, ErrorAutenticacion, ErrorRed, iniciarSesion, obtenerDatosTurno } from '../infraestructura/api';
import { AlmacenLocal, type DatosTurnoLocal, type SesionLocal, type TurnoLocal } from '../infraestructura/almacenLocal';
import { TurnoDeclarado, type TipoTurno } from '../dominio/turno';
import './estilos.css';

const app = document.querySelector<HTMLElement>('#app');
const almacen = new AlmacenLocal();
const TIPOS: TipoTurno[] = ['M', 'T', 'MT', 'GC', 'GD', 'N', 'GN', 'D'];

if (app) void iniciarAplicacion();

async function iniciarAplicacion(): Promise<void> {
  try {
    const sesion = await almacen.obtenerSesion();
    if (sesion) await mostrarDeclaracion(sesion);
    else mostrarIngreso();
  } catch {
    mostrarIngreso('No se pudo abrir el almacenamiento local del teléfono.');
  }
}

function mostrarIngreso(error = ''): void {
  if (!app) return;
  app.replaceChildren();
  const encabezado = crear('header', '');
  encabezado.append(crear('h1', 'Hogar'), crear('p', 'Ingresa para declarar tu turno.', 'subtitulo'));
  const tarjeta = crear('section', '', 'tarjeta');
  const formulario = crear('form');
  const usuario = crear('input');
  usuario.name = 'usuario'; usuario.autocomplete = 'username'; usuario.required = true;
  const contrasena = crear('input');
  contrasena.name = 'contrasena'; contrasena.type = 'password'; contrasena.autocomplete = 'current-password'; contrasena.required = true;
  formulario.append(campo('Usuario', usuario), campo('Contraseña', contrasena));
  const boton = crear('button', 'Ingresar'); boton.type = 'submit'; formulario.append(boton);
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    void (async () => {
      boton.disabled = true;
      borrarMensaje(tarjeta);
      try {
        const sesion = await iniciarSesion(usuario.value.trim(), contrasena.value);
        await mostrarDeclaracion(sesion);
      } catch (fallo) {
        mensaje(tarjeta, 'error', fallo instanceof ErrorRed ? 'No hay conexión con el servidor.'
          : 'Usuario o contraseña incorrectos');
        contrasena.value = '';
      } finally { boton.disabled = false; }
    })();
  });
  tarjeta.append(formulario);
  if (error) mensaje(tarjeta, 'error', error);
  app.append(encabezado, tarjeta);
}

async function mostrarDeclaracion(sesion: SesionLocal): Promise<void> {
  if (!app) return;
  app.replaceChildren();
  const encabezado = crear('header', '');
  encabezado.append(crear('h1', 'Hogar'), crear('p', `Hola, ${sesion.cuenta.nombreCompleto}. Declara tu turno para continuar.`, 'subtitulo'));
  const tarjeta = crear('section', '', 'tarjeta');
  const formulario = crear('form');
  const sugerido = TurnoDeclarado.sugerirTipo(new Date());
  const tipo = crear('select'); tipo.name = 'tipo';
  for (const valor of TIPOS) {
    const opcion = crear('option', valor); opcion.value = valor; opcion.selected = valor === sugerido; tipo.append(opcion);
  }
  formulario.append(campo(`Tipo de turno · sugerido ${sugerido}`, tipo));

  const ahora = new Date();
  const vigente = await almacen.obtenerTurnoVigente(ahora);
  const encadenar = crear('input'); encadenar.type = 'checkbox'; encadenar.name = 'encadenar';
  const siguienteTipo = crear('select');
  const contiguos = vigente ? tiposContiguos(vigente) : [];
  const grupo = crear('fieldset', '', 'opciones');
  const leyenda = crear('legend', 'Encadenar con un turno contiguo'); grupo.append(leyenda);
  const etiquetaCadena = crear('label'); etiquetaCadena.append(encadenar, crear('span', 'Extender el turno vigente'));
  grupo.append(etiquetaCadena);
  for (const valor of contiguos) { const opcion = crear('option', valor); opcion.value = valor; siguienteTipo.append(opcion); }
  const campoSiguiente = campo('Siguiente turno', siguienteTipo);
  campoSiguiente.hidden = true;
  grupo.append(campoSiguiente);
  grupo.hidden = contiguos.length === 0;
  encadenar.addEventListener('change', () => { campoSiguiente.hidden = !encadenar.checked; tipo.disabled = encadenar.checked; });
  formulario.append(grupo);

  const boton = crear('button', 'Declarar turno'); boton.type = 'submit'; formulario.append(boton);
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    void enviarDeclaracion({ sesion, tarjeta, boton, tipo: tipo.value as TipoTurno,
      encadenar: encadenar.checked, siguiente: siguienteTipo.value as TipoTurno, vigente });
  });
  tarjeta.append(formulario);
  app.append(encabezado, tarjeta);
}

async function enviarDeclaracion(datos: {
  sesion: SesionLocal; tarjeta: HTMLElement; boton: HTMLButtonElement; tipo: TipoTurno;
  encadenar: boolean; siguiente: TipoTurno; vigente?: TurnoLocal;
}): Promise<void> {
  const { sesion, tarjeta, boton, vigente } = datos;
  boton.disabled = true;
  borrarMensaje(tarjeta);
  try {
    if (datos.tipo === 'D') {
      renderizarFinal(sesion, { tipo: 'D', inicioVentana: new Date().toISOString(), finVentana: new Date().toISOString() }, await almacen.obtenerDatosTurno(), false);
      return;
    }

    const encadenado = datos.encadenar && vigente !== undefined;
    const tipo = encadenado ? vigente.tipo : datos.tipo;
    const declaradoEn = encadenado ? new Date(vigente.declaradoEn) : new Date();
    const encadenados = encadenado ? [...vigente.encadenados, datos.siguiente] : [];
    const turnoReglas = new TurnoDeclarado(encadenado ? vigente.codigo : crypto.randomUUID(), tipo, declaradoEn);
    for (const previo of encadenados) turnoReglas.encadenar(previo);
    const turnoLocal: TurnoLocal = {
      codigo: turnoReglas.codigo, tipo, declaradoEn: declaradoEn.toISOString(), encadenados,
      inicioVentana: turnoReglas.inicioVentana.toISOString(), finVentana: turnoReglas.finVentana.toISOString(),
      pendiente: false,
    };
    await almacen.guardarTurno(turnoLocal);

    try {
      const respuesta = await declararTurno(tipo, declaradoEn, encadenados, turnoReglas.codigo);
      if (respuesta.resultado !== 'ACEPTADO' && respuesta.resultado !== 'DUPLICADO') {
        throw new ErrorApi(200, `El servidor rechazó el turno: ${respuesta.resultado}`);
      }
      const datosTurno = await obtenerDatosTurno(respuesta.codigo);
      await almacen.guardarDatosTurno(datosTurno);
      renderizarFinal(sesion, turnoLocal, datosTurno, false);
    } catch (error) {
      if (error instanceof ErrorAutenticacion) {
        await almacen.borrarSesion();
        mostrarIngreso('Usuario o contraseña incorrectos');
        return;
      }
      if (!(error instanceof ErrorRed)) throw error;
      await almacen.marcarTurnoPendiente(turnoLocal.codigo);
      const datosPrevios = await almacen.obtenerDatosTurno();
      const turnoPendiente = { ...turnoLocal, pendiente: true };
      renderizarFinal(sesion, turnoPendiente, datosPrevios, true);
    }
  } catch (error) {
    mensaje(tarjeta, 'error', error instanceof Error ? error.message : 'No se pudo declarar el turno.');
  } finally { boton.disabled = false; }
}

function tiposContiguos(turno: TurnoLocal): TipoTurno[] {
  return TIPOS.filter((tipo) => tipo !== 'D' && puedeEncadenar(turno, tipo));
}

function puedeEncadenar(turno: TurnoLocal, tipo: TipoTurno): boolean {
  try {
    const dominio = new TurnoDeclarado(turno.codigo, turno.tipo, new Date(turno.declaradoEn));
    for (const previo of turno.encadenados) dominio.encadenar(previo);
    dominio.encadenar(tipo);
    return true;
  } catch { return false; }
}

function renderizarFinal(sesion: SesionLocal, turno: Pick<TurnoLocal, 'tipo' | 'inicioVentana' | 'finVentana'>,
                         datos: DatosTurnoLocal | undefined, sinActualizar: boolean): void {
  if (!app) return;
  app.replaceChildren();
  const encabezado = crear('header', '');
  encabezado.append(crear('h1', 'Hogar'), crear('p', 'Turno declarado', 'subtitulo'));
  const resumen = crear('section', '', 'tarjeta cabecera-resumen');
  resumen.append(crear('h2', sesion.cuenta.nombreCompleto));
  resumen.append(crear('p', `Turno ${turno.tipo}`));
  if (turno.tipo === 'D') mensaje(resumen, 'aviso', 'Descanso: sin registro');
  resumen.append(crear('p', `${formatoHora(turno.inicioVentana)} – ${formatoHora(turno.finVentana)}`));
  if (sinActualizar) mensaje(resumen, 'aviso', 'Datos sin actualizar');
  const residentes = crear('section');
  residentes.append(crear('h2', 'Residentes'));
  const lista = crear('ul', '', 'lista-residentes');
  for (const residente of datos?.expedientes ?? []) {
    const fila = crear('li', '', 'residente');
    const insignia = crear('span', residente.iniciales, 'iniciales');
    insignia.style.backgroundColor = residente.color;
    insignia.setAttribute('aria-label', `Color ${residente.color}`);
    fila.append(insignia, crear('span', residente.nombreVisible, 'nombre'));
    lista.append(fila);
  }
  if (!datos?.expedientes.length) lista.append(crear('li', 'No hay datos de turno guardados en este teléfono.', 'residente'));
  residentes.append(lista);
  const acciones = crear('section', '', 'acciones');
  const boton = crear('button', 'Cerrar sesión', 'secundario');
  boton.addEventListener('click', () => {
    boton.disabled = true;
    void cerrarSesion().then(() => mostrarIngreso()).catch((error: unknown) => {
      boton.disabled = false;
      mensaje(resumen, 'error', error instanceof ErrorRed ? 'No hay conexión; la sesión sigue abierta.' : 'No se pudo cerrar sesión.');
    });
  });
  acciones.append(boton);
  app.append(encabezado, resumen, residentes, acciones);
}

function formatoHora(iso: string): string {
  return new Intl.DateTimeFormat('es-PE', { timeZone: 'America/Lima', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(iso));
}

function campo(etiqueta: string, control: HTMLElement): HTMLLabelElement {
  const label = crear('label'); label.append(crear('span', etiqueta), control); return label;
}

function crear<K extends keyof HTMLElementTagNameMap>(tag: K, texto = '', clase = ''): HTMLElementTagNameMap[K] {
  const elemento = document.createElement(tag);
  if (texto) elemento.textContent = texto;
  if (clase) elemento.className = clase;
  return elemento;
}

function mensaje(contenedor: HTMLElement, clase: 'aviso' | 'error', texto: string): void {
  borrarMensaje(contenedor);
  const alerta = crear('p', texto, clase); alerta.setAttribute('role', 'status'); contenedor.append(alerta);
}

function borrarMensaje(contenedor: HTMLElement): void {
  contenedor.querySelectorAll('.aviso, .error').forEach((elemento) => elemento.remove());
}
