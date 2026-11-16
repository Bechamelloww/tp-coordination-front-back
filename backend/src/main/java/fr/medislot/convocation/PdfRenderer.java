package fr.medislot.convocation;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
/**
 * Modèle Thymeleaf (HTML) → PDF, en mémoire, par OpenHTMLtoPDF.
 * Mode accessible (PDF/UA) : texte balisé, titre et langue lus par les lecteurs d'écran.
 * Ce mode exige des polices embarquées : sans elles, le rendu échoue. Les deux graisses de DejaVu Sans
 * (licence libre, voir fonts/LICENSE-DejaVu.txt) couvrent les accents français.
 */
@Component
public class PdfRenderer {
 static final String FAMILY="DejaVu Sans";
 private final ITemplateEngine templates;
 public PdfRenderer(ITemplateEngine templates){this.templates=templates;}
 /** template : nom sous templates/ sans .html ; le modèle est exposé sous le nom name. */
 public byte[] render(String template,String name,Object model){
  Context context=new Context(Locale.FRENCH);context.setVariable(name,model);
  String html=templates.process(template,context);
  try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
   PdfRendererBuilder builder=new PdfRendererBuilder();
   builder.useFastMode();
   builder.usePdfUaAccessibility(true);
   font(builder,"fonts/DejaVuSans.ttf",400);
   font(builder,"fonts/DejaVuSans-Bold.ttf",700);
   builder.withHtmlContent(html,null);
   builder.toStream(out);
   builder.run();
   return out.toByteArray();
  }catch(IOException e){throw new UncheckedIOException(e);}
 }
 private void font(PdfRendererBuilder builder,String path,int weight){
  builder.useFont(()->open(path),FAMILY,weight,FontStyle.NORMAL,true);
 }
 private InputStream open(String path){
  InputStream in=PdfRenderer.class.getClassLoader().getResourceAsStream(path);
  if(in==null)throw new IllegalStateException("Police absente du classpath : "+path);
  return in;
 }
}
