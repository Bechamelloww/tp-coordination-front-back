import {test,expect} from '@playwright/test';
test('une personne réserve puis annule son rendez-vous',async({page})=>{
 await page.goto('/');await expect(page.getByText('11 praticiens à découvrir')).toBeVisible();
 await page.getByRole('button',{name:'Se connecter',exact:true}).click();
 await page.getByLabel('Adresse e-mail',{exact:true}).fill('patient@medislot.test');await page.getByLabel('Mot de passe',{exact:true}).fill(process.env.DEMO_PASSWORD??'');
 await page.getByRole('dialog').getByRole('button',{name:'Se connecter',exact:true}).click();await expect(page.getByRole('button',{name:'Camille · Déconnexion'})).toBeVisible();
 await page.getByRole('button',{name:/Réserver avec/}).first().click();const form=page.getByRole('dialog');await form.getByLabel('Motif du rendez-vous').selectOption('FOLLOW_UP');await form.getByLabel('Pour moi').check();
 await form.getByRole('button',{name:/^(Valider|Réserver ce créneau)$/}).click();
 await expect(page.getByText(/^Votre rendez-vous est confirmé/)).toBeVisible();
 await page.getByRole('button',{name:/^Annuler le rendez-vous/}).first().click();await page.getByRole('button',{name:'Confirmer l’annulation'}).click();
 await expect(page.getByText('Votre rendez-vous a été annulé.',{exact:true})).toBeVisible();
});
