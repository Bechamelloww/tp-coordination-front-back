package fr.medislot;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.mockito.Mockito.verify;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest @Testcontainers
class ActivityLogIntegrationTest {
 static final String PASSWORD = UUID.randomUUID().toString();
 @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:18");

 @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
  r.add("spring.datasource.url", db::getJdbcUrl);
  r.add("spring.datasource.username", db::getUsername);
  r.add("spring.datasource.password", db::getPassword);
  r.add("medislot.demo-seed", () -> "true");
  r.add("medislot.demo-password", () -> PASSWORD);
 }

 @Autowired WebApplicationContext context;
 @MockitoBean JavaMailSender mail;
 MockMvc mvc;
 static boolean recorded;

 @BeforeEach void recordActionsOnce() throws Exception {
  mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  if (recorded) return;
  recorded = true;
  mvc.perform(post("/api/admin/members").header("X-Clinic", "cabinet-b").with(user("admin-b@medislot.test").roles("PATIENT")).with(csrf())
    .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"autre@medislot.test\"}")).andExpect(status().isCreated());
  mvc.perform(post("/api/master/clinics").with(user("master@medislot.test").roles("MASTER")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
    .content("{\"slug\":\"cabinet-z\",\"name\":\"Cabinet Zola\",\"city\":\"Nantes\",\"address\":\"3 rue Zola\",\"color\":\"#123456\"}")).andExpect(status().isCreated());
  MockHttpSession session = new MockHttpSession();
  mvc.perform(post("/api/login").session(session).param("username", "patient@medislot.test").param("password", PASSWORD).with(csrf())).andExpect(status().isAccepted());
  ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
  verify(mail).send(sent.capture());
  Matcher code = Pattern.compile("\\b(\\d{6})\\b").matcher(sent.getValue().getText());
  if (!code.find()) throw new AssertionError("pas de code dans le message");
  mvc.perform(post("/api/login/code").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code.group(1) + "\"}"))
    .andExpect(status().isNoContent());
 }

 private ResultActions clinicActivity(String clinic, String email, String query) throws Exception {
  return mvc.perform(get("/api/admin/activity?" + query).header("X-Clinic", clinic).with(user(email).roles("PATIENT")));
 }

 private ResultActions allActivity(String query) throws Exception {
  return mvc.perform(get("/api/master/activity?" + query).with(user("master@medislot.test").roles("MASTER")));
 }

 @Test void leDirecteurNeVoitQueLHistoriqueDeSonCabinet() throws Exception {
  clinicActivity("cabinet-b", "admin-b@medislot.test", "")
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.entries[*].action", hasItem("MEMBER_ADDED")))
    .andExpect(jsonPath("$.entries[*].clinicSlug", everyItem(is("cabinet-b"))))
    .andExpect(jsonPath("$.entries[0].actorEmail").value("admin-b@medislot.test"))
    .andExpect(jsonPath("$.entries[0].detail", containsString("Alex Martin")));
  clinicActivity("cabinet-a", "admin-a@medislot.test", "")
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.entries[*].clinicSlug", not(hasItem("cabinet-b"))))
    .andExpect(jsonPath("$.entries[*].action", not(hasItem("LOGIN"))));
  clinicActivity("cabinet-b", "admin-a@medislot.test", "").andExpect(status().isNotFound());
  clinicActivity("cabinet-a", "patient@medislot.test", "").andExpect(status().isForbidden());
  mvc.perform(get("/api/master/activity").with(user("admin-b@medislot.test").roles("PATIENT"))).andExpect(status().isForbidden());
 }

 @Test void leMasterVoitToutEtFiltre() throws Exception {
  allActivity("").andExpect(status().isOk())
    .andExpect(jsonPath("$.entries[*].action", hasItems("MEMBER_ADDED", "CLINIC_CREATED", "LOGIN")));
  allActivity("outside=true").andExpect(jsonPath("$.entries[*].clinicSlug", everyItem(nullValue())))
    .andExpect(jsonPath("$.entries[*].actorEmail", hasItem("patient@medislot.test")));
  allActivity("clinic=cabinet-z").andExpect(jsonPath("$.entries", hasSize(1)))
    .andExpect(jsonPath("$.entries[0].action").value("CLINIC_CREATED"))
    .andExpect(jsonPath("$.entries[0].detail").value("Cabinet Zola · Nantes"));
  allActivity("action=LOGIN").andExpect(jsonPath("$.entries[*].action", everyItem(is("LOGIN"))));
  allActivity("text=alex").andExpect(jsonPath("$.entries[*].action", hasItem("MEMBER_ADDED")));
  allActivity("text=100%25").andExpect(jsonPath("$.entries", hasSize(0)));
  allActivity("from=2000-01-01&to=2000-01-02").andExpect(jsonPath("$.entries", hasSize(0))).andExpect(jsonPath("$.more").value(false));
 }

 @Test void lesFiltresInvalidesSontRefuses() throws Exception {
  allActivity("action=INCONNU").andExpect(status().isBadRequest());
  allActivity("clinic=inconnu").andExpect(status().isNotFound());
  allActivity("from=2026-02-01&to=2026-01-01").andExpect(status().isBadRequest());
 }
}
