package fr.medislot.statistics;
import fr.medislot.statistics.StatisticsService.ClinicStatistics;
import fr.medislot.statistics.StatisticsService.NetworkStatistics;
import fr.medislot.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
@RestController
public class StatisticsController {
 private final StatisticsService statistics;
 public StatisticsController(StatisticsService statistics){this.statistics=statistics;}

 @GetMapping("/api/admin/statistics") public ClinicStatistics currentClinic(){
  return statistics.forClinic(TenantContext.require());
 }

 @GetMapping("/api/master/statistics") public NetworkStatistics network(){
  return statistics.forNetwork();
 }

 @GetMapping("/api/master/statistics/{slug}") public ClinicStatistics clinic(@PathVariable String slug){
  return statistics.forClinic(slug).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cabinet introuvable."));
 }
}
