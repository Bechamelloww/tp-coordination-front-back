package fr.medislot.domain;
import jakarta.persistence.*;
/** Les cabinets supprimés (deleted_at posé) sont invisibles pour toute requête JPA : filtre, listes, connexion Google. */
@Entity @Table(name="clinic") @org.hibernate.annotations.SQLRestriction("deleted_at is null")
public class Clinic {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public String slug; public String name; public String city; public String color;
}
