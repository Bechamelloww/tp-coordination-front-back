import {describe,it,expect} from 'vitest';
import {autourDe,distanceKm,formatDistance,lienCabinet,RAYON_KM} from './geo';
import type {Professional} from './types';

const bellecour={latitude:45.7576,longitude:4.8317};
const charite={latitude:45.7568,longitude:4.8319};
const paris={latitude:48.8566,longitude:2.3522},lyon={latitude:45.7640,longitude:4.8357};

describe('Distance et formats',()=>{
 it('calcule une distance à vol d’oiseau réaliste',()=>{
  expect(distanceKm(paris,lyon)).toBeGreaterThan(390);expect(distanceKm(paris,lyon)).toBeLessThan(393);// ≈ 391,5 km
  expect(distanceKm(bellecour,charite)*1000).toBeCloseTo(90,-1);
  expect(distanceKm(lyon,lyon)).toBe(0);
 });
 it('affiche mètres et kilomètres en français',()=>{
  expect(formatDistance(0.111)).toBe('110 m');
  expect(formatDistance(3.24)).toBe('3,2 km');
  expect(formatDistance(392.4)).toBe('392 km');
 });
 it('montre le cabinet seul sur OpenStreetMap',()=>{
  expect(lienCabinet(charite)).toBe('https://www.openstreetmap.org/?mlat=45.7568&mlon=4.8319#map=17/45.7568/4.8319');
 });
});

const pro=(id:number,name:string,latitude:number|null,longitude:number|null):Professional=>({id,name,specialty:'general',city:'Lyon',address:'',bio:'',fee:30,latitude,longitude});

describe('Autour de moi',()=>{
 it('garde les praticiens à moins de 5 km, du plus proche au plus loin',()=>{
  const r=autourDe(bellecour,[
   pro(1,'Dr Loin',45.6975,4.8868),// Vénissieux, ≈ 7,9 km
   pro(2,'Dr Croix-Rousse',45.7743,4.8318),// ≈ 1,9 km
   pro(3,'Dr Charité',45.7568,4.8319),// ≈ 90 m
   pro(4,'Dr Paris',48.8664,2.37),
   pro(5,'Dr Sans adresse',null,null),
  ]);
  expect(RAYON_KM).toBe(5);
  expect(r.map(x=>x.professional.id)).toEqual([3,2]);
  expect(r[1].distanceKm).toBeCloseTo(1.86,1);
 });
 it('départage deux cabinets à la même distance par le nom',()=>{
  const r=autourDe(bellecour,[pro(2,'Dr Zoé',45.7568,4.8319),pro(1,'Dr Anne',45.7568,4.8319)]);
  expect(r.map(x=>x.professional.name)).toEqual(['Dr Anne','Dr Zoé']);
 });
 it('ne trouve personne dans le golfe de Guinée',()=>{
  expect(autourDe({latitude:0,longitude:0},[pro(1,'Dr Charité',45.7568,4.8319)])).toEqual([]);
 });
});
