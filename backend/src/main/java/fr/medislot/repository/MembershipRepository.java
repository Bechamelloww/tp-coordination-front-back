package fr.medislot.repository;
import fr.medislot.domain.Clinic;
import fr.medislot.domain.Membership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface MembershipRepository extends JpaRepository<Membership,Membership.Key> {
 boolean existsByUserIdAndClinicId(Long userId, Long clinicId);
 Optional<Membership> findByUserIdAndClinicId(Long userId, Long clinicId);
 /** Gérant : compte admin d'au moins un cabinet. */
 boolean existsByUserIdAndRole(Long userId, String role);
 /** Un patient qui réserve dans un cabinet en devient membre ; sans effet s'il l'est déjà. */
 @org.springframework.data.jpa.repository.Modifying @org.springframework.transaction.annotation.Transactional
 @Query(value="insert into membership(user_id,clinic_id) values (:userId,:clinicId) on conflict do nothing", nativeQuery=true)
 void join(Long userId, Long clinicId);
 @Query("select c from Clinic c, Membership m where m.clinicId=c.id and m.userId=:userId order by c.slug")
 List<Clinic> clinicsOf(Long userId);
 @Query("select c, m.role from Clinic c, Membership m where m.clinicId=c.id and m.userId=:userId order by c.slug")
 List<Object[]> clinicsWithRoleOf(Long userId);
}
