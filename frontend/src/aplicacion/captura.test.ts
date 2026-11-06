import { describe, expect, it } from 'vitest';
import { construirRegistro, residentesPara, type EntradaCaptura } from './captura';
import type { DatosTurnoLocal, TurnoLocal } from '../infraestructura/almacenLocal';
const turno: TurnoLocal = { codigo:'turno-sintetico',tipo:'M',declaradoEn:'2026-03-02T08:00:00-05:00',encadenados:[],inicioVentana:'2026-03-02T07:00:00-05:00',finVentana:'2026-03-02T13:00:00-05:00',pendiente:false };
const base = { codigo:'registro-sintetico',expediente:'exp-sintetico',actividad:'ACT-1',versionPlantilla:1,pasos:[{orden:1,cumplido:true}],oportunidad:true,noQuiso:false,nivel:'NIVEL-1',secuenciaRelevante:true,secuenciaCorrecta:true };
const anterior = { expediente:'exp-sintetico',actividad:'ACT-1',versionPlantilla:1,pasos:[{orden:1,cumplido:false}],secuenciaCorrecta:false,oportunidad:true };
const crear = (entrada: Partial<EntradaCaptura>, precarga: typeof anterior | undefined = anterior) => construirRegistro({...base,...entrada},precarga,turno,new Date('2026-03-02T09:00:00Z'),new Date('2026-03-02T09:01:00Z'));
describe('construirRegistro (AD-06, RF-18, RF-32, RF-70, RF-98)',()=>{
  it('deja pasos y nivel vacíos sin oportunidad y con negativa',()=>{
    expect(crear({oportunidad:false,motivoSinOportunidad:'OTRO',motivoOtro:'lluvia'})).toMatchObject({pasos:[],nivel:undefined,motivoOtro:'lluvia'});
    expect(crear({noQuiso:true})).toMatchObject({pasos:[],nivel:undefined,secuenciaCorrecta:undefined});
  });
  it('con oportunidad conserva todos los pasos y exige nivel en la validación del dominio',()=>expect(crear({}).pasos).toEqual(base.pasos));
  it('elimina el texto Otro al volver a Sí',()=>expect(crear({oportunidad:true,motivoSinOportunidad:'OTRO',motivoOtro:'texto'}).motivoOtro).toBeUndefined());
  it('marca cambios de oportunidad, pasos y secuencia solo con precarga compatible',()=>{
    expect(crear({oportunidad:false,motivoSinOportunidad:'FALTAN_RECURSOS'}).camposModificados).toEqual(['OPORTUNIDAD','PASOS','SECUENCIA']);
    expect(construirRegistro(base,undefined,turno,new Date(),new Date()).camposModificados).toEqual([]);
    expect(crear({}, {...anterior,versionPlantilla:2}).camposModificados).toEqual([]);
  });
  it('usa la precarga correspondiente a cada residente',()=>{
    const otro=construirRegistro({...base,expediente:'exp-otro'}, {...anterior,expediente:'exp-otro',pasos:[{orden:1,cumplido:false}]},turno,new Date(),new Date());
    expect(otro.pasos).toEqual([{orden:1,cumplido:true}]);
  });
});

describe('residentesPara (AD-06, RF-18)', () => {
  const datos: DatosTurnoLocal = {
    expedientes: [
      {codigo:'exp-a',nombreVisible:'Residente A',iniciales:'RA',color:'#111111'},
      {codigo:'exp-b',nombreVisible:'Residente B',iniciales:'RB',color:'#222222'},
    ],
    actividadesResidente: [
      {expediente:'exp-a',actividad:'ACT-1',estado:'PRIORITARIA'},
      {expediente:'exp-b',actividad:'ACT-1',estado:'NO_CORRESPONDE'},
      {expediente:'exp-b',actividad:'ACT-2',estado:'ASIGNADA'},
    ], actividades:[], escala:{}, ultimosRegistros:[],
  };
  it('filtra por la actividad elegida y excluye NO_CORRESPONDE', () => {
    expect(residentesPara(datos,'ACT-1').map((r)=>r.codigo)).toEqual(['exp-a']);
    expect(residentesPara(datos,'ACT-2').map((r)=>r.codigo)).toEqual(['exp-b']);
  });
});
