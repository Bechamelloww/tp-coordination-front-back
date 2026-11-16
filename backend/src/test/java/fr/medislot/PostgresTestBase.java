package fr.medislot;
import fr.medislot.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
@SpringBootTest
public abstract class PostgresTestBase {
 static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:18");
 static {DB.start();}
 @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);r.add("medislot.demo-seed",()->"true");r.add("medislot.demo-password",()->java.util.UUID.randomUUID().toString());}
 @Autowired private JdbcTemplate baseJdbc;
 protected Long clinicId(String slug){return baseJdbc.queryForObject("select id from clinic where slug=?",Long.class,slug);}
 // par défaut, chaque test s'exécute dans cabinet-a
 @BeforeEach void useClinicA(){TenantContext.set(clinicId("cabinet-a"));}
 @AfterEach void clearClinic(){TenantContext.clear();}
}
