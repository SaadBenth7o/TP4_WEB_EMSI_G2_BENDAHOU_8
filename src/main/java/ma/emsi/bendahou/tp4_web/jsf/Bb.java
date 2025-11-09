package ma.emsi.bendahou.tp4_web.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import ma.emsi.bendahou.tp4_web.llm.LlmClient;
import ma.emsi.bendahou.tp4_web.rag.MagasinEmbeddings;
import ma.emsi.bendahou.tp4_web.rag.RagService;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
     * Obtient le contexte JSF actuel. FacesContext ne peut pas être injecté dans un bean @ViewScoped.
     * Il doit être obtenu via getCurrentInstance() à chaque utilisation.
     */
    private FacesContext getFacesContext() {
        return FacesContext.getCurrentInstance();
    }

    /**
     * Service RAG pour le RAG conditionnel et multi-documents.
     * Marqué comme transient car RagService est injecté et peut ne pas être sérialisable.
     */
    @Inject
    private transient RagService ragService;

    // Instance du client LLM
    // Marqué comme transient car LlmClient n'est pas sérialisable
    private transient LlmClient llmClient;
    
    /**
     * Fichier PDF uploadé par l'utilisateur (pour le mode Upload de Document).
     * Marqué comme transient car Part n'est pas sérialisable.
     */
    private transient jakarta.servlet.http.Part fichier;
    
    /**
     * Message pour l'upload de fichier.
     */
    private String messagePourChargementFichier;
    
    /**
     * Magasin d'embeddings pour le document uploadé (mode Upload de Document).
     * Marqué comme transient car MagasinEmbeddings peut contenir des objets non sérialisables.
     */
    private transient MagasinEmbeddings magasinEmbeddingsUpload;

    /**
     * Obligatoire pour un bean CDI (classe gérée par CDI), s'il y a un autre constructeur.
     */
    public Bb() {
    }
    
    /**
     * Réinitialise les objets injectés après la désérialisation.
     * Les objets marqués comme transient ne sont pas automatiquement réinjectés par CDI.
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        // Réinjecter ragService si nécessaire (après désérialisation)
        if (ragService == null) {
            try {
                ragService = jakarta.enterprise.inject.spi.CDI.current().select(RagService.class).get();
            } catch (Exception e) {
                // Si CDI n'est pas disponible, on laisse null
            }
        }
    }
    
    /**
     * Méthode personnalisée pour la sérialisation.
     * Assure que tous les objets sérialisables sont correctement sérialisés.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }
    
    /**
     * Méthode personnalisée pour la désérialisation.
     * Réinitialise les objets transient après la désérialisation.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // Réinjecter ragService après la désérialisation
        if (ragService == null) {
            try {
                ragService = jakarta.enterprise.inject.spi.CDI.current().select(RagService.class).get();
            } catch (Exception e) {
                // Si CDI n'est pas disponible, on laisse null
            }
        }
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
     * Getter pour le fichier uploadé.
     */
    public jakarta.servlet.http.Part getFichier() {
        return fichier;
    }
    
    /**
     * Setter pour le fichier uploadé.
     * Gère le cas où le formulaire n'est pas multipart (ne fait rien dans ce cas).
     */
    public void setFichier(jakarta.servlet.http.Part fichier) {
        try {
            // Vérifier si le fichier est valide (ne pas essayer d'accéder aux méthodes si null)
            if (fichier != null) {
                // Essayer d'accéder au nom du fichier pour vérifier que c'est bien un multipart
                fichier.getSubmittedFileName(); // Vérification que c'est un vrai Part
                this.fichier = fichier;
            } else {
                this.fichier = null;
            }
        } catch (Exception e) {
            // Si une erreur survient (formulaire non multipart), ignorer silencieusement
            // Cela peut arriver si JSF essaie de binder le champ même si le formulaire n'est pas multipart
            this.fichier = null;
        }
    }
    
    /**
     * Getter pour le message de chargement de fichier.
     */
    public String getMessagePourChargementFichier() {
        return messagePourChargementFichier;
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
            getFacesContext().addMessage(null, message);
            return null;
        }
        
        // Vérifier qu'un rôle est défini ou qu'un fichier a été uploadé
        if ((roleSysteme == null || roleSysteme.isBlank()) && 
            (magasinEmbeddingsUpload == null || messagePourChargementFichier == null || messagePourChargementFichier.isEmpty())) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Rôle ou fichier requis", "Vous devez indiquer le rôle de l'assistant ou uploader un fichier PDF");
            getFacesContext().addMessage(null, message);
            return null;
        }

        // S'assurer que ragService est disponible (peut être null après désérialisation)
        if (ragService == null) {
            try {
                ragService = jakarta.enterprise.inject.spi.CDI.current().select(RagService.class).get();
            } catch (Exception e) {
                // Si CDI n'est pas disponible, on continue sans ragService
            }
        }
        
        if (llmClient == null) {
            llmClient = new LlmClient();
            
            // Configurer le service RAG dans le client LLM
            if (ragService != null) {
                llmClient.setRagService(ragService);
            }
            
            // Déterminer le mode RAG selon le rôle choisi (ou utiliser "desactive" si aucun rôle)
            String modeRag = (roleSysteme != null && !roleSysteme.isBlank()) 
                    ? determinerModeRag(roleSysteme) 
                    : "desactive";
            llmClient.setModeRag(modeRag);
            
            // Configurer le magasin d'embeddings uploadé si disponible (fonctionne avec tous les modes)
            if (magasinEmbeddingsUpload != null) {
                llmClient.setMagasinEmbeddingsUpload(magasinEmbeddingsUpload);
            }

            // On configure le rôle système si c'est la première question et qu'un rôle est défini
            // Si un fichier a été uploadé, le rôle n'est pas obligatoire
            if (this.conversation.isEmpty()) {
                if (roleSysteme != null && !roleSysteme.isBlank()) {
                    llmClient.setSystemRole(roleSysteme);
                    this.roleSystemeChangeable = false;
                } else if (magasinEmbeddingsUpload != null) {
                    // Si un fichier a été uploadé mais pas de rôle, utiliser un rôle par défaut pour l'upload
                    String roleParDefaut = "You are a helpful assistant. You answer questions based on the document uploaded by the user. When the user uploads a PDF document, you use the context from that document to answer their questions.";
                    llmClient.setSystemRole(roleParDefaut);
                    this.roleSystemeChangeable = false;
                }
            }
        } else {
            // Mettre à jour le mode RAG si le rôle change (ou utiliser "desactive" si aucun rôle)
            String modeRag = (roleSysteme != null && !roleSysteme.isBlank()) 
                    ? determinerModeRag(roleSysteme) 
                    : "desactive";
            llmClient.setModeRag(modeRag);
            
            // Configurer le magasin d'embeddings uploadé si disponible (fonctionne avec tous les modes)
            // Toujours mettre à jour pour s'assurer que le magasin est à jour
            if (magasinEmbeddingsUpload != null) {
                llmClient.setMagasinEmbeddingsUpload(magasinEmbeddingsUpload);
            }
        }

        // S'assurer que le magasin d'embeddings uploadé est toujours configuré dans le LlmClient
        // (au cas où il a été uploadé après la création du client)
        if (magasinEmbeddingsUpload != null && llmClient != null) {
            llmClient.setMagasinEmbeddingsUpload(magasinEmbeddingsUpload);
        }
        
        // Appel de la classe LlmClient
        try {
            reponse = llmClient.chat(question);

            afficherConversation();
        } catch (Exception e) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Erreur LLM", "Erreur lors de la communication avec le LLM: " + e.getMessage());
            getFacesContext().addMessage(null, message);
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
     * Téléchargement de fichier PDF qui est ajouté dans la base de données vectorielle.
     */
    public void upload() {
        // getSubmittedFileName() retourne le nom du fichier sur le disque du client.
        if (this.fichier != null && this.fichier.getSubmittedFileName() != null 
                && this.fichier.getSubmittedFileName().endsWith(".pdf")) {
            try {
                // Initialiser le magasin d'embeddings si nécessaire
                if (magasinEmbeddingsUpload == null) {
                    magasinEmbeddingsUpload = new MagasinEmbeddings();
                }
                
                // Charger le fichier dans la BD vectorielle
                InputStream inputStream = fichier.getInputStream();
                magasinEmbeddingsUpload.ajouter(inputStream);
                inputStream.close();
                
                messagePourChargementFichier = "✅ Fichier PDF chargé avec succès : " + fichier.getSubmittedFileName();
                
                // Mettre à jour le LlmClient avec le magasin d'embeddings si le client existe déjà
                if (llmClient != null) {
                    llmClient.setMagasinEmbeddingsUpload(magasinEmbeddingsUpload);
                }
                
                // Afficher un message de succès
                FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO,
                        "Fichier uploadé", "Le fichier PDF a été chargé avec succès");
                getFacesContext().addMessage(null, message);
            } catch (Exception e) {
                messagePourChargementFichier = "❌ Erreur lors du chargement du fichier : " + e.getMessage();
                
                // Afficher un message d'erreur
                FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        "Erreur upload", "Erreur lors du chargement du fichier : " + e.getMessage());
                getFacesContext().addMessage(null, message);
            }
        } else {
            messagePourChargementFichier = "❌ Veuillez sélectionner un fichier PDF valide";
            
            // Afficher un message d'erreur
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Fichier invalide", "Veuillez sélectionner un fichier PDF valide");
            getFacesContext().addMessage(null, message);
        }
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
        }

        return this.listeRolesSysteme;
    }

}
