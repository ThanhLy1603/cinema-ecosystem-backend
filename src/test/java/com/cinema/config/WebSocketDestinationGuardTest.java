package com.cinema.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WebSocketDestinationGuardTest {

    private final WebSocketDestinationGuard webSocketDestinationGuard = new WebSocketDestinationGuard();
    private final MessageChannel messageChannel = mock(MessageChannel.class);

    @Test
    void subscribingToAShowtimeSeatTopicIsAllowed() {
        Message<byte[]> message = stompMessage(StompCommand.SUBSCRIBE, "/topic/showtimes/12/seats");

        assertSame(message, webSocketDestinationGuard.preSend(message, messageChannel));
    }

    @Test
    void subscribingToAnyOtherDestinationIsRejected() {
        assertThrows(MessageDeliveryException.class, () -> webSocketDestinationGuard.preSend(
                stompMessage(StompCommand.SUBSCRIBE, "/topic/showtimes/abc/seats"), messageChannel));
        assertThrows(MessageDeliveryException.class, () -> webSocketDestinationGuard.preSend(
                stompMessage(StompCommand.SUBSCRIBE, "/topic/admin"), messageChannel));
    }

    @Test
    void clientsCannotSendToTheBrokerToSpoofSeatStatus() {
        assertThrows(MessageDeliveryException.class, () -> webSocketDestinationGuard.preSend(
                stompMessage(StompCommand.SEND, "/topic/showtimes/12/seats"), messageChannel));
    }

    @Test
    void connectAndHeartbeatFramesPass() {
        Message<byte[]> message = stompMessage(StompCommand.CONNECT, null);

        assertSame(message, webSocketDestinationGuard.preSend(message, messageChannel));
    }

    private Message<byte[]> stompMessage(StompCommand stompCommand, String destination) {
        StompHeaderAccessor stompHeaderAccessor = StompHeaderAccessor.create(stompCommand);
        if (destination != null) {
            stompHeaderAccessor.setDestination(destination);
        }
        stompHeaderAccessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], stompHeaderAccessor.getMessageHeaders());
    }
}
