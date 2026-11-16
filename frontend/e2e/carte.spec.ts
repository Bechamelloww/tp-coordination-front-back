import {test,expect} from '@playwright/test';
// « Autour de moi (5 km) » : liste triée et carte. Position simulée place Bellecour (TP, aide A1) ; les tuiles sont
// bloquées : le test ne dépend pas d'Internet et ne sollicite pas OpenStreetMap.
test.use({geolocation:{latitude:45.7576,longitude:4.8317},permissions:['geolocation']});

test('autour de Bellecour : six praticiens, du plus proche au plus loin, sur la carte et dans la liste',async({page})=>{
 await page.route('https://tile.openstreetmap.org/**',r=>r.abort());
 const versMediSlot:string[]=[];
 page.on('request',r=>{if(!r.url().startsWith('https://tile.'))versMediSlot.push(r.url()+' '+(r.postData()??''));});

 await page.goto('/');await expect(page.getByText(/praticiens? à découvrir/)).toBeVisible();
 await page.getByRole('button',{name:'Autour de moi (5 km)'}).click();
 await expect(page.getByRole('heading',{name:'6 praticiens à moins de 5 km'})).toBeVisible();

 // La liste se lit sans la carte : triée par distance, la plus proche d'abord.
 const noms=page.locator('.doctor-card .doctor-name');
 await expect(noms).toHaveCount(6);
 await expect(noms.first()).toHaveText('Dr Amel Benali');
 await expect(page.locator('.doctor-card').first().locator('.distance')).toContainText('À 90 m de vous');
 await expect(page.locator('.doctor-card').filter({hasText:'Dr Zoé Garnier'})).toHaveCount(0);// Vénissieux, hors du rayon

 // La carte : un cercle de 5 km, six praticiens et « Vous », l'attribution OpenStreetMap.
 const carte=page.getByRole('region',{name:'Carte OpenStreetMap : 6 praticiens à moins de 5 km de votre position'});
 await expect(carte).toBeVisible();
 await expect(carte.locator('path.leaflet-interactive')).toHaveCount(7);
 await expect(carte.locator('.leaflet-overlay-pane path')).toHaveCount(8);// + le cercle, non cliquable
 await expect(carte.getByText('OpenStreetMap contributors')).toBeVisible();

 // Un point ouvre la fiche, et la fiche passe devant la carte (les calques Leaflet, z-index 400 à 1000, restent dans la carte).
 await carte.locator('path.leaflet-interactive').nth(5).click();// Dr Yann Lambert, le plus loin : aucun autre point dessus
 const fiche=page.getByRole('dialog');
 await expect(fiche.getByRole('heading',{name:'Dr Yann Lambert'})).toBeVisible();
 const centreCarte=await carte.boundingBox();
 expect(await page.evaluate(([x,y])=>!!document.elementFromPoint(x,y)?.closest('.overlay'),[centreCarte!.x+centreCarte!.width/2,centreCarte!.y+centreCarte!.height/2])).toBe(true);
 await fiche.getByRole('button',{name:'Fermer'}).click();

 // Avec un filtre : la même recherche, restreinte à la cardiologie.
 await page.getByLabel('Spécialité').selectOption('cardiology');
 await page.getByRole('button',{name:/Rechercher/}).click();
 await expect(page.getByRole('heading',{name:'2 praticiens à moins de 5 km'})).toBeVisible();
 const carteCardio=page.getByRole('region',{name:'Carte OpenStreetMap : 2 praticiens à moins de 5 km de votre position'});
 await expect(carteCardio.locator('path.leaflet-interactive')).toHaveCount(3);

 // La position ne part jamais vers MediSlot.
 expect(versMediSlot.filter(u=>u.includes('45.75')||u.includes('4.83'))).toEqual([]);
});

test.describe('permission refusée',()=>{
 test.use({permissions:[]});
 test('un message explique le refus et la liste reste affichée',async({page,context})=>{
  await context.clearPermissions();
  await page.goto('/');await expect(page.getByText(/praticiens? à découvrir/)).toBeVisible();
  await page.getByRole('button',{name:'Autour de moi (5 km)'}).click();
  await expect(page.getByRole('alert').filter({hasText:'Localisation refusée'})).toBeVisible();
  await expect(page.getByText(/praticiens? à découvrir/)).toBeVisible();
 });
});
