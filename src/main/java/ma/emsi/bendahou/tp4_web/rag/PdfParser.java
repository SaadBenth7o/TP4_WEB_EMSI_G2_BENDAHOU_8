package ma.emsi.bendahou.tp4_web.rag;

import dev.langchain4j.data.document.Document;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;

/**
 * Parser personnalisé pour les fichiers PDF utilisant Apache PDFBox.
 */
public class PdfParser {
    
    /**
     * Parse un fichier PDF depuis un InputStream et retourne un Document LangChain4j.
     * 
     * @param inputStream le flux d'entrée du PDF
     * @return un Document contenant le texte extrait du PDF
     * @throws IOException si une erreur survient lors de la lecture du PDF
     */
    public static Document parse(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return Document.from(text);
        }
    }
}

