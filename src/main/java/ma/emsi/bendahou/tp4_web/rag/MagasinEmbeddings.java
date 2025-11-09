package ma.emsi.bendahou.tp4_web.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.faces.view.ViewScoped;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Classe pour stocker les embeddings des segments.
 */
@ViewScoped
public class MagasinEmbeddings implements Serializable {

    private EmbeddingStore<TextSegment> embeddingStore;
    private EmbeddingModel embeddingModel;
    private List<EmbeddingWithSegment> embeddings;

    public MagasinEmbeddings() {
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        this.embeddingModel = new SimpleEmbeddingModel();
        this.embeddings = new ArrayList<>();
    }

    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * Stocker les embeddings des segments.
     *
     * @param inputStream le flux d'entrée du document dont les segments doivent être stockés.
     */
    public void ajouter(InputStream inputStream) {
        try {
            // Création du Document depuis le PDF
            Document documentPdf = PdfParser.parse(inputStream);
            ajouterDocument(documentPdf);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du parsing du document", e);
        }
    }

    /**
     * Ajouter les chunks d'un document dans le magasin d'embeddings.
     * @param document le document à ajouter
     */
    public void ajouterDocument(Document document) {
        // Découper le document en segments (chunks)
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 50);
        java.util.List<TextSegment> segments = splitter.split(document);

        // Créer les embeddings pour chaque segment et les stocker
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);
            // Stocker aussi dans notre structure personnalisée pour la recherche
            embeddings.add(new EmbeddingWithSegment(1.0, embedding, segment));
        }
    }
    
    /**
     * Récupère le contexte pertinent pour une question donnée en utilisant le magasin d'embeddings.
     * 
     * @param question la question de l'utilisateur
     * @return le contexte pertinent extrait du document, ou null si aucun contexte n'est trouvé
     */
    public String recupererContexte(String question) {
        if (embeddings == null || embeddings.isEmpty()) {
            return null; // Aucun document chargé
        }
        
        try {
            // Créer l'embedding de la question
            Embedding questionEmbedding = embeddingModel.embed(question).content();
            
            // Calculer la similarité cosinus pour chaque embedding et trier
            List<EmbeddingWithSegment> matches = embeddings.stream()
                    .map(embeddingWithSegment -> {
                        double similarity = cosineSimilarity(questionEmbedding.vectorAsList(), 
                                                            embeddingWithSegment.getEmbedding().vectorAsList());
                        return new EmbeddingWithSegment(similarity, embeddingWithSegment.getEmbedding(), 
                                                       embeddingWithSegment.getSegment());
                    })
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore())) // Trier par score décroissant
                    .limit(5) // Prendre les 5 meilleurs
                    .filter(match -> match.getScore() > 0.3) // Filtrer par seuil de similarité plus bas (0.3 au lieu de 0.6)
                    .collect(Collectors.toList());
            
            if (matches.isEmpty()) {
                // Si aucun match n'est trouvé avec le seuil, prendre au moins les 3 meilleurs segments
                // même avec un score plus bas pour garantir qu'on retourne quelque chose
                List<EmbeddingWithSegment> allMatches = embeddings.stream()
                        .map(embeddingWithSegment -> {
                            double similarity = cosineSimilarity(questionEmbedding.vectorAsList(), 
                                                                embeddingWithSegment.getEmbedding().vectorAsList());
                            return new EmbeddingWithSegment(similarity, embeddingWithSegment.getEmbedding(), 
                                                           embeddingWithSegment.getSegment());
                        })
                        .sorted((a, b) -> Double.compare(b.getScore(), a.getScore())) // Trier par score décroissant
                        .limit(3) // Prendre les 3 meilleurs
                        .collect(Collectors.toList());
                
                if (allMatches.isEmpty()) {
                    return null;
                }
                
                matches = allMatches;
            }
            
            // Construire le contexte à partir des segments pertinents
            StringBuilder contexte = new StringBuilder();
            contexte.append("Contexte extrait du document uploadé:\n\n");
            for (EmbeddingWithSegment match : matches) {
                contexte.append(match.getSegment().text()).append("\n\n");
            }
            
            return contexte.toString();
        } catch (Exception e) {
            // En cas d'erreur, retourner null
            return null;
        }
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

