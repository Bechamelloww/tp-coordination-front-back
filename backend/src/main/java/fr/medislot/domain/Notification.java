package fr.medislot.domain;
import jakarta.persistence.*;
@Entity @Table(name="notification")
public class Notification {
 public Long clinicId;
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public Long patientId; public String message; public java.time.Instant createdAt;
}
