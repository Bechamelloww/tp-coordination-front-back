package fr.medislot.domain;
import jakarta.persistence.*;
@Entity @Table(name="professional")
public class Professional {
 public Long clinicId;
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public String name; public String specialty; public String city; public String address; @Column(length=1000) public String bio; public int fee;
 /** Position du cabinet (WGS 84), facultative. */
 public Double latitude; public Double longitude;
}
