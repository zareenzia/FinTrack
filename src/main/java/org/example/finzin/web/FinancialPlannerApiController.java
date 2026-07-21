package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.InvestmentEntity;
import org.example.finzin.entity.LoanEntity;
import org.example.finzin.entity.SubscriptionEntity;
import org.example.finzin.entity.WishlistGoalEntity;
import org.example.finzin.repository.InvestmentRepository;
import org.example.finzin.repository.LoanRepository;
import org.example.finzin.repository.SubscriptionRepository;
import org.example.finzin.repository.WishlistGoalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/financial-planner")
public class FinancialPlannerApiController {

    private final InvestmentRepository investmentRepository;
    private final LoanRepository loanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WishlistGoalRepository wishlistGoalRepository;

    public FinancialPlannerApiController(InvestmentRepository investmentRepository,
                                         LoanRepository loanRepository,
                                         SubscriptionRepository subscriptionRepository,
                                         WishlistGoalRepository wishlistGoalRepository) {
        this.investmentRepository = investmentRepository;
        this.loanRepository = loanRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.wishlistGoalRepository = wishlistGoalRepository;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    // ════════════════════════════════════════════════
    // DASHBOARD SUMMARY
    // ════════════════════════════════════════════════

    @GetMapping("/summary")
    public Map<String, Object> getDashboardSummary(HttpServletRequest request) {
        Long userId = getUserId(request);

        // Investments summary
        List<InvestmentEntity> investments = investmentRepository.findByUserId(userId);
        double totalInvestmentValue = investments.stream()
                .mapToDouble(i -> i.getQuantity() * i.getCurrentPrice()).sum();
        double totalPurchaseValue = investments.stream()
                .mapToDouble(i -> i.getQuantity() * i.getPurchasePrice()).sum();

        // Loans summary
        List<LoanEntity> activeLoans = loanRepository.findByUserIdAndStatus(userId, "ACTIVE");

        // Subscriptions
        List<SubscriptionEntity> activeSubs = subscriptionRepository.findByUserIdAndStatus(userId, "ACTIVE");
        LocalDate today = LocalDate.now();
        LocalDate nextMonth = today.plusDays(30);
        long upcomingRenewals = activeSubs.stream()
                .filter(s -> s.getRenewalDate() != null
                        && !s.getRenewalDate().isBefore(today)
                        && !s.getRenewalDate().isAfter(nextMonth))
                .count();

        // Wishlist goals
        List<WishlistGoalEntity> goals = wishlistGoalRepository.findByUserId(userId);
        double totalSaved = goals.stream().mapToDouble(WishlistGoalEntity::getSavedAmount).sum();
        double totalTarget = goals.stream().mapToDouble(WishlistGoalEntity::getTargetAmount).sum();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("investmentValue", totalInvestmentValue);
        summary.put("investmentCount", investments.size());
        summary.put("activeLoans", activeLoans.size());
        summary.put("loanRemainingBalance", activeLoans.stream().mapToDouble(LoanEntity::getRemainingBalance).sum());
        summary.put("activeSubscriptions", activeSubs.size());
        summary.put("upcomingRenewals", upcomingRenewals);
        summary.put("goalsSaved", totalSaved);
        summary.put("goalsTarget", totalTarget);
        summary.put("goalsCount", goals.size());
        return summary;
    }

    // ════════════════════════════════════════════════
    // INVESTMENTS
    // ════════════════════════════════════════════════

    @GetMapping("/investments")
    public List<Map<String, Object>> getInvestments(HttpServletRequest request) {
        Long userId = getUserId(request);
        return investmentRepository.findByUserId(userId).stream()
                .map(this::toInvestmentMap)
                .collect(Collectors.toList());
    }

    @PostMapping("/investments")
    public ResponseEntity<?> createInvestment(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long userId = getUserId(request);
        try {
            InvestmentEntity entity = new InvestmentEntity();
            entity.setUserId(userId);
            mapToInvestment(body, entity);
            InvestmentEntity saved = investmentRepository.save(entity);
            return ResponseEntity.status(HttpStatus.CREATED).body(toInvestmentMap(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/investments/{id}")
    public ResponseEntity<?> updateInvestment(HttpServletRequest request, @PathVariable Long id,
                                               @RequestBody Map<String, Object> body) {
        Long userId = getUserId(request);
        return investmentRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .map(entity -> {
                    mapToInvestment(body, entity);
                    return ResponseEntity.ok(toInvestmentMap(investmentRepository.save(entity)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/investments/{id}")
    public ResponseEntity<?> deleteInvestment(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        return investmentRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .map(entity -> {
                    investmentRepository.delete(entity);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void mapToInvestment(Map<String, Object> body, InvestmentEntity entity) {
        entity.setName((String) body.get("name"));
        entity.setInvestmentType((String) body.get("investmentType"));
        entity.setPlatform((String) body.get("platform"));
        entity.setPurchaseDate(LocalDate.parse((String) body.get("purchaseDate")));
        entity.setQuantity(toDouble(body.get("quantity")));
        entity.setPurchasePrice(toDouble(body.get("purchasePrice")));
        entity.setCurrentPrice(toDouble(body.get("currentPrice")));
        entity.setNotes((String) body.get("notes"));
    }

    private Map<String, Object> toInvestmentMap(InvestmentEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("investmentType", e.getInvestmentType());
        m.put("platform", e.getPlatform());
        m.put("purchaseDate", e.getPurchaseDate() != null ? e.getPurchaseDate().toString() : null);
        m.put("quantity", e.getQuantity());
        m.put("purchasePrice", e.getPurchasePrice());
        m.put("currentPrice", e.getCurrentPrice());
        double currentValue = e.getQuantity() * e.getCurrentPrice();
        double costBasis = e.getQuantity() * e.getPurchasePrice();
        m.put("currentValue", currentValue);
        m.put("profitLoss", currentValue - costBasis);
        m.put("returnPercent", costBasis > 0 ? ((currentValue - costBasis) / costBasis) * 100 : 0);
        m.put("notes", e.getNotes());
        m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return m;
    }

    // ════════════════════════════════════════════════
    // LOANS
    // ════════════════════════════════════════════════

    @GetMapping("/loans")
    public List<Map<String, Object>> getLoans(HttpServletRequest request) {
        Long userId = getUserId(request);
        return loanRepository.findByUserId(userId).stream()
                .map(this::toLoanMap)
                .collect(Collectors.toList());
    }

    @PostMapping("/loans")
    public ResponseEntity<?> createLoan(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long userId = getUserId(request);
        try {
            LoanEntity entity = new LoanEntity();
            entity.setUserId(userId);
            mapToLoan(body, entity);
            LoanEntity saved = loanRepository.save(entity);
            return ResponseEntity.status(HttpStatus.CREATED).body(toLoanMap(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/loans/{id}")
    public ResponseEntity<?> updateLoan(HttpServletRequest request, @PathVariable Long id,
                                        @RequestBody Map<String, Object> body) {
        Long userId = getUserId(request);
        return loanRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .map(entity -> {
                    mapToLoan(body, entity);
                    return ResponseEntity.ok(toLoanMap(loanRepository.save(entity)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/loans/{id}")
    public ResponseEntity<?> deleteLoan(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        return loanRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .map(entity -> {
                    loanRepository.delete(entity);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Mark an EMI payment — reduces remaining balance by the emi amount. */
    @PostMapping("/loans/{id}/pay-emi")
    public ResponseEntity<?> payEmi(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        return loanRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .map(entity -> {
                    double payment = entity.getEmiAmount() != null ? entity.getEmiAmount() : 0;
                    double newBalance = Math.max(0, entity.getRemainingBalance() - payment);
                    entity.setRemainingBalance(newBalance);
                    if (newBalance == 0) entity.setStatus("CLOSED");
                    return ResponseEntity.ok(toLoanMap(loanRepository.save(entity)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void mapToLoan(Map<String, Object> body, LoanEntity entity) {
        entity.setLoanName((String) body.get("loanName"));
        entity.setLoanType((String) body.get("loanType"));
        entity.setLenderBorrower((String) body.get("lenderBorrower"));
        entity.setPrincipalAmount(toDouble(body.get("principalAmount")));
        entity.setInterestRate(toDoubleOrNull(body.get("interestRate")));
        entity.setEmiAmount(toDoubleOrNull(body.get("emiAmount")));
        entity.setLoanStartDate(LocalDate.parse((String) body.get("loanStartDate")));
        String endDate = (String) body.get("loanEndDate");
        entity.setLoanEndDate(endDate != null && !endDate.isBlank() ? LocalDate.parse(endDate) : null);
        entity.setRemainingBalance(toDouble(body.get("remainingBalance")));
        entity.setPaymentFrequency((String) body.get("paymentFrequency"));
        entity.setStatus(body.get("status") != null ? (String) body.get("status") : "ACTIVE");
        entity.setNotes((String) body.get("notes"));
    }

    private Map<String, Object> toLoanMap(LoanEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("loanName", e.getLoanName());
        m.put("loanType", e.getLoanType());
        m.put("lenderBorrower", e.getLenderBorrower());
        m.put("principalAmount", e.getPrincipalAmount());
        m.put("interestRate", e.getInterestRate());
        m.put("emiAmount", e.getEmiAmount());
        m.put("loanStartDate", e.getLoanStartDate() != null ? e.getLoanStartDate().toString() : null);
        m.put("loanEndDate", e.getLoanEndDate() != null ? e.getLoanEndDate().toString() : null);
        m.put("remainingBalance", e.getRemainingBalance());
        m.put("paymentFrequency", e.getPaymentFrequency());
        m.put("status", e.getStatus());
        double paid = e.getPrincipalAmount() - e.getRemainingBalance();
        m.put("paidAmount", Math.max(0, paid));
        m.put("progressPercent", e.getPrincipalAmount() > 0
                ? Math.min(100, (paid / e.getPrincipalAmount()) * 100) : 0);
        double totalInterest = e.getInterestRate() != null && e.getEmiAmount() != null
                ? calculateTotalInterest(e) : 0;
        m.put("totalInterest", totalInterest);
        m.put("notes", e.getNotes());
        m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return m;
    }

    private double calculateTotalInterest(LoanEntity e) {
        if (e.getEmiAmount() == null || e.getLoanEndDate() == null) return 0;
        long months = ChronoUnit.MONTHS.between(e.getLoanStartDate(), e.getLoanEndDate());
        return Math.max(0, (e.getEmiAmount() * months) - e.getPrincipalAmount());
    }

    // ════════════════════════════════════════════════
    // SUBSCRIPTIONS
    // ════════════════════════════════════════════════

    @GetMapping("/subscriptions")
    public List<Map<String, Object>> getSubscriptions(HttpServletRequest request) {
        Long userId = getUserId(request);
        return subscriptionRepository.findByUserId(userId).stream()
                .map(this::toSubscriptionMap)
                .collect(Collectors.toList());
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<?> createSubscription(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long userId = getUserId(request);
        try {
            SubscriptionEntity entity = new SubscriptionEntity();
            entity.setUserId(userId);
            mapToSubscription(body, entity);
            SubscriptionEntity saved = subscriptionRepository.save(entity);
            return ResponseEntity.status(HttpStatus.CREATED).body(toSubscriptionMap(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/subscriptions/{id}")
    public ResponseEntity<?> updateSubscription(HttpServletRequest request, @PathVariable Long id,
                                                 @RequestBody Map<String, Object> body) {
        Long userId = getUserId(request);
        return subscriptionRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .map(entity -> {
                    mapToSubscription(body, entity);
                    return ResponseEntity.ok(toSubscriptionMap(subscriptionRepository.save(entity)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/subscriptions/{id}")
    public ResponseEntity<?> deleteSubscription(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        return subscriptionRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .map(entity -> {
                    subscriptionRepository.delete(entity);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void mapToSubscription(Map<String, Object> body, SubscriptionEntity entity) {
        entity.setName((String) body.get("name"));
        entity.setCategory((String) body.get("category"));
        entity.setBillingCycle((String) body.get("billingCycle"));
        entity.setCost(toDouble(body.get("cost")));
        String rd = (String) body.get("renewalDate");
        entity.setRenewalDate(rd != null && !rd.isBlank() ? LocalDate.parse(rd) : null);
        entity.setPaymentMethod((String) body.get("paymentMethod"));
        entity.setPaymentAccount((String) body.get("paymentAccount"));
        Object ar = body.get("autoRenewal");
        entity.setAutoRenewal(ar instanceof Boolean ? (Boolean) ar : Boolean.TRUE);
        entity.setStatus(body.get("status") != null ? (String) body.get("status") : "ACTIVE");
        entity.setNotes((String) body.get("notes"));
    }

    private Map<String, Object> toSubscriptionMap(SubscriptionEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("category", e.getCategory());
        m.put("billingCycle", e.getBillingCycle());
        m.put("cost", e.getCost());
        double monthlyCost = "YEARLY".equals(e.getBillingCycle()) ? e.getCost() / 12.0 : e.getCost();
        double yearlyCost  = "MONTHLY".equals(e.getBillingCycle()) ? e.getCost() * 12.0 : e.getCost();
        m.put("monthlyCost", monthlyCost);
        m.put("yearlyCost", yearlyCost);
        m.put("renewalDate", e.getRenewalDate() != null ? e.getRenewalDate().toString() : null);
        m.put("paymentMethod", e.getPaymentMethod());
        m.put("paymentAccount", e.getPaymentAccount());
        m.put("autoRenewal", e.getAutoRenewal());
        m.put("status", e.getStatus());
        // Days until renewal
        if (e.getRenewalDate() != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), e.getRenewalDate());
            m.put("daysUntilRenewal", days);
        } else {
            m.put("daysUntilRenewal", null);
        }
        m.put("notes", e.getNotes());
        m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return m;
    }

    // ════════════════════════════════════════════════
    // WISHLIST GOALS
    // ════════════════════════════════════════════════

    @GetMapping("/goals")
    public List<Map<String, Object>> getGoals(HttpServletRequest request) {
        Long userId = getUserId(request);
        return wishlistGoalRepository.findByUserId(userId).stream()
                .map(this::toGoalMap)
                .collect(Collectors.toList());
    }

    @PostMapping("/goals")
    public ResponseEntity<?> createGoal(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long userId = getUserId(request);
        try {
            WishlistGoalEntity entity = new WishlistGoalEntity();
            entity.setUserId(userId);
            mapToGoal(body, entity);
            WishlistGoalEntity saved = wishlistGoalRepository.save(entity);
            return ResponseEntity.status(HttpStatus.CREATED).body(toGoalMap(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/goals/{id}")
    public ResponseEntity<?> updateGoal(HttpServletRequest request, @PathVariable Long id,
                                        @RequestBody Map<String, Object> body) {
        Long userId = getUserId(request);
        return wishlistGoalRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .map(entity -> {
                    mapToGoal(body, entity);
                    return ResponseEntity.ok(toGoalMap(wishlistGoalRepository.save(entity)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/goals/{id}")
    public ResponseEntity<?> deleteGoal(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        return wishlistGoalRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .map(entity -> {
                    wishlistGoalRepository.delete(entity);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Mark goal as completed */
    @PostMapping("/goals/{id}/complete")
    public ResponseEntity<?> completeGoal(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        return wishlistGoalRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId))
                .map(entity -> {
                    entity.setStatus("COMPLETED");
                    entity.setSavedAmount(entity.getTargetAmount());
                    return ResponseEntity.ok(toGoalMap(wishlistGoalRepository.save(entity)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void mapToGoal(Map<String, Object> body, WishlistGoalEntity entity) {
        entity.setGoalName((String) body.get("goalName"));
        entity.setCategory((String) body.get("category"));
        entity.setTargetAmount(toDouble(body.get("targetAmount")));
        entity.setSavedAmount(toDoubleOrDefault(body.get("savedAmount"), 0.0));
        String td = (String) body.get("targetDate");
        entity.setTargetDate(td != null && !td.isBlank() ? LocalDate.parse(td) : null);
        entity.setPriority(body.get("priority") != null ? (String) body.get("priority") : "MEDIUM");
        entity.setStatus(body.get("status") != null ? (String) body.get("status") : "IN_PROGRESS");
        entity.setIcon((String) body.get("icon"));
        entity.setNotes((String) body.get("notes"));
    }

    private Map<String, Object> toGoalMap(WishlistGoalEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("goalName", e.getGoalName());
        m.put("category", e.getCategory());
        m.put("targetAmount", e.getTargetAmount());
        m.put("savedAmount", e.getSavedAmount());
        double remaining = Math.max(0, e.getTargetAmount() - e.getSavedAmount());
        m.put("remainingAmount", remaining);
        double pct = e.getTargetAmount() > 0 ? Math.min(100, (e.getSavedAmount() / e.getTargetAmount()) * 100) : 0;
        m.put("progressPercent", pct);
        m.put("targetDate", e.getTargetDate() != null ? e.getTargetDate().toString() : null);
        m.put("priority", e.getPriority());
        m.put("status", e.getStatus());
        m.put("icon", e.getIcon());
        m.put("notes", e.getNotes());
        m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return m;
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    private double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    private Double toDoubleOrNull(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private double toDoubleOrDefault(Object v, double def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return def; }
    }
}
