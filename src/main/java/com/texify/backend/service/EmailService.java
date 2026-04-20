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
