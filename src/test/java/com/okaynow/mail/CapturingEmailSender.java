package com.okaynow.mail;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Profile("test")
@Primary
public class CapturingEmailSender implements EmailSender {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");
    private static final AtomicReference<String> LAST_BODY = new AtomicReference<>();

    @Override
    public void send(String to, String subject, String textBody) {
        LAST_BODY.set(textBody);
    }

    public static String lastCode() {
        String body = LAST_BODY.get();
        if (body == null) {
            throw new IllegalStateException("No email captured");
        }
        Matcher m = CODE.matcher(body);
        if (!m.find()) {
            throw new IllegalStateException("No OTP found in email body: " + body);
        }
        return m.group(1);
    }

    public static void clear() {
        LAST_BODY.set(null);
    }
}
