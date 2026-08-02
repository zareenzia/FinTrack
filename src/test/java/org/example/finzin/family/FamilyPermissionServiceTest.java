package org.example.finzin.family;

import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.repository.HouseholdMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * FamilyPermissionService is the authorization gate every household-scoped endpoint depends on —
 * thorough coverage here since a bug in requireMember/requireAdmin would be a direct privilege
 * escalation or lockout across the entire Family module.
 */
@ExtendWith(MockitoExtension.class)
class FamilyPermissionServiceTest {

    private static final Long HOUSEHOLD_ID = 100L;
    private static final Long USER_ID = 42L;

    @Mock private HouseholdMemberRepository householdMemberRepository;

    private FamilyPermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new FamilyPermissionService(householdMemberRepository);
    }

    private HouseholdMemberEntity member(String role) {
        HouseholdMemberEntity m = new HouseholdMemberEntity();
        m.setHouseholdId(HOUSEHOLD_ID);
        m.setUserId(USER_ID);
        m.setRole(role);
        return m;
    }

    @Test
    void requireMemberReturnsTheMembershipWhenTheUserBelongsToTheHousehold() {
        HouseholdMemberEntity existing = member("MEMBER");
        when(householdMemberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, USER_ID)).thenReturn(Optional.of(existing));

        HouseholdMemberEntity result = permissionService.requireMember(USER_ID, HOUSEHOLD_ID);

        assertSame(existing, result);
    }

    @Test
    void requireMemberThrowsNotMemberWhenTheUserDoesNotBelongToTheHousehold() {
        when(householdMemberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, USER_ID)).thenReturn(Optional.empty());

        FamilyException ex = assertThrows(FamilyException.class, () -> permissionService.requireMember(USER_ID, HOUSEHOLD_ID));

        assertEquals("NOT_MEMBER", ex.getErrorTag());
    }

    @Test
    void requireAdminReturnsTheMembershipWhenTheUserIsAnAdmin() {
        HouseholdMemberEntity admin = member("ADMIN");
        when(householdMemberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, USER_ID)).thenReturn(Optional.of(admin));

        HouseholdMemberEntity result = permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID);

        assertSame(admin, result);
    }

    @Test
    void requireAdminThrowsNotAdminWhenTheUserIsAPlainMember() {
        HouseholdMemberEntity plainMember = member("MEMBER");
        when(householdMemberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, USER_ID)).thenReturn(Optional.of(plainMember));

        FamilyException ex = assertThrows(FamilyException.class, () -> permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID));

        assertEquals("NOT_ADMIN", ex.getErrorTag(), "a non-admin member must be rejected distinctly from a non-member");
    }

    @Test
    void requireAdminThrowsNotMemberRatherThanNotAdminWhenTheUserIsntAMemberAtAll() {
        // requireAdmin delegates to requireMember first — a total stranger to the household must get
        // NOT_MEMBER, not be misreported as merely "not an admin" (which would leak that a household
        // with that id exists and has members).
        when(householdMemberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, USER_ID)).thenReturn(Optional.empty());

        FamilyException ex = assertThrows(FamilyException.class, () -> permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID));

        assertEquals("NOT_MEMBER", ex.getErrorTag());
    }

    @Test
    void requireAdminIsCaseSensitiveAndRejectsALowercaseAdminRoleValue() {
        // Role comparison is a plain .equals("ADMIN") — guards against a future accidental
        // case-insensitive regression that would silently change who counts as an admin.
        HouseholdMemberEntity lowercaseRole = member("admin");
        when(householdMemberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, USER_ID)).thenReturn(Optional.of(lowercaseRole));

        FamilyException ex = assertThrows(FamilyException.class, () -> permissionService.requireAdmin(USER_ID, HOUSEHOLD_ID));

        assertEquals("NOT_ADMIN", ex.getErrorTag());
    }

    @Test
    void requireMemberScopesStrictlyToTheGivenHouseholdId() {
        // A member of household 200 must not be treated as a member of household 100 — verifies the
        // lookup is genuinely (householdId, userId) scoped, not just userId.
        when(householdMemberRepository.findByHouseholdIdAndUserId(HOUSEHOLD_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(FamilyException.class, () -> permissionService.requireMember(USER_ID, HOUSEHOLD_ID));
    }
}
