package com.cinema.modules.booking.service;

import com.cinema.config.ClockConfig;
import com.cinema.config.WebSocketConfig;
import com.cinema.config.WebSocketDestinationGuard;
import com.cinema.modules.booking.dto.message.SeatStateChange;
import com.cinema.modules.booking.enums.SeatAvailability;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketMessagingAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = SeatStatusBroadcastIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeatStatusBroadcastIntegrationTest {

    @Configuration
    @ImportAutoConfiguration({
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebSocketServletAutoConfiguration.class,
            WebSocketMessagingAutoConfiguration.class,
            JacksonAutoConfiguration.class
    })
    @Import({WebSocketConfig.class, WebSocketDestinationGuard.class, SeatStatusBroadcaster.class, ClockConfig.class})
    static class TestApplication {
    }

    @LocalServerPort
    private int port;

    @Autowired
    private SeatStatusBroadcaster seatStatusBroadcaster;

    private WebSocketStompClient webSocketStompClient;
    private StompSession stompSession;

    @BeforeEach
    void connect() throws Exception {
        webSocketStompClient = new WebSocketStompClient(new StandardWebSocketClient());
        webSocketStompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompSession = webSocketStompClient
                .connectAsync("ws://localhost:" + port + "/ws/cinema/websocket", new WebSocketHttpHeaders(),
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void disconnect() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
        }
        webSocketStompClient.stop();
    }

    @Test
    @SuppressWarnings("unchecked")
    void subscribersOfAShowtimeReceiveSeatChangesQuickly() throws Exception {
        BlockingQueue<Map<String, Object>> receivedMessages = new LinkedBlockingQueue<>();
        stompSession.subscribe("/topic/showtimes/5/seats", new CapturingFrameHandler(receivedMessages));
        Thread.sleep(300);

        long startNanos = System.nanoTime();
        seatStatusBroadcaster.broadcast(5L, List.of(new SeatStateChange(11L, SeatAvailability.HELD)));
        Map<String, Object> message = receivedMessages.poll(2, TimeUnit.SECONDS);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertNotNull(message);
        assertEquals(5, message.get("showtimeId"));
        List<Map<String, Object>> changes = (List<Map<String, Object>>) message.get("changes");
        assertEquals(11, changes.get(0).get("seatId"));
        assertEquals("HELD", changes.get(0).get("state"));
        assertTrue(elapsedMillis < 100, "Broadcast took " + elapsedMillis + " ms");
    }

    @Test
    void subscribersOfOtherShowtimesDoNotReceiveTheChange() throws Exception {
        BlockingQueue<Map<String, Object>> otherShowtimeMessages = new LinkedBlockingQueue<>();
        stompSession.subscribe("/topic/showtimes/6/seats", new CapturingFrameHandler(otherShowtimeMessages));
        Thread.sleep(300);

        seatStatusBroadcaster.broadcast(5L, List.of(new SeatStateChange(11L, SeatAvailability.SOLD)));

        assertNull(otherShowtimeMessages.poll(500, TimeUnit.MILLISECONDS));
    }

    private static class CapturingFrameHandler implements StompFrameHandler {

        private final BlockingQueue<Map<String, Object>> receivedMessages;

        CapturingFrameHandler(BlockingQueue<Map<String, Object>> receivedMessages) {
            this.receivedMessages = receivedMessages;
        }

        @Override
        public Type getPayloadType(StompHeaders stompHeaders) {
            return Map.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handleFrame(StompHeaders stompHeaders, Object payload) {
            receivedMessages.add((Map<String, Object>) payload);
        }
    }
}
