package fr.medislot.repository;
import fr.medislot.domain.Appointment;
import org.springframework.data.jpa.repository.*;
public interface AppointmentRepository extends JpaRepository<Appointment,Long> { boolean existsBySlotIdAndStatus(Long slotId,String status);
 java.util.List<Appointment> findByPatientIdOrderByCreatedAtDesc(Long patientId);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select a from Appointment a where a.id=:id") java.util.Optional<Appointment> lockById(Long id);
 /** Le patient a-t-il déjà un rendez-vous confirmé à venir chez ce praticien ? excludeId : le rendez-vous qu'on déplace (sinon -1). */
 @Query("select count(a)>0 from Appointment a, Slot s where s.id=a.slotId and a.patientId=:patientId and a.status='CONFIRMED' and s.professionalId=:professionalId and s.startsAt>:now and a.id<>:excludeId")
 boolean hasUpcomingWith(Long patientId, Long professionalId, java.time.Instant now, Long excludeId); }
