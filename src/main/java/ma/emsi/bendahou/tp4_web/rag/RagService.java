package ma.emsi.bendahou.tp4_web.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service RAG qui gère le RAG conditionnel et multi-documents.
 */
@ApplicationScoped
public class RagService {

    private final Map<String, EmbeddingStore<TextSegment>> magasinsParPdf;
    private final Map<String, List<EmbeddingWithSegment>> embeddingsParPdf;
    private EmbeddingModel embeddingModel;
    private RoutageIntelligent routageIntelligent;

    public RagService() {
        this.magasinsParPdf = new HashMap<>();
        this.embeddingsParPdf = new HashMap<>();
    }

    /**
     * Initialise les composants nécessaires (appelé après l'injection de dépendances).
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        // Utiliser un modèle d'embedding simple (sera remplacé par un vrai modèle si disponible)
        // Pour l'instant, on va créer un modèle d'embedding basique
        // Note: Dans une vraie application, vous devriez utiliser un modèle d'embedding approprié
        this.embeddingModel = new SimpleEmbeddingModel();
        this.routageIntelligent = new RoutageIntelligent();
    }

    /**
     * Charge un PDF dans un magasin d'embeddings spécifique.
     * 
     * @param nomPdf le nom du fichier PDF
     * @param inputStream le flux d'entrée du PDF
     */
    public void chargerPdf(String nomPdf, InputStream inputStream) {
        // Créer un nouveau magasin d'embeddings pour ce PDF
        EmbeddingStore<TextSegment> magasin = new InMemoryEmbeddingStore<>();
        
        // Parser le document PDF
        Document document;
        try {
            document = PdfParser.parse(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du parsing du PDF " + nomPdf, e);
        }
        
        // Découper le document en segments
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 50);
        List<TextSegment> segments = splitter.split(document);
        
        // Créer les embeddings et les stocker
        List<EmbeddingWithSegment> embeddings = new ArrayList<>();
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment).content();
            magasin.add(embedding, segment);
            // Stocker aussi dans notre structure personnalisée pour la recherche
            embeddings.add(new EmbeddingWithSegment(1.0, embedding, segment));
        }
        
        // Stocker le magasin associé au nom du PDF
        magasinsParPdf.put(nomPdf.toLowerCase(), magasin);
        embeddingsParPdf.put(nomPdf.toLowerCase(), embeddings);
    }

    /**
     * Récupère le contexte pertinent pour une question donnée en utilisant le RAG.
     * 
     * @param question la question de l'utilisateur
     * @return le contexte pertinent extrait des documents, ou null si aucun contexte n'est trouvé
     */
    public String recupererContexte(String question) {
        // Déterminer quel PDF est pertinent
        String pdfPertinent = routageIntelligent.determinerPdfPertinent(question);
        
        if ("aucun".equals(pdfPertinent)) {
            return null; // Pas de RAG nécessaire
        }
        
        // Récupérer le magasin d'embeddings correspondant
        EmbeddingStore<TextSegment> magasin = magasinsParPdf.get(pdfPertinent.toLowerCase());
        if (magasin == null) {
            return null; // Le PDF n'a pas été chargé
        }
        
        // Créer l'embedding de la question
        Embedding questionEmbedding = embeddingModel.embed(question).content();
        
        // Rechercher les segments les plus pertinents (top 3) en utilisant la similarité cosinus
        List<EmbeddingWithSegment> allEmbeddings = embeddingsParPdf.get(pdfPertinent.toLowerCase());
        if (allEmbeddings == null) {
            return null;
        }
        
        // Calculer la similarité cosinus pour chaque embedding et trier
        List<EmbeddingWithSegment> matches = allEmbeddings.stream()
                .map(embeddingWithSegment -> {
                    double similarity = cosineSimilarity(questionEmbedding.vectorAsList(), 
                                                        embeddingWithSegment.getEmbedding().vectorAsList());
                    return new EmbeddingWithSegment(similarity, embeddingWithSegment.getEmbedding(), 
                                                   embeddingWithSegment.getSegment());
                })
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore())) // Trier par score décroissant
                .limit(3) // Prendre les 3 meilleurs
                .filter(match -> match.getScore() > 0.6) // Filtrer par seuil de similarité
                .collect(Collectors.toList());
        
        if (matches.isEmpty()) {
            return null;
        }
        
        // Construire le contexte à partir des segments pertinents
        StringBuilder contexte = new StringBuilder();
        contexte.append("Contexte extrait du document ").append(pdfPertinent).append(":\n\n");
        for (EmbeddingWithSegment match : matches) {
            contexte.append(match.getSegment().text()).append("\n\n");
        }
        
        return contexte.toString();
    }

    /**
     * Détermine si la question nécessite du RAG.
     * 
     * @param question la question de l'utilisateur
     * @return true si la question nécessite du RAG, false sinon
     */
    public boolean necessiteRag(String question) {
        return routageIntelligent.necessiteRag(question);
    }
    
    /**
     * Calcule la similarité cosinus entre deux vecteurs.
     * 
     * @param vector1 le premier vecteur
     * @param vector2 le deuxième vecteur
     * @return la similarité cosinus (entre -1 et 1)
     */
    private double cosineSimilarity(List<Float> vector1, List<Float> vector2) {
        if (vector1.size() != vector2.size()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vector1.size(); i++) {
            double v1 = vector1.get(i);
            double v2 = vector2.get(i);
            dotProduct += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}

