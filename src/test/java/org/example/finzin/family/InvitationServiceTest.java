package org.example.finzin.family;

import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdInvitationEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.repository.HouseholdInvitationRepository;
import org.example.finzin.repository.HouseholdMemberRepository;
import org.example.finzin.repository.HouseholdRepository;
import org.example.finzin.repository.UserRepository;
import org.example.finzin.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    private static final Long HOUSEHOLD_ID = 1L;
    private static final Long INVITER_ID = 10L;
    private static final Long INVITEE_ID = 20L;

    @Mock private HouseholdInvitationRepository invitationRepository;
    @Mock private HouseholdMemberRepository memberRepository;
    @Mock private HouseholdRepository householdRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    private InvitationService invitationService;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationService(invitationRepository, memberRepository, householdRepository,
                userRepository, notificationService);
    }

    private HouseholdEntity household() {
        HouseholdEntity h = new HouseholdEntity();
        h.setId(HOUSEHOLD_ID);
        h.setName("The Family");
        return h;
    }

    private UserEntity user(Long id) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setFullName("User " + id);
        return u;
    }

    private HouseholdInvitationEntity invitation(String status) {
        HouseholdInvitationEntity inv = new HouseholdInvitationEntity();
        inv.setId(99L);
        inv.setHouseholdId(HOUSEHOLD_ID);
        inv.setInvitedByUserId(INVITER_ID);
        inv.setInviteeUserId(INVITEE_ID);
        inv.setStatus(status);
        return inv;
    }

    // ===================== requireInvitation =====================

    @Test
    void requireInvitationThrowsNotFoundWhenMissing() {
        when(invitationRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(FamilyException.class, () -> invitationService.requireInvitation(1L));
    }

    // ===================== invite =====================

    @Test
    void inviteRejectsABlankEmailOrUsername() {
        assertThrows(FamilyException.class, () -> invitationService.invite(household(), INVITER_ID, "  ", null));
    }

    @Test
    void inviteRejectsAnUnknownEmailOrUsername() {
        when(userRepository.findByEmailIgnoreCase("nobody@x.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("nobody@x.com")).thenReturn(Optional.empty());

        assertThrows(FamilyException.class, () -> invitationService.invite(household(), INVITER_ID, "nobody@x.com", null));
    }

    @Test
    void inviteRejectsInvitingYourself() {
        UserEntity self = user(INVITER_ID);
        when(userRepository.findByEmailIgnoreCase("me@x.com")).thenReturn(Optional.of(self));

        assertThrows(FamilyException.class, () -> invitationService.invite(household(), INVITER_ID, "me@x.com", null));
    }

    @Test
    void inviteRejectsAnInviteeWhoAlreadyBelongsToAHousehold() {
        UserEntity invitee = user(INVITEE_ID);
        when(userRepository.findByEmailIgnoreCase("them@x.com")).thenReturn(Optional.of(invitee));
        when(memberRepository.findByUserId(INVITEE_ID)).thenReturn(Optional.of(new HouseholdMemberEntity()));

        FamilyException ex = assertThrows(FamilyException.class,
                () -> invitationService.invite(household(), INVITER_ID, "them@x.com", null));
        assertEquals("CONFLICT", ex.getErrorTag());
    }

    @Test
    void inviteRejectsADuplicatePendingInvitationForTheSameInvitee() {
        UserEntity invitee = user(INVITEE_ID);
        when(userRepository.findByEmailIgnoreCase("them@x.com")).thenReturn(Optional.of(invitee));
        when(memberRepository.findByUserId(INVITEE_ID)).thenReturn(Optional.empty());
        when(invitationRepository.findByHouseholdIdAndStatus(HOUSEHOLD_ID, "PENDING"))
                .thenReturn(List.of(invitation("PENDING")));

        FamilyException ex = assertThrows(FamilyException.class,
                () -> invitationService.invite(household(), INVITER_ID, "them@x.com", null));
        assertEquals("CONFLICT", ex.getErrorTag());
    }

    @Test
    void inviteFallsBackToUsernameLookupWhenEmailLookupMisses() {
        UserEntity invitee = user(INVITEE_ID);
        when(userRepository.findByEmailIgnoreCase("bob")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(invitee));
        when(memberRepository.findByUserId(INVITEE_ID)).thenReturn(Optional.empty());
        when(invitationRepository.findByHouseholdIdAndStatus(HOUSEHOLD_ID, "PENDING")).thenReturn(List.of());
        when(invitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HouseholdInvitationEntity saved = invitationService.invite(household(), INVITER_ID, "bob", "Sibling");

        assertEquals(INVITEE_ID, saved.getInviteeUserId());
        assertEquals("Sibling", saved.getRelationshipLabel());
        verify(notificationService).create(eq(INVITEE_ID), eq("HOUSEHOLD_INVITE"), anyString(), anyString(), eq("HOUSEHOLD_INVITATION"), any());
    }

    // ===================== accept =====================

    @Test
    void acceptRejectsAResponseFromSomeoneOtherThanTheInvitee() {
        HouseholdInvitationEntity inv = invitation("PENDING");
        assertThrows(FamilyException.class, () -> invitationService.accept(inv, 999L));
    }

    @Test
    void acceptRejectsAnAlreadyRespondedInvitation() {
        HouseholdInvitationEntity inv = invitation("ACCEPTED");
        FamilyException ex = assertThrows(FamilyException.class, () -> invitationService.accept(inv, INVITEE_ID));
        assertEquals("CONFLICT", ex.getErrorTag());
    }

    @Test
    void acceptRejectsAnInviteeWhoAlreadyJoinedAnotherHouseholdInTheMeantime() {
        HouseholdInvitationEntity inv = invitation("PENDING");
        when(memberRepository.findByUserId(INVITEE_ID)).thenReturn(Optional.of(new HouseholdMemberEntity()));

        assertThrows(FamilyException.class, () -> invitationService.accept(inv, INVITEE_ID));
    }

    @Test
    void acceptCreatesTheMembershipCarriesOverTheRelationshipLabelAndNotifiesTheInviter() {
        HouseholdInvitationEntity inv = invitation("PENDING");
        inv.setRelationshipLabel("Sibling");
        when(memberRepository.findByUserId(INVITEE_ID)).thenReturn(Optional.empty());
        when(invitationRepository.save(inv)).thenReturn(inv);
        when(memberRepository.save(any())).thenAnswer(a -> a.getArgument(0));
        when(householdRepository.findById(HOUSEHOLD_ID)).thenReturn(Optional.of(household()));

        HouseholdMemberEntity saved = invitationService.accept(inv, INVITEE_ID);

        assertEquals("ACCEPTED", inv.getStatus());
        assertNotNull(inv.getRespondedAt());
        assertEquals("MEMBER", saved.getRole());
        assertEquals("Sibling", saved.getRelationshipLabel());
        assertEquals(INVITEE_ID, saved.getUserId());
        verify(notificationService).create(eq(INVITER_ID), eq("HOUSEHOLD_INVITE_ACCEPTED"), anyString(), anyString(), eq("HOUSEHOLD"), eq(HOUSEHOLD_ID));
    }

    // ===================== reject =====================

    @Test
    void rejectRejectsAResponseFromSomeoneOtherThanTheInvitee() {
        HouseholdInvitationEntity inv = invitation("PENDING");
        assertThrows(FamilyException.class, () -> invitationService.reject(inv, 999L));
    }

    @Test
    void rejectRejectsAnAlreadyRespondedInvitation() {
        HouseholdInvitationEntity inv = invitation("REJECTED");
        assertThrows(FamilyException.class, () -> invitationService.reject(inv, INVITEE_ID));
    }

    @Test
    void rejectMarksTheInvitationRejectedAndNotifiesTheInviter() {
        HouseholdInvitationEntity inv = invitation("PENDING");
        lenient().when(invitationRepository.save(inv)).thenReturn(inv);
        when(householdRepository.findById(HOUSEHOLD_ID)).thenReturn(Optional.of(household()));

        invitationService.reject(inv, INVITEE_ID);

        assertEquals("REJECTED", inv.getStatus());
        assertNotNull(inv.getRespondedAt());
        verify(notificationService).create(eq(INVITER_ID), eq("HOUSEHOLD_INVITE_REJECTED"), anyString(), anyString(), eq("HOUSEHOLD"), eq(HOUSEHOLD_ID));
    }

    // ===================== cancel =====================

    @Test
    void cancelRejectsAnInvitationThatIsNoLongerPending() {
        HouseholdInvitationEntity inv = invitation("ACCEPTED");
        assertThrows(FamilyException.class, () -> invitationService.cancel(inv));
    }

    @Test
    void cancelMarksAPendingInvitationCancelled() {
        HouseholdInvitationEntity inv = invitation("PENDING");
        when(invitationRepository.save(inv)).thenReturn(inv);

        invitationService.cancel(inv);

        assertEquals("CANCELLED", inv.getStatus());
        assertNotNull(inv.getRespondedAt());
    }

    // ===================== pendingFor* =====================

    @Test
    void pendingForHouseholdDelegatesToTheRepository() {
        when(invitationRepository.findByHouseholdIdAndStatus(HOUSEHOLD_ID, "PENDING")).thenReturn(List.of(invitation("PENDING")));
        assertEquals(1, invitationService.pendingForHousehold(HOUSEHOLD_ID).size());
    }

    @Test
    void pendingForUserDelegatesToTheRepository() {
        when(invitationRepository.findByInviteeUserIdAndStatus(INVITEE_ID, "PENDING")).thenReturn(List.of(invitation("PENDING")));
        assertEquals(1, invitationService.pendingForUser(INVITEE_ID).size());
    }

    // ===================== toResponse =====================

    @Test
    void toResponseDegradesGracefullyWhenRelatedEntitiesAreMissing() {
        HouseholdInvitationEntity inv = invitation("PENDING");
        when(householdRepository.findById(HOUSEHOLD_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(INVITER_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(INVITEE_ID)).thenReturn(Optional.empty());

        var response = invitationService.toResponse(inv);

        assertEquals(99L, response.id());
        assertEquals(HOUSEHOLD_ID, response.householdId());
        assertEquals("PENDING", response.status());
        assertEquals(null, response.householdName());
    }
}
