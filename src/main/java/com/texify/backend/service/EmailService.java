package com.texify.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${texify.mail.from}")
    private String from;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String to, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        String html = """
                <p>Hello,</p>
                <p>We received a request to reset your Texify password. Click the link below to choose a new one:</p>
                <p><a href="%s">Reset my password</a></p>
                <p>This link expires in 1 hour.</p>
                <p>If you did not request a password reset, you can safely ignore this email.</p>
                """.formatted(link);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setFrom(from);
            helper.setSubject("Reset your Texify password");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Password reset email sent to '{}'", to);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to '{}'", to, e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    public void sendOAuth2WelcomeEmail(String to, String firstName, String providerName) {
        String html = """
                <p>Bonjour %s,</p>
                <p>Votre compte Texify a été créé avec succès via %s.</p>
                <p>Vous pouvez désormais vous connecter à tout moment en utilisant votre compte %s.</p>
                <p>Si vous n'êtes pas à l'origine de cette inscription, contactez-nous immédiatement.</p>
                """.formatted(firstName, providerName, providerName);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setFrom(from);
            helper.setSubject("Bienvenue sur Texify !");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("OAuth2 welcome email sent to '{}'", to);
        } catch (MessagingException e) {
            log.error("Failed to send OAuth2 welcome email to '{}'", to, e);
            throw new RuntimeException("Failed to send OAuth2 welcome email", e);
        }
    }

    public void sendVerificationEmail(String to, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        String html = """
                <p>Bonjour,</p>
                <p>Merci de vous être inscrit sur Texify. Cliquez sur le lien ci-dessous pour activer votre compte :</p>
                <p><a href="%s">Vérifier mon adresse e-mail</a></p>
                <p>Ce lien expire dans 24 heures.</p>
                <p>Si vous n'avez pas créé de compte, ignorez simplement cet e-mail.</p>
                """.formatted(link);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setFrom(from);
            helper.setSubject("Vérifiez votre adresse e-mail Texify");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Verification email sent to '{}'", to);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to '{}'", to, e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }
}
