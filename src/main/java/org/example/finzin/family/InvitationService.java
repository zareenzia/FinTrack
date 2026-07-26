package org.example.finzin.family;

import org.example.finzin.entity.HouseholdEntity;
import org.example.finzin.entity.HouseholdInvitationEntity;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.example.finzin.entity.UserEntity;
import org.example.finzin.family.dto.InvitationResponse;
import org.example.finzin.repository.HouseholdInvitationRepository;
import org.example.finzin.repository.HouseholdMemberRepository;
import org.example.finzin.repository.HouseholdRepository;
import org.example.finzin.repository.UserRepository;
import org.example.finzin.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvitationService {

    private final HouseholdInvitationRepository invitationRepository;
    private final HouseholdMemberRepository memberRepository;
    private final HouseholdRepository householdRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public InvitationService(HouseholdInvitationRepository invitationRepository, HouseholdMemberRepository memberRepository,
                              HouseholdRepository householdRepository, UserRepository userRepository,
                              NotificationService notificationService) {
        this.invitationRepository = invitationRepository;
        this.memberRepository = memberRepository;
        this.householdRepository = householdRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public HouseholdInvitationEntity requireInvitation(Long invitationId) {
        return invitationRepository.findById(invitationId).orElseThrow(() -> FamilyException.notFound("Invitation"));
    }

    public HouseholdInvitationEntity invite(HouseholdEntity household, Long invitedByUserId, String emailOrUsername, String relationshipLabel) {
        if (emailOrUsername == null || emailOrUsername.isBlank()) throw FamilyException.badRequest("Email or username is required");
        String query = emailOrUsername.trim();
        UserEntity invitee = userRepository.findByEmailIgnoreCase(query)
                .or(() -> userRepository.findByUsernameIgnoreCase(query))
                .orElseThrow(() -> FamilyException.badRequest("No registered user found with that email or username."));
        if (invitee.getId().equals(invitedByUserId)) throw FamilyException.badRequest("You can't invite yourself.");
        if (memberRepository.findByUserId(invitee.getId()).isPresent()) {
            throw FamilyException.conflict("That user already belongs to a household.");
        }
        boolean alreadyPending = invitationRepository.findByHouseholdIdAndStatus(household.getId(), "PENDING").stream()
                .anyMatch(inv -> inv.getInviteeUserId().equals(invitee.getId()));
        if (alreadyPending) throw FamilyException.conflict("There's already a pending invitation for that user.");

        HouseholdInvitationEntity invitation = new HouseholdInvitationEntity();
        invitation.setHouseholdId(household.getId());
        invitation.setInvitedByUserId(invitedByUserId);
        invitation.setInviteeUserId(invitee.getId());
        invitation.setRelationshipLabel(relationshipLabel);
        HouseholdInvitationEntity saved = invitationRepository.save(invitation);

        notificationService.create(invitee.getId(), "HOUSEHOLD_INVITE",
                "Household Invitation",
                "You've been invited to join \"" + household.getName() + "\".",
                "HOUSEHOLD_INVITATION", saved.getId());
        return saved;
    }

    @Transactional
    public HouseholdMemberEntity accept(HouseholdInvitationEntity invitation, Long userId) {
        if (!invitation.getInviteeUserId().equals(userId)) throw FamilyException.notFound("Invitation");
        if (!"PENDING".equals(invitation.getStatus())) {
            throw FamilyException.conflict("This invitation has already been " + invitation.getStatus().toLowerCase() + ".");
        }
        if (memberRepository.findByUserId(userId).isPresent()) throw FamilyException.conflict("You already belong to a household.");

        invitation.setStatus("ACCEPTED");
        invitation.setRespondedAt(LocalDateTime.now());
        invitationRepository.save(invitation);

        HouseholdMemberEntity member = new HouseholdMemberEntity();
        member.setHouseholdId(invitation.getHouseholdId());
        member.setUserId(userId);
        member.setRole("MEMBER");
        member.setRelationshipLabel(invitation.getRelationshipLabel());
        HouseholdMemberEntity saved = memberRepository.save(member);

        HouseholdEntity household = householdRepository.findById(invitation.getHouseholdId()).orElse(null);
        String householdName = household != null ? household.getName() : "the household";
        notificationService.create(invitation.getInvitedByUserId(), "HOUSEHOLD_INVITE_ACCEPTED",
                "Invitation Accepted",
                "Your invitation to join \"" + householdName + "\" was accepted.",
                "HOUSEHOLD", invitation.getHouseholdId());
        return saved;
    }

    public void reject(HouseholdInvitationEntity invitation, Long userId) {
        if (!invitation.getInviteeUserId().equals(userId)) throw FamilyException.notFound("Invitation");
        if (!"PENDING".equals(invitation.getStatus())) {
            throw FamilyException.conflict("This invitation has already been " + invitation.getStatus().toLowerCase() + ".");
        }
        invitation.setStatus("REJECTED");
        invitation.setRespondedAt(LocalDateTime.now());
        invitationRepository.save(invitation);

        HouseholdEntity household = householdRepository.findById(invitation.getHouseholdId()).orElse(null);
        String householdName = household != null ? household.getName() : "the household";
        notificationService.create(invitation.getInvitedByUserId(), "HOUSEHOLD_INVITE_REJECTED",
                "Invitation Declined",
                "Your invitation to join \"" + householdName + "\" was declined.",
                "HOUSEHOLD", invitation.getHouseholdId());
    }

    public void cancel(HouseholdInvitationEntity invitation) {
        if (!"PENDING".equals(invitation.getStatus())) throw FamilyException.conflict("This invitation is no longer pending.");
        invitation.setStatus("CANCELLED");
        invitation.setRespondedAt(LocalDateTime.now());
        invitationRepository.save(invitation);
    }

    public List<HouseholdInvitationEntity> pendingForHousehold(Long householdId) {
        return invitationRepository.findByHouseholdIdAndStatus(householdId, "PENDING");
    }

    public List<HouseholdInvitationEntity> pendingForUser(Long userId) {
        return invitationRepository.findByInviteeUserIdAndStatus(userId, "PENDING");
    }

    public InvitationResponse toResponse(HouseholdInvitationEntity invitation) {
        HouseholdEntity household = householdRepository.findById(invitation.getHouseholdId()).orElse(null);
        UserEntity invitedBy = userRepository.findById(invitation.getInvitedByUserId()).orElse(null);
        UserEntity invitee = userRepository.findById(invitation.getInviteeUserId()).orElse(null);
        return new InvitationResponse(
                invitation.getId(), invitation.getHouseholdId(), household != null ? household.getName() : null,
                invitation.getInvitedByUserId(), invitedBy != null ? invitedBy.getFullName() : null,
                invitation.getInviteeUserId(), invitee != null ? invitee.getFullName() : null,
                invitation.getRelationshipLabel(), invitation.getStatus(),
                invitation.getCreatedAt() != null ? invitation.getCreatedAt().toString() : null,
                invitation.getRespondedAt() != null ? invitation.getRespondedAt().toString() : null
        );
    }
}
