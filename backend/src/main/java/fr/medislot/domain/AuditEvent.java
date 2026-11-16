package fr.medislot.domain;
import jakarta.persistence.*;
@Entity @Table(name="audit_event")
public class AuditEvent {
 @org.hibernate.annotations.TenantId public Long clinicId;
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public Long appointmentId; public Long actorId; public String action; public java.time.Instant createdAt;
}
