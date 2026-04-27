package com.texify.backend.security;

import com.texify.backend.entity.AuthProvider;
import com.texify.backend.entity.User;
import com.texify.backend.service.OAuth2UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2UserService oAuth2UserService;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        String email = (String) oAuth2User.getAttributes().get("email");
        if (email == null) {
            log.warn("OAuth2 login rejected: no public email from {}", registrationId);
            getRedirectStrategy().sendRedirect(request, response,
                    frontendUrl + "/login?error=oauth2_no_email");
            return;
        }

        String[] names = extractNames(oAuth2User, registrationId);
        AuthProvider provider = "google".equals(registrationId) ? AuthProvider.GOOGLE : AuthProvider.GITHUB;

        User user = oAuth2UserService.processOAuth2User(email, names[0], names[1], provider);
        UserDetails principal = userDetailsService.loadUserByUsername(user.getEmail());
        String jwt = jwtService.generateToken(principal);

        log.info("OAuth2 login successful for '{}' via {}", email, registrationId);
        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response,
                frontendUrl + "/oauth2/callback?token=" + jwt);
    }

    private String[] extractNames(OAuth2User oAuth2User, String registrationId) {
        Map<String, Object> attrs = oAuth2User.getAttributes();
        if ("google".equals(registrationId)) {
            return new String[]{
                    (String) attrs.getOrDefault("given_name", ""),
                    (String) attrs.getOrDefault("family_name", "")
            };
        }
        // GitHub returns a single "name" field and a "login" (username) as fallback
        String fullName = (String) attrs.get("name");
        if (fullName != null && !fullName.isBlank()) {
            int space = fullName.indexOf(' ');
            if (space > 0) {
                return new String[]{fullName.substring(0, space), fullName.substring(space + 1)};
            }
            return new String[]{fullName, ""};
        }
        return new String[]{(String) attrs.getOrDefault("login", ""), ""};
    }
}
