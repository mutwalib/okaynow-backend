package com.okaynow.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.smtp-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:OkayNow <mutwalibb@gmail.com>}")
    private String from;

    @Override
    public void send(String to, String subject, String textBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(InternetAddress.parse(from, false)[0]);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, false);
            mailSender.send(message);
            log.info("SMTP sent subject='{}' to={}", subject, to);
        } catch (MailException | jakarta.mail.MessagingException ex) {
            log.error("SMTP failed subject='{}' to={}: {}", subject, to, ex.getMessage());
            throw new IllegalStateException("Failed to send email. Please try again shortly.", ex);
        }
    }
}
