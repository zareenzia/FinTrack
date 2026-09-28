package org.example.finzin.service;

import org.example.finzin.entity.UserEntity;
import org.example.finzin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit test for UserDetailsServiceImpl, Spring Security's entry point for loading a
 * user by username or email during authentication.
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock private UserRepository userRepository;

    private UserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserDetailsServiceImpl(userRepository);
    }

    @Test
    void loadUserByUsernameThrowsUsernameNotFoundExceptionWhenNoMatch() {
        when(userRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("ghost"));

        assertEquals("User not found: ghost", ex.getMessage());
    }

    @Test
    void loadUserByUsernameReturnsUserDetailsWrappingEmailPasswordHashAndUserRoleWhenFound() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("nabil");
        user.setEmail("nabil@example.com");
        user.setPasswordHash("bcrypt-hash");
        when(userRepository.findByUsernameOrEmail("nabil", "nabil")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("nabil");

        assertEquals("nabil@example.com", result.getUsername(), "UserDetails.username must be the account's email");
        assertEquals("bcrypt-hash", result.getPassword());
        assertTrue(result.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_USER"::equals));
    }

    @Test
    void loadUserByUsernameLooksUpByEitherUsernameOrEmailUsingTheSameInputForBoth() {
        UserEntity user = new UserEntity();
        user.setId(2L);
        user.setUsername("nabil");
        user.setEmail("nabil@example.com");
        user.setPasswordHash("bcrypt-hash");
        when(userRepository.findByUsernameOrEmail("nabil@example.com", "nabil@example.com")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("nabil@example.com");

        assertEquals("nabil@example.com", result.getUsername());
    }
}
