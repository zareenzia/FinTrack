package org.example.finzin.family;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdInvitationEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.entity.SettlementEntity;
import org.example.finzin.entity.SharedTransactionEntity;
import org.example.finzin.family.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/households")
public class FamilyController {

    private final HouseholdService householdService;
    private final InvitationService invitationService;
    private final SharedExpenseService sharedExpenseService;
    private final SettlementService settlementService;
    private final FamilyPermissionService permissionService;

    public FamilyController(HouseholdService householdService, InvitationService invitationService,
                             SharedExpenseService sharedExpenseService, SettlementService settlementService,
                             FamilyPermissionService permissionService) {
        this.householdService = householdService;
        this.invitationService = invitationService;
        this.sharedExpenseService = sharedExpenseService;
        this.settlementService = settlementService;
        this.permissionService = permissionService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L;
    }

    // ════════════════════════════════════════════════
    // HOUSEHOLD
    // ════════════════════════════════════════════════

    @GetMapping("/mine")
    public ResponseEntity<?> mine(HttpServletRequest request) {
        Long userId = getUserId(request);
        HouseholdMemberEntity membership = householdService.currentMembership(userId);
        if (membership == null) return ResponseEntity.ok(HouseholdResponse.none());
        HouseholdEntity household = householdService.requireHousehold(membership.getHouseholdId());
        return ResponseEntity.ok(householdService.toResponse(household, userId));
    }

    @PostMapping
    public ResponseEntity<?> create(HttpServletRequest request, @RequestBody CreateHouseholdRequest body) {
        Long userId = getUserId(request);
        try {
            HouseholdEntity saved = householdService.create(userId, body != null ? body.name() : null);
            return ResponseEntity.status(HttpStatus.CREATED).body(householdService.toResponse(saved, userId));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(HttpServletRequest request, @PathVariable Long id, @RequestBody UpdateHouseholdRequest body) {
        Long userId = getUserId(request);
        try {
            permissionService.requireAdmin(userId, id);
            HouseholdEntity household = householdService.requireHousehold(id);
            HouseholdEntity saved = householdService.update(household, body != null ? body.name() : null);
            return ResponseEntity.ok(householdService.toResponse(saved, userId));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        try {
            permissionService.requireAdmin(userId, id);
            HouseholdEntity household = householdService.requireHousehold(id);
            householdService.delete(household);
            return ResponseEntity.noContent().build();
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @PostMapping("/{id}/transfer-ownership")
    public ResponseEntity<?> transferOwnership(HttpServletRequest request, @PathVariable Long id, @RequestBody TransferOwnershipRequest body) {
        Long userId = getUserId(request);
        try {
            permissionService.requireAdmin(userId, id);
            HouseholdEntity household = householdService.requireHousehold(id);
            if (body == null || body.newOwnerUserId() == null) return ResponseEntity.badRequest().body(Map.of("error", "newOwnerUserId is required"));
            householdService.transferOwnership(household, body.newOwnerUserId());
            return ResponseEntity.ok(householdService.toResponse(household, userId));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<?> leave(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        try {
            permissionService.requireMember(userId, id);
            HouseholdEntity household = householdService.requireHousehold(id);
            householdService.leave(household, userId);
            return ResponseEntity.noContent().build();
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @DeleteMapping("/{id}/members/{memberUserId}")
    public ResponseEntity<?> removeMember(HttpServletRequest request, @PathVariable Long id, @PathVariable Long memberUserId) {
        Long userId = getUserId(request);
        try {
            permissionService.requireAdmin(userId, id);
            HouseholdEntity household = householdService.requireHousehold(id);
            householdService.removeMember(household, memberUserId);
            return ResponseEntity.noContent().build();
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    // ════════════════════════════════════════════════
    // INVITATIONS
    // ════════════════════════════════════════════════

    @PostMapping("/{id}/invitations")
    public ResponseEntity<?> invite(HttpServletRequest request, @PathVariable Long id, @RequestBody InvitationRequest body) {
        Long userId = getUserId(request);
        try {
            permissionService.requireAdmin(userId, id);
            HouseholdEntity household = householdService.requireHousehold(id);
            HouseholdInvitationEntity saved = invitationService.invite(household, userId,
                    body != null ? body.emailOrUsername() : null, body != null ? body.relationshipLabel() : null);
            return ResponseEntity.status(HttpStatus.CREATED).body(invitationService.toResponse(saved));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @GetMapping("/{id}/invitations")
    public ResponseEntity<?> pendingInvitationsForHousehold(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        try {
            permissionService.requireAdmin(userId, id);
            List<InvitationResponse> result = invitationService.pendingForHousehold(id).stream()
                    .map(invitationService::toResponse).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @GetMapping("/invitations/mine")
    public List<InvitationResponse> pendingInvitationsForMe(HttpServletRequest request) {
        Long userId = getUserId(request);
        return invitationService.pendingForUser(userId).stream().map(invitationService::toResponse).collect(Collectors.toList());
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public ResponseEntity<?> acceptInvitation(HttpServletRequest request, @PathVariable Long invitationId) {
        Long userId = getUserId(request);
        try {
            HouseholdInvitationEntity invitation = invitationService.requireInvitation(invitationId);
            invitationService.accept(invitation, userId);
            HouseholdEntity household = householdService.requireHousehold(invitation.getHouseholdId());
            return ResponseEntity.ok(householdService.toResponse(household, userId));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @PostMapping("/invitations/{invitationId}/reject")
    public ResponseEntity<?> rejectInvitation(HttpServletRequest request, @PathVariable Long invitationId) {
        Long userId = getUserId(request);
        try {
            HouseholdInvitationEntity invitation = invitationService.requireInvitation(invitationId);
            invitationService.reject(invitation, userId);
            return ResponseEntity.noContent().build();
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @DeleteMapping("/invitations/{invitationId}")
    public ResponseEntity<?> cancelInvitation(HttpServletRequest request, @PathVariable Long invitationId) {
        Long userId = getUserId(request);
        try {
            HouseholdInvitationEntity invitation = invitationService.requireInvitation(invitationId);
            permissionService.requireAdmin(userId, invitation.getHouseholdId());
            invitationService.cancel(invitation);
            return ResponseEntity.noContent().build();
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    // ════════════════════════════════════════════════
    // SHARED EXPENSES
    // ════════════════════════════════════════════════

    @PostMapping("/{id}/expenses")
    public ResponseEntity<?> createSharedExpense(HttpServletRequest request, @PathVariable Long id, @RequestBody SharedExpenseRequest body) {
        Long userId = getUserId(request);
        try {
            permissionService.requireMember(userId, id);
            HouseholdEntity household = householdService.requireHousehold(id);
            SharedTransactionEntity saved = sharedExpenseService.createSharedExpense(household, userId, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(sharedExpenseService.toResponse(saved));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @GetMapping("/{id}/expenses")
    public ResponseEntity<?> listSharedExpenses(HttpServletRequest request, @PathVariable Long id,
                                                 @RequestParam(required = false) String month) {
        Long userId = getUserId(request);
        try {
            permissionService.requireMember(userId, id);
            LocalDate start = null;
            LocalDate end = null;
            if (month != null && !month.isBlank()) {
                YearMonth ym = YearMonth.parse(month);
                start = ym.atDay(1);
                end = ym.atEndOfMonth();
            }
            List<SharedExpenseResponse> result = sharedExpenseService.listForHousehold(id, start, end).stream()
                    .map(sharedExpenseService::toResponse).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (FamilyException e) {
            return mapFamilyException(e);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "month must be in yyyy-MM format"));
        }
    }

    @DeleteMapping("/{id}/expenses/{expenseId}")
    public ResponseEntity<?> unshareExpense(HttpServletRequest request, @PathVariable Long id, @PathVariable Long expenseId) {
        Long userId = getUserId(request);
        try {
            HouseholdMemberEntity member = permissionService.requireMember(userId, id);
            SharedTransactionEntity tx = sharedExpenseService.requireSharedTransaction(expenseId, id);
            boolean isAdmin = "ADMIN".equals(member.getRole());
            if (!isAdmin && !tx.getPayerUserId().equals(userId)) {
                return mapFamilyException(FamilyException.notAdmin());
            }
            sharedExpenseService.unshareExpense(tx);
            return ResponseEntity.noContent().build();
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    // ════════════════════════════════════════════════
    // SETTLEMENTS
    // ════════════════════════════════════════════════

    @GetMapping("/{id}/settlements/summary")
    public ResponseEntity<?> settlementSummary(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        try {
            permissionService.requireMember(userId, id);
            HouseholdEntity household = householdService.requireHousehold(id);
            return ResponseEntity.ok(settlementService.computeSummary(household));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @PostMapping("/{id}/settlements")
    public ResponseEntity<?> recordSettlement(HttpServletRequest request, @PathVariable Long id, @RequestBody SettlementRequest body) {
        Long userId = getUserId(request);
        try {
            permissionService.requireMember(userId, id);
            HouseholdEntity household = householdService.requireHousehold(id);
            SettlementEntity saved = settlementService.recordSettlement(household, userId, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(settlementService.toResponse(saved));
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    @GetMapping("/{id}/settlements")
    public ResponseEntity<?> settlementHistory(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        try {
            permissionService.requireMember(userId, id);
            List<SettlementResponse> result = settlementService.history(id).stream()
                    .map(settlementService::toResponse).collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (FamilyException e) {
            return mapFamilyException(e);
        }
    }

    // ════════════════════════════════════════════════
    // DASHBOARD
    // ════════════════════════════════════════════════

    @GetMapping("/dashboard-summary")
    public FamilyDashboardSummaryResponse dashboardSummary(HttpServletRequest request) {
        Long userId = getUserId(request);
        HouseholdMemberEntity membership = householdService.currentMembership(userId);
        if (membership == null) return FamilyDashboardSummaryResponse.none();
        HouseholdEntity household = householdService.requireHousehold(membership.getHouseholdId());
        return settlementService.computeDashboardSummary(household, userId);
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
