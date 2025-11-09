package ma.emsi.bendahou.tp4_web.rag;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.InputStream;

/**
 * Service d'initialisation pour charger les PDF au démarrage de l'application.
 */
@Named
@ApplicationScoped
public class InitialisationRag {

    @Inject
    private RagService ragService;

    /**
     * Initialise les PDF au démarrage de l'application.
     */
    @PostConstruct
    public void initialiser() {
        try {
            // Charger les 3 PDF depuis les ressources
            chargerPdf("ArtDeco.pdf");
            chargerPdf("gothique.pdf");
            chargerPdf("moderne.pdf");
            
            System.out.println("✅ Initialisation RAG terminée : 3 PDF chargés avec succès");
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de l'initialisation RAG: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Charge un PDF depuis les ressources.
     * 
     * @param nomPdf le nom du fichier PDF
     */
    private void chargerPdf(String nomPdf) {
        try {
            // Charger le PDF depuis les ressources
            InputStream inputStream = getClass().getClassLoader()
                    .getResourceAsStream("pdfs/" + nomPdf);
            
            if (inputStream == null) {
                System.err.println("❌ Fichier PDF non trouvé: " + nomPdf);
                return;
            }
            
            // Charger le PDF dans le service RAG
            ragService.chargerPdf(nomPdf, inputStream);
            System.out.println("✅ PDF chargé: " + nomPdf);
            
            inputStream.close();
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du chargement du PDF " + nomPdf + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}

