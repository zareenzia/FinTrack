package org.example.finzin.ai;

import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.AiConversationEntity;
import org.example.finzin.entity.AiMessageEntity;
import org.example.finzin.repository.AiConversationRepository;
import org.example.finzin.repository.AiMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test (no Spring context), matching {@code AIServiceTest}'s convention.
 * Covers ownership scoping ({@link ConversationService#findOwned}, the seam every other method
 * routes through), the auto-title/truncation rule, the "index once per completed turn — only on
 * the assistant's reply" rule, and that delete tears down messages/index/cache together.
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long OTHER_USER_ID = 99L;
    private static final Long CONVERSATION_ID = 7L;

    @Mock private AiConversationRepository conversationRepository;
    @Mock private AiMessageRepository messageRepository;
    @Mock private DocumentIndexer documentIndexer;
    @Mock private QueryEmbeddingCache queryEmbeddingCache;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(conversationRepository, messageRepository, documentIndexer, queryEmbeddingCache);
        lenient().when(conversationRepository.save(any(AiConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(messageRepository.save(any(AiMessageEntity.class))).thenAnswer(inv -> {
            AiMessageEntity m = inv.getArgument(0);
            if (m.getId() == null) m.setId(500L);
            return m;
        });
    }

    private AiConversationEntity conversation(Long id, Long userId, String title) {
        AiConversationEntity c = new AiConversationEntity();
        c.setId(id);
        c.setUserId(userId);
        c.setTitle(title);
        return c;
    }

    @Test
    void findOwnedReturnsNullWhenConversationBelongsToAnotherUser() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation(CONVERSATION_ID, OTHER_USER_ID, "Their chat")));

        AiConversationEntity result = service.findOwned(CONVERSATION_ID, USER_ID);

        assertNull(result);
    }

    @Test
    void findOwnedReturnsNullWhenConversationDoesNotExist() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.empty());

        assertNull(service.findOwned(CONVERSATION_ID, USER_ID));
    }

    @Test
    void findOwnedReturnsTheConversationWhenOwnedByTheCaller() {
        AiConversationEntity owned = conversation(CONVERSATION_ID, USER_ID, "My chat");
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(owned));

        assertEquals(owned, service.findOwned(CONVERSATION_ID, USER_ID));
    }

    @Test
    void renameReturnsNullWithoutSavingWhenNotOwned() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation(CONVERSATION_ID, OTHER_USER_ID, "Their chat")));

        AiConversationEntity result = service.rename(CONVERSATION_ID, USER_ID, "New title");

        assertNull(result);
        verify(conversationRepository, never()).save(any());
        verify(documentIndexer, never()).indexConversationTurn(any(), any());
    }

    @Test
    void renameUpdatesTitleAndReindexesTheConversation() {
        AiConversationEntity owned = conversation(CONVERSATION_ID, USER_ID, "Old title");
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(owned));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(CONVERSATION_ID)).thenReturn(List.of());

        AiConversationEntity result = service.rename(CONVERSATION_ID, USER_ID, "New title");

        assertEquals("New title", result.getTitle());
        verify(documentIndexer).indexConversationTurn(any(AiConversationEntity.class), any());
    }

    @Test
    void deleteTearsDownMessagesConversationIndexAndCacheTogether() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation(CONVERSATION_ID, USER_ID, "My chat")));

        boolean deleted = service.delete(CONVERSATION_ID, USER_ID);

        assertTrue(deleted);
        verify(messageRepository).deleteByConversationId(CONVERSATION_ID);
        verify(conversationRepository).deleteById(CONVERSATION_ID);
        verify(documentIndexer).deleteConversation(USER_ID, CONVERSATION_ID);
        verify(queryEmbeddingCache).evictConversation(CONVERSATION_ID);
    }

    @Test
    void deleteReturnsFalseAndTouchesNothingWhenNotOwned() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation(CONVERSATION_ID, OTHER_USER_ID, "Their chat")));

        boolean deleted = service.delete(CONVERSATION_ID, USER_ID);

        assertFalse(deleted);
        verify(messageRepository, never()).deleteByConversationId(any());
        verify(conversationRepository, never()).deleteById(any());
        verify(documentIndexer, never()).deleteConversation(any(), any());
    }

    @Test
    void appendingAUserMessageSetsAnAutoTitleOnlyWhenTitleWasNull() {
        AiConversationEntity untitled = conversation(CONVERSATION_ID, USER_ID, null);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(untitled));

        service.appendMessage(CONVERSATION_ID, USER_ID, "user",
                "How much did I spend on groceries last month, roughly speaking, across every account?", null);

        assertEquals(41, untitled.getTitle().length(), "40 chars + ellipsis char");
        assertTrue(untitled.getTitle().endsWith("…"));
        verify(documentIndexer, never()).indexConversationTurn(any(), any()); // only indexed on assistant reply
    }

    @Test
    void appendingAUserMessageNeverOverwritesAnExistingTitle() {
        AiConversationEntity titled = conversation(CONVERSATION_ID, USER_ID, "Existing title");
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(titled));

        service.appendMessage(CONVERSATION_ID, USER_ID, "user", "another message", null);

        assertEquals("Existing title", titled.getTitle());
    }

    @Test
    void appendingAnAssistantMessageIndexesTheConversationTurnButToolMessagesDoNot() {
        AiConversationEntity conv = conversation(CONVERSATION_ID, USER_ID, "Title");
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conv));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(CONVERSATION_ID)).thenReturn(List.of());

        service.appendMessage(CONVERSATION_ID, USER_ID, "tool", "{}", "getAccountBalances");
        verify(documentIndexer, never()).indexConversationTurn(any(), any());

        service.appendMessage(CONVERSATION_ID, USER_ID, "assistant", "Here's your answer.", null);
        verify(documentIndexer, times(1)).indexConversationTurn(any(AiConversationEntity.class), any());
    }

    @Test
    void getRecentMessagesReturnsOnlyTheLastTwentyAndNeverThrowsWhenFewerExist() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation(CONVERSATION_ID, USER_ID, "T")));
        List<AiMessageEntity> all = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            AiMessageEntity m = new AiMessageEntity();
            m.setId((long) i);
            m.setContent("msg-" + i);
            all.add(m);
        }
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(CONVERSATION_ID)).thenReturn(all);

        List<AiMessageEntity> recent = service.getRecentMessages(CONVERSATION_ID, USER_ID);

        assertEquals(20, recent.size());
        assertEquals("msg-5", recent.get(0).getContent()); // the oldest of the last 20 (25 - 20 = index 5)
    }

    @Test
    void getAllMessagesReturnsEmptyListRatherThanThrowingWhenNotOwned() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation(CONVERSATION_ID, OTHER_USER_ID, "T")));

        List<AiMessageEntity> messages = service.getAllMessages(CONVERSATION_ID, USER_ID);

        assertTrue(messages.isEmpty());
        verify(messageRepository, never()).findByConversationIdOrderByCreatedAtAsc(anyLong());
    }

    @Test
    void listForUserDelegatesToTheOrderedRepositoryQuery() {
        AiConversationEntity c = conversation(1L, USER_ID, "T");
        when(conversationRepository.findByUserIdOrderByUpdatedAtDesc(USER_ID)).thenReturn(List.of(c));

        assertEquals(List.of(c), service.listForUser(USER_ID));
    }

    @Test
    void createPersistsANewConversationScopedToTheGivenUser() {
        service.create(USER_ID);

        org.mockito.ArgumentCaptor<AiConversationEntity> captor = org.mockito.ArgumentCaptor.forClass(AiConversationEntity.class);
        verify(conversationRepository).save(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
    }
}
