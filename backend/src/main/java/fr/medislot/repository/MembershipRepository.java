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
 @Query("select c from Clinic c, Membership m where m.clinicId=c.id and m.userId=:userId order by c.slug")
 List<Clinic> clinicsOf(Long userId);
 /** Cabinets du compte avec son rôle dans chacun : [Clinic, String]. */
 @Query("select c, m.role from Clinic c, Membership m where m.clinicId=c.id and m.userId=:userId order by c.slug")
 List<Object[]> clinicsWithRoleOf(Long userId);
}
