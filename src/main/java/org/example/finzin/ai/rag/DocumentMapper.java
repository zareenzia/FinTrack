package org.example.finzin.ai.rag;

import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.AiConversationEntity;
import org.example.finzin.entity.AiMessageEntity;
import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.GoldAssetEntity;
import org.example.finzin.entity.NoteEntity;
import org.example.finzin.entity.TodoEntity;
import org.example.finzin.entity.TransactionEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pure, stateless text-building functions — one per indexed entity type. No DB/network calls;
 * anything that needs a resolved name (e.g. budget category names) is passed in already-resolved
 * by the caller rather than looked up here.
 */
@Component
public class DocumentMapper {

    public record MappedDocument(String title, String content, Map<String, Object> metadata) {}

    public MappedDocument mapTransaction(TransactionEntity t) {
        String categoryName = t.getCategory() != null ? t.getCategory().getName() : "Uncategorized";
        String dateStr = t.getDate() != null ? t.getDate().toLocalDate().toString() : "";
        String title = t.getDescription();
        String content = String.format("%s - ৳%.2f (%s) on %s, type: %s",
                t.getDescription(), t.getAmount(), categoryName, dateStr, t.getTransactionType());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("amount", t.getAmount());
        metadata.put("type", t.getTransactionType());
        metadata.put("category", categoryName);
        metadata.put("date", dateStr);
        return new MappedDocument(title, content, metadata);
    }

    public MappedDocument mapNote(NoteEntity n) {
        StringBuilder content = new StringBuilder();
        content.append(n.getTitle() != null ? n.getTitle() : "").append("\n");
        content.append(n.getContent() != null ? n.getContent() : "");
        if (n.getTags() != null && !n.getTags().isBlank()) {
            content.append("\nTags: ").append(n.getTags());
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("pinned", n.getPinned());
        metadata.put("archived", n.getArchived());
        return new MappedDocument(n.getTitle(), content.toString(), metadata);
    }

    public MappedDocument mapTodo(TodoEntity t) {
        String content = String.format("%s: %s (priority: %s, status: %s, due: %s)",
                t.getTitle(), nullToEmpty(t.getDescription()), nullToEmpty(t.getPriority()),
                nullToEmpty(t.getStatus()), t.getDueDate() != null ? t.getDueDate().toString() : "none");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("priority", t.getPriority());
        metadata.put("status", t.getStatus());
        metadata.put("completed", t.getCompleted());
        return new MappedDocument(t.getTitle(), content, metadata);
    }

    public MappedDocument mapAccount(AccountEntity a) {
        String bankOrProvider = a.getBankName() != null ? a.getBankName() : a.getProvider();
        String content = String.format("%s (%s%s) - Balance: ৳%.2f, Status: %s",
                a.getAccountNickname(), a.getAccountType(),
                bankOrProvider != null ? ", " + bankOrProvider : "",
                a.getCurrentBalance(), a.getStatus());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("accountType", a.getAccountType());
        metadata.put("currentBalance", a.getCurrentBalance());
        metadata.put("status", a.getStatus());
        return new MappedDocument(a.getAccountNickname(), content, metadata);
    }

    public MappedDocument mapGoldAsset(GoldAssetEntity g) {
        String content = String.format("%s - %s, %s, %.2f %s, estimated value ৳%.2f%s",
                g.getAssetName(), g.getGoldType(), g.getPurity(), g.getWeight(), g.getWeightUnit(),
                g.getCurrentValue() != null ? g.getCurrentValue() : 0.0,
                g.getNotes() != null && !g.getNotes().isBlank() ? ". Notes: " + g.getNotes() : "");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("goldType", g.getGoldType());
        metadata.put("purity", g.getPurity());
        metadata.put("currentValue", g.getCurrentValue());
        return new MappedDocument(g.getAssetName(), content, metadata);
    }

    /** categoryStatuses/savingsStatuses are the already-resolved maps from BudgetPlanService (categoryName included). */
    public MappedDocument mapBudgetPlan(BudgetPlanEntity plan, List<Map<String, Object>> categoryStatuses, List<Map<String, Object>> savingsStatuses) {
        StringBuilder content = new StringBuilder();
        content.append(String.format("%s: %s %s, planned income ৳%.2f, planned savings ৳%.2f, status %s.",
                plan.getName(), plan.getPeriodType(), plan.getPeriod(),
                plan.getPlannedIncome(), plan.getPlannedSavings(), plan.getStatus()));
        if (categoryStatuses != null && !categoryStatuses.isEmpty()) {
            String categories = categoryStatuses.stream()
                    .map(c -> String.valueOf(c.get("categoryName")) + ": ৳" + c.get("budgetAmount"))
                    .collect(Collectors.joining(", "));
            content.append(" Categories: ").append(categories).append(".");
        }
        if (savingsStatuses != null && !savingsStatuses.isEmpty()) {
            String savings = savingsStatuses.stream()
                    .map(s -> String.valueOf(s.get("categoryName")) + ": target ৳" + s.get("targetAmount"))
                    .collect(Collectors.joining(", "));
            content.append(" Savings goals: ").append(savings).append(".");
        }
        if (plan.getNotes() != null && !plan.getNotes().isBlank()) {
            content.append(" Notes: ").append(plan.getNotes());
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("periodType", plan.getPeriodType());
        metadata.put("period", plan.getPeriod());
        metadata.put("status", plan.getStatus());
        return new MappedDocument(plan.getName(), content.toString(), metadata);
    }

    public MappedDocument mapConversation(AiConversationEntity conversation, List<AiMessageEntity> recentMessages) {
        String title = conversation.getTitle() != null ? conversation.getTitle() : "New chat";
        String transcript = recentMessages.stream()
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .map(m -> capitalize(m.getRole()) + ": " + m.getContent())
                .collect(Collectors.joining("\n"));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("messageCount", recentMessages.size());
        return new MappedDocument(title, title + "\n" + transcript, metadata);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String capitalize(String s) { return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
