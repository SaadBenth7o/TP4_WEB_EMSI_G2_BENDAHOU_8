package ma.emsi.bendahou.tp4_web.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

/**
 * Classe pour stocker un embedding avec son segment et son score de similarité.
 */
public class EmbeddingWithSegment {
    private final double score;
    private final Embedding embedding;
    private final TextSegment segment;

    public EmbeddingWithSegment(double score, Embedding embedding, TextSegment segment) {
        this.score = score;
        this.embedding = embedding;
        this.segment = segment;
    }

    public double getScore() {
        return score;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public TextSegment getSegment() {
        return segment;
    }
}

