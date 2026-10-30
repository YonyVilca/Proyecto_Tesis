import type { TipoTurno } from '../dominio/turno';
import { AlmacenLocal, type DatosTurnoLocal, type SesionLocal, type TurnoLocal } from './almacenLocal';

const almacen = new AlmacenLocal();

export class ErrorAutenticacion extends Error {
  constructor() { super('Usuario o contraseña incorrectos'); this.name = 'ErrorAutenticacion'; }
}

export class ErrorRed extends Error {
  constructor() { super('No hay conexión con el servidor'); this.name = 'ErrorRed'; }
}

export class ErrorApi extends Error {
  constructor(readonly estado: number, mensaje: string) { super(mensaje); this.name = 'ErrorApi'; }
}

interface SesionRespuesta extends SesionLocal { }
interface CuentaRespuesta { codigo: string; nombreCompleto: string; rol: string }
interface DatosRespuesta {
  expedientes: DatosTurnoLocal['expedientes'];
  actividadesResidente: DatosTurnoLocal['actividadesResidente'];
  actividades: unknown[];
  escala: unknown;
  ultimosRegistros: unknown[];
}

/** Inicia sesión y guarda el token Bearer en el almacén del teléfono (RF-01, RF-02, DC-06). */
export async function iniciarSesion(usuario: string, contrasena: string): Promise<SesionLocal> {
  let respuesta: Response;
  try {
    respuesta = await fetch('/api/sesiones', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ usuario, contrasena }),
    });
  } catch { throw new ErrorRed(); }
  if (respuesta.status === 401) throw new ErrorAutenticacion();
  if (!respuesta.ok) throw new ErrorApi(respuesta.status, 'No se pudo iniciar sesión');
  const cuerpo = await respuesta.json() as { token: string; cuenta: CuentaRespuesta };
  const sesion: SesionRespuesta = { token: cuerpo.token, cuenta: cuerpo.cuenta };
  await almacen.guardarSesion(sesion);
  return sesion;
}

/** Revoca la sesión en el servidor y elimina el token local (RF-01, RF-02). */
export async function cerrarSesion(): Promise<boolean> {
  const sesion = await sesionActiva();
  if (!navigator.onLine) { await almacen.borrarSesion(); return false; }
  let respuesta: Response;
  try {
    respuesta = await fetch('/api/sesiones/actual', { method: 'DELETE', headers: bearer(sesion.token) });
  } catch { throw new ErrorRed(); }
  if (respuesta.status === 401 || respuesta.status === 204 || respuesta.ok) {
    await almacen.borrarSesion();
    return true;
  }
  throw new ErrorApi(respuesta.status, 'No se pudo cerrar sesión');
}

/** Consulta el turno vigente asociado a la sesión (RF-02, RF-90, DD-56). */
export async function obtenerTurnoVigente(): Promise<TurnoLocal | undefined> {
  const sesion = await sesionActiva();
  const respuesta = await fetchConToken('/api/turnos/vigente', sesion.token);
  if (respuesta.status === 204) return undefined;
  const cuerpo = await respuesta.json() as Omit<TurnoLocal, 'pendiente'>;
  return { ...cuerpo, pendiente: false };
}

/** Declara el turno con identificador único y manda la hora civil de Lima (RF-12, RF-90, D-66). */
export async function declararTurno(tipo: TipoTurno, declaradoEn: Date, encadenados: TipoTurno[] = [], codigo: string = crypto.randomUUID()): Promise<{ codigo: string; resultado: string }> {
  const sesion = await sesionActiva();
  const fechaLima = aFechaLima(declaradoEn);
  const respuesta = await fetchConToken(`/api/turnos/${codigo}`, sesion.token, {
    method: 'PUT', headers: { ...bearer(sesion.token), 'Content-Type': 'application/json' },
    body: JSON.stringify({ tipo, declaradoEn: fechaLima, encadenados }),
  });
  const cuerpo = await respuesta.json() as { resultado: string };
  return { codigo, resultado: cuerpo.resultado };
}

/** Descarga la configuración operativa asociada al turno declarado (RF-101, DD-43). */
export async function obtenerDatosTurno(codigo: string): Promise<DatosTurnoLocal> {
  const sesion = await sesionActiva();
  const respuesta = await fetchConToken(`/api/turnos/${codigo}/datos`, sesion.token);
  const cuerpo = await respuesta.json() as DatosRespuesta;
  return cuerpo;
}

async function sesionActiva(): Promise<SesionLocal> {
  const sesion = await almacen.obtenerSesion();
  if (!sesion) throw new ErrorAutenticacion();
  return sesion;
}

async function fetchConToken(url: string, token: string, init: RequestInit = {}): Promise<Response> {
  let respuesta: Response;
  try {
    respuesta = await fetch(url, { ...init, headers: { ...bearer(token), ...init.headers } });
  } catch { throw new ErrorRed(); }
  if (respuesta.status === 401) throw new ErrorAutenticacion();
  if (!respuesta.ok) throw new ErrorApi(respuesta.status, 'No se pudieron cargar los datos del turno');
  return respuesta;
}

function bearer(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

/** Serializa un instante para que el servidor reciba hora civil y offset de Lima (D-66). */
function aFechaLima(instante: Date): string {
  const partes = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(instante);
  const valor = (tipo: Intl.DateTimeFormatPartTypes) => partes.find((parte) => parte.type === tipo)?.value ?? '00';
  return `${valor('year')}-${valor('month')}-${valor('day')}T${valor('hour')}:${valor('minute')}:${valor('second')}-05:00`;
}
