package org.example.finzin.family;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdGoalContributionEntity;
import org.example.finzin.entity.HouseholdGoalEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.family.dto.GoalContributionRequest;
import org.example.finzin.family.dto.HouseholdGoalRequest;
import org.example.finzin.family.dto.HouseholdGoalResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/households/{id}/goals")
public class HouseholdGoalController {

    private final HouseholdGoalService goalService;
    private final HouseholdService householdService;
    private final FamilyPermissionService permissionService;

    public HouseholdGoalController(HouseholdGoalService goalService, HouseholdService householdService,
                                    FamilyPermissionService permissionService) {
        this.goalService = goalService;
        this.householdService = householdService;
        this.permissionService = permissionService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return (Long) userId;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        try {
            permissionService.requireMember(userId, id);
            List<HouseholdGoalResponse> result = goalService.listForHousehold(id).stream()
                    .map(goalService::toResponse).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @PostMapping
    public ResponseEntity<?> create(HttpServletRequest request, @PathVariable Long id, @RequestBody HouseholdGoalRequest body) {
        Long userId = getUserId(request);
        try {
            permissionService.requireMember(userId, id);
            HouseholdEntity household = householdService.requireHousehold(id);
            HouseholdGoalEntity saved = goalService.create(household, userId, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(goalService.toResponse(saved));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @PutMapping("/{goalId}")
    public ResponseEntity<?> update(HttpServletRequest request, @PathVariable Long id, @PathVariable Long goalId,
                                     @RequestBody HouseholdGoalRequest body) {
        Long userId = getUserId(request);
        try {
            HouseholdMemberEntity member = permissionService.requireMember(userId, id);
            HouseholdGoalEntity goal = goalService.requireGoal(goalId, id);
            requireCreatorOrAdmin(member, goal, userId);
            HouseholdGoalEntity saved = goalService.update(goal, body);
            return ResponseEntity.ok(goalService.toResponse(saved));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @PatchMapping("/{goalId}/archive")
    public ResponseEntity<?> archive(HttpServletRequest request, @PathVariable Long id, @PathVariable Long goalId) {
        Long userId = getUserId(request);
        try {
            HouseholdMemberEntity member = permissionService.requireMember(userId, id);
            HouseholdGoalEntity goal = goalService.requireGoal(goalId, id);
            requireCreatorOrAdmin(member, goal, userId);
            HouseholdGoalEntity saved = goalService.archive(goal);
            return ResponseEntity.ok(goalService.toResponse(saved));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @DeleteMapping("/{goalId}")
    public ResponseEntity<?> delete(HttpServletRequest request, @PathVariable Long id, @PathVariable Long goalId) {
        Long userId = getUserId(request);
        try {
            HouseholdMemberEntity member = permissionService.requireMember(userId, id);
            HouseholdGoalEntity goal = goalService.requireGoal(goalId, id);
            requireCreatorOrAdmin(member, goal, userId);
            goalService.delete(goal);
            return ResponseEntity.noContent().build();
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @PostMapping("/{goalId}/contributions")
    public ResponseEntity<?> contribute(HttpServletRequest request, @PathVariable Long id, @PathVariable Long goalId,
                                        @RequestBody GoalContributionRequest body) {
        Long userId = getUserId(request);
        try {
            permissionService.requireMember(userId, id);
            HouseholdEntity household = householdService.requireHousehold(id);
            HouseholdGoalEntity goal = goalService.requireGoal(goalId, id);
            goalService.contribute(household, goal, userId, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(goalService.toResponse(goal));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @DeleteMapping("/{goalId}/contributions/{contributionId}")
    public ResponseEntity<?> removeContribution(HttpServletRequest request, @PathVariable Long id, @PathVariable Long goalId,
                                                 @PathVariable Long contributionId) {
        Long userId = getUserId(request);
        try {
            HouseholdMemberEntity member = permissionService.requireMember(userId, id);
            HouseholdGoalEntity goal = goalService.requireGoal(goalId, id);
            HouseholdGoalContributionEntity contribution = goalService.requireContribution(contributionId, goalId);
            boolean isAdmin = "ADMIN".equals(member.getRole());
            if (!isAdmin && !contribution.getUserId().equals(userId)) {
                return mapFamilyException(FamilyException.notAdmin());
            }
            goalService.removeContribution(goal, contribution);
            return ResponseEntity.noContent().build();
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    private void requireCreatorOrAdmin(HouseholdMemberEntity member, HouseholdGoalEntity goal, Long userId) {
        boolean isAdmin = "ADMIN".equals(member.getRole());
        if (!isAdmin && !goal.getCreatedByUserId().equals(userId)) {
            throw FamilyException.notAdmin();
        }
    }

    private ResponseEntity<?> mapFamilyException(FamilyException e) {
        HttpStatus status = switch (e.getErrorTag()) {
            case "NOT_MEMBER", "NOT_ADMIN" -> HttpStatus.FORBIDDEN;
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("error", e.getUserMessage()));
    }
}
