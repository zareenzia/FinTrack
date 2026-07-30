package org.example.finzin.family;

import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.entity.SettlementEntity;
import org.example.finzin.entity.SharedTransactionEntity;
import org.example.finzin.entity.SharedTransactionShareEntity;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.family.dto.FamilyDashboardSummaryResponse;
import org.example.finzin.family.dto.MemberBalance;
import org.example.finzin.family.dto.SettlementRequest;
import org.example.finzin.family.dto.SettlementResponse;
import org.example.finzin.family.dto.SettlementSummaryResponse;
import org.example.finzin.family.dto.SuggestedTransfer;
import org.example.finzin.repository.HouseholdMemberRepository;
import org.example.finzin.repository.SettlementRepository;
import org.example.finzin.repository.SharedTransactionRepository;
import org.example.finzin.repository.SharedTransactionShareRepository;
import org.example.finzin.repository.UserRepository;
import org.example.finzin.service.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Computes who-owes-whom for a household (cumulative, all-time — a settlement recorded in any
 * period nets against the running balance, so balances are never scoped to "this month" alone) and
 * records actual settlement ledger entries. */
@Service
public class SettlementService {

    private final SharedTransactionRepository sharedTransactionRepository;
    private final SharedTransactionShareRepository shareRepository;
    private final SettlementRepository settlementRepository;
    private final HouseholdMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public SettlementService(SharedTransactionRepository sharedTransactionRepository, SharedTransactionShareRepository shareRepository,
                              SettlementRepository settlementRepository, HouseholdMemberRepository memberRepository,
                              UserRepository userRepository, NotificationService notificationService) {
        this.sharedTransactionRepository = sharedTransactionRepository;
        this.shareRepository = shareRepository;
        this.settlementRepository = settlementRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public SettlementSummaryResponse computeSummary(HouseholdEntity household) {
        List<HouseholdMemberEntity> members = memberRepository.findByHouseholdId(household.getId());
        List<SharedTransactionEntity> allShared = sharedTransactionRepository.findByHouseholdIdOrderByExpenseDateDesc(household.getId());
        List<Long> sharedTxIds = allShared.stream().map(SharedTransactionEntity::getId).collect(Collectors.toList());
        List<SharedTransactionShareEntity> allShares = sharedTxIds.isEmpty() ? List.of() : shareRepository.findBySharedTransactionIdIn(sharedTxIds);
        List<SettlementEntity> allSettlements = settlementRepository.findByHouseholdIdOrderBySettledAtDesc(household.getId());

        Map<Long, Double> paidMap = new HashMap<>();
        for (SharedTransactionEntity tx : allShared) paidMap.merge(tx.getPayerUserId(), tx.getTotalAmount(), Double::sum);

        Map<Long, Double> fairShareMap = new HashMap<>();
        for (SharedTransactionShareEntity share : allShares) fairShareMap.merge(share.getUserId(), share.getShareAmount(), Double::sum);

        // A recorded settlement (from -> to, amount) moves both parties' balances toward zero:
        // it reduces what "from" still owes, and reduces what "to" is still owed.
        Map<Long, Double> settlementAdjustment = new HashMap<>();
        for (SettlementEntity s : allSettlements) {
            settlementAdjustment.merge(s.getFromUserId(), s.getAmount(), Double::sum);
            settlementAdjustment.merge(s.getToUserId(), -s.getAmount(), Double::sum);
        }

        List<MemberBalance> balances = new ArrayList<>();
        Map<Long, Double> netBalanceByUser = new HashMap<>();
        for (HouseholdMemberEntity member : members) {
            Long userId = member.getUserId();
            double paid = paidMap.getOrDefault(userId, 0.0);
            double fairShare = fairShareMap.getOrDefault(userId, 0.0);
            double net = round2(paid - fairShare + settlementAdjustment.getOrDefault(userId, 0.0));
            netBalanceByUser.put(userId, net);
            UserEntity user = userRepository.findById(userId).orElse(null);
            balances.add(new MemberBalance(userId, user != null ? user.getFullName() : null, round2(paid), round2(fairShare), net));
        }

        List<SuggestedTransfer> transfers = simplifyDebts(netBalanceByUser);
        String periodStart = household.getCreatedAt() != null ? household.getCreatedAt().toLocalDate().toString() : null;
        return new SettlementSummaryResponse(periodStart, LocalDate.now().toString(), balances, transfers);
    }

    /** Greedy debt-simplification: repeatedly match the largest creditor with the largest debtor,
     * producing the minimal set of transfers that zero out every balance. Trivial for 2 members,
     * generalizes cleanly to N. */
    private List<SuggestedTransfer> simplifyDebts(Map<Long, Double> netBalanceByUser) {
        List<Map.Entry<Long, Double>> creditors = new ArrayList<>();
        List<Map.Entry<Long, Double>> debtors = new ArrayList<>();
        for (Map.Entry<Long, Double> e : netBalanceByUser.entrySet()) {
            if (e.getValue() > 0.01) creditors.add(new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
            else if (e.getValue() < -0.01) debtors.add(new AbstractMap.SimpleEntry<>(e.getKey(), -e.getValue()));
        }
        creditors.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        debtors.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Double> creditorRemaining = creditors.stream().map(Map.Entry::getValue).collect(Collectors.toCollection(ArrayList::new));
        List<Double> debtorRemaining = debtors.stream().map(Map.Entry::getValue).collect(Collectors.toCollection(ArrayList::new));

        List<SuggestedTransfer> result = new ArrayList<>();
        int ci = 0, di = 0;
        while (ci < creditors.size() && di < debtors.size()) {
            double amount = round2(Math.min(creditorRemaining.get(ci), debtorRemaining.get(di)));
            if (amount > 0.01) {
                Long fromUserId = debtors.get(di).getKey();
                Long toUserId = creditors.get(ci).getKey();
                UserEntity fromUser = userRepository.findById(fromUserId).orElse(null);
                UserEntity toUser = userRepository.findById(toUserId).orElse(null);
                result.add(new SuggestedTransfer(fromUserId, fromUser != null ? fromUser.getFullName() : null,
                        toUserId, toUser != null ? toUser.getFullName() : null, amount));
            }
            creditorRemaining.set(ci, round2(creditorRemaining.get(ci) - amount));
            debtorRemaining.set(di, round2(debtorRemaining.get(di) - amount));
            if (creditorRemaining.get(ci) <= 0.01) ci++;
            if (debtorRemaining.get(di) <= 0.01) di++;
        }
        return result;
    }

    public SettlementEntity recordSettlement(HouseholdEntity household, Long fromUserId, SettlementRequest request) {
        if (request.toUserId() == null) throw FamilyException.badRequest("toUserId is required");
        if (request.amount() == null || request.amount() <= 0) throw FamilyException.badRequest("amount must be a positive number");
        if (request.toUserId().equals(fromUserId)) throw FamilyException.badRequest("You can't settle with yourself.");
        memberRepository.findByHouseholdIdAndUserId(household.getId(), request.toUserId())
                .orElseThrow(() -> FamilyException.badRequest("That user is not a member of this household."));

        SettlementEntity settlement = new SettlementEntity();
        settlement.setHouseholdId(household.getId());
        settlement.setFromUserId(fromUserId);
        settlement.setToUserId(request.toUserId());
        settlement.setAmount(request.amount());
        settlement.setNote(request.note());
        SettlementEntity saved = settlementRepository.save(settlement);

        notificationService.create(request.toUserId(), "HOUSEHOLD_SETTLEMENT_RECORDED",
                "Settlement Recorded",
                "A settlement of ৳" + saved.getAmount() + " was recorded in \"" + household.getName() + "\".",
                "SETTLEMENT", saved.getId());
        return saved;
    }

    public List<SettlementEntity> history(Long householdId) {
        return settlementRepository.findByHouseholdIdOrderBySettledAtDesc(householdId);
    }

    public SettlementResponse toResponse(SettlementEntity s) {
        UserEntity from = userRepository.findById(s.getFromUserId()).orElse(null);
        UserEntity to = userRepository.findById(s.getToUserId()).orElse(null);
        return new SettlementResponse(s.getId(), s.getHouseholdId(), s.getFromUserId(), from != null ? from.getFullName() : null,
                s.getToUserId(), to != null ? to.getFullName() : null, s.getAmount(), s.getNote(),
                s.getSettledAt() != null ? s.getSettledAt().toString() : null);
    }

    public FamilyDashboardSummaryResponse computeDashboardSummary(HouseholdEntity household, Long userId) {
        YearMonth currentMonth = YearMonth.now();
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();
        List<SharedTransactionEntity> thisMonth = sharedTransactionRepository
                .findByHouseholdIdAndExpenseDateBetween(household.getId(), start, end);
        double thisMonthTotal = thisMonth.stream().mapToDouble(SharedTransactionEntity::getTotalAmount).sum();
        int memberCount = memberRepository.findByHouseholdId(household.getId()).size();

        SettlementSummaryResponse summary = computeSummary(household);
        double myBalance = summary.balances().stream()
                .filter(b -> b.userId().equals(userId))
                .map(MemberBalance::netBalance)
                .findFirst().orElse(0.0);

        return new FamilyDashboardSummaryResponse(true, household.getName(), memberCount,
                round2(thisMonthTotal), round2(myBalance), thisMonth.size());
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
