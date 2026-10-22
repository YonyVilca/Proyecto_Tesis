import { openDB, type DBSchema, type IDBPDatabase } from 'idb';
import type { TipoTurno } from '../dominio/turno';

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
  guardarDatosTurno(datos: DatosTurnoLocal): Promise<void>;
  obtenerDatosTurno(): Promise<DatosTurnoLocal | undefined>;
}

interface HogarDB extends DBSchema {
  sesiones: { key: 'actual'; value: SesionLocal };
  turnos: { key: string; value: TurnoLocal };
  datos: { key: 'actual'; value: DatosTurnoLocal };
}

const DB_NOMBRE = 'hogar-local';
const DB_VERSION = 1;

/** IndexedDB persistente del dispositivo (DC-06, RF-02, RF-101, D-67). */
export class AlmacenLocal implements IAlmacenLocal {
  private db?: Promise<IDBPDatabase<HogarDB>>;

  constructor(private readonly nombreDb = DB_NOMBRE) { }

  private abrir(): Promise<IDBPDatabase<HogarDB>> {
    this.db ??= openDB<HogarDB>(this.nombreDb, DB_VERSION, {
      upgrade(base) {
        base.createObjectStore('sesiones');
        base.createObjectStore('turnos');
        base.createObjectStore('datos');
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

  async guardarDatosTurno(datos: DatosTurnoLocal): Promise<void> {
    await (await this.abrir()).put('datos', structuredClone(datos), 'actual');
  }

  async obtenerDatosTurno(): Promise<DatosTurnoLocal | undefined> {
    return (await this.abrir()).get('datos', 'actual');
  }
}
