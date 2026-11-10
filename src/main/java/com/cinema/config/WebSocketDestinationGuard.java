package com.cinema.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class WebSocketDestinationGuard implements ChannelInterceptor {

    private static final Pattern ALLOWED_SUBSCRIPTION = Pattern.compile("^/topic/showtimes/\\d+/seats$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel messageChannel) {
        StompHeaderAccessor stompHeaderAccessor = StompHeaderAccessor.wrap(message);
        StompCommand stompCommand = stompHeaderAccessor.getCommand();
        if (StompCommand.SEND.equals(stompCommand)) {
            throw new MessageDeliveryException("Máy khách không được phép gửi tin nhắn");
        }
        if (StompCommand.SUBSCRIBE.equals(stompCommand)) {
            String destination = stompHeaderAccessor.getDestination();
            if (destination == null || !ALLOWED_SUBSCRIPTION.matcher(destination).matches()) {
                throw new MessageDeliveryException("Kênh đăng ký không hợp lệ");
            }
        }
        return message;
    }
}
