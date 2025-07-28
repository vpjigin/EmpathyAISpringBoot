package com.solocrew;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AudioStreamingWebSocketHandler audioStreamingHandler;

    public WebSocketConfig(AudioStreamingWebSocketHandler audioStreamingHandler) {
        this.audioStreamingHandler = audioStreamingHandler;
        System.out.println("WebSocketConfig initialized with handler: " + audioStreamingHandler);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        System.out.println("Registering WebSocket handlers...");
        registry.addHandler(audioStreamingHandler, "/ws/audio-stream")
                .setAllowedOrigins("*")
                .withSockJS(); // Add SockJS fallback support
        
        // Also register without SockJS for native WebSocket support
        registry.addHandler(audioStreamingHandler, "/ws/audio-stream-native")
                .setAllowedOrigins("*");
        
        System.out.println("WebSocket handlers registered successfully");
    }
}