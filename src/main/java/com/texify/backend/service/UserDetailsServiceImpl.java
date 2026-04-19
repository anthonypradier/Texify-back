package com.texify.backend.service;

import com.texify.backend.entity.User;
import com.texify.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security {@link UserDetailsService} implementation backed by the database.
 * <p>
 * Loads a {@link User} entity by email and converts it into a transient
 * {@link UserDetails} object understood by Spring Security. Keeping this
 * conversion here — rather than on the entity itself — preserves a clean
 * separation between the domain model and the security framework.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a {@link UserDetails} for the given email address.
     * <p>
     * The email is used as the Spring Security "username". The resulting
     * {@link UserDetails} is a transient Spring Security value object built from
     * the database entity; it is not persisted and does not carry entity state.
     * </p>
     *
     * @param email the email address submitted during authentication
     * @return a {@link UserDetails} built from the matching {@link User} entity
     * @throws UsernameNotFoundException if no account exists for the given email
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading UserDetails for '{}'", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("No account found for email '{}'", email);
                    return new UsernameNotFoundException("No account found for email: " + email);
                });

        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(user.getRole().name())
                .disabled(!user.isEnabled())
                .build();
    }
}
