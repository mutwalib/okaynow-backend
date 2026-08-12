package com.okaynow.mail;

public interface EmailSender {

    void send(String to, String subject, String textBody);
}
