package fr.medislot.repository;
import fr.medislot.domain.Slot;
import org.springframework.data.jpa.repository.*;
public interface SlotRepository extends JpaRepository<Slot,Long> { @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select s from Slot s where s.id=:id") java.util.Optional<Slot> lockById(Long id);
 /** Cabinet supprimé entre le choix du créneau et la réservation (lu après le verrou du créneau, que la suppression prend aussi). */
 @Query(value="select exists(select 1 from clinic where id=:clinicId and deleted_at is not null)",nativeQuery=true) boolean clinicDeleted(Long clinicId);
 @Query("select s from Slot s where s.professionalId=:professionalId and s.startsAt>=:from and s.startsAt<:until and not exists (select a.id from Appointment a where a.slotId=s.id and a.status='CONFIRMED') order by s.startsAt")
 java.util.List<Slot> available(Long professionalId, java.time.Instant from, java.time.Instant until);
 /** Débuts des créneaux déjà créés pour un praticien à partir d'un instant (complément des données de démo). */
 @Query("select s.startsAt from Slot s where s.professionalId=:professionalId and s.startsAt>=:from")
 java.util.List<java.time.Instant> startsFrom(Long professionalId, java.time.Instant from); }
