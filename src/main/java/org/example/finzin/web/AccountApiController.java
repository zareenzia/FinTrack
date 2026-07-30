package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.AccountEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.CreditCardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/accounts")
public class AccountApiController {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final DocumentIndexer documentIndexer;
    private final CreditCardService creditCardService;

    public AccountApiController(AccountRepository accountRepository, TransactionRepository transactionRepository,
                                 DocumentIndexer documentIndexer, CreditCardService creditCardService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.documentIndexer = documentIndexer;
        this.creditCardService = creditCardService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    @GetMapping
    public List<Map<String, Object>> getAccounts(HttpServletRequest request) {
        Long userId = getUserId(request);
        return accountRepository.findByUserId(userId).stream()
                .map(this::toAccountResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<AccountEntity> accounts = accountRepository.findByUserIdAndStatus(userId, "ACTIVE");
        double totalBank = accounts.stream()
                .filter(a -> "BANK".equals(a.getAccountType()) || "DEBIT_CARD".equals(a.getAccountType()))
                .mapToDouble(AccountEntity::getCurrentBalance).sum();
        double totalMfs = accounts.stream()
                .filter(a -> "MFS".equals(a.getAccountType()))
                .mapToDouble(AccountEntity::getCurrentBalance).sum();
        double totalCash = accounts.stream()
                .filter(a -> "CASH".equals(a.getAccountType()))
                .mapToDouble(AccountEntity::getCurrentBalance).sum();
        double totalCreditOutstanding = accounts.stream()
                .filter(a -> "CREDIT_CARD".equals(a.getAccountType()))
                .mapToDouble(AccountEntity::getCurrentBalance).sum();
        double totalAvailable = totalBank + totalMfs + totalCash;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalBank", totalBank);
        summary.put("totalMfs", totalMfs);
        summary.put("totalCash", totalCash);
        summary.put("totalCreditOutstanding", totalCreditOutstanding);
        summary.put("totalAvailable", totalAvailable);
        return summary;
    }

    @PostMapping
    public ResponseEntity<?> createAccount(HttpServletRequest request, @RequestBody AccountRequest body) {
        Long userId = getUserId(request);
        if (body == null || body.accountType() == null || body.accountNickname() == null || body.accountNickname().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "accountType and accountNickname are required"));
        }
        AccountEntity entity = new AccountEntity();
        entity.setUserId(userId);
        mapRequestToEntity(body, entity);
        entity.setCurrentBalance(body.openingBalance() != null ? body.openingBalance() : 0.0);
        AccountEntity saved = accountRepository.save(entity);
        documentIndexer.indexAccount(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(toAccountResponse(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAccount(HttpServletRequest request, @PathVariable Long id, @RequestBody AccountRequest body) {
        Long userId = getUserId(request);
        AccountEntity entity = accountRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Account not found"));
        }
        if (body == null || body.accountType() == null || body.accountNickname() == null || body.accountNickname().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "accountType and accountNickname are required"));
        }
        double oldOpeningBalance = entity.getOpeningBalance();
        double oldCurrentBalance = entity.getCurrentBalance();
        mapRequestToEntity(body, entity);
        if (body.currentBalance() != null) {
            // A direct correction to the live balance wins outright — shift openingBalance by the
            // same amount so "openingBalance + ledger effect since" (e.g. the credit-card balance
            // recompute that runs on every startup) still lands on exactly this corrected value,
            // instead of silently reverting it on the next restart.
            entity.setOpeningBalance(oldOpeningBalance + (body.currentBalance() - oldCurrentBalance));
            entity.setCurrentBalance(body.currentBalance());
        } else {
            double balanceDiff = entity.getOpeningBalance() - oldOpeningBalance;
            entity.setCurrentBalance(oldCurrentBalance + balanceDiff);
        }
        AccountEntity saved = accountRepository.save(entity);
        documentIndexer.indexAccount(saved);
        return ResponseEntity.ok(toAccountResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        AccountEntity entity = accountRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Account not found"));
        }
        boolean hasTransactions = transactionRepository.existsBySourceAccountIdOrDestinationAccountId(id, id);
        if (hasTransactions) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete account with associated transactions"));
        }
        accountRepository.deleteById(id);
        documentIndexer.deleteAccount(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> toggleStatus(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        AccountEntity entity = accountRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Account not found"));
        }
        entity.setStatus("ACTIVE".equals(entity.getStatus()) ? "INACTIVE" : "ACTIVE");
        accountRepository.save(entity);
        documentIndexer.indexAccount(entity);
        return ResponseEntity.ok(toAccountResponse(entity));
    }

    private void mapRequestToEntity(AccountRequest body, AccountEntity entity) {
        entity.setAccountType(body.accountType());
        entity.setAccountNickname(body.accountNickname().trim());
        entity.setBankName(body.bankName());
        entity.setAccountNumber(body.accountNumber());
        entity.setCardType(body.cardType());
        entity.setLinkedAccountId(body.linkedAccountId());
        entity.setProvider(body.provider());
        entity.setMobileNumber(body.mobileNumber());
        entity.setCreditLimit(body.creditLimit());
        entity.setStatementDay(body.statementDay());
        entity.setDueDay(body.dueDay());
        entity.setCreditLimitBehavior(body.creditLimitBehavior() != null ? body.creditLimitBehavior() : "WARN");
        entity.setOpeningBalance(body.openingBalance() != null ? body.openingBalance() : 0.0);
        entity.setStatus(body.status() != null ? body.status() : "ACTIVE");
    }

    private Map<String, Object> toAccountResponse(AccountEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("accountType", entity.getAccountType());
        map.put("bankName", entity.getBankName());
        map.put("accountNickname", entity.getAccountNickname());
        map.put("accountNumber", entity.getAccountNumber());
        map.put("cardType", entity.getCardType());
        map.put("linkedAccountId", entity.getLinkedAccountId());
        map.put("provider", entity.getProvider());
        map.put("mobileNumber", entity.getMobileNumber());
        map.put("creditLimit", entity.getCreditLimit());
        map.put("statementDay", entity.getStatementDay());
        map.put("dueDay", entity.getDueDay());
        map.put("creditLimitBehavior", entity.getCreditLimitBehavior());
        map.put("openingBalance", entity.getOpeningBalance());
        map.put("currentBalance", entity.getCurrentBalance());
        map.put("status", entity.getStatus());
        map.put("createdAt", entity.getCreatedAt().toString());
        map.put("updatedAt", entity.getUpdatedAt().toString());
        if (CreditCardService.isCreditCard(entity)) {
            CreditCardService.CreditCardStats stats = creditCardService.getStats(entity);
            map.put("availableCredit", stats.availableCredit());
            map.put("utilizationPercent", stats.utilizationPercent());
            map.put("minimumPaymentEstimate", stats.minimumPaymentEstimate());
            map.put("daysUntilDue", stats.daysUntilDue());
        }
        return map;
    }

    @GetMapping("/{id}/ledger")
    public ResponseEntity<?> getLedger(HttpServletRequest request, @PathVariable Long id,
                                        @RequestParam(required = false) String startDate,
                                        @RequestParam(required = false) String endDate,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String type,
                                        @RequestParam(required = false) String merchant) {
        Long userId = getUserId(request);
        AccountEntity account = accountRepository.findById(id).orElse(null);
        if (account == null || !account.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Account not found"));
        }
        LocalDate start = parseLocalDate(startDate);
        LocalDate end = parseLocalDate(endDate);
        List<CreditCardService.LedgerEntry> ledger = creditCardService.getLedger(userId, id, start, end, category, type, merchant);
        return ResponseEntity.ok(ledger);
    }

    private LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private record AccountRequest(
            String accountType, String bankName, String accountNickname,
            String accountNumber, String cardType, Long linkedAccountId,
            String provider, String mobileNumber, Double creditLimit,
            Integer statementDay, Integer dueDay, String creditLimitBehavior,
            Double openingBalance, String status, Double currentBalance
    ) {}
}
