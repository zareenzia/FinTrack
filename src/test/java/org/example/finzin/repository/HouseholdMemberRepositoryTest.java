package org.example.finzin.repository;

import org.example.finzin.AbstractIntegrationTest;
import org.example.finzin.entity.HouseholdMemberEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HouseholdMemberRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HouseholdMemberRepository repository;

    private HouseholdMemberEntity member(Long householdId, Long userId, String role) {
        HouseholdMemberEntity m = new HouseholdMemberEntity();
        m.setHouseholdId(householdId);
        m.setUserId(userId);
        m.setRole(role);
        return m;
    }

    @Test
    void findByHouseholdIdReturnsAllMembersOfHousehold() {
        entityManager.persistAndFlush(member(1L, 10L, "ADMIN"));
        entityManager.persistAndFlush(member(1L, 11L, "MEMBER"));
        entityManager.persistAndFlush(member(2L, 20L, "ADMIN"));
        entityManager.clear();

        List<HouseholdMemberEntity> result = repository.findByHouseholdId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void findByHouseholdIdReturnsEmptyWhenNoMembers() {
        assertTrue(repository.findByHouseholdId(999L).isEmpty());
    }

    @Test
    void findByHouseholdIdOrderByJoinedAtAscOrdersOldestFirst() throws InterruptedException {
        HouseholdMemberEntity first = entityManager.persistFlushFind(member(1L, 10L, "ADMIN"));
        Thread.sleep(5);
        HouseholdMemberEntity second = entityManager.persistFlushFind(member(1L, 11L, "MEMBER"));
        entityManager.clear();

        List<HouseholdMemberEntity> result = repository.findByHouseholdIdOrderByJoinedAtAsc(1L);

        assertEquals(2, result.size());
        assertEquals(first.getId(), result.get(0).getId(), "the earliest-joined member must come first");
        assertEquals(second.getId(), result.get(1).getId());
    }

    @Test
    void findByUserIdReturnsMatchingMembership() {
        entityManager.persistAndFlush(member(1L, 10L, "ADMIN"));
        entityManager.clear();

        Optional<HouseholdMemberEntity> result = repository.findByUserId(10L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getHouseholdId());
    }

    @Test
    void findByUserIdReturnsEmptyWhenNotFound() {
        assertTrue(repository.findByUserId(999L).isEmpty());
    }

    @Test
    void findByHouseholdIdAndUserIdReturnsMatchingMembership() {
        entityManager.persistAndFlush(member(1L, 10L, "ADMIN"));
        entityManager.clear();

        Optional<HouseholdMemberEntity> result = repository.findByHouseholdIdAndUserId(1L, 10L);

        assertTrue(result.isPresent());
        assertEquals("ADMIN", result.get().getRole());
    }

    @Test
    void findByHouseholdIdAndUserIdReturnsEmptyWhenNoMatch() {
        entityManager.persistAndFlush(member(1L, 10L, "ADMIN"));
        entityManager.clear();

        assertTrue(repository.findByHouseholdIdAndUserId(1L, 999L).isEmpty());
        assertTrue(repository.findByHouseholdIdAndUserId(999L, 10L).isEmpty());
    }

    @Test
    void deleteByHouseholdIdRemovesOnlyThatHouseholdsMembers() {
        HouseholdMemberEntity m1 = entityManager.persistAndFlush(member(1L, 10L, "ADMIN"));
        HouseholdMemberEntity m2 = entityManager.persistAndFlush(member(2L, 20L, "ADMIN"));
        entityManager.clear();

        repository.deleteByHouseholdId(1L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(m1.getId()).isEmpty());
        assertTrue(repository.findById(m2.getId()).isPresent());
    }

    @Test
    void deleteByUserIdRemovesOnlyThatUsersMemberships() {
        HouseholdMemberEntity m1 = entityManager.persistAndFlush(member(1L, 10L, "ADMIN"));
        HouseholdMemberEntity m2 = entityManager.persistAndFlush(member(2L, 20L, "ADMIN"));
        entityManager.clear();

        repository.deleteByUserId(10L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(repository.findById(m1.getId()).isEmpty());
        assertTrue(repository.findById(m2.getId()).isPresent());
    }
}
