/** Tipos permitidos al declarar una jornada (RF-12, RF-90, DD-27). */
export type TipoTurno = 'M' | 'T' | 'MT' | 'GC' | 'GD' | 'N' | 'GN' | 'D';

const ZONA_LIMA = 'America/Lima';
const HORA = { siete: 7, trece: 13, diecinueve: 19 } as const;
const milisegundosMinuto = 60_000;
const desfaseLimaMs = 5 * 60 * milisegundosMinuto;

/** Turno declarado y su ventana operativa en hora civil de Lima (RF-12, RF-90, D-66, DD-27). */
export class TurnoDeclarado {
  readonly codigo: string;
  readonly tipo: TipoTurno;
  readonly declaradoEn: Date;
  private readonly inicio: Date;
  private fin: Date;
  private readonly siguientes: TipoTurno[] = [];

  constructor(codigo: string, tipo: TipoTurno, declaradoEn: Date) {
    this.codigo = codigo;
    this.tipo = tipo;
    this.declaradoEn = new Date(declaradoEn);
    const [inicio, fin] = ventana(tipo, fechaLocalLima(declaradoEn));
    this.inicio = inicio;
    this.fin = fin;
  }

  /** Instantes Date reales (UTC) equivalentes a las marcas civiles de Lima. */
  get inicioVentana(): Date { return new Date(this.inicio.getTime() + desfaseLimaMs); }
  get finVentana(): Date { return new Date(this.fin.getTime() + desfaseLimaMs); }
  get encadenados(): readonly TipoTurno[] { return [...this.siguientes]; }

  static sugerirTipo(momento: Date): TipoTurno {
    const hora = fechaLocalLima(momento).getUTCHours();
    if (hora >= HORA.siete && hora < HORA.trece) return 'M';
    if (hora >= HORA.trece && hora < HORA.diecinueve) return 'T';
    return 'N';
  }

  habilitaRegistro(): boolean {
    return this.tipo !== 'D';
  }

  /** El dispositivo habilita captura sin tolerancia; el servidor aplica su propia tolerancia (D-66). */
  contieneMomento(momento: Date, toleranciaMinutos = 0): boolean {
    if (!this.habilitaRegistro()) return false;
    const valor = fechaLocalLima(momento).getTime();
    return valor >= this.inicio.getTime() - toleranciaMinutos * milisegundosMinuto
      && valor <= this.fin.getTime() + toleranciaMinutos * milisegundosMinuto;
  }

  /** Amplía la ventana solo si el siguiente tipo empieza al cerrar la ventana vigente (RF-90). */
  encadenar(tipo: TipoTurno): void {
    if (!this.habilitaRegistro() || tipo === 'D') throw new Error('El descanso no se encadena');
    const [, nuevoFin] = ventana(tipo, new Date(this.fin));
    const [nuevoInicio] = ventana(tipo, new Date(this.fin));
    if (nuevoInicio.getTime() !== this.fin.getTime()) {
      throw new RangeError(`El turno ${tipo} no empieza donde termina la ventana`);
    }
    this.siguientes.push(tipo);
    this.fin = nuevoFin;
  }
}

/** Convierte un instante a una fecha civil de Lima representada como UTC para hacer aritmética local. */
function fechaLocalLima(instante: Date): Date {
  const partes = new Intl.DateTimeFormat('en-CA', {
    timeZone: ZONA_LIMA,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(instante);
  const valor = (tipo: Intl.DateTimeFormatPartTypes) => Number(partes.find((parte) => parte.type === tipo)?.value);
  return new Date(Date.UTC(valor('year'), valor('month') - 1, valor('day'), valor('hour'), valor('minute'), valor('second'), instante.getUTCMilliseconds()));
}

/** Las marcas Date de salida expresan los componentes de la hora civil de Lima. */
function ventana(tipo: TipoTurno, momentoLima: Date): [Date, Date] {
  const dia = Date.UTC(momentoLima.getUTCFullYear(), momentoLima.getUTCMonth(), momentoLima.getUTCDate());
  const marca = (diaBase: number, hora: number) => new Date(diaBase + hora * 60 * milisegundosMinuto);
  switch (tipo) {
    case 'M': return [marca(dia, HORA.siete), marca(dia, HORA.trece)];
    case 'T': return [marca(dia, HORA.trece), marca(dia, HORA.diecinueve)];
    case 'MT': case 'GC': case 'GD': return [marca(dia, HORA.siete), marca(dia, HORA.diecinueve)];
    case 'N': case 'GN': {
      const inicioDia = momentoLima.getUTCHours() < HORA.siete ? dia - 86_400_000 : dia;
      return [marca(inicioDia, HORA.diecinueve), marca(inicioDia + 86_400_000, HORA.siete)];
    }
    case 'D': return [new Date(momentoLima), new Date(momentoLima)];
  }
}
