package org.example.finzin.ai;

import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.GoldAssetEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.BudgetPlanService;
import org.example.finzin.service.FinancialSummaryService;
import org.example.finzin.service.gold.GoldAssetService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thin, LLM-shaped façade over existing repositories/services. Every method here is the
 * extension seam for future phases (RAG, forecasting) — none of it duplicates a calculation
 * that already lives elsewhere in the app.
 */
@Service
public class FinancialContextService {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetPlanService budgetPlanService;
    private final FinancialSummaryService financialSummaryService;
    private final GoldAssetService goldAssetService;

    public FinancialContextService(TransactionRepository transactionRepository, AccountRepository accountRepository,
                                    CategoryRepository categoryRepository, BudgetPlanService budgetPlanService,
                                    FinancialSummaryService financialSummaryService, GoldAssetService goldAssetService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.budgetPlanService = budgetPlanService;
        this.financialSummaryService = financialSummaryService;
        this.goldAssetService = goldAssetService;
    }

    public List<Map<String, Object>> getAccountBalances(Long userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(this::toAccountMap)
                .collect(Collectors.toList());
    }

    /** month: "YYYY-MM", defaults to the current month when blank/unparseable. */
    public Map<String, Object> getMonthlyExpenses(Long userId, String month) {
        YearMonth ym = parseMonth(month);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();

        double income = nz(transactionRepository.sumByUserIdAndTypeAndDateRange(userId, "income", start, end));
        double expense = nz(transactionRepository.sumByUserIdAndTypeAndDateRange(userId, "expense", start, end));
        double savings = nz(transactionRepository.sumByUserIdAndTypeAndDateRange(userId, "savings", start, end));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", ym.toString());
        result.put("income", income);
        result.put("expense", expense);
        result.put("savings", savings);
        result.put("net", income - expense - savings);
        return result;
    }

    /** categoryName: fuzzy-matched (case-insensitive contains) against the user's own categories. month: "YYYY-MM", optional. */
    public Map<String, Object> getExpenseByCategory(Long userId, String categoryName, String month) {
        Map<String, Object> result = new LinkedHashMap<>();
        CategoryEntity category = findCategoryByName(userId, categoryName);
        if (category == null) {
            result.put("error", "No category matching \"" + categoryName + "\" was found for this user.");
            return result;
        }
        YearMonth ym = parseMonth(month);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
        double spent = nz(transactionRepository.sumByUserIdAndTypeAndCategoryAndDateRange(userId, "expense", category.getId(), start, end));

        result.put("category", category.getName());
        result.put("month", ym.toString());
        result.put("totalSpent", spent);
        return result;
    }

    /** monthA/monthB: "YYYY-MM"; each defaults to the current month when blank/unparseable. */
    public Map<String, Object> getMonthComparison(Long userId, String monthA, String monthB) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("monthA", getMonthlyExpenses(userId, monthA));
        result.put("monthB", getMonthlyExpenses(userId, monthB));
        return result;
    }

    public List<Map<String, Object>> getRecentTransactions(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return transactionRepository.findByUserIdOrderByDateDesc(userId).stream()
                .limit(safeLimit)
                .map(this::toTransactionMap)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getBudgetStatus(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        var plan = budgetPlanService.getCurrentPlan(userId);
        if (plan == null) {
            result.put("hasBudget", false);
            result.put("message", "This user has no active budget plan for the current period.");
            return result;
        }
        var categoryStatuses = budgetPlanService.computeCategoryStatuses(plan);
        var savingsStatuses = budgetPlanService.computeSavingsStatuses(plan);
        var summary = budgetPlanService.computeSummary(plan, categoryStatuses);
        int score = budgetPlanService.computeBudgetScore(plan, categoryStatuses, savingsStatuses, summary);

        result.put("hasBudget", true);
        result.put("planName", plan.getName());
        result.put("period", plan.getPeriod());
        result.put("summary", summary);
        result.put("categories", categoryStatuses);
        result.put("savingsGoals", savingsStatuses);
        result.put("budgetScore", score);
        return result;
    }

    public Map<String, Object> getNetWorth(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("netWorth", financialSummaryService.getNetWorth(userId));
        result.put("balance", financialSummaryService.getBalance(userId));
        result.put("totalSavingsContributed", financialSummaryService.getTotalSavings(userId));
        result.put("totalAssets", financialSummaryService.getTotalAssets(userId));
        return result;
    }

    public Map<String, Object> getSavings(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSavingsContributed", financialSummaryService.getTotalSavings(userId));
        var plan = budgetPlanService.getCurrentPlan(userId);
        result.put("activeSavingsGoals", plan == null ? List.of() : budgetPlanService.computeSavingsStatuses(plan));
        return result;
    }

    public Map<String, Object> getAssets(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<GoldAssetEntity> goldAssets = goldAssetService.getAssetsForUser(userId);
        result.put("goldAssets", goldAssets.stream().map(this::toGoldAssetMap).collect(Collectors.toList()));
        result.put("totalGoldValue", goldAssetService.getTotalGoldValueForUser(userId));
        result.put("totalGoldWeightGrams", goldAssetService.getTotalGoldWeightInGrams(userId));
        return result;
    }

    // ── mapping helpers ──────────────────────────────────────────────────────

    private Map<String, Object> toAccountMap(AccountEntity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nickname", a.getAccountNickname());
        m.put("type", a.getAccountType());
        m.put("bankOrProvider", a.getBankName() != null ? a.getBankName() : a.getProvider());
        m.put("currentBalance", a.getCurrentBalance());
        m.put("status", a.getStatus());
        return m;
    }

    private Map<String, Object> toTransactionMap(TransactionEntity t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", t.getDate() != null ? t.getDate().toLocalDate().toString() : null);
        m.put("description", t.getDescription());
        m.put("amount", t.getAmount());
        m.put("type", t.getTransactionType());
        m.put("category", t.getCategory() != null ? t.getCategory().getName() : null);
        return m;
    }

    private Map<String, Object> toGoldAssetMap(GoldAssetEntity g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", g.getAssetName());
        m.put("goldType", g.getGoldType());
        m.put("purity", g.getPurity());
        m.put("weight", g.getWeight());
        m.put("weightUnit", g.getWeightUnit());
        m.put("currentValue", g.getCurrentValue());
        return m;
    }

    private CategoryEntity findCategoryByName(Long userId, String categoryName) {
        if (categoryName == null || categoryName.isBlank()) return null;
        String needle = categoryName.trim().toLowerCase(Locale.ROOT);
        return categoryRepository.findByUserId(userId).stream()
                .filter(c -> c.getName() != null && c.getName().toLowerCase(Locale.ROOT).contains(needle))
                .min(Comparator.comparingInt(c -> c.getName().length())) // prefer the closest/shortest match
                .orElse(null);
    }

    private YearMonth parseMonth(String month) {
        if (month != null && !month.isBlank()) {
            try {
                return YearMonth.parse(month.trim(), DateTimeFormatter.ofPattern("yyyy-MM"));
            } catch (Exception ignored) {
                // fall through to current month
            }
        }
        return YearMonth.now();
    }

    private static double nz(Double value) {
        return value == null ? 0 : value;
    }
}
