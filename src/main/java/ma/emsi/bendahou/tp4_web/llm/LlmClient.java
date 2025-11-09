package ma.emsi.bendahou.tp4_web.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;

public class LlmClient {
    // Clé pour l'API du LLM
    private final String key;

    // Rôle de l'assistant choisi par l'utilisateur
    private String systemRole;

    // Interface pour les interactions LLM
    private Assistant assistant;

    // Mémoire de l'assistant pour garder l'historique de la conversation
    private ChatMemory chatMemory;

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

    public void setSystemRole(String systemRole) {
        this.chatMemory.clear();

        this.systemRole = systemRole;

        this.chatMemory.add(SystemMessage.from(systemRole));
    }

    public String chat(String prompt) {
        return this.assistant.chat(prompt);
    }
}