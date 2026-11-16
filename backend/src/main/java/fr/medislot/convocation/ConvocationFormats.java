package fr.medislot.convocation;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
/**
 * Mise en forme française de la convocation.
 * Le créneau est stocké en Instant (UTC). Un formateur sans fuseau imprimerait l'heure UTC :
 * 8 h 30 en hiver, 7 h 30 en été, pour un rendez-vous à 9 h 30 à Paris. D'où withZone(PARIS) sur chaque formateur,
 * le même fuseau que la liste « Mes rendez-vous » (Intl.DateTimeFormat, timeZone 'Europe/Paris').
 */
public final class ConvocationFormats {
 public static final ZoneId PARIS=ZoneId.of("Europe/Paris");
 static final DateTimeFormatter DAY=DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",Locale.FRENCH).withZone(PARIS);
 static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("H 'h' mm",Locale.FRENCH).withZone(PARIS);
 /** Mêmes libellés que specialties dans frontend/src/types.ts. */
 static final Map<String,String> SPECIALTIES=Map.of("general","Médecine générale","cardiology","Cardiologie","dermatology","Dermatologie","dentistry","Chirurgie dentaire");
 private ConvocationFormats(){}
 /** « mardi 12 janvier 2027 » */
 public static String day(Instant at){return DAY.format(at);}
 /** « 9 h 30 » */
 public static String time(Instant at){return TIME.format(at);}
 public static String specialty(String code){return SPECIALTIES.getOrDefault(code,code);}
 /** Référence à rappeler au cabinet : le numéro du rendez-vous, rien d'autre. */
 public static String reference(Long appointmentId){return "RDV-%06d".formatted(appointmentId);}
}
