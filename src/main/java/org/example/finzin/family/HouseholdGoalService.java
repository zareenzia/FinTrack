package org.example.finzin.family;

import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdGoalContributionEntity;
import org.example.finzin.entity.HouseholdGoalEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.family.dto.GoalContributionRequest;
import org.example.finzin.family.dto.HouseholdGoalRequest;
import org.example.finzin.family.dto.HouseholdGoalResponse;
import org.example.finzin.repository.HouseholdGoalContributionRepository;
import org.example.finzin.repository.HouseholdGoalRepository;
import org.example.finzin.repository.HouseholdMemberRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.repository.UserRepository;
import org.example.finzin.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/** A joint household savings goal — multiple members contribute their own real transactions
 * (type="savings") toward one shared target, tracked via an append-only contribution ledger.
 * Progress is always derived live, never cached, matching how personal savings goals work. */
@Service
public class HouseholdGoalService {

    private final HouseholdGoalRepository goalRepository;
    private final HouseholdGoalContributionRepository contributionRepository;
    private final HouseholdMemberRepository memberRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public HouseholdGoalService(HouseholdGoalRepository goalRepository, HouseholdGoalContributionRepository contributionRepository,
                                HouseholdMemberRepository memberRepository, TransactionRepository transactionRepository,
                                UserRepository userRepository, NotificationService notificationService) {
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
        this.memberRepository = memberRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public HouseholdGoalEntity requireGoal(Long id, Long householdId) {
        HouseholdGoalEntity goal = goalRepository.findById(id).orElseThrow(() -> FamilyException.notFound("Household goal"));
        if (!goal.getHouseholdId().equals(householdId)) throw FamilyException.notFound("Household goal");
        return goal;
    }

    public HouseholdGoalEntity create(HouseholdEntity household, Long creatorUserId, HouseholdGoalRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw FamilyException.badRequest("Goal name is required");
        }
        if (request.targetAmount() == null || request.targetAmount() <= 0) {
            throw FamilyException.badRequest("targetAmount must be a positive number");
        }
        HouseholdGoalEntity goal = new HouseholdGoalEntity();
        goal.setHouseholdId(household.getId());
        goal.setName(request.name().trim());
        goal.setTargetAmount(request.targetAmount());
        goal.setTargetDate(parseDate(request.targetDate()));
        goal.setCreatedByUserId(creatorUserId);
        goal.setStatus("ACTIVE");
        return goalRepository.save(goal);
    }

    public HouseholdGoalEntity update(HouseholdGoalEntity goal, HouseholdGoalRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw FamilyException.badRequest("Goal name is required");
        }
        if (request.targetAmount() == null || request.targetAmount() <= 0) {
            throw FamilyException.badRequest("targetAmount must be a positive number");
        }
        goal.setName(request.name().trim());
        goal.setTargetAmount(request.targetAmount());
        goal.setTargetDate(parseDate(request.targetDate()));
        return goalRepository.save(goal);
    }

    public HouseholdGoalEntity archive(HouseholdGoalEntity goal) {
        goal.setStatus("ARCHIVED");
        return goalRepository.save(goal);
    }

    /** Hard-delete is only allowed for a goal that has never received a contribution — deleting one
     * with history would silently destroy other members' financial records. Archive instead. */
    @Transactional
    public void delete(HouseholdGoalEntity goal) {
        if (!contributionRepository.findByHouseholdGoalIdOrderByContributedAtDesc(goal.getId()).isEmpty()) {
            throw FamilyException.conflict("This goal has contributions recorded — archive it instead of deleting it.");
        }
        goalRepository.delete(goal);
    }

    public HouseholdGoalContributionEntity contribute(HouseholdEntity household, HouseholdGoalEntity goal, Long contributorUserId, GoalContributionRequest request) {
        if ("ARCHIVED".equals(goal.getStatus())) throw FamilyException.conflict("This goal is archived and no longer accepts contributions.");
        if (request == null || request.transactionId() == null) throw FamilyException.badRequest("transactionId is required");
        TransactionEntity transaction = transactionRepository.findByIdAndUserId(request.transactionId(), contributorUserId)
                .orElseThrow(() -> FamilyException.badRequest("That transaction doesn't exist or doesn't belong to you."));
        if (!"savings".equalsIgnoreCase(transaction.getTransactionType())) {
            throw FamilyException.badRequest("Only a savings-type transaction can be contributed toward a goal.");
        }
        if (contributionRepository.findByTransactionId(transaction.getId()).isPresent()) {
            throw FamilyException.conflict("This transaction has already been contributed to a goal.");
        }

        HouseholdGoalContributionEntity contribution = new HouseholdGoalContributionEntity();
        contribution.setHouseholdGoalId(goal.getId());
        contribution.setUserId(contributorUserId);
        contribution.setTransactionId(transaction.getId());
        contribution.setAmount(transaction.getAmount());
        contribution.setNote(request.note());
        contribution.setContributedAt(transaction.getDate() != null ? transaction.getDate().toLocalDate() : LocalDate.now());
        HouseholdGoalContributionEntity saved = contributionRepository.save(contribution);

        double totalContributed = contributionRepository.findByHouseholdGoalIdOrderByContributedAtDesc(goal.getId()).stream()
                .mapToDouble(HouseholdGoalContributionEntity::getAmount).sum();
        boolean justAchieved = totalContributed >= goal.getTargetAmount() && !"ACHIEVED".equals(goal.getStatus());
        if (justAchieved) {
            goal.setStatus("ACHIEVED");
            goalRepository.save(goal);
        }

        List<Long> memberIds = memberRepository.findByHouseholdId(household.getId()).stream()
                .map(HouseholdMemberEntity::getUserId).collect(Collectors.toList());
        for (Long memberId : memberIds) {
            if (memberId.equals(contributorUserId)) continue;
            if (justAchieved) {
                notificationService.create(memberId, "HOUSEHOLD_GOAL_ACHIEVED", "Goal Achieved!",
                        "\"" + goal.getName() + "\" reached its target of ৳" + goal.getTargetAmount() + ".",
                        "HOUSEHOLD_GOAL", goal.getId());
            } else {
                notificationService.create(memberId, "HOUSEHOLD_GOAL_CONTRIBUTION", "Goal Contribution Added",
                        transaction.getDescription() + " was contributed to \"" + goal.getName() + "\".",
                        "HOUSEHOLD_GOAL", goal.getId());
            }
        }
        return saved;
    }

    /** Removing a contribution can drop an ACHIEVED goal back below its target — re-evaluate so the
     * status never claims more than the live total actually supports. */
    @Transactional
    public void removeContribution(HouseholdGoalEntity goal, HouseholdGoalContributionEntity contribution) {
        contributionRepository.delete(contribution);
        if ("ACHIEVED".equals(goal.getStatus())) {
            double remainingTotal = contributionRepository.findByHouseholdGoalIdOrderByContributedAtDesc(goal.getId()).stream()
                    .mapToDouble(HouseholdGoalContributionEntity::getAmount).sum();
            if (remainingTotal < goal.getTargetAmount()) {
                goal.setStatus("ACTIVE");
                goalRepository.save(goal);
            }
        }
    }

    public HouseholdGoalContributionEntity requireContribution(Long id, Long goalId) {
        HouseholdGoalContributionEntity contribution = contributionRepository.findById(id)
                .orElseThrow(() -> FamilyException.notFound("Contribution"));
        if (!contribution.getHouseholdGoalId().equals(goalId)) throw FamilyException.notFound("Contribution");
        return contribution;
    }

    public List<HouseholdGoalEntity> listForHousehold(Long householdId) {
        return goalRepository.findByHouseholdIdOrderByCreatedAtDesc(householdId);
    }

    public HouseholdGoalResponse toResponse(HouseholdGoalEntity goal) {
        List<HouseholdGoalContributionEntity> contributions = contributionRepository.findByHouseholdGoalIdOrderByContributedAtDesc(goal.getId());
        double totalContributed = contributions.stream().mapToDouble(HouseholdGoalContributionEntity::getAmount).sum();
        double percentComplete = goal.getTargetAmount() > 0 ? round2(totalContributed / goal.getTargetAmount() * 100.0) : 0.0;
        List<HouseholdGoalResponse.ContributionView> views = contributions.stream().map(c -> {
            UserEntity user = userRepository.findById(c.getUserId()).orElse(null);
            return new HouseholdGoalResponse.ContributionView(c.getId(), c.getUserId(), user != null ? user.getFullName() : null,
                    c.getAmount(), c.getNote(), c.getContributedAt() != null ? c.getContributedAt().toString() : null);
        }).collect(Collectors.toList());
        UserEntity creator = userRepository.findById(goal.getCreatedByUserId()).orElse(null);
        return new HouseholdGoalResponse(goal.getId(), goal.getHouseholdId(), goal.getName(), goal.getTargetAmount(),
                goal.getTargetDate() != null ? goal.getTargetDate().toString() : null, goal.getStatus(),
                round2(totalContributed), percentComplete, views, goal.getCreatedByUserId(),
                creator != null ? creator.getFullName() : null, goal.getCreatedAt() != null ? goal.getCreatedAt().toString() : null);
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw FamilyException.badRequest("targetDate must be in yyyy-MM-dd format");
        }
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
