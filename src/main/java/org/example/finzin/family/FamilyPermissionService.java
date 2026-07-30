package org.example.finzin.family;

import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.repository.HouseholdMemberRepository;
import org.springframework.stereotype.Service;

/** Small, centralized gate for every household-scoped endpoint — throws FamilyException (mapped to
 * 403/404 by the controller) rather than returning a boolean, so callers can't forget to check it. */
@Service
public class FamilyPermissionService {

    private final HouseholdMemberRepository householdMemberRepository;

    public FamilyPermissionService(HouseholdMemberRepository householdMemberRepository) {
        this.householdMemberRepository = householdMemberRepository;
    }

    public HouseholdMemberEntity requireMember(Long userId, Long householdId) {
        HouseholdMemberEntity member = householdMemberRepository.findByHouseholdIdAndUserId(householdId, userId).orElse(null);
        if (member == null) throw FamilyException.notMember();
        return member;
    }

    public HouseholdMemberEntity requireAdmin(Long userId, Long householdId) {
        HouseholdMemberEntity member = requireMember(userId, householdId);
        if (!"ADMIN".equals(member.getRole())) throw FamilyException.notAdmin();
        return member;
    }
}
