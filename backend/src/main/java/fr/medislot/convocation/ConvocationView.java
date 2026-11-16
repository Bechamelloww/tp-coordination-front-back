package fr.medislot.convocation;
/**
 * Ce que la convocation imprime, déjà mis en forme : le modèle Thymeleaf n'a plus aucun calcul à faire.
 * Données minimales : ni e-mail, ni motif, ni téléphone (minimisation).
 */
public record ConvocationView(String patientName,String professionalName,String specialty,String day,String time,String address,String city,String reference){}
