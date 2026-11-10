/** Resume el estado local de envío de registros (HU-23, RF-31, CU-02). */
export function resumenEnvio(pendientes: number, rechazados: number): string {
  if (rechazados > 0) return `${rechazados} registro(s) no aceptados por el servidor`;
  if (pendientes > 0) return `${pendientes} registro(s) pendientes de envío. Se enviarán cuando haya señal.`;
  return 'Todo enviado.';
}
