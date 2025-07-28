package com.solocrew;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AssemblyAIStreamingServiceV2 {

    @Value("${assemblyai.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, StreamingSessionV2> activeSessions;

    private static final String ASSEMBLYAI_STREAMING_URL = "wss://streaming.assemblyai.com/v3/ws";

    public AssemblyAIStreamingServiceV2() {
        this.objectMapper = new ObjectMapper();
        this.activeSessions = new ConcurrentHashMap<>();
    }

    public CompletableFuture<StreamingSessionV2> createStreamingSession(String sessionId, TranscriptCallback callback) {
        CompletableFuture<StreamingSessionV2> future = new CompletableFuture<>();
        
        try {
            String connectionUrl = ASSEMBLYAI_STREAMING_URL + "?sample_rate=16000&format_turns=true";
            System.out.println("=== ASSEMBLYAI V2 CONNECTION ATTEMPT ===");
            System.out.println("URL: " + connectionUrl);
            System.out.println("API Key length: " + apiKey.length());
            System.out.println("========================================");
            
            URI serverUri = new URI(connectionUrl);
            
            StandardWebSocketClient client = new StandardWebSocketClient();
            
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.add("Authorization", apiKey);
            
            WebSocketHandler handler = new WebSocketHandler() {
                private WebSocketSession webSocketSession;
                
                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    this.webSocketSession = session;
                    System.out.println("=== ASSEMBLYAI V2 CONNECTION OPENED ===");
                    System.out.println("Session: " + sessionId);
                    System.out.println("WebSocket Session: " + session.getId());
                    System.out.println("======================================");
                    
                    StreamingSessionV2 streamingSession = new StreamingSessionV2(sessionId, session, callback);
                    activeSessions.put(sessionId, streamingSession);
                    future.complete(streamingSession);
                }

                @Override
                public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                    if (message instanceof TextMessage) {
                        String payload = ((TextMessage) message).getPayload();
                        
                        System.out.println("=== ASSEMBLYAI V2 RAW RESPONSE ===");
                        System.out.println("Session: " + sessionId);
                        System.out.println("Raw JSON: " + payload);
                        System.out.println("=================================");
                        
                        try {
                            JsonNode jsonMessage = objectMapper.readTree(payload);
                            
                            if (jsonMessage.has("type")) {
                                String messageType = jsonMessage.get("type").asText();
                                
                                if ("Begin".equals(messageType)) {
                                    String assId = jsonMessage.has("id") ? jsonMessage.get("id").asText() : "unknown";
                                    System.out.println("=== ASSEMBLYAI V2 SESSION STARTED ===");
                                    System.out.println("AssemblyAI Session ID: " + assId);
                                    System.out.println("====================================");
                                } else if ("Turn".equals(messageType)) {
                                    String transcript = jsonMessage.has("transcript") ? jsonMessage.get("transcript").asText() : "";
                                    boolean isFormatted = jsonMessage.has("turn_is_formatted") ? jsonMessage.get("turn_is_formatted").asBoolean() : false;
                                    
                                    if (!transcript.trim().isEmpty()) {
                                        if (isFormatted) {
                                            System.out.println("=== FINAL TRANSCRIPT V2 ===");
                                            System.out.println("Text: " + transcript);
                                            System.out.println("==========================");
                                            callback.onTranscript(transcript, true);
                                        } else {
                                            System.out.println("=== PARTIAL TRANSCRIPT V2 ===");
                                            System.out.println("Text: " + transcript);
                                            System.out.println("=============================");
                                        }
                                    }
                                } else if ("Termination".equals(messageType)) {
                                    System.out.println("=== ASSEMBLYAI V2 SESSION TERMINATED ===");
                                    System.out.println("=======================================");
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Error processing AssemblyAI V2 message: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                    System.err.println("AssemblyAI V2 WebSocket transport error for session " + sessionId + ": " + exception.getMessage());
                    exception.printStackTrace();
                    callback.onError(new Exception(exception));
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                    System.out.println("AssemblyAI V2 WebSocket closed for session " + sessionId + ": " + closeStatus.toString());
                    activeSessions.remove(sessionId);
                    callback.onClose();
                }

                @Override
                public boolean supportsPartialMessages() {
                    return false;
                }
            };
            
            client.doHandshake(handler, headers, serverUri).get();
            
        } catch (Exception e) {
            System.err.println("Failed to create AssemblyAI V2 streaming session: " + e.getMessage());
            e.printStackTrace();
            future.completeExceptionally(e);
        }
        
        return future;
    }

    public void closeSession(String sessionId) {
        StreamingSessionV2 session = activeSessions.get(sessionId);
        if (session != null) {
            session.close();
            activeSessions.remove(sessionId);
        }
    }

    public interface TranscriptCallback {
        void onTranscript(String text, boolean isFinal);
        void onClose();
        void onError(Exception ex);
    }

    public static class StreamingSessionV2 {
        private final String sessionId;
        private final WebSocketSession webSocketSession;
        private final TranscriptCallback callback;

        public StreamingSessionV2(String sessionId, WebSocketSession webSocketSession, TranscriptCallback callback) {
            this.sessionId = sessionId;
            this.webSocketSession = webSocketSession;
            this.callback = callback;
        }

        public void sendAudioData(byte[] audioData) {
            if (webSocketSession != null && webSocketSession.isOpen()) {
                try {
                    webSocketSession.sendMessage(new BinaryMessage(ByteBuffer.wrap(audioData)));
                } catch (Exception e) {
                    System.err.println("Error sending audio data: " + e.getMessage());
                }
            }
        }

        public void close() {
            if (webSocketSession != null && webSocketSession.isOpen()) {
                try {
                    webSocketSession.close();
                } catch (Exception e) {
                    System.err.println("Error closing AssemblyAI V2 session: " + e.getMessage());
                }
            }
        }

        public String getSessionId() {
            return sessionId;
        }
    }
}