package com.cinema.modules.auth.service;

import com.cinema.config.properties.AppMailProperties;
import com.cinema.modules.auth.event.EmailVerificationRequestedEvent;
import com.cinema.modules.auth.event.PasswordResetRequestedEvent;
import com.cinema.modules.auth.service.interfaces.IAuthMailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
public class AuthMailService implements IAuthMailService {

    static final String PASSWORD_RESET_PATH = "/reset-password";
    static final String EMAIL_VERIFICATION_PATH = "/verify-email";

    private final JavaMailSender javaMailSender;
    private final AppMailProperties appMailProperties;

    public AuthMailService(JavaMailSender javaMailSender, AppMailProperties appMailProperties) {
        this.javaMailSender = javaMailSender;
        this.appMailProperties = appMailProperties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Override
    public void onPasswordResetRequested(PasswordResetRequestedEvent passwordResetRequestedEvent) {
        String link = buildLink(PASSWORD_RESET_PATH, passwordResetRequestedEvent.rawToken());
        String body = "Xin chào " + passwordResetRequestedEvent.fullName() + ",\n\n"
                + "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản CinemaSphere của bạn.\n"
                + "Bấm vào liên kết dưới đây để đặt mật khẩu mới:\n\n" + link + "\n\n"
                + "Liên kết có hiệu lực trong 30 phút và chỉ dùng được một lần. "
                + "Nếu bạn không yêu cầu, hãy bỏ qua email này, mật khẩu của bạn vẫn được giữ nguyên.\n\n"
                + "CinemaSphere";
        send(passwordResetRequestedEvent.email(), "[CinemaSphere] Đặt lại mật khẩu", body);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Override
    public void onEmailVerificationRequested(EmailVerificationRequestedEvent emailVerificationRequestedEvent) {
        String link = buildLink(EMAIL_VERIFICATION_PATH, emailVerificationRequestedEvent.rawToken());
        String body = "Xin chào " + emailVerificationRequestedEvent.fullName() + ",\n\n"
                + "Cảm ơn bạn đã đăng ký CinemaSphere. Bấm vào liên kết dưới đây để xác thực email:\n\n"
                + link + "\n\n"
                + "Liên kết có hiệu lực trong 24 giờ. Nếu bạn không đăng ký tài khoản này, hãy bỏ qua email.\n\n"
                + "CinemaSphere";
        send(emailVerificationRequestedEvent.email(), "[CinemaSphere] Xác thực email của bạn", body);
    }

    String buildLink(String path, String rawToken) {
        String baseUrl = appMailProperties.frontendBaseUrl();
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + path + "?token=" + rawToken;
    }

    private void send(String toEmail, String subject, String body) {
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(appMailProperties.from());
        simpleMailMessage.setTo(toEmail);
        simpleMailMessage.setSubject(subject);
        simpleMailMessage.setText(body);
        try {
            javaMailSender.send(simpleMailMessage);
        } catch (MailException mailException) {
            log.warn("Không gửi được email '{}' tới {}", subject, maskEmail(toEmail), mailException);
        }
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + email.substring(Math.max(atIndex, 0));
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
