package com.okaynow.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.smtp-enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String textBody) {
        log.info("""
                ===== OkayNow email (dev/log) =====
                To: {}
                Subject: {}
                {}
                ===================================
                """, to, subject, textBody);
    }
}
