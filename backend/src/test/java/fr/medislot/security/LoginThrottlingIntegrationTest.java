package fr.medislot.security;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @Testcontainers
class LoginThrottlingIntegrationTest {
 static final String PASSWORD=java.util.UUID.randomUUID().toString();
 @Container static PostgreSQLContainer db=new PostgreSQLContainer("postgres:18");
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);r.add("medislot.demo-seed",()->"true");r.add("medislot.demo-password",()->PASSWORD);}
 @Autowired WebApplicationContext context;
 @MockitoBean JavaMailSender mail;
 MockMvc mvc;
 @BeforeEach void setUp(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 org.springframework.test.web.servlet.ResultActions login(String email,String password) throws Exception {
  return mvc.perform(post("/api/login").with(csrf()).param("username",email).param("password",password));
 }
 String lastCode(){
  ArgumentCaptor<SimpleMailMessage> sent=ArgumentCaptor.forClass(SimpleMailMessage.class);
  verify(mail,atLeastOnce()).send(sent.capture());
  Matcher m=Pattern.compile("\\b(\\d{6})\\b").matcher(sent.getValue().getText());
  if(!m.find())throw new AssertionError("pas de code dans le message");
  return m.group(1);
 }
 @Test void blocksTheAddressAfterFiveFailures() throws Exception {
  login("autre@medislot.test",PASSWORD).andExpect(status().isAccepted());
  for(int i=0;i<LoginAttempts.MAX_FAILURES;i++)login("autre@medislot.test","mauvais-mot-de-passe").andExpect(status().isUnauthorized());
  login("autre@medislot.test",PASSWORD).andExpect(status().isTooManyRequests())
   .andExpect(header().exists("Retry-After"))
   .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  login("patient@medislot.test",PASSWORD).andExpect(status().isAccepted());
 }
 @Test void blocksEncodedSpellingsOfTheLoginPath() throws Exception {
  for(int i=0;i<LoginAttempts.MAX_FAILURES;i++)login("encode@medislot.test","mauvais-mot-de-passe").andExpect(status().isUnauthorized());
  mvc.perform(post(java.net.URI.create("/api/%6cogin")).with(csrf()).param("username","patient@medislot.test").param("password",PASSWORD))
   .andExpect(status().is4xxClientError());
  verify(mail,never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
 }
 @Test void thePasswordAloneOpensNoSession() throws Exception {
  MockHttpSession session=new MockHttpSession();
  mvc.perform(post("/api/login").session(session).with(csrf()).param("username","patient@medislot.test").param("password",PASSWORD))
   .andExpect(status().isAccepted()).andExpect(jsonPath("$.etape").value("code")).andExpect(jsonPath("$.destination").value("p***@medislot.test"));
  mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
 }
 @Test void theMailedCodeOpensTheSession() throws Exception {
  MockHttpSession session=new MockHttpSession();
  mvc.perform(post("/api/login").session(session).with(csrf()).param("username","patient@medislot.test").param("password",PASSWORD)).andExpect(status().isAccepted());
  String before=session.getId();
  mvc.perform(post("/api/login/code").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\""+lastCode()+"\"}"))
   .andExpect(status().isNoContent());
  if(before.equals(session.getId()))throw new AssertionError("l'identifiant de session n'a pas changé");
  mvc.perform(get("/api/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("patient@medislot.test"));
 }
 @Test void aWrongCodeOpensNoSession() throws Exception {
  MockHttpSession session=new MockHttpSession();
  mvc.perform(post("/api/login").session(session).with(csrf()).param("username","patient@medislot.test").param("password",PASSWORD)).andExpect(status().isAccepted());
  String wrong=lastCode().equals("000000")?"111111":"000000";
  mvc.perform(post("/api/login/code").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\""+wrong+"\"}"))
   .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("CODE_INCORRECT"));
  mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
 }
}
