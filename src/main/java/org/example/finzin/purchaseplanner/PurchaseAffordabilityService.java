package org.example.finzin.purchaseplanner;

import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.BudgetPlanEntity;
import org.example.finzin.entity.LoanEntity;
import org.example.finzin.entity.PurchaseItemEntity;
import org.example.finzin.entity.RecurringTransactionEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.LoanRepository;
import org.example.finzin.repository.RecurringTransactionRepository;
import org.example.finzin.repository.SubscriptionRepository;
import org.example.finzin.service.BudgetPlanService;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-calculation affordability + decision-matrix engine — no AI. Kept separate from
 * {@link PurchaseItemService} so the arithmetic is independently readable/testable.
 *
 * "Emergency fund" and "investment commitments" have no first-class entity in this app; they are
 * documented heuristics, not guesses baked silently into the math: an emergency fund is any current
 * budget plan's savings-goal category whose name contains "emergency", and investment commitments
 * are the monthly-equivalent of active recurring transactions of type "savings" (the closest
 * existing proxy for a recurring auto-invest transfer). Both degrade to 0 if the user has none,
 * never blocking the calculation.
 */
@Service
public class PurchaseAffordabilityService {

    private static final int MAX_WAIT_MONTHS = 24;

    private final AccountRepository accountRepository;
    private final LoanRepository loanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final BudgetPlanService budgetPlanService;

    public PurchaseAffordabilityService(AccountRepository accountRepository, LoanRepository loanRepository,
                                         SubscriptionRepository subscriptionRepository,
                                         RecurringTransactionRepository recurringTransactionRepository,
                                         BudgetPlanService budgetPlanService) {
        this.accountRepository = accountRepository;
        this.loanRepository = loanRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.budgetPlanService = budgetPlanService;
    }

    public record AffordabilityContext(double availableBalance, double emergencyFundReserve,
                                        double remainingMonthlyBudget, double monthlyFixedCommitments,
                                        List<PurchaseItemEntity> activeItems) {}

    public record AffordabilityResult(String status, String statusLabel, Integer monthsRemaining,
                                       double netAvailableNow, double monthlySavingsCapacity) {}

    public record DecisionMatrixResult(double needScore, double urgencyScore, double budgetScore,
                                        double savingsScore, double overallReadinessPercent) {}

    /** One batched query set per list-call — reused across every item, never re-queried per item. */
    public AffordabilityContext buildContext(Long userId, List<PurchaseItemEntity> activeItems) {
        double availableBalance = accountRepository.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .filter(a -> !"CREDIT_CARD".equals(a.getAccountType()))
                .mapToDouble(AccountEntity::getCurrentBalance)
                .sum();

        double remainingMonthlyBudget = 0;
        double emergencyFundReserve = 0;
        BudgetPlanEntity currentPlan = budgetPlanService.getCurrentPlan(userId);
        if (currentPlan != null) {
            List<Map<String, Object>> categoryStatuses = budgetPlanService.computeCategoryStatuses(currentPlan);
            remainingMonthlyBudget = categoryStatuses.stream().mapToDouble(c -> (Double) c.get("remainingAmount")).sum();

            List<Map<String, Object>> savingsStatuses = budgetPlanService.computeSavingsStatuses(currentPlan);
            emergencyFundReserve = savingsStatuses.stream()
                    .filter(s -> String.valueOf(s.get("categoryName")).toLowerCase(Locale.ROOT).contains("emergency"))
                    .mapToDouble(s -> (Double) s.get("currentAmount"))
                    .sum();
        }

        double monthlyLoanEmi = loanRepository.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .mapToDouble(this::monthlyEmi).sum();
        double monthlySubscriptions = subscriptionRepository.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .mapToDouble(s -> "YEARLY".equals(s.getBillingCycle()) ? s.getCost() / 12.0 : s.getCost())
                .sum();
        double monthlyInvestments = recurringTransactionRepository.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .filter(r -> "savings".equalsIgnoreCase(r.getTransactionType()))
                .mapToDouble(this::monthlyEquivalent).sum();

        double monthlyFixedCommitments = monthlyLoanEmi + monthlySubscriptions + monthlyInvestments;
        return new AffordabilityContext(availableBalance, emergencyFundReserve, remainingMonthlyBudget,
                monthlyFixedCommitments, activeItems);
    }

    private double monthlyEmi(LoanEntity loan) {
        if (loan.getEmiAmount() == null) return 0;
        String freq = loan.getPaymentFrequency();
        if ("WEEKLY".equals(freq)) return loan.getEmiAmount() * 52 / 12.0;
        if ("YEARLY".equals(freq)) return loan.getEmiAmount() / 12.0;
        if ("ONE_TIME".equals(freq)) return 0;
        return loan.getEmiAmount(); // MONTHLY or unspecified
    }

    private double monthlyEquivalent(RecurringTransactionEntity r) {
        int interval = r.getIntervalValue() == null || r.getIntervalValue() < 1 ? 1 : r.getIntervalValue();
        double unitsPerMonth = switch (r.getFrequency() == null ? "" : r.getFrequency()) {
            case "DAILY" -> 30.0;
            case "WEEKLY" -> 4.33;
            case "MONTHLY" -> 1.0;
            case "QUARTERLY" -> 1.0 / 3;
            case "YEARLY" -> 1.0 / 12;
            default -> 0.0;
        };
        return r.getAmount() * unitsPerMonth / interval;
    }

    private double linkedSavingsAmount(PurchaseItemEntity item) {
        if (item.getLinkedSavingsGoalId() == null) return 0;
        return budgetPlanService.findSavingsBudgetStatus(item.getUserId(), item.getLinkedSavingsGoalId())
                .map(s -> (Double) s.get("currentAmount"))
                .orElse(0.0);
    }

    public AffordabilityResult computeAffordability(PurchaseItemEntity item, AffordabilityContext ctx) {
        double linkedSavings = linkedSavingsAmount(item);

        double otherReserved = 0;
        if (item.getTargetMonth() != null && !item.getTargetMonth().isBlank()) {
            otherReserved = ctx.activeItems().stream()
                    .filter(o -> !o.getId().equals(item.getId()))
                    .filter(o -> item.getTargetMonth().equals(o.getTargetMonth()))
                    .mapToDouble(PurchaseItemEntity::getEstimatedPrice)
                    .sum();
        }

        double netAvailableNow = Math.max(0, ctx.availableBalance() - ctx.emergencyFundReserve()) + linkedSavings - otherReserved;
        double monthlySavingsCapacity = Math.max(0, ctx.remainingMonthlyBudget() - ctx.monthlyFixedCommitments());
        double shortfall = item.getEstimatedPrice() - netAvailableNow;

        if (shortfall <= 0) {
            return new AffordabilityResult("CAN_BUY_NOW", "✅ Can Buy Now", 0, netAvailableNow, monthlySavingsCapacity);
        }
        if (monthlySavingsCapacity <= 0) {
            return new AffordabilityResult("NOT_AFFORDABLE", "❌ Not Affordable", null, netAvailableNow, monthlySavingsCapacity);
        }
        int monthsRemaining = (int) Math.ceil(shortfall / monthlySavingsCapacity);
        if (monthsRemaining > MAX_WAIT_MONTHS) {
            return new AffordabilityResult("NOT_AFFORDABLE", "❌ Not Affordable", null, netAvailableNow, monthlySavingsCapacity);
        }
        String label = "⚠ Wait " + monthsRemaining + " Month" + (monthsRemaining == 1 ? "" : "s");
        return new AffordabilityResult("WAIT", label, monthsRemaining, netAvailableNow, monthlySavingsCapacity);
    }

    public DecisionMatrixResult computeDecisionMatrix(PurchaseItemEntity item, AffordabilityContext ctx) {
        double needScore = switch (item.getNeedLevel() == null ? "" : item.getNeedLevel()) {
            case "MUST_HAVE" -> 5.0;
            case "SHOULD_HAVE" -> 3.0;
            default -> 1.0;
        };

        double priorityBase = switch (item.getPriority() == null ? "" : item.getPriority()) {
            case "CRITICAL" -> 5.0;
            case "HIGH" -> 4.0;
            case "MEDIUM" -> 2.5;
            default -> 1.0;
        };
        long monthsUntilTarget = 0;
        if (item.getTargetMonth() != null && !item.getTargetMonth().isBlank()) {
            try {
                YearMonth target = YearMonth.parse(item.getTargetMonth());
                monthsUntilTarget = Math.max(0, ChronoUnit.MONTHS.between(YearMonth.now(), target));
            } catch (Exception ignored) {
                // malformed/blank target month — treat as "no target", urgency unaffected by month
            }
        }
        double urgencyScore = clamp(priorityBase - monthsUntilTarget * 0.25, 0, 5);

        double budgetRatio = ctx.remainingMonthlyBudget() <= 0 ? Double.MAX_VALUE : item.getEstimatedPrice() / ctx.remainingMonthlyBudget();
        double budgetScore;
        if (budgetRatio <= 0.25) budgetScore = 5;
        else if (budgetRatio <= 0.50) budgetScore = 4;
        else if (budgetRatio <= 0.75) budgetScore = 3;
        else if (budgetRatio <= 1.00) budgetScore = 2;
        else if (budgetRatio <= 2.00) budgetScore = 1;
        else budgetScore = 0;

        double linkedSavings = linkedSavingsAmount(item);
        double availableForCoverage = item.getLinkedSavingsGoalId() != null
                ? linkedSavings
                : Math.max(0, ctx.availableBalance() - ctx.emergencyFundReserve());
        double coveragePercent = item.getEstimatedPrice() == null || item.getEstimatedPrice() <= 0
                ? 0 : (availableForCoverage / item.getEstimatedPrice()) * 100;
        double savingsScore;
        if (coveragePercent >= 100) savingsScore = 5;
        else if (coveragePercent >= 75) savingsScore = 4;
        else if (coveragePercent >= 50) savingsScore = 3;
        else if (coveragePercent >= 25) savingsScore = 2;
        else if (coveragePercent > 0) savingsScore = 1;
        else savingsScore = 0;

        double overallReadinessPercent = (needScore * 0.15 + urgencyScore * 0.15 + budgetScore * 0.35 + savingsScore * 0.35) / 5.0 * 100;

        return new DecisionMatrixResult(needScore, urgencyScore, budgetScore, savingsScore, overallReadinessPercent);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
