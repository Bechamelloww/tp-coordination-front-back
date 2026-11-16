package fr.medislot.repository;
import fr.medislot.domain.AppUser;
import org.springframework.data.jpa.repository.*;
public interface UserRepository extends JpaRepository<AppUser,Long> { java.util.Optional<AppUser> findByEmail(String email); boolean existsByEmail(String email);
 java.util.Optional<AppUser> findByGoogleSub(String googleSub);
 /** Verrou du patient : deux réservations simultanées du même patient passent l'une après l'autre. Toujours pris après les créneaux. */
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select u from AppUser u where u.id=:id") java.util.Optional<AppUser> lockById(Long id); }
