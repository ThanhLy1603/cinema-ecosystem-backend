package com.cinema.modules.auth.service;

import com.cinema.config.AppMailProperties;
import com.cinema.modules.auth.event.EmailVerificationRequestedEvent;
import com.cinema.modules.auth.event.PasswordResetRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthMailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    private AuthMailService authMailService(String frontendBaseUrl) {
        return new AuthMailService(
                javaMailSender, new AppMailProperties("CinemaSphere <no-reply@cinema.vn>", frontendBaseUrl));
    }

    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void passwordResetEmailContainsTheFrontendLinkWithTheRawToken() {
        authMailService("http://localhost:3000/")
                .onPasswordResetRequested(new PasswordResetRequestedEvent("an@cinema.vn", "Nguyễn An", "tok_en-1"));

        SimpleMailMessage simpleMailMessage = sentMessage();
        assertArrayEquals(new String[] {"an@cinema.vn"}, simpleMailMessage.getTo());
        assertEquals("CinemaSphere <no-reply@cinema.vn>", simpleMailMessage.getFrom());
        assertTrue(simpleMailMessage.getSubject().contains("Đặt lại mật khẩu"));
        assertTrue(simpleMailMessage.getText().contains("http://localhost:3000/reset-password?token=tok_en-1"));
        assertTrue(simpleMailMessage.getText().contains("30 phút"));
    }

    @Test
    void verificationEmailContainsTheVerifyLink() {
        authMailService("https://cinema.example.com")
                .onEmailVerificationRequested(new EmailVerificationRequestedEvent("an@cinema.vn", "An", "tok-2"));

        SimpleMailMessage simpleMailMessage = sentMessage();
        assertTrue(simpleMailMessage.getText().contains("https://cinema.example.com/verify-email?token=tok-2"));
        assertTrue(simpleMailMessage.getText().contains("24 giờ"));
    }

    @Test
    void mailServerFailureIsSwallowedSoTheCallerNeverSeesIt() {
        doThrow(new MailSendException("smtp down")).when(javaMailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> authMailService("http://localhost:3000")
                .onPasswordResetRequested(new PasswordResetRequestedEvent("an@cinema.vn", "An", "tok")));
    }

    @Test
    void eventsNeverPrintTheRawTokenInLogs() {
        assertTrue(!new PasswordResetRequestedEvent("an@cinema.vn", "An", "secret-token").toString().contains("secret-token"));
        assertTrue(!new EmailVerificationRequestedEvent("an@cinema.vn", "An", "secret-token").toString().contains("secret-token"));
    }
}
