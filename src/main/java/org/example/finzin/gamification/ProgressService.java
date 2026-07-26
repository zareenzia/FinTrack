package org.example.finzin.gamification;

import org.example.finzin.ai.FinancialHealthService;
import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.InvestmentEntity;
import org.example.finzin.entity.LoanEntity;
import org.example.finzin.entity.UserStatCounterEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.BudgetPlanRepository;
import org.example.finzin.repository.InvestmentRepository;
import org.example.finzin.repository.LoanRepository;
import org.example.finzin.repository.UserStatCounterRepository;
import org.example.finzin.service.BudgetPlanService;
import org.example.finzin.service.CreditCardService;
import org.example.finzin.service.FinancialSummaryService;
import org.example.finzin.service.gold.GoldAssetService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves the current value of a small, fixed vocabulary of metric keys — either from a
 * per-user incremental counter (for COUNT/CUMULATIVE_SUM achievements, updated as events happen)
 * or live from an existing service/repository (for SINGLE_VALUE_REACHED achievements — current
 * state, not something to track incrementally). Adding a new achievement at a new threshold for
 * an existing metric key is a pure DB insert; only a genuinely new metric key needs a new case here.
 */
@Service
public class ProgressService {

    private final UserStatCounterRepository counterRepository;
    private final FinancialSummaryService financialSummaryService;
    private final AccountRepository accountRepository;
    private final InvestmentRepository investmentRepository;
    private final LoanRepository loanRepository;
    private final GoldAssetService goldAssetService;
    private final CreditCardService creditCardService;
    private final StreakService streakService;
    private final ChallengeService challengeService;
    private final FinancialHealthService financialHealthService;
    private final BudgetPlanRepository budgetPlanRepository;
    private final BudgetPlanService budgetPlanService;

    public ProgressService(UserStatCounterRepository counterRepository, FinancialSummaryService financialSummaryService,
                            AccountRepository accountRepository, InvestmentRepository investmentRepository,
                            LoanRepository loanRepository, GoldAssetService goldAssetService,
                            CreditCardService creditCardService, StreakService streakService,
                            ChallengeService challengeService, FinancialHealthService financialHealthService,
                            BudgetPlanRepository budgetPlanRepository, BudgetPlanService budgetPlanService) {
        this.counterRepository = counterRepository;
        this.financialSummaryService = financialSummaryService;
        this.accountRepository = accountRepository;
        this.investmentRepository = investmentRepository;
        this.loanRepository = loanRepository;
        this.goldAssetService = goldAssetService;
        this.creditCardService = creditCardService;
        this.streakService = streakService;
        this.challengeService = challengeService;
        this.financialHealthService = financialHealthService;
        this.budgetPlanRepository = budgetPlanRepository;
        this.budgetPlanService = budgetPlanService;
    }

    // ---- Counter-backed metrics (COUNT / CUMULATIVE_SUM achievements) ----

    /**
     * Adds {@code delta} to the named counter (1 for a count, an amount for a cumulative sum) and
     * returns the new total. Also feeds the same delta into any current-period challenge tracking
     * this exact metric — one integration point, so none of the ~10 call sites need their own
     * separate challenge-progress call.
     */
    public double incrementCounter(Long userId, String counterKey, double delta) {
        UserStatCounterEntity counter = counterRepository.findByUserIdAndCounterKey(userId, counterKey).orElseGet(() -> {
            UserStatCounterEntity entity = new UserStatCounterEntity();
            entity.setUserId(userId);
            entity.setCounterKey(counterKey);
            entity.setCounterValue(0.0);
            return entity;
        });
        counter.setCounterValue(counter.getCounterValue() + delta);
        double newValue = counterRepository.save(counter).getCounterValue();
        challengeService.recordProgress(userId, counterKey, delta);
        return newValue;
    }

    public double getCounterValue(Long userId, String counterKey) {
        return counterRepository.findByUserIdAndCounterKey(userId, counterKey).map(UserStatCounterEntity::getCounterValue).orElse(0.0);
    }

    // ---- Metric resolution (current value for a given metricKey) ----

    public double resolveMetric(Long userId, String metricKey) {
        return switch (metricKey) {
            case "transactions.count", "notes.count", "todos.completed_count", "receipts.scanned_count",
                 "ai.conversations_count", "investments.count", "loans.paid_off_count", "goals.completed_count",
                 "budget.plans_created", "savings.total" -> getCounterValue(userId, metricKey);
            case "investments.distinct_types" -> resolveDistinctInvestmentTypes(userId);
            case "investments.portfolio_value" -> resolvePortfolioValue(userId);
            case "networth.value" -> financialSummaryService.getNetWorth(userId);
            case "loans.debt_reduced_percent" -> resolveDebtReducedPercent(userId);
            case "loans.debt_free" -> resolveDebtFree(userId);
            case "creditcard.headroom_percent" -> resolveCreditCardHeadroom(userId);
            case "assets.count" -> resolveAssetsCount(userId);
            case "assets.total_value" -> goldAssetService.getTotalGoldValueForUser(userId);
            case "streak.daily_active" -> resolveStreakDays(userId, "DAILY_ACTIVE");
            case "networth.growth_percent" -> resolveNetWorthGrowthPercent(userId);
            case "budget.consecutive_months_within" -> resolveBudgetConsecutiveMonthsWithin(userId);
            default -> 0.0;
        };
    }

    private double resolveDistinctInvestmentTypes(Long userId) {
        return investmentRepository.findByUserId(userId).stream()
                .map(InvestmentEntity::getInvestmentType)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .count();
    }

    private double resolvePortfolioValue(Long userId) {
        return investmentRepository.findByUserId(userId).stream()
                .mapToDouble(i -> safe(i.getQuantity()) * safe(i.getCurrentPrice()))
                .sum();
    }

    private double resolveDebtReducedPercent(Long userId) {
        List<LoanEntity> loans = loanRepository.findByUserId(userId);
        if (loans.isEmpty()) return 0.0;
        double principal = loans.stream().mapToDouble(l -> safe(l.getPrincipalAmount())).sum();
        double remaining = loans.stream().mapToDouble(l -> safe(l.getRemainingBalance())).sum();
        if (principal <= 0) return 0.0;
        return Math.max(0.0, Math.min(100.0, ((principal - remaining) / principal) * 100.0));
    }

    private double resolveDebtFree(Long userId) {
        List<LoanEntity> loans = loanRepository.findByUserId(userId);
        if (loans.isEmpty()) return 0.0; // never having had a loan isn't "debt-free" as an achievement
        boolean allClosed = loans.stream().allMatch(l -> safe(l.getRemainingBalance()) <= 0);
        return allClosed ? 1.0 : 0.0;
    }

    private double resolveCreditCardHeadroom(Long userId) {
        List<AccountEntity> creditCards = accountRepository.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .filter(a -> "CREDIT_CARD".equals(a.getAccountType()))
                .toList();
        if (creditCards.isEmpty()) return 0.0;
        double maxUtilization = creditCards.stream()
                .mapToDouble(a -> creditCardService.getStats(a).utilizationPercent())
                .max().orElse(100.0);
        return Math.max(0.0, 100.0 - maxUtilization);
    }

    private double resolveAssetsCount(Long userId) {
        // Live count is fine here — a user's gold-asset list is small, no need for a counter.
        return getCounterValue(userId, "assets.count");
    }

    private double resolveStreakDays(Long userId, String streakType) {
        var streak = streakService.get(userId, streakType);
        return streak != null && streak.getCurrentStreak() != null ? streak.getCurrentStreak() : 0.0;
    }

    /** Reuses {@link FinancialHealthService}'s own snapshot-vs-current-month comparison rather than re-deriving it. */
    private double resolveNetWorthGrowthPercent(Long userId) {
        Double growth = financialHealthService.calculate(userId).netWorthGrowthPercent();
        return growth != null ? growth : 0.0;
    }

    /**
     * Counts the streak of most-recently-closed MONTH budget plans (most recent first) that stayed
     * within budget in every category, stopping at the first one that didn't — reusing
     * {@link BudgetPlanService#computeCategoryStatuses} rather than re-deriving spend-vs-budget.
     * Only plans the user actually created are considered (this app doesn't auto-generate one per
     * calendar month), so "consecutive" means consecutive among those, not calendar-contiguous.
     */
    private double resolveBudgetConsecutiveMonthsWithin(Long userId) {
        List<BudgetPlanEntity> closedMonthlyPlans = budgetPlanRepository.findByUserId(userId).stream()
                .filter(p -> "MONTH".equals(p.getPeriodType()))
                .filter(p -> p.getEndDate().isBefore(LocalDate.now()))
                .sorted(Comparator.comparing(BudgetPlanEntity::getEndDate).reversed())
                .toList();

        int consecutive = 0;
        for (BudgetPlanEntity plan : closedMonthlyPlans) {
            boolean withinBudget = budgetPlanService.computeCategoryStatuses(plan).stream()
                    .noneMatch(c -> (Double) c.get("percentUsed") > 100);
            if (!withinBudget) break;
            consecutive++;
        }
        return consecutive;
    }

    private double safe(Double value) {
        return value != null ? value : 0.0;
    }
}
