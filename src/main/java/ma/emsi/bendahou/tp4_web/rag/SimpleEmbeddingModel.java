package ma.emsi.bendahou.tp4_web.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Modèle d'embedding simple utilisant un hash pour créer des vecteurs d'embedding.
 * Note: Ceci est une implémentation simplifiée pour la démonstration.
 * Dans une vraie application, vous devriez utiliser un modèle d'embedding approprié.
 */
public class SimpleEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSION = 384; // Dimension standard pour les embeddings

    @Override
    public Response<Embedding> embed(String text) {
        return Response.from(createEmbedding(text));
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return embed(textSegment.text());
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>();
        for (TextSegment segment : textSegments) {
            embeddings.add(createEmbedding(segment.text()));
        }
        return Response.from(embeddings);
    }

    @Override
    public int dimension() {
        return DIMENSION;
    }

    /**
     * Crée un embedding à partir d'un texte en utilisant un hash.
     * 
     * @param text le texte à convertir en embedding
     * @return un Embedding
     */
    private Embedding createEmbedding(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            
            // Créer un vecteur d'embedding à partir du hash
            float[] vector = new float[DIMENSION];
            for (int i = 0; i < DIMENSION; i++) {
                // Utiliser les bytes du hash pour créer des valeurs flottantes
                int byteIndex = i % hash.length;
                vector[i] = (hash[byteIndex] & 0xFF) / 255.0f - 0.5f; // Normaliser entre -0.5 et 0.5
            }
            
            return new Embedding(vector);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la création de l'embedding", e);
        }
    }
}

