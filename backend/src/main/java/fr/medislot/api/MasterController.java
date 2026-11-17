package fr.medislot.api;
import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/master")
public class MasterController {
 private final JdbcTemplate jdbc;
 public MasterController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record ClinicStats(String slug,String name,String city,String color,long professionals,long upcoming,long members,List<String> admins){}

 @GetMapping("/clinics") public List<ClinicStats> clinics(){
  return jdbc.query("""
   select c.slug,c.name,c.city,c.color,
    (select count(*) from professional p where p.clinic_id=c.id) professionals,
    (select count(*) from appointment a join slot s on s.id=a.slot_id where a.clinic_id=c.id and a.status='CONFIRMED' and s.starts_at>=now()) upcoming,
    (select count(*) from membership m where m.clinic_id=c.id) members,
    array(select u.email from membership m join app_user u on u.id=m.user_id where m.clinic_id=c.id and m.role='ADMIN' order by u.email) admins
   from clinic c order by c.slug""",
   (rs,i)->new ClinicStats(rs.getString("slug"),rs.getString("name"),rs.getString("city"),rs.getString("color"),
    rs.getLong("professionals"),rs.getLong("upcoming"),rs.getLong("members"),strings(rs.getArray("admins"))));
 }

 private static List<String> strings(Array array) throws SQLException {return List.of((String[])array.getArray());}
}
