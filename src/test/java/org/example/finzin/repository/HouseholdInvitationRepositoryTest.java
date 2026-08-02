package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.HouseholdInvitationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HouseholdInvitationRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HouseholdInvitationRepository repository;

    private HouseholdInvitationEntity invitation(Long householdId, Long invitedByUserId, Long inviteeUserId, String status) {
        HouseholdInvitationEntity inv = new HouseholdInvitationEntity();
        inv.setHouseholdId(householdId);
        inv.setInvitedByUserId(invitedByUserId);
        inv.setInviteeUserId(inviteeUserId);
        inv.setStatus(status);
        return inv;
    }

    @Test
    void findByHouseholdIdReturnsAllInvitationsForHousehold() {
        entityManager.persistAndFlush(invitation(1L, 100L, 200L, "PENDING"));
        entityManager.persistAndFlush(invitation(1L, 100L, 201L, "ACCEPTED"));
        entityManager.persistAndFlush(invitation(2L, 300L, 400L, "PENDING"));
        entityManager.clear();

        List<HouseholdInvitationEntity> result = repository.findByHouseholdId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findByHouseholdIdReturnsEmptyWhenNoneForHousehold() {
        assertTrue(repository.findByHouseholdId(999L).isEmpty());
    }

    @Test
    void findByHouseholdIdAndStatusFiltersByStatus() {
        entityManager.persistAndFlush(invitation(1L, 100L, 200L, "PENDING"));
        entityManager.persistAndFlush(invitation(1L, 100L, 201L, "ACCEPTED"));
        entityManager.clear();

        List<HouseholdInvitationEntity> result = repository.findByHouseholdIdAndStatus(1L, "PENDING");

        assertEquals(1, result.size());
        assertEquals(200L, result.get(0).getInviteeUserId());
    }

    @Test
    void findByHouseholdIdAndStatusReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(invitation(1L, 100L, 200L, "PENDING"));
        entityManager.clear();

        assertTrue(repository.findByHouseholdIdAndStatus(1L, "REJECTED").isEmpty());
    }

    @Test
    void findByInviteeUserIdAndStatusFiltersByStatus() {
        entityManager.persistAndFlush(invitation(1L, 100L, 200L, "PENDING"));
        entityManager.persistAndFlush(invitation(2L, 300L, 200L, "ACCEPTED"));
        entityManager.clear();

        List<HouseholdInvitationEntity> result = repository.findByInviteeUserIdAndStatus(200L, "PENDING");

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getHouseholdId());
    }

    @Test
    void findByInviteeUserIdAndStatusReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(invitation(1L, 100L, 200L, "PENDING"));
        entityManager.clear();

        assertTrue(repository.findByInviteeUserIdAndStatus(200L, "ACCEPTED").isEmpty());
    }

    @Test
    void findByInvitedByUserIdReturnsMatchingInvitations() {
        entityManager.persistAndFlush(invitation(1L, 100L, 200L, "PENDING"));
        entityManager.persistAndFlush(invitation(1L, 999L, 201L, "PENDING"));
        entityManager.clear();

        List<HouseholdInvitationEntity> result = repository.findByInvitedByUserId(100L);

        assertEquals(1, result.size());
        assertEquals(200L, result.get(0).getInviteeUserId());
    }

    @Test
    void findByInvitedByUserIdReturnsEmptyWhenNoneMatch() {
        assertTrue(repository.findByInvitedByUserId(999L).isEmpty());
    }

    @Test
    void findByInviteeUserIdReturnsMatchingInvitations() {
        entityManager.persistAndFlush(invitation(1L, 100L, 200L, "PENDING"));
        entityManager.persistAndFlush(invitation(1L, 100L, 999L, "PENDING"));
        entityManager.clear();

        List<HouseholdInvitationEntity> result = repository.findByInviteeUserId(200L);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getInvitedByUserId());
    }

    @Test
    void findByInviteeUserIdReturnsEmptyWhenNoneMatch() {
        assertTrue(repository.findByInviteeUserId(999L).isEmpty());
    }

    @Test
    void deleteByHouseholdIdRemovesOnlyThatHouseholdsInvitations() {
        HouseholdInvitationEntity i1 = entityManager.persistAndFlush(invitation(1L, 100L, 200L, "PENDING"));
        HouseholdInvitationEntity i2 = entityManager.persistAndFlush(invitation(2L, 300L, 400L, "PENDING"));
        entityManager.clear();

        repository.deleteByHouseholdId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(i1.getId()).isEmpty());
        assertTrue(repository.findById(i2.getId()).isPresent());
    }

    @Test
    void deleteByInvitedByUserIdRemovesOnlyThatInvitersInvitations() {
        HouseholdInvitationEntity i1 = entityManager.persistAndFlush(invitation(1L, 100L, 200L, "PENDING"));
        HouseholdInvitationEntity i2 = entityManager.persistAndFlush(invitation(1L, 999L, 201L, "PENDING"));
        entityManager.clear();

        repository.deleteByInvitedByUserId(100L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(i1.getId()).isEmpty());
        assertTrue(repository.findById(i2.getId()).isPresent());
    }

    @Test
    void deleteByInviteeUserIdRemovesOnlyThatInviteesInvitations() {
        HouseholdInvitationEntity i1 = entityManager.persistAndFlush(invitation(1L, 100L, 200L, "PENDING"));
        HouseholdInvitationEntity i2 = entityManager.persistAndFlush(invitation(1L, 100L, 999L, "PENDING"));
        entityManager.clear();

        repository.deleteByInviteeUserId(200L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(i1.getId()).isEmpty());
        assertTrue(repository.findById(i2.getId()).isPresent());
    }
}
