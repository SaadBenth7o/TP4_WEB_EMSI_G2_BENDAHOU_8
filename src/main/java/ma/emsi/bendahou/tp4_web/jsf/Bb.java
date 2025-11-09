package ma.emsi.bendahou.tp4_web.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import ma.emsi.bendahou.tp4_web.llm.LlmClient;
import ma.emsi.bendahou.tp4_web.rag.RagService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing bean pour la page JSF index.xhtml.
 * Portée view pour conserver l'état de la conversation qui dure pendant plusieurs requêtes HTTP.
 * La portée view nécessite l'implémentation de Serializable (le backing bean peut être mis en mémoire secondaire).
 */
@Named
@ViewScoped
public class Bb implements Serializable {

    /**
     * Rôle "système" que l'on attribuera plus tard à un LLM.
     * Valeur par défaut que l'utilisateur peut modifier.
     * Possible d'écrire un nouveau rôle dans la liste déroulante.
     */
    private String roleSysteme;

    /**
     * Quand le rôle est choisi par l'utilisateur dans la liste déroulante,
     * il n'est plus possible de le modifier (voir code de la page JSF), sauf si on veut un nouveau chat.
     */
    private boolean roleSystemeChangeable = true;

    /**
     * Liste de tous les rôles de l'API prédéfinis.
     */
    private List<SelectItem> listeRolesSysteme;

    /**
     * Dernière question posée par l'utilisateur.
     */
    private String question;
    /**
     * Dernière réponse de l'API OpenAI.
     */
    private String reponse;
    /**
     * La conversation depuis le début.
     */
    private StringBuilder conversation = new StringBuilder();

    /**
     * Contexte JSF. Utilisé pour qu'un message d'erreur s'affiche dans le formulaire.
     */
    @Inject
    private FacesContext facesContext;

    /**
     * Service RAG pour le RAG conditionnel et multi-documents.
     */
    @Inject
    private RagService ragService;

    // Instance du client LLM
    private LlmClient llmClient;

    /**
     * Obligatoire pour un bean CDI (classe gérée par CDI), s'il y a un autre constructeur.
     */
    public Bb() {
    }

    public String getRoleSysteme() {
        return roleSysteme;
    }

    public void setRoleSysteme(String roleSysteme) {
        this.roleSysteme = roleSysteme;
    }

    public boolean isRoleSystemeChangeable() {
        return roleSystemeChangeable;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReponse() {
        return reponse;
    }

    /**
     * setter indispensable pour le textarea.
     *
     * @param reponse la réponse à la question.
     */
    public void setReponse(String reponse) {
        this.reponse = reponse;
    }

    public String getConversation() {
        return conversation.toString();
    }

    public void setConversation(String conversation) {
        this.conversation = new StringBuilder(conversation);
    }

    /**
     * Envoie la question au serveur.
     * En attendant de l'envoyer à un LLM, le serveur fait un traitement quelconque, juste pour tester :
     * Le traitement consiste à copier la question en minuscules et à l'entourer avec "||". Le rôle système
     * est ajouté au début de la première réponse.
     *
     * @return null pour rester sur la même page.
     */
    public String envoyer() {
        if (question == null || question.isBlank()) {
            // Erreur ! Le formulaire va être réaffiché en réponse à la requête POST, avec un message d'erreur.
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Texte question vide", "Il manque le texte de la question");
            facesContext.addMessage(null, message);
            return null;
        }


        if (llmClient == null) {
            llmClient = new LlmClient();
            
            // Configurer le service RAG dans le client LLM
            llmClient.setRagService(ragService);
            
            // Déterminer le mode RAG selon le rôle choisi
            String modeRag = determinerModeRag(roleSysteme);
            llmClient.setModeRag(modeRag);

            // On configure le rôle système si c'est la première question
            if (this.conversation.isEmpty()) {
                llmClient.setSystemRole(roleSysteme);
                this.roleSystemeChangeable = false;
            }
        }

        // Appel de la classe LlmClient
        try {
            reponse = llmClient.chat(question);

            afficherConversation();
        } catch (Exception e) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Erreur LLM", "Erreur lors de la communication avec le LLM: " + e.getMessage());
            facesContext.addMessage(null, message);
        }

        return null;
    }

    /**
     * Pour un nouveau chat.
     * Termine la portée view en retournant "index" (la page index.xhtml sera affichée après le traitement
     * effectué pour construire la réponse) et pas null. null aurait indiqué de rester dans la même page (index.xhtml)
     * sans changer de vue.
     * Le fait de changer de vue va faire supprimer l'instance en cours du backing bean par CDI et donc on reprend
     * tout comme au début puisqu'une nouvelle instance du backing va être utilisée par la page index.xhtml.
     *
     * @return "index"
     */
    public String nouveauChat() {
        return "index";
    }

    /**
     * Pour afficher la conversation dans le textArea de la page JSF.
     */
    private void afficherConversation() {
        this.conversation.append("== User:\n").append(question).append("\n== Serveur:\n").append(reponse).append("\n");
    }

    /**
     * Détermine le mode RAG selon le rôle choisi.
     * 
     * @param roleSysteme le rôle système choisi
     * @return le mode RAG : "conditionnel", "multi-documents", ou "desactive"
     */
    private String determinerModeRag(String roleSysteme) {
        if (roleSysteme == null || roleSysteme.isEmpty()) {
            return "conditionnel"; // Par défaut
        }
        
        // RAG Conditionnel : détecte automatiquement si la question nécessite du RAG
        if (roleSysteme.contains("RAG Conditionnel") || roleSysteme.contains("conditionnel")) {
            return "conditionnel";
        }
        
        // RAG Multi-Documents : utilise toujours le RAG avec routage automatique
        if (roleSysteme.contains("RAG Multi-Documents") || roleSysteme.contains("multi-documents") || roleSysteme.contains("routage")) {
            return "multi-documents";
        }
        
        // Mode Standard : pas de RAG
        if (roleSysteme.contains("Mode Standard") || roleSysteme.contains("standard") || roleSysteme.contains("sans RAG")) {
            return "desactive";
        }
        
        // Par défaut, mode conditionnel
        return "conditionnel";
    }

    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            // Génère les rôles de l'API prédéfinis adaptés au projet RAG sur l'architecture
            this.listeRolesSysteme = new ArrayList<>();
            
            // Rôle 1 : RAG Conditionnel (détecte automatiquement si la question nécessite du RAG)
            String role = """
                    You are an expert in architecture. You help users understand different architectural styles.
                    When users ask questions about architecture (Art Déco, Gothic, Modern), you use the provided context from the documents to give accurate and detailed answers.
                    If the context is provided, base your answer on it. If no context is provided, answer based on your general knowledge.
                    RAG Conditionnel: Le système détecte automatiquement si votre question concerne l'architecture et utilise le RAG seulement si nécessaire.
                    """;
            // 1er argument : la valeur du rôle, 2ème argument : le libellé du rôle
            this.listeRolesSysteme.add(new SelectItem(role, "RAG Conditionnel (RAG)"));

            // Rôle 2 : RAG Multi-Documents (utilise toujours le RAG avec routage automatique entre les 3 PDF)
            role = """
                    You are an architecture guide. You help users discover and understand architectural styles and monuments.
                    When users ask about architectural styles (Art Déco, Gothic, Modern), use the provided context from the documents to give detailed information.
                    You can also suggest architectural sites to visit and explain their historical and architectural significance.
                    RAG Multi-Documents: Le système utilise toujours le RAG avec routage automatique entre les 3 PDF (ArtDeco.pdf, gothique.pdf, moderne.pdf).
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "RAG Multi-Documents (RAG Routage)"));

            // Rôle 3 : Mode Standard (sans RAG, réponse directe du LLM)
            role = """
                    You are a helpful assistant. You answer questions directly without using any document context.
                    You provide general information and help users with their questions.
                    Mode Standard: Le système répond directement sans utiliser le RAG ni les documents PDF.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Mode Standard (LLM)"));
        }

        return this.listeRolesSysteme;
    }

}
