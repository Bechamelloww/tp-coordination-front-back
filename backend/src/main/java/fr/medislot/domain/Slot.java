package fr.medislot.domain;
import jakarta.persistence.*;
@Entity @Table(name="slot")
public class Slot {
 public Long clinicId;
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public Long professionalId; public java.time.Instant startsAt; public java.time.Instant endsAt;
}
