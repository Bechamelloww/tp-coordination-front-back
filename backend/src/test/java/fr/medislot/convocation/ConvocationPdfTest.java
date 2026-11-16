package fr.medislot.convocation;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import static org.junit.jupiter.api.Assertions.*;
/**
 * Le vrai rendu : modèle Thymeleaf + OpenHTMLtoPDF, relu avec PDFBox.
 * Écrit target/convocation-exemple.pdf pour une relecture « comme un patient ».
 */
class ConvocationPdfTest {
 static byte[] pdf;
 static final ConvocationView HELENE=new ConvocationView("Hélène Durand","Dr Léa Moreau","Chirurgie dentaire","mardi 12 janvier 2027","9 h 30","12 boulevard Saint-Germain","Paris","RDV-000042");

 @BeforeAll static void render() throws Exception {
  // Même moteur que l'application (SpringTemplateEngine, SpEL), même dossier templates/.
  ClassLoaderTemplateResolver resolver=new ClassLoaderTemplateResolver();resolver.setPrefix("templates/");resolver.setSuffix(".html");resolver.setCharacterEncoding("UTF-8");
  SpringTemplateEngine engine=new SpringTemplateEngine();engine.setTemplateResolver(resolver);
  pdf=new PdfRenderer(engine).render("convocation","c",HELENE);
  Files.createDirectories(Path.of("target"));Files.write(Path.of("target","convocation-exemple.pdf"),pdf);
 }
 @Test void cEstUnPdf(){
  assertEquals("%PDF-",new String(pdf,0,5));
 }
 @Test void leTexteImprimeEstJusteAccentsCompris() throws Exception {
  try(PDDocument doc=Loader.loadPDF(pdf)){
   String text=new PDFTextStripper().getText(doc);
   for(String expected:new String[]{"Hélène Durand","mardi 12 janvier 2027","9 h 30","Dr Léa Moreau","Chirurgie dentaire","12 boulevard Saint-Germain, Paris","RDV-000042","Heure de Paris"})
    assertTrue(text.contains(expected),"Absent du PDF : "+expected);
   assertEquals(1,doc.getNumberOfPages());
  }
 }
 @Test void lesPolicesSontEmbarquees() throws Exception {
  try(PDDocument doc=Loader.loadPDF(pdf)){
   var resources=doc.getPage(0).getResources();int fonts=0;
   for(COSName name:resources.getFontNames()){PDFont font=resources.getFont(name);fonts++;
    assertTrue(font.isEmbedded(),"Police non embarquée : "+font.getName());
    assertTrue(font.getName().contains("DejaVuSans"),"Police inattendue : "+font.getName());}
   assertTrue(fonts>=2,"Regular et Bold attendues, trouvées : "+fonts);
  }
 }
 @Test void leDocumentEstBaliseEtTitre() throws Exception {
  try(PDDocument doc=Loader.loadPDF(pdf)){
   assertEquals("Convocation MediSlot",doc.getDocumentInformation().getTitle());
   assertNotNull(doc.getDocumentCatalog().getStructureTreeRoot(),"PDF non balisé (mode accessible)");
   assertEquals("fr",doc.getDocumentCatalog().getLanguage());
  }
 }
}
