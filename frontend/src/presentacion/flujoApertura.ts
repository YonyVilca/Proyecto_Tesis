import type { DatosTurnoLocal, IAlmacenLocal, SesionLocal, TurnoLocal } from '../infraestructura/almacenLocal';

/** Resuelve la apertura según SD-01 1.3 y 1.3.1 (RF-02, DD-56). */
export async function recuperarTurnoAlAbrir(
  sesion: SesionLocal,
  almacen: IAlmacenLocal,
  haySenal: boolean,
  consultarTurno: () => Promise<TurnoLocal | undefined>,
  descargarDatos: (codigo: string) => Promise<DatosTurnoLocal>,
  ahora = new Date(),
): Promise<TurnoLocal | undefined> {
  const local = await almacen.obtenerTurnoVigente(ahora);
  if (local) return local;
  if (!haySenal) return undefined;
  let turno: TurnoLocal | undefined;
  try { turno = await consultarTurno(); } catch { return undefined; }
  if (!turno) return undefined;
  await almacen.guardarTurno(turno);
  try {
    const datos = await descargarDatos(turno.codigo);
    await almacen.guardarDatosTurno(datos);
  } catch { /* Se conserva el turno recuperado y cualquier dato local existente. */ }
  return turno;
}
