package fr.medislot.convocation;
import fr.medislot.api.Errors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Les en-têtes du contrat et les erreurs en problem+json. */
class ConvocationControllerTest {
 ConvocationService service=mock(ConvocationService.class);
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new ConvocationController(service)).setControllerAdvice(new Errors()).build();
 static final String URL="/api/appointments/42/convocation.pdf";
 static final String PATIENT="helene@medislot.test";

 void pdf(){when(service.build(PATIENT,42L)).thenReturn("%PDF-1.7".getBytes());when(service.filename(42L)).thenReturn("convocation-42.pdf");}

 @Test void afficheEnLigneParDefautAvecLesQuatreEnTetes() throws Exception {
  pdf();
  mvc.perform(get(URL).principal(()->PATIENT).accept("application/pdf","application/problem+json")).andExpect(status().isOk())
   .andExpect(header().string("Content-Type","application/pdf"))
   .andExpect(header().string("Content-Disposition","inline; filename=\"convocation-42.pdf\""))
   .andExpect(header().string("Cache-Control","no-store"))
   .andExpect(header().string("X-Content-Type-Options","nosniff"))
   .andExpect(content().bytes("%PDF-1.7".getBytes()));
 }
 @Test void proposeLEnregistrementAvecDownloadTrue() throws Exception {
  pdf();
  mvc.perform(get(URL+"?download=true").principal(()->PATIENT)).andExpect(status().isOk())
   .andExpect(header().string("Content-Disposition","attachment; filename=\"convocation-42.pdf\""))
   .andExpect(header().string("Cache-Control","no-store"));
 }
 @Test void unRendezVousDUnAutreDonneUn404EnProblemJson() throws Exception {
  when(service.build(PATIENT,42L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,ConvocationService.NOT_FOUND));
  mvc.perform(get(URL).principal(()->PATIENT).accept("application/pdf","application/problem+json")).andExpect(status().isNotFound())
   .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
   .andExpect(jsonPath("$.detail").value(ConvocationService.NOT_FOUND));
 }
 @Test void unRendezVousAnnuleDonneUn409EnProblemJson() throws Exception {
  when(service.build(PATIENT,42L)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,ConvocationService.CANCELLED));
  mvc.perform(get(URL).principal(()->PATIENT).accept("application/pdf","application/problem+json")).andExpect(status().isConflict())
   .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
   .andExpect(jsonPath("$.detail").value(ConvocationService.CANCELLED));
 }
}
