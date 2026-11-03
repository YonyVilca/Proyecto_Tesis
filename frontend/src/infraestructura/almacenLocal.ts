import { openDB, type DBSchema, type IDBPDatabase } from 'idb';
import type { TipoTurno } from '../dominio/turno';
import type { RegistroDatos } from '../dominio/registro';

/** Datos mínimos de la cuenta necesarios para recuperar la vista tras cerrar la PWA (RF-02). */
export interface SesionLocal {
  token: string;
  cuenta: { codigo: string; nombreCompleto: string; rol: string };
}

/** Registro local para recuperar el turno mientras el dispositivo está sin señal (D-67). */
export interface TurnoLocal {
  codigo: string;
  tipo: TipoTurno;
  declaradoEn: string;
  encadenados: TipoTurno[];
  inicioVentana: string;
  finVentana: string;
  pendiente: boolean;
  rechazado?: boolean;
}

export interface DatosTurnoLocal {
  expedientes: Array<{ codigo: string; nombreVisible: string; iniciales: string; color: string }>;
  actividadesResidente: Array<{ expediente: string; actividad: string; estado: string }>;
  actividades: unknown[];
  escala: unknown;
  ultimosRegistros: unknown[];
}

/** Contrato de almacenamiento local de la PWA (DC-06, RF-02, RF-101, D-67). */
export interface IAlmacenLocal {
  guardarSesion(sesion: SesionLocal): Promise<void>;
  obtenerSesion(): Promise<SesionLocal | undefined>;
  borrarSesion(): Promise<void>;
  guardarTurno(turno: TurnoLocal): Promise<void>;
  obtenerTurnoVigente(ahora: Date): Promise<TurnoLocal | undefined>;
  marcarTurnoPendiente(codigo: string): Promise<void>;
  obtenerTurnosPendientes(): Promise<TurnoLocal[]>;
  obtenerTurno(codigo: string): Promise<TurnoLocal | undefined>;
  marcarTurnoEnviado(codigo: string): Promise<void>;
  marcarTurnoRechazado(codigo: string): Promise<void>;
  obtenerTurnosRechazados(): Promise<TurnoLocal[]>;
  guardarDatosTurno(datos: DatosTurnoLocal): Promise<void>;
  obtenerDatosTurno(): Promise<DatosTurnoLocal | undefined>;
  guardarRegistro(registro: RegistroDatos): Promise<void>;
  obtenerPendientes(): Promise<RegistroDatos[]>;
  obtenerRegistrosRechazados(): Promise<RegistroDatos[]>;
  marcarRegistroEnviado(codigo: string): Promise<void>;
  marcarRegistroRechazado(codigo: string, motivo: string): Promise<void>;
}

interface HogarDB extends DBSchema {
  sesiones: { key: 'actual'; value: SesionLocal };
  turnos: { key: string; value: TurnoLocal };
  datos: { key: 'actual'; value: DatosTurnoLocal };
  registros: { key: string; value: RegistroDatos };
}

const DB_NOMBRE = 'hogar-local';
const DB_VERSION = 2;

/** IndexedDB persistente del dispositivo (DC-06, RF-02, RF-101, D-67). */
export class AlmacenLocal implements IAlmacenLocal {
  private db?: Promise<IDBPDatabase<HogarDB>>;

  constructor(private readonly nombreDb = DB_NOMBRE) { }

  private abrir(): Promise<IDBPDatabase<HogarDB>> {
    this.db ??= openDB<HogarDB>(this.nombreDb, DB_VERSION, {
      upgrade(base) {
        if (!base.objectStoreNames.contains('sesiones')) base.createObjectStore('sesiones');
        if (!base.objectStoreNames.contains('turnos')) base.createObjectStore('turnos');
        if (!base.objectStoreNames.contains('datos')) base.createObjectStore('datos');
        if (!base.objectStoreNames.contains('registros')) base.createObjectStore('registros');
      },
    });
    return this.db;
  }

  async guardarSesion(sesion: SesionLocal): Promise<void> {
    await (await this.abrir()).put('sesiones', structuredClone(sesion), 'actual');
  }

  async obtenerSesion(): Promise<SesionLocal | undefined> {
    return (await this.abrir()).get('sesiones', 'actual');
  }

  async borrarSesion(): Promise<void> {
    await (await this.abrir()).delete('sesiones', 'actual');
  }

  async guardarTurno(turno: TurnoLocal): Promise<void> {
    await (await this.abrir()).put('turnos', structuredClone(turno), turno.codigo);
  }

  async obtenerTurnoVigente(ahora: Date): Promise<TurnoLocal | undefined> {
    const turnos = await (await this.abrir()).getAll('turnos');
    const instante = ahora.getTime();
    return turnos.filter((turno) => turno.tipo !== 'D'
      && Date.parse(turno.inicioVentana) <= instante && instante <= Date.parse(turno.finVentana))
      .sort((a, b) => Date.parse(b.declaradoEn) - Date.parse(a.declaradoEn))[0];
  }

  async marcarTurnoPendiente(codigo: string): Promise<void> {
    const base = await this.abrir();
    const transaccion = base.transaction('turnos', 'readwrite');
    const turno = await transaccion.store.get(codigo);
    if (turno) await transaccion.store.put({ ...turno, pendiente: true }, codigo);
    await transaccion.done;
  }

  async obtenerTurnosPendientes(): Promise<TurnoLocal[]> {
    return (await (await this.abrir()).getAll('turnos')).filter((turno) => turno.pendiente && !turno.rechazado)
      .sort((a, b) => Date.parse(a.declaradoEn) - Date.parse(b.declaradoEn));
  }

  async obtenerTurno(codigo: string): Promise<TurnoLocal | undefined> { return (await this.abrir()).get('turnos', codigo); }

  async marcarTurnoEnviado(codigo: string): Promise<void> {
    await this.actualizarTurno(codigo, { pendiente: false, rechazado: false });
  }

  async marcarTurnoRechazado(codigo: string): Promise<void> {
    await this.actualizarTurno(codigo, { pendiente: false, rechazado: true });
  }

  async obtenerTurnosRechazados(): Promise<TurnoLocal[]> { return (await (await this.abrir()).getAll('turnos')).filter((t) => t.rechazado); }

  private async actualizarTurno(codigo: string, estado: Pick<TurnoLocal, 'pendiente' | 'rechazado'>): Promise<void> {
    const base = await this.abrir();
    const tx = base.transaction('turnos', 'readwrite');
    const turno = await tx.store.get(codigo);
    if (turno) await tx.store.put({ ...turno, ...estado }, codigo);
    await tx.done;
  }

  async guardarDatosTurno(datos: DatosTurnoLocal): Promise<void> {
    await (await this.abrir()).put('datos', structuredClone(datos), 'actual');
  }

  async obtenerDatosTurno(): Promise<DatosTurnoLocal | undefined> {
    return (await this.abrir()).get('datos', 'actual');
  }

  async guardarRegistro(registro: RegistroDatos): Promise<void> {
    const base = await this.abrir();
    const tx = base.transaction(['registros', 'datos'], 'readwrite');
    await tx.objectStore('registros').put(structuredClone(registro), registro.codigo);
    const datos = await tx.objectStore('datos').get('actual');
    if (datos) {
      const resumen = { codigo: registro.codigo, expediente: registro.expediente, actividad: registro.actividad,
        versionPlantilla: registro.versionPlantilla, pasos: registro.pasos.map(({ orden, cumplido }) => ({ orden, cumplido })),
        secuenciaCorrecta: registro.secuenciaCorrecta };
      const previos = datos.ultimosRegistros as Array<Record<string, unknown>>;
      const filtrados = previos.filter((r) => !(r.expediente === registro.expediente && r.actividad === registro.actividad));
      await tx.objectStore('datos').put({ ...datos, ultimosRegistros: [resumen, ...filtrados] }, 'actual');
    }
    await tx.done;
  }

  async obtenerPendientes(): Promise<RegistroDatos[]> {
    return (await (await this.abrir()).getAll('registros')).filter((r) => r.pendiente && !r.rechazado)
      .sort((a, b) => Date.parse(a.inicioCaptura) - Date.parse(b.inicioCaptura));
  }

  async obtenerRegistrosRechazados(): Promise<RegistroDatos[]> { return (await (await this.abrir()).getAll('registros')).filter((r) => Boolean(r.rechazado)); }

  async marcarRegistroEnviado(codigo: string): Promise<void> { await this.actualizarRegistro(codigo, { pendiente: false, rechazado: undefined }); }
  async marcarRegistroRechazado(codigo: string, motivo: string): Promise<void> { await this.actualizarRegistro(codigo, { pendiente: false, rechazado: motivo }); }

  private async actualizarRegistro(codigo: string, estado: Pick<RegistroDatos, 'pendiente' | 'rechazado'>): Promise<void> {
    const base = await this.abrir();
    const tx = base.transaction('registros', 'readwrite');
    const registro = await tx.store.get(codigo);
    if (registro) await tx.store.put({ ...registro, ...estado }, codigo);
    await tx.done;
  }
}
