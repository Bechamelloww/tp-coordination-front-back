package fr.medislot.domain;
import jakarta.persistence.*;
@Entity @Table(name="app_user")
public class AppUser {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public static final String PATIENT="PATIENT", MASTER="MASTER";
 public String email; public String password; public String name; public String role;
 public String googleSub;
}
