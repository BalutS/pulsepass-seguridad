package com.pulsepass.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pulsepass.domain.enums.Role;
import com.pulsepass.domain.model.User;
import com.pulsepass.domain.model.UserCredential;
import com.pulsepass.repository.UserCredentialRepository;
import com.pulsepass.repository.UserRepository;
import com.pulsepass.repository.UserRoleRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    private static final String HASH = "$2a$10$hash-ficticio-solo-para-tests";

    @Mock private UserRepository userRepository;
    @Mock private UserCredentialRepository credentialRepository;
    @Mock private UserRoleRepository roleRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userRepository, credentialRepository, roleRepository);
    }

    private static User user(long id, String username, boolean active) {
        User user = new User(username, username + "@email.com");
        ReflectionTestUtils.setField(user, "id", id);
        user.setActive(active);
        return user;
    }

    private void givenCredentialAndRoles(User user, Role... roles) {
        when(credentialRepository.findById(user.getId()))
                .thenReturn(Optional.of(new UserCredential(user, HASH, LocalDateTime.of(2026, 10, 1, 8, 0))));
        when(roleRepository.findRolesByUserId(user.getId())).thenReturn(List.of(roles));
    }

    @Test
    void loadsByUsername_withIdHashAndRoleAuthorities() {
        User andrea = user(7L, "andrea", true);
        when(userRepository.findByUsername("andrea")).thenReturn(Optional.of(andrea));
        givenCredentialAndRoles(andrea, Role.USER, Role.STAFF);

        SecurityPrincipal principal = service.loadUserByUsername("andrea");

        assertThat(principal.getUserId()).isEqualTo(7L);
        assertThat(principal.getUsername()).isEqualTo("andrea");
        assertThat(principal.getPassword()).isEqualTo(HASH);
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_STAFF");
    }

    @Test
    void loadsByEmail_whenIdentifierIsNotAUsername() {
        User andrea = user(7L, "andrea", true);
        when(userRepository.findByUsername("andrea@email.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("andrea@email.com")).thenReturn(Optional.of(andrea));
        givenCredentialAndRoles(andrea, Role.USER);

        assertThat(service.loadUserByUsername("  andrea@email.com ").getUserId()).isEqualTo(7L);
    }

    @Test
    void unknownIdentifier_throwsUsernameNotFound() {
        when(userRepository.findByUsername("nadie")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nadie"))
                .isInstanceOf(UsernameNotFoundException.class);
        verifyNoInteractions(credentialRepository, roleRepository);
    }

    @Test
    void legacyUserWithoutCredential_cannotAuthenticate() {
        User legacy = user(3L, "legacy", true);
        when(userRepository.findByUsername("legacy")).thenReturn(Optional.of(legacy));
        when(credentialRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("legacy"))
                .isInstanceOf(UsernameNotFoundException.class);
        verifyNoInteractions(roleRepository);
    }

    @Test
    void inactiveUser_isReportedAsDisabled() {
        User inactive = user(8L, "carlos", false);
        when(userRepository.findByUsername("carlos")).thenReturn(Optional.of(inactive));
        givenCredentialAndRoles(inactive, Role.USER);

        assertThat(service.loadUserByUsername("carlos").isEnabled()).isFalse();
    }

    @Test
    void userWithoutRoles_hasNoAuthorities() {
        User andrea = user(7L, "andrea", true);
        when(userRepository.findByUsername("andrea")).thenReturn(Optional.of(andrea));
        givenCredentialAndRoles(andrea);

        assertThat(service.loadUserByUsername("andrea").getAuthorities()).isEmpty();
    }

    @Test
    void rolesAreReadFromPersistenceOnEveryCall_revokedRoleDisappears() {
        User laura = user(9L, "laura", true);
        when(userRepository.findByUsername("laura")).thenReturn(Optional.of(laura));
        when(credentialRepository.findById(9L))
                .thenReturn(Optional.of(new UserCredential(laura, HASH, LocalDateTime.now())));
        when(roleRepository.findRolesByUserId(9L))
                .thenReturn(List.of(Role.USER, Role.STAFF))
                .thenReturn(List.of(Role.USER));

        assertThat(service.loadUserByUsername("laura").getAuthorities()).hasSize(2);
        assertThat(service.loadUserByUsername("laura").getAuthorities()).hasSize(1);
    }

    @Test
    void loadPrincipalById_returnsTheSamePrincipal() {
        User andrea = user(7L, "andrea", true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(andrea));
        givenCredentialAndRoles(andrea, Role.ADMIN);

        SecurityPrincipal principal = service.loadPrincipalById(7L);

        assertThat(principal.getUserId()).isEqualTo(7L);
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadPrincipalById_unknownId_throwsUsernameNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadPrincipalById(99L))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void principalErasesTheHashAfterAuthentication_andNeverPrintsIt() {
        SecurityPrincipal principal = new SecurityPrincipal(1L, "andrea", HASH, List.of(), true);

        assertThat(principal.toString()).doesNotContain(HASH);
        principal.eraseCredentials();
        assertThat(principal.getPassword()).isNull();
    }
}
