package com.texify.backend.service;

import com.texify.backend.entity.AuthProvider;
import com.texify.backend.entity.Role;
import com.texify.backend.entity.User;
import com.texify.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    /**
     * Finds an existing user by email or creates a new enabled account for an OAuth2 login.
     * Sends a welcome email only on first creation.
     */
    @Transactional
    public User processOAuth2User(String email, String firstName, String lastName, AuthProvider provider) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .firstName(firstName)
                            .lastName(lastName)
                            .role(Role.ROLE_USER)
                            .authProvider(provider)
                            .enabled(true)
                            .build();
                    User saved = userRepository.save(newUser);
                    log.info("New OAuth2 account created for '{}' via {}", email, provider);
                    emailService.sendOAuth2WelcomeEmail(email, firstName, provider.name());
                    return saved;
                });
    }
}
