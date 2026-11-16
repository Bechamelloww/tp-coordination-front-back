package fr.medislot.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateTenantConfig {
 @Bean HibernatePropertiesCustomizer tenantResolver(ClinicTenantResolver resolver) {
  return props -> props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
 }
}
