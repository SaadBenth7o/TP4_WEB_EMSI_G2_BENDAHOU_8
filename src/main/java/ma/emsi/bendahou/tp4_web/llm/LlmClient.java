package ma.emsi.bendahou.tp4_web.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import ma.emsi.bendahou.tp4_web.rag.RagService;

public class LlmClient {
    // Clé pour l'API du LLM
    private final String key;

    // Rôle de l'assistant choisi par l'utilisateur
    private String systemRole;

    // Interface pour les interactions LLM
    private Assistant assistant;

    // Mémoire de l'assistant pour garder l'historique de la conversation
    private ChatMemory chatMemory;

    // Service RAG pour le RAG conditionnel et multi-documents
    private RagService ragService;
    
    // Mode RAG : "conditionnel", "multi-documents", ou "desactive"
    private String modeRag = "conditionnel";

    public LlmClient() {
        // Récupère la clé secrète pour travailler avec l'API du LLM, mise dans une variable d'environnement
        // du système d'exploitation.
        this.key = System.getenv("GEMINI_KEY");

        if (key == null || key.isEmpty()) {
            System.err.println("❌ ERREUR: La clé GEMINI_KEY n'est pas définie!");
            throw new RuntimeException("La clé API GEMINI_KEY n'est pas configurée");
        }

        ChatModel chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(key)
                .modelName("gemini-2.5-flash")
                .build();

        this.chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .build();
    }

    /**
     * Définit le service RAG à utiliser pour le RAG conditionnel et multi-documents.
     * 
     * @param ragService le service RAG
     */
    public void setRagService(RagService ragService) {
        this.ragService = ragService;
    }
    
    /**
     * Définit le mode RAG : "conditionnel", "multi-documents", ou "desactive".
     * 
     * @param modeRag le mode RAG
     */
    public void setModeRag(String modeRag) {
        this.modeRag = modeRag;
    }

    public void setSystemRole(String systemRole) {
        this.chatMemory.clear();

        this.systemRole = systemRole;

        this.chatMemory.add(SystemMessage.from(systemRole));
    }

    /**
     * Envoie un message au LLM avec RAG selon le mode configuré.
     * 
     * @param prompt le message de l'utilisateur
     * @return la réponse du LLM
     */
    public String chat(String prompt) {
        // Mode RAG désactivé : pas de RAG
        if ("desactive".equals(modeRag) || ragService == null) {
            return this.assistant.chat(prompt);
        }
        
        // Mode RAG conditionnel : vérifier si la question nécessite du RAG
        if ("conditionnel".equals(modeRag)) {
            if (ragService.necessiteRag(prompt)) {
                // Récupérer le contexte pertinent depuis les documents
                String contexte = ragService.recupererContexte(prompt);
                
                if (contexte != null && !contexte.isEmpty()) {
                    // Construire le prompt enrichi avec le contexte
                    String promptEnrichi = String.format(
                        "Contexte fourni:\n%s\n\nQuestion de l'utilisateur: %s\n\nRéponds à la question en utilisant le contexte fourni. Si le contexte ne contient pas d'information pertinente, dis-le clairement.",
                        contexte, prompt
                    );
                    return this.assistant.chat(promptEnrichi);
                }
            }
            // Pas de RAG nécessaire ou contexte non trouvé, utiliser le prompt normal
            return this.assistant.chat(prompt);
        }
        
        // Mode RAG multi-documents : toujours utiliser le RAG avec routage automatique
        if ("multi-documents".equals(modeRag)) {
            // Toujours essayer de récupérer le contexte (routage automatique)
            String contexte = ragService.recupererContexte(prompt);
            
            if (contexte != null && !contexte.isEmpty()) {
                // Construire le prompt enrichi avec le contexte
                String promptEnrichi = String.format(
                    "Contexte fourni:\n%s\n\nQuestion de l'utilisateur: %s\n\nRéponds à la question en utilisant le contexte fourni. Si le contexte ne contient pas d'information pertinente, dis-le clairement.",
                    contexte, prompt
                );
                return this.assistant.chat(promptEnrichi);
            }
            // Si aucun contexte n'est trouvé, utiliser le prompt normal
            return this.assistant.chat(prompt);
        }
        
        // Par défaut, utiliser le prompt normal
        return this.assistant.chat(prompt);
    }
}