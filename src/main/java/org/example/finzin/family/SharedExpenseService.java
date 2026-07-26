package org.example.finzin.family;

import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.entity.SharedTransactionEntity;
import org.example.finzin.entity.SharedTransactionShareEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.family.dto.SharedExpenseRequest;
import org.example.finzin.family.dto.SharedExpenseResponse;
import org.example.finzin.repository.HouseholdMemberRepository;
import org.example.finzin.repository.SharedTransactionRepository;
import org.example.finzin.repository.SharedTransactionShareRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.repository.UserRepository;
import org.example.finzin.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Links an already-created personal transaction (created via the normal POST /api/transactions flow)
 * into a household's shared ledger — never creates a transaction itself. */
@Service
public class SharedExpenseService {

    private static final List<String> SPLIT_METHODS = List.of("EQUAL", "PERCENTAGE", "FIXED_AMOUNT");

    private final SharedTransactionRepository sharedTransactionRepository;
    private final SharedTransactionShareRepository shareRepository;
    private final HouseholdMemberRepository memberRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public SharedExpenseService(SharedTransactionRepository sharedTransactionRepository, SharedTransactionShareRepository shareRepository,
                                 HouseholdMemberRepository memberRepository, TransactionRepository transactionRepository,
                                 UserRepository userRepository, NotificationService notificationService) {
        this.sharedTransactionRepository = sharedTransactionRepository;
        this.shareRepository = shareRepository;
        this.memberRepository = memberRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public SharedTransactionEntity requireSharedTransaction(Long id, Long householdId) {
        SharedTransactionEntity tx = sharedTransactionRepository.findById(id).orElseThrow(() -> FamilyException.notFound("Shared expense"));
        if (!tx.getHouseholdId().equals(householdId)) throw FamilyException.notFound("Shared expense");
        return tx;
    }

    @Transactional
    public SharedTransactionEntity createSharedExpense(HouseholdEntity household, Long payerUserId, SharedExpenseRequest request) {
        if (request.transactionId() == null) throw FamilyException.badRequest("transactionId is required");
        TransactionEntity transaction = transactionRepository.findByIdAndUserId(request.transactionId(), payerUserId)
                .orElseThrow(() -> FamilyException.badRequest("That transaction doesn't exist or doesn't belong to you."));
        if (sharedTransactionRepository.findByTransactionId(transaction.getId()).isPresent()) {
            throw FamilyException.conflict("This transaction has already been shared.");
        }
        String splitMethod = request.splitMethod();
        if (splitMethod == null || !SPLIT_METHODS.contains(splitMethod)) {
            throw FamilyException.badRequest("splitMethod must be one of EQUAL, PERCENTAGE, FIXED_AMOUNT");
        }
        List<Long> householdMemberIds = memberRepository.findByHouseholdId(household.getId()).stream()
                .map(HouseholdMemberEntity::getUserId).collect(Collectors.toList());
        if (!householdMemberIds.contains(payerUserId)) throw FamilyException.notMember();

        double totalAmount = transaction.getAmount();
        List<ComputedShare> computedShares = computeShares(splitMethod, totalAmount, request.shares(), householdMemberIds);

        SharedTransactionEntity sharedTransaction = new SharedTransactionEntity();
        sharedTransaction.setHouseholdId(household.getId());
        sharedTransaction.setTransactionId(transaction.getId());
        sharedTransaction.setPayerUserId(payerUserId);
        sharedTransaction.setTotalAmount(totalAmount);
        sharedTransaction.setSplitMethod(splitMethod);
        sharedTransaction.setDescription(transaction.getDescription());
        sharedTransaction.setCategory(transaction.getCategory() != null ? transaction.getCategory().getName() : null);
        LocalDate expenseDate = transaction.getDate() != null ? transaction.getDate().toLocalDate() : LocalDate.now();
        sharedTransaction.setExpenseDate(expenseDate);
        SharedTransactionEntity saved = sharedTransactionRepository.save(sharedTransaction);

        for (ComputedShare cs : computedShares) {
            SharedTransactionShareEntity share = new SharedTransactionShareEntity();
            share.setSharedTransactionId(saved.getId());
            share.setUserId(cs.userId());
            share.setShareAmount(cs.amount());
            share.setSharePercent(cs.percent());
            share.setIsPayer(cs.userId().equals(payerUserId));
            shareRepository.save(share);
        }

        for (Long memberId : householdMemberIds) {
            if (!memberId.equals(payerUserId) && computedShares.stream().anyMatch(cs -> cs.userId().equals(memberId))) {
                notificationService.create(memberId, "HOUSEHOLD_EXPENSE_ADDED",
                        "Shared Expense Added",
                        transaction.getDescription() + " was added to \"" + household.getName() + "\".",
                        "SHARED_TRANSACTION", saved.getId());
            }
        }
        return saved;
    }

    /** Removes the shared-ledger link only — never touches the real personal transaction. */
    @Transactional
    public void unshareExpense(SharedTransactionEntity sharedTransaction) {
        shareRepository.deleteBySharedTransactionId(sharedTransaction.getId());
        sharedTransactionRepository.deleteById(sharedTransaction.getId());
    }

    public List<SharedTransactionEntity> listForHousehold(Long householdId, LocalDate start, LocalDate end) {
        if (start != null && end != null) {
            return sharedTransactionRepository.findByHouseholdIdAndExpenseDateBetween(householdId, start, end);
        }
        return sharedTransactionRepository.findByHouseholdIdOrderByExpenseDateDesc(householdId);
    }

    public SharedExpenseResponse toResponse(SharedTransactionEntity tx) {
        List<SharedTransactionShareEntity> shares = shareRepository.findBySharedTransactionId(tx.getId());
        UserEntity payer = userRepository.findById(tx.getPayerUserId()).orElse(null);
        List<SharedExpenseResponse.ShareView> shareViews = shares.stream().map(s -> {
            UserEntity user = userRepository.findById(s.getUserId()).orElse(null);
            return new SharedExpenseResponse.ShareView(s.getUserId(), user != null ? user.getFullName() : null,
                    s.getShareAmount(), s.getSharePercent(), s.getIsPayer());
        }).collect(Collectors.toList());
        return new SharedExpenseResponse(tx.getId(), tx.getHouseholdId(), tx.getTransactionId(), tx.getPayerUserId(),
                payer != null ? payer.getFullName() : null, tx.getTotalAmount(), tx.getSplitMethod(), tx.getDescription(),
                tx.getCategory(), tx.getExpenseDate() != null ? tx.getExpenseDate().toString() : null, shareViews,
                tx.getCreatedAt() != null ? tx.getCreatedAt().toString() : null);
    }

    private record ComputedShare(Long userId, Double amount, Double percent) {}

    private List<ComputedShare> computeShares(String splitMethod, double totalAmount,
                                               List<SharedExpenseRequest.ShareInput> shareInputs, List<Long> householdMemberIds) {
        if (shareInputs == null || shareInputs.isEmpty()) throw FamilyException.badRequest("At least one contributor is required");
        List<Long> contributorIds = shareInputs.stream().map(SharedExpenseRequest.ShareInput::userId).collect(Collectors.toList());
        for (Long id : contributorIds) {
            if (id == null || !householdMemberIds.contains(id)) throw FamilyException.badRequest("All contributors must be household members");
        }
        Set<Long> distinct = new HashSet<>(contributorIds);
        if (distinct.size() != contributorIds.size()) throw FamilyException.badRequest("Duplicate contributor in shares");

        switch (splitMethod) {
            case "EQUAL": {
                double each = round2(totalAmount / contributorIds.size());
                List<ComputedShare> result = new ArrayList<>();
                double runningTotal = 0;
                for (int i = 0; i < contributorIds.size(); i++) {
                    double amt = (i == contributorIds.size() - 1) ? round2(totalAmount - runningTotal) : each;
                    runningTotal += amt;
                    result.add(new ComputedShare(contributorIds.get(i), amt, null));
                }
                return result;
            }
            case "PERCENTAGE": {
                double percentSum = shareInputs.stream().mapToDouble(s -> s.percent() != null ? s.percent() : 0).sum();
                if (Math.abs(percentSum - 100.0) > 0.5) throw FamilyException.badRequest("Percentages must add up to 100");
                return shareInputs.stream().map(s -> {
                    double percent = s.percent() != null ? s.percent() : 0;
                    return new ComputedShare(s.userId(), round2(totalAmount * percent / 100.0), percent);
                }).collect(Collectors.toList());
            }
            case "FIXED_AMOUNT": {
                double amountSum = shareInputs.stream().mapToDouble(s -> s.amount() != null ? s.amount() : 0).sum();
                if (Math.abs(amountSum - totalAmount) > 1.0) throw FamilyException.badRequest("Fixed amounts must add up to the total expense amount");
                return shareInputs.stream()
                        .map(s -> new ComputedShare(s.userId(), round2(s.amount() != null ? s.amount() : 0), null))
                        .collect(Collectors.toList());
            }
            default:
                throw FamilyException.badRequest("Unsupported split method");
        }
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
