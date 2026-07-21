package org.example.finzin.ai;

import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.AiConversationEntity;
import org.example.finzin.entity.AiMessageEntity;
import org.example.finzin.repository.AiConversationRepository;
import org.example.finzin.repository.AiMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConversationService {
    private static final int MAX_REPLAY_MESSAGES = 20;
    private static final int AUTO_TITLE_MAX_LENGTH = 40;

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final DocumentIndexer documentIndexer;
    private final QueryEmbeddingCache queryEmbeddingCache;

    public ConversationService(AiConversationRepository conversationRepository, AiMessageRepository messageRepository,
                               DocumentIndexer documentIndexer, QueryEmbeddingCache queryEmbeddingCache) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.documentIndexer = documentIndexer;
        this.queryEmbeddingCache = queryEmbeddingCache;
    }

    public List<AiConversationEntity> listForUser(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public AiConversationEntity create(Long userId) {
        AiConversationEntity entity = new AiConversationEntity();
        entity.setUserId(userId);
        return conversationRepository.save(entity);
    }

    /** Returns null if the conversation doesn't exist or isn't owned by userId. */
    public AiConversationEntity findOwned(Long conversationId, Long userId) {
        AiConversationEntity entity = conversationRepository.findById(conversationId).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return null;
        }
        return entity;
    }

    public AiConversationEntity rename(Long conversationId, Long userId, String title) {
        AiConversationEntity entity = findOwned(conversationId, userId);
        if (entity == null) return null;
        entity.setTitle(title);
        AiConversationEntity saved = conversationRepository.save(entity);
        documentIndexer.indexConversationTurn(saved, getRecentMessages(conversationId, userId));
        return saved;
    }

    @Transactional
    public boolean delete(Long conversationId, Long userId) {
        AiConversationEntity entity = findOwned(conversationId, userId);
        if (entity == null) return false;
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
        documentIndexer.deleteConversation(userId, conversationId);
        queryEmbeddingCache.evictConversation(conversationId);
        return true;
    }

    public List<AiMessageEntity> getAllMessages(Long conversationId, Long userId) {
        AiConversationEntity owned = findOwned(conversationId, userId);
        if (owned == null) return List.of();
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /** Last N user/assistant turns for replay into a new model call — tool-role rows are never replayed. */
    public List<AiMessageEntity> getRecentMessages(Long conversationId, Long userId) {
        List<AiMessageEntity> all = getAllMessages(conversationId, userId);
        int fromIndex = Math.max(0, all.size() - MAX_REPLAY_MESSAGES);
        return all.subList(fromIndex, all.size());
    }

    public AiMessageEntity appendMessage(Long conversationId, Long userId, String role, String content, String toolName) {
        AiMessageEntity message = new AiMessageEntity();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setToolName(toolName);
        AiMessageEntity saved = messageRepository.save(message);

        AiConversationEntity conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation != null) {
            if (conversation.getTitle() == null && "user".equals(role)) {
                conversation.setTitle(autoTitle(content));
            }
            AiConversationEntity savedConversation = conversationRepository.save(conversation); // bumps updatedAt via @PreUpdate
            // Index once per completed turn (on the assistant's reply) rather than on every
            // intermediate user/tool message, to avoid re-embedding the transcript repeatedly per turn.
            if ("assistant".equals(role)) {
                documentIndexer.indexConversationTurn(savedConversation, getRecentMessages(conversationId, userId));
            }
        }
        return saved;
    }

    private String autoTitle(String firstUserMessage) {
        if (firstUserMessage == null) return "New chat";
        String trimmed = firstUserMessage.trim();
        if (trimmed.isEmpty()) return "New chat";
        return trimmed.length() <= AUTO_TITLE_MAX_LENGTH ? trimmed : trimmed.substring(0, AUTO_TITLE_MAX_LENGTH) + "…";
    }
}
