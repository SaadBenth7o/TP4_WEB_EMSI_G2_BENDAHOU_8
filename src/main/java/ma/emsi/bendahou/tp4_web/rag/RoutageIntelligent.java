package ma.emsi.bendahou.tp4_web.rag;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;

import java.util.Map;

/**
 * Interface pour le routage intelligent.
 */
interface RouterAssistant {
    String route(String prompt);
}

/**
 * Service de routage intelligent pour sélectionner le bon PDF selon la question.
 */
public class RoutageIntelligent {

    private static final String PROMPT_ROUTAGE = """
            Tu es un assistant qui aide à déterminer quel document PDF est le plus pertinent pour répondre à une question.
            
            Les documents disponibles sont :
            1. ArtDeco.pdf - Document sur l'architecture Art Déco
            2. gothique.pdf - Document sur l'architecture gothique
            3. moderne.pdf - Document sur l'architecture moderne
            
            Analyse la question suivante et réponds UNIQUEMENT par le nom du fichier PDF le plus pertinent (ArtDeco.pdf, gothique.pdf, ou moderne.pdf).
            Si la question ne concerne aucun de ces sujets, réponds "aucun".
            
            Question: %s
            """;

    private final ChatModel chatModel;

    public RoutageIntelligent() {
        String key = System.getenv("GEMINI_KEY");
        if (key == null || key.isEmpty()) {
            throw new RuntimeException("La clé API GEMINI_KEY n'est pas configurée");
        }
        this.chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(key)
                .modelName("gemini-2.0-flash-exp")
                .build();
    }

    /**
     * Détermine quel PDF est le plus pertinent pour la question donnée.
     * 
     * @param question la question de l'utilisateur
     * @return le nom du fichier PDF le plus pertinent, ou "aucun" si aucun PDF n'est pertinent
     */
    public String determinerPdfPertinent(String question) {
        String prompt = String.format(PROMPT_ROUTAGE, question);
        // Utiliser la méthode appropriée pour générer du texte
        // Créer un assistant temporaire pour générer la réponse
        RouterAssistant assistant = AiServices.builder(RouterAssistant.class)
                .chatModel(chatModel)
                .build();
        String reponse = assistant.route(prompt);
        
        // Nettoyer la réponse pour extraire le nom du fichier
        String reponseNettoyee = reponse.trim().toLowerCase();
        
        // Vérifier si la réponse contient un nom de fichier valide
        // Mapping entre les noms de fichiers en minuscules et les noms réels
        Map<String, String> mappingFichiers = Map.of(
            "artdeco.pdf", "ArtDeco.pdf",
            "gothique.pdf", "gothique.pdf",
            "moderne.pdf", "moderne.pdf"
        );
        
        for (Map.Entry<String, String> entry : mappingFichiers.entrySet()) {
            if (reponseNettoyee.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // Si aucun fichier valide n'est trouvé, vérifier si c'est "aucun"
        if (reponseNettoyee.contains("aucun")) {
            return "aucun";
        }
        
        // Par défaut, retourner "aucun" si on ne peut pas déterminer
        return "aucun";
    }

    /**
     * Détermine si la question nécessite du RAG (si elle concerne l'architecture).
     * 
     * @param question la question de l'utilisateur
     * @return true si la question nécessite du RAG, false sinon
     */
    public boolean necessiteRag(String question) {
        String pdfPertinent = determinerPdfPertinent(question);
        return !"aucun".equals(pdfPertinent);
    }
}

