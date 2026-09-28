package org.example.finzin.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.entity.AiDocumentEmbeddingEntity;
import org.example.finzin.repository.AiDocumentEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for EmbeddingService's own orchestration logic — the underlying
 * {@link EmbeddingClient} is mocked throughout, mirroring the client-level test boundary already
 * covered separately by OpenAIEmbeddingClientTest/MockEmbeddingClientTest. Focuses on what belongs
 * to this class specifically: the content-hash cache check that skips needless re-embedding,
 * create-vs-update upsert behavior, metadata JSON serialization, and delete delegation.
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private AiDocumentEmbeddingRepository documentRepository;
    @Mock private VectorRepository vectorRepository;
    @Mock private EmbeddingClient embeddingClient;

    private EmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingService(documentRepository, vectorRepository, embeddingClient, new ObjectMapper());
    }

    /** Independently reproduces the service's private sha256() helper so tests can assert on the
     *  real hash without depending on EmbeddingService's internals — SHA-256 of UTF-8 text is a
     *  well-defined public algorithm, not a guess at implementation detail. */
    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ================================================================================
    // indexDocument — new document
    // ================================================================================

    @Test
    void indexDocumentCreatesNewRowEmbedsContentAndUpsertsVectorWhenNoneExists() {
        when(documentRepository.findByUserIdAndEntityTypeAndEntityId(USER_ID, "TRANSACTION", 5L))
                .thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenAnswer(inv -> {
            AiDocumentEmbeddingEntity e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });
        float[] vector = {0.1f, 0.2f, 0.3f};
        when(embeddingClient.embed("some content")).thenReturn(vector);

        service.indexDocument(USER_ID, IndexedEntityType.TRANSACTION, 5L, "Title", "some content", Map.of("k", "v"));

        ArgumentCaptor<AiDocumentEmbeddingEntity> captor = ArgumentCaptor.forClass(AiDocumentEmbeddingEntity.class);
        verify(documentRepository).save(captor.capture());
        AiDocumentEmbeddingEntity saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals("TRANSACTION", saved.getEntityType());
        assertEquals(5L, saved.getEntityId());
        assertEquals("Title", saved.getTitle());
        assertEquals("some content", saved.getContent());
        assertEquals(sha256("some content"), saved.getContentHash());
        assertTrue(saved.getMetadata().contains("\"k\":\"v\""));
        verify(vectorRepository).upsertEmbedding(99L, vector);
    }

    @Test
    void indexDocumentWritesNullMetadataWhenMapIsNullOrEmpty() {
        when(documentRepository.findByUserIdAndEntityTypeAndEntityId(any(), any(), any())).thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(embeddingClient.embed(any())).thenReturn(new float[0]);

        service.indexDocument(USER_ID, IndexedEntityType.NOTE, 1L, "T", "content", Map.of());
        service.indexDocument(USER_ID, IndexedEntityType.NOTE, 2L, "T", "content2", null);

        ArgumentCaptor<AiDocumentEmbeddingEntity> captor = ArgumentCaptor.forClass(AiDocumentEmbeddingEntity.class);
        verify(documentRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertNull(captor.getAllValues().get(0).getMetadata());
        assertNull(captor.getAllValues().get(1).getMetadata());
    }

    // ================================================================================
    // indexDocument — cache hit (unchanged content)
    // ================================================================================

    @Test
    void indexDocumentSkipsReEmbeddingWhenContentHashIsUnchanged() {
        AiDocumentEmbeddingEntity existing = new AiDocumentEmbeddingEntity();
        existing.setId(7L);
        existing.setContentHash(sha256("same content"));
        when(documentRepository.findByUserIdAndEntityTypeAndEntityId(USER_ID, "NOTE", 3L))
                .thenReturn(Optional.of(existing));

        service.indexDocument(USER_ID, IndexedEntityType.NOTE, 3L, "Title", "same content", Map.of());

        verify(documentRepository, never()).save(any());
        verifyNoInteractions(embeddingClient);
        verifyNoInteractions(vectorRepository);
    }

    // ================================================================================
    // indexDocument — cache miss (content changed) reuses the existing row
    // ================================================================================

    @Test
    void indexDocumentReindexesAndReusesTheSameRowWhenContentHasChanged() {
        AiDocumentEmbeddingEntity existing = new AiDocumentEmbeddingEntity();
        existing.setId(7L);
        existing.setContentHash(sha256("old content"));
        when(documentRepository.findByUserIdAndEntityTypeAndEntityId(USER_ID, "NOTE", 3L))
                .thenReturn(Optional.of(existing));
        when(documentRepository.save(existing)).thenReturn(existing);
        float[] vector = {0.5f};
        when(embeddingClient.embed("new content")).thenReturn(vector);

        service.indexDocument(USER_ID, IndexedEntityType.NOTE, 3L, "New Title", "new content", Map.of());

        verify(documentRepository).save(existing);
        assertEquals("new content", existing.getContent());
        assertEquals("New Title", existing.getTitle());
        assertEquals(sha256("new content"), existing.getContentHash());
        verify(vectorRepository).upsertEmbedding(7L, vector);
    }

    // ================================================================================
    // deleteDocument
    // ================================================================================

    @Test
    void deleteDocumentDelegatesToRepositoryWithEntityTypeName() {
        service.deleteDocument(USER_ID, IndexedEntityType.TODO, 2L);

        verify(documentRepository).deleteByUserIdAndEntityTypeAndEntityId(USER_ID, "TODO", 2L);
    }
}
