package fr.medislot.tenant;

/** Cabinet de la requête en cours, posé par TenantFilter et toujours retiré à la fin. */
public final class TenantContext {
 private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

 private TenantContext() {}

 public static void set(Long clinicId) { CURRENT.set(clinicId); }

 public static Long get() { return CURRENT.get(); }

 public static Long require() {
  Long id = CURRENT.get();
  if (id == null) throw new IllegalStateException("Aucun cabinet dans le contexte.");
  return id;
 }

 public static void clear() { CURRENT.remove(); }
}
