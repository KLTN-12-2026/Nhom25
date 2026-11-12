package org.example.meetingbe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration
@EnableWebSocket
@EnableScheduling
public class WebSocketConfigMeeting implements WebSocketConfigurer {
    private final WebSocketHandler handler;
    public WebSocketConfigMeeting(WebSocketHandler handler) { this.handler = handler; }
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/signaling")
                .setAllowedOrigins("http://localhost:4200");
    }

}
