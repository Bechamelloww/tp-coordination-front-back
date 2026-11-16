package fr.medislot.domain;
import jakarta.persistence.*;
@Entity @Table(name="appointment")
public class Appointment {
 @org.hibernate.annotations.TenantId public Long clinicId;
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public Long patientId; public Long slotId; public String status; public java.time.Instant createdAt;
}
