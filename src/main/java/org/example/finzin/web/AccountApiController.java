package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.AccountEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public AccountApiController(AccountRepository accountRepository, TransactionRepository transactionRepository, DocumentIndexer documentIndexer) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.documentIndexer = documentIndexer;
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
        double balanceDiff = (body.openingBalance() != null ? body.openingBalance() : entity.getOpeningBalance()) - entity.getOpeningBalance();
        mapRequestToEntity(body, entity);
        entity.setCurrentBalance(entity.getCurrentBalance() + balanceDiff);
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
        map.put("openingBalance", entity.getOpeningBalance());
        map.put("currentBalance", entity.getCurrentBalance());
        map.put("status", entity.getStatus());
        map.put("createdAt", entity.getCreatedAt().toString());
        map.put("updatedAt", entity.getUpdatedAt().toString());
        return map;
    }

    private record AccountRequest(
            String accountType, String bankName, String accountNickname,
            String accountNumber, String cardType, Long linkedAccountId,
            String provider, String mobileNumber, Double creditLimit,
            Integer statementDay, Integer dueDay, Double openingBalance, String status
    ) {}
}
