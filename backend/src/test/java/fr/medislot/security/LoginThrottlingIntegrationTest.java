package fr.medislot.security;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @Testcontainers
class LoginThrottlingIntegrationTest {
 static final String PASSWORD=java.util.UUID.randomUUID().toString();
 @Container static PostgreSQLContainer db=new PostgreSQLContainer("postgres:18");
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);r.add("medislot.demo-seed",()->"true");r.add("medislot.demo-password",()->PASSWORD);}
 @Autowired WebApplicationContext context;
 MockMvc mvc;
 @BeforeEach void setUp(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 org.springframework.test.web.servlet.ResultActions login(String email,String password) throws Exception {
  return mvc.perform(post("/api/login").with(csrf()).param("username",email).param("password",password));
 }
 @Test void blocksTheAddressAfterFiveFailures() throws Exception {
  login("autre@medislot.test",PASSWORD).andExpect(status().isNoContent());
  for(int i=0;i<LoginAttempts.MAX_FAILURES;i++)login("autre@medislot.test","mauvais-mot-de-passe").andExpect(status().isUnauthorized());
  login("autre@medislot.test",PASSWORD).andExpect(status().isTooManyRequests())
   .andExpect(header().exists("Retry-After"))
   .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  login("patient@medislot.test",PASSWORD).andExpect(status().isNoContent());
 }
}
