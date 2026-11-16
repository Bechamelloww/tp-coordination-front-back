package fr.medislot.config;
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
/**
 * Renvoie l'identifiant de trace dans l'en-tête X-Trace-Id (convention MediSlot).
 * Le front l'affiche dans ses messages d'erreur, le support le recopie dans le ticket.
 * Le span serveur créé par l'agent Java est le span courant ; sans agent, le contexte est invalide et rien n'est ajouté.
 * Ordonné en premier : l'en-tête est posé avant toute réponse, y compris les refus de Spring Security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdHeaderFilter extends OncePerRequestFilter {
 public static final String HEADER="X-Trace-Id";
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException{
  var contexte=Span.current().getSpanContext();
  if(contexte.isValid())response.setHeader(HEADER,contexte.getTraceId());
  chain.doFilter(request,response);
 }
}
