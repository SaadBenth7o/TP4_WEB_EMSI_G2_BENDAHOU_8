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

/**
 * Classe pour stocker les embeddings des segments.
 */
@ViewScoped
public class MagasinEmbeddings implements Serializable {

    private EmbeddingStore<TextSegment> embeddingStore;
    private EmbeddingModel embeddingModel;

    public MagasinEmbeddings() {
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        this.embeddingModel = new SimpleEmbeddingModel();
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
        }
    }
}

