package fr.medislot.domain;
import jakarta.persistence.*;
import java.io.Serializable;
@Entity @Table(name="membership") @IdClass(Membership.Key.class)
public class Membership {
 public static final String MEMBER="MEMBER", ADMIN="ADMIN";
 public record Key(Long userId, Long clinicId) implements Serializable {}
 @Id public Long userId; @Id public Long clinicId;
 public String role=MEMBER;
}
