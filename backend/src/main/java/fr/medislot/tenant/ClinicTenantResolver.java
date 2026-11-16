package fr.medislot.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/** Donne à Hibernate le cabinet des colonnes @TenantId. Sans cabinet, l'identifiant 0 ne correspond à aucune ligne. */
@Component
public class ClinicTenantResolver implements CurrentTenantIdentifierResolver<Long> {
 static final Long NO_CLINIC = 0L;

 @Override public Long resolveCurrentTenantIdentifier() {
  Long id = TenantContext.get();
  return id != null ? id : NO_CLINIC;
 }

 @Override public boolean validateExistingCurrentSessions() { return true; }
}
