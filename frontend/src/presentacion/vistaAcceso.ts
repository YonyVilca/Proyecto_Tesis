import { cerrarSesion, declararTurno, enviarRegistroPendiente, enviarTurnoPendiente, ErrorApi, ErrorAutenticacion, ErrorRed, iniciarSesion, obtenerDatosTurno, obtenerTurnoVigente } from '../infraestructura/api';
import { AlmacenLocal, type DatosTurnoLocal, type SesionLocal, type TurnoLocal } from '../infraestructura/almacenLocal';
import { TurnoDeclarado, type TipoTurno } from '../dominio/turno';
import { recuperarTurnoAlAbrir } from './flujoApertura';
import { MotorEnvio } from '../aplicacion/motorEnvio';
import { validarRegistro, type RegistroDatos } from '../dominio/registro';
import './estilos.css';

const app = document.querySelector<HTMLElement>('#app');
const almacen = new AlmacenLocal();
const TIPOS: TipoTurno[] = ['M', 'T', 'MT', 'GC', 'GD', 'N', 'GN', 'D'];
const motorEnvio = new MotorEnvio(almacen, { enviarTurno: enviarTurnoPendiente, obtenerDatos: obtenerDatosTurno, enviarRegistro: enviarRegistroPendiente });
let temporizadorEnvio: number | undefined;
let turnoMostrado: TurnoLocal | undefined;
let sesionMostrada: SesionLocal | undefined;

async function procesarPendientes(): Promise<void> {
  await motorEnvio.enviarPendientes();
  if (turnoMostrado && sesionMostrada) {
    const actualizado = await almacen.obtenerTurno(turnoMostrado.codigo);
    if (actualizado && turnoMostrado.pendiente && !actualizado.pendiente) {
      const t = actualizado;
      turnoMostrado = t;
      renderizarFinal(sesionMostrada, t, await almacen.obtenerDatosTurno(), false,
        t.rechazado ? 'El servidor no aceptó el turno. Si ya declaraste uno en este horario, vuelve a abrir la aplicación.' : '');
    }
    programarSiguienteEnvio();
  }
}

function programarSiguienteEnvio(): void {
  if (temporizadorEnvio !== undefined) window.clearTimeout(temporizadorEnvio);
  void Promise.all([almacen.obtenerTurnosPendientes(), almacen.obtenerPendientes()]).then(([turnos, registros]) => {
    if (turnos.length || registros.length) temporizadorEnvio = window.setTimeout(() => void procesarPendientes(), 60_000);
  });
}

window.addEventListener('online', () => void procesarPendientes());

if (app) void iniciarAplicacion();

async function iniciarAplicacion(): Promise<void> {
  try {
    const sesion = await almacen.obtenerSesion();
    if (sesion) await abrirConSesion(sesion);
    else mostrarIngreso();
  } catch {
    mostrarIngreso('No se pudo abrir el almacenamiento local del teléfono.');
  }
}

async function abrirConSesion(sesion: SesionLocal): Promise<void> {
  await motorEnvio.enviarPendientes();
  const turno = await recuperarTurnoAlAbrir(sesion, almacen, navigator.onLine,
    obtenerTurnoVigente, obtenerDatosTurno);
  if (turno) { turnoMostrado = turno; sesionMostrada = sesion; renderizarFinal(sesion, turno, await almacen.obtenerDatosTurno(), turno.pendiente,
    turno.rechazado ? 'El servidor no aceptó el turno. Si ya declaraste uno en este horario, vuelve a abrir la aplicación.' : ''); programarSiguienteEnvio(); }
  else await mostrarDeclaracion(sesion);
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
        await abrirConSesion(sesion);
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
    turnoMostrado = turnoLocal; sesionMostrada = sesion;
    await almacen.guardarTurno(turnoLocal);

    try {
      const respuesta = await declararTurno(tipo, declaradoEn, encadenados, turnoReglas.codigo);
      if (respuesta.resultado !== 'ACEPTADO' && respuesta.resultado !== 'DUPLICADO') {
        renderizarFinal(sesion, turnoLocal, await almacen.obtenerDatosTurno(), true,
          'El servidor no aceptó el turno. Si ya declaraste uno en este horario, vuelve a abrir la aplicación.');
        return;
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
                         datos: DatosTurnoLocal | undefined, sinActualizar: boolean, avisoServidor = ''): void {
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
  if (avisoServidor) mensaje(resumen, 'error', avisoServidor);
  void almacen.obtenerRegistrosRechazados().then((rechazados) => {
    if (resumen.isConnected && rechazados.length) mensaje(resumen, 'aviso', `Hay ${rechazados.length} registro(s) rechazado(s) por el servidor.`);
  });
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
  if (datos && turno.tipo !== 'D') {
    const registrar = crear('button', 'Registrar');
    registrar.addEventListener('click', () => {
      const local = turnoMostrado;
      if (!local || !new TurnoDeclarado(local.codigo, local.tipo, new Date(local.declaradoEn)).contieneMomento(new Date())) {
        mensaje(resumen, 'error', 'La ventana del turno está cerrada'); return;
      }
      mostrarRegistro(sesion, local, datos);
    });
    acciones.append(registrar);
  }
  const boton = crear('button', 'Cerrar sesión', 'secundario');
  boton.addEventListener('click', () => {
    boton.disabled = true;
    void cerrarSesion().then((revocada) => mostrarIngreso(revocada ? '' : 'La sesión se cerró solo en este teléfono.')).catch((error: unknown) => {
      if (error instanceof ErrorRed) {
        void almacen.borrarSesion().then(() => mostrarIngreso('La sesión se cerró solo en este teléfono.'))
          .catch(() => {
            boton.disabled = false;
            mensaje(resumen, 'error', 'No se pudo cerrar sesión en este teléfono.');
          });
        return;
      }
      boton.disabled = false;
      mensaje(resumen, 'error', 'No se pudo cerrar sesión.');
    });
  });
  acciones.append(boton);
  app.append(encabezado, resumen, residentes, acciones);
}

interface ActividadUI { codigo: string; nombre: string; modalidad: string; secuenciaRelevante: boolean; plantilla: { version: number; pasos: Array<{ orden: number; descripcion: string }> } }
interface ResumenUI { expediente: string; actividad: string; versionPlantilla: number; pasos: Array<{ orden: number; cumplido: boolean }>; secuenciaCorrecta?: boolean }

function mostrarRegistro(sesion: SesionLocal, turno: TurnoLocal, datos: DatosTurnoLocal): void {
  if (!app) return;
  const actividades = datos.actividades as ActividadUI[];
  const hoy = new Set(datos.actividadesResidente.filter((ar) => ar.estado !== 'NO_CORRESPONDE').map((ar) => ar.actividad));
  const opciones = actividades.filter((a) => a.modalidad === 'PASOS' && hoy.has(a.codigo))
    .sort((a, b) => prioridad(datos, b.codigo) - prioridad(datos, a.codigo));
  app.replaceChildren();
  const encabezado = crear('header'); encabezado.append(crear('h1', 'Registrar actividad'), crear('p', 'Completa la observación por residente.', 'subtitulo'));
  const tarjeta = crear('section', '', 'tarjeta');
  if (!opciones.length) { mensaje(tarjeta, 'aviso', 'No hay actividades por pasos que correspondan hoy.'); }
  const formulario = crear('form');
  const actividad = crear('select');
  opciones.forEach((a) => { const o = crear('option', a.nombre); o.value = a.codigo; actividad.append(o); });
  const residente = crear('select');
  residente.multiple = true;
  datos.expedientes.filter((r) => datos.actividadesResidente.some((ar) => ar.expediente === r.codigo && ar.estado !== 'NO_CORRESPONDE' && opciones.some((a) => a.codigo === ar.actividad)))
    .forEach((r, index) => { const o = crear('option', r.nombreVisible); o.value = r.codigo; o.selected = index === 0; residente.append(o); });
  formulario.append(campo('Actividad', actividad), campo('Residente', residente));
  const campos = crear('div');
  const oportunidad = crear('select');
  [['SI','Sí, hubo oportunidad'], ['NO','No hubo oportunidad']].forEach(([v,t]) => { const o=crear('option',t); o.value=v; oportunidad.append(o); });
  const motivo = crear('select');
  const motivos = [['NORMA_DEL_HOGAR','Norma del hogar'],['FALTAN_RECURSOS','Faltan recursos'],['CUIDADOR_CON_OTRO_RESIDENTE','Cuidador con otro residente'],['NO_ESTABA_EN_EL_HOGAR','No estaba en el hogar'],['ENFERMO_O_INDISPUESTO','Enfermo o indispuesto'],['OTRO','Otro']];
  motivos.forEach(([v,t])=>{const o=crear('option',t);o.value=v;motivo.append(o);});
  const otro = crear('input'); otro.placeholder='Describe el motivo';
  const noQuiso = crear('input'); noQuiso.type='checkbox';
  const nota = crear('input'); nota.placeholder='Nota opcional';
  const nivel = crear('select');
  const elegirNivel = crear('option', 'Elige un nivel de apoyo'); elegirNivel.value = ''; elegirNivel.selected = true; nivel.append(elegirNivel);
  ((datos.escala as { niveles?: Array<{ codigo: string; nombre: string }> } | undefined)?.niveles ?? []).forEach((n)=>{const o=crear('option',n.nombre);o.value=n.codigo;nivel.append(o);});
  const pasos = crear('fieldset','', 'opciones'); pasos.append(crear('legend','Pasos'));
  const secuencia = crear('select'); [['true','Correcta'],['false','Incorrecta']].forEach(([v,t])=>{const o=crear('option',t);o.value=v;secuencia.append(o);});
  let inicioCaptura = new Date();
  const actualizar = () => {
    campos.replaceChildren(); pasos.replaceChildren(crear('legend','Pasos'));
    const a = opciones.find((x)=>x.codigo===actividad.value); if (!a) return;
    const previous = datos.ultimosRegistros as ResumenUI[];
    const precarga = previous.find((r)=>r.expediente===residente.value && r.actividad===a.codigo && r.versionPlantilla===a.plantilla.version);
    a.plantilla.pasos.forEach((p)=>{const c=crear('input'); c.type='checkbox'; c.checked=precarga?.pasos.find((x)=>x.orden===p.orden)?.cumplido ?? false; const l=crear('label');l.append(c,crear('span',p.descripcion)); pasos.append(l);});
    campos.append(campo('Oportunidad',oportunidad),campo('Motivo',motivo),campo('Otro',otro),campo('Podía y no quiso',noQuiso),campo('Nota',nota),campo('Nivel de apoyo',nivel));
    if (a.secuenciaRelevante) { secuencia.value=String(precarga?.secuenciaCorrecta ?? true); campos.append(campo('Secuencia',secuencia)); }
    motivo.hidden=oportunidad.value!=='NO'; otro.hidden=!(oportunidad.value==='NO'&&motivo.value==='OTRO');
    noQuiso.disabled=oportunidad.value==='NO'; nivel.hidden=oportunidad.value==='NO'||noQuiso.checked;
  };
  oportunidad.addEventListener('change',actualizar); motivo.addEventListener('change',actualizar); noQuiso.addEventListener('change',actualizar);
  actividad.addEventListener('change',()=>{inicioCaptura=new Date();actualizar();}); residente.addEventListener('change',()=>{inicioCaptura=new Date();actualizar();});
  campos.append(campo('Oportunidad',oportunidad),campo('Motivo',motivo),campo('Otro',otro),campo('Podía y no quiso',noQuiso),campo('Nota',nota),campo('Nivel de apoyo',nivel));
  formulario.append(campos,pasos);
  const guardar=crear('button','Confirmar'); guardar.type='submit'; const cancelar=crear('button','Cancelar','secundario'); cancelar.type='button'; cancelar.addEventListener('click',()=>renderizarFinal(sesion,turno,datos,false));
  formulario.append(guardar,cancelar); formulario.addEventListener('submit',(e)=>{e.preventDefault();void (async()=>{
    const a=opciones.find((x)=>x.codigo===actividad.value); if(!a)return;
    const residentesSeleccionados=Array.from(residente.selectedOptions).map((o)=>o.value);
    if(!residentesSeleccionados.length){mensaje(tarjeta,'error','Elige uno o varios residentes.');return;}
    const pasosCapturados=Array.from(pasos.querySelectorAll<HTMLInputElement>('input[type=checkbox]')).map((c,i)=>({orden:i+1,cumplido:c.checked}));
    try {
      for (const expediente of residentesSeleccionados) {
        const anterior=(datos.ultimosRegistros as ResumenUI[]).find((r)=>r.expediente===expediente&&r.actividad===a.codigo&&r.versionPlantilla===a.plantilla.version);
        const registro:RegistroDatos={codigo:crypto.randomUUID(),turno:turno.codigo,expediente,actividad:a.codigo,versionPlantilla:a.plantilla.version,inicioCaptura:inicioCaptura.toISOString(),finCaptura:new Date().toISOString(),origen:'TACTIL',oportunidad:oportunidad.value==='SI',motivoSinOportunidad:oportunidad.value==='NO'?motivo.value as RegistroDatos['motivoSinOportunidad']:undefined,motivoOtro:motivo.value==='OTRO'?otro.value:undefined,noQuiso:oportunidad.value==='SI'&&noQuiso.checked,nota:nota.value||undefined,nivel:oportunidad.value==='SI'&&!noQuiso.checked?nivel.value:undefined,pasos:pasosCapturados,secuenciaCorrecta:a.secuenciaRelevante?secuencia.value==='true':undefined,camposModificados:[...(anterior&&JSON.stringify(anterior.pasos)!==JSON.stringify(pasosCapturados)?['PASOS']:[]),...(anterior&&a.secuenciaRelevante&&anterior.secuenciaCorrecta!==(secuencia.value==='true')?['SECUENCIA']:[]),...(oportunidad.value==='NO'?['OPORTUNIDAD']:[])],pendiente:true};
        validarRegistro(registro); await almacen.guardarRegistro(registro);
      }
      await procesarPendientes(); renderizarFinal(sesion,turno,await almacen.obtenerDatosTurno(),false);
    } catch(error){mensaje(tarjeta,'error',error instanceof Error?error.message:'No se pudo guardar el registro.');}
  })();});
  actualizar(); tarjeta.append(formulario); app.append(encabezado,tarjeta);
}

function prioridad(datos: DatosTurnoLocal, codigo: string): number { return datos.actividadesResidente.some((a)=>a.actividad===codigo&&a.estado==='PRIORITARIA')?1:0; }

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
