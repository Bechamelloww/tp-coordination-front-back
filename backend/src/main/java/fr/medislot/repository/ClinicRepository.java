package fr.medislot.repository;
import fr.medislot.domain.Clinic;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ClinicRepository extends JpaRepository<Clinic,Long> { Optional<Clinic> findBySlug(String slug); }
