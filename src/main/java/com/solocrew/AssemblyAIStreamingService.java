package com.solocrew;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AssemblyAIStreamingService {

    @Value("${assemblyai.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, StreamingSession> activeSessions;

    private static final String ASSEMBLYAI_STREAMING_URL = "wss://streaming.assemblyai.com/v3/ws";

    public AssemblyAIStreamingService() {
        this.objectMapper = new ObjectMapper();
        this.activeSessions = new ConcurrentHashMap<>();
    }

    public CompletableFuture<StreamingSession> createStreamingSession(String sessionId, TranscriptCallback callback) {
        CompletableFuture<StreamingSession> future = new CompletableFuture<>();
        
        try {
            String connectionUrl = ASSEMBLYAI_STREAMING_URL + "?sample_rate=16000&format_turns=true";
            System.out.println("=== ASSEMBLYAI CONNECTION ATTEMPT ===");
            System.out.println("URL: " + connectionUrl);
            System.out.println("API Key length: " + apiKey.length());
            System.out.println("=====================================");
            
            URI serverUri = new URI(connectionUrl);
            
            // Create headers with authorization (like in JavaScript example)
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", apiKey);
            
            WebSocketClient client = new WebSocketClient(serverUri, headers) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("=== ASSEMBLYAI CONNECTION OPENED ===");
                    System.out.println("Session: " + sessionId);
                    System.out.println("Handshake: " + handshake.getHttpStatus() + " " + handshake.getHttpStatusMessage());
                    System.out.println("===================================");
                    
                    try {
                        StreamingSession session = new StreamingSession(sessionId, this, callback);
                        activeSessions.put(sessionId, session);
                        future.complete(session);
                        System.out.println("AssemblyAI session created successfully for: " + sessionId);
                        
                    } catch (Exception e) {
                        System.err.println("Error creating AssemblyAI session: " + e.getMessage());
                        future.completeExceptionally(e);
                    }
                }

                @Override
                public void onMessage(String message) {
                    try {
                        System.out.println("=== ASSEMBLYAI RAW RESPONSE ===");
                        System.out.println("Session: " + sessionId);
                        System.out.println("Raw JSON: " + message);
                        System.out.println("===============================");
                        
                        JsonNode jsonMessage = objectMapper.readTree(message);
                        
                        if (jsonMessage.has("type")) {
                            String messageType = jsonMessage.get("type").asText();
                            
                            if ("Begin".equals(messageType)) {
                                String sessionId = jsonMessage.has("id") ? jsonMessage.get("id").asText() : "unknown";
                                System.out.println("=== ASSEMBLYAI SESSION STARTED ===");
                                System.out.println("Session ID: " + sessionId);
                                System.out.println("Message: " + jsonMessage);
                                System.out.println("=================================");
                            } else if ("Turn".equals(messageType)) {
                                String transcript = jsonMessage.has("transcript") ? jsonMessage.get("transcript").asText() : "";
                                boolean isFormatted = jsonMessage.has("turn_is_formatted") ? jsonMessage.get("turn_is_formatted").asBoolean() : false;
                                
                                if (!transcript.trim().isEmpty()) {
                                    if (isFormatted) {
                                        System.out.println("=== FINAL TRANSCRIPT ===");
                                        System.out.println("Text: " + transcript);
                                        System.out.println("Formatted: " + isFormatted);
                                        System.out.println("====================");
                                        callback.onTranscript(transcript, true);
                                    } else {
                                        System.out.println("=== PARTIAL TRANSCRIPT ===");
                                        System.out.println("Text: " + transcript);
                                        System.out.println("========================");
                                        // Don't send partial transcripts to callback
                                    }
                                }
                            } else if ("Termination".equals(messageType)) {
                                double audioDuration = jsonMessage.has("audio_duration_seconds") ? jsonMessage.get("audio_duration_seconds").asDouble() : 0.0;
                                double sessionDuration = jsonMessage.has("session_duration_seconds") ? jsonMessage.get("session_duration_seconds").asDouble() : 0.0;
                                System.out.println("=== ASSEMBLYAI SESSION TERMINATED ===");
                                System.out.println("Audio Duration: " + audioDuration + "s");
                                System.out.println("Session Duration: " + sessionDuration + "s");
                                System.out.println("====================================");
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing AssemblyAI message: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("AssemblyAI WebSocket closed for session " + sessionId + ": " + reason);
                    activeSessions.remove(sessionId);
                    callback.onClose();
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("AssemblyAI WebSocket error for session " + sessionId + ": " + ex.getMessage());
                    callback.onError(ex);
                }
            };

            // Configure SSL for TLS 1.2+
            try {
                // Force TLS 1.2 or higher
                System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
                System.setProperty("jdk.tls.client.protocols", "TLSv1.2,TLSv1.3");
                
                SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(null, null, new SecureRandom());
                SSLSocketFactory factory = sslContext.getSocketFactory();
                client.setSocketFactory(factory);
                System.out.println("SSL context configured for TLS 1.2+");
            } catch (Exception e) {
                System.err.println("Failed to configure SSL: " + e.getMessage());
                e.printStackTrace();
            }

            // Set connection timeout
            client.setConnectionLostTimeout(60);
            
            System.out.println("Attempting to connect to AssemblyAI...");
            client.connect();
            
            // Wait a bit to see if connection establishes
            Thread.sleep(2000);
            
        } catch (Exception e) {
            System.err.println("Failed to create AssemblyAI streaming session: " + e.getMessage());
            future.completeExceptionally(e);
        }
        
        return future;
    }

    public void closeSession(String sessionId) {
        StreamingSession session = activeSessions.get(sessionId);
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

    public static class StreamingSession {
        private final String sessionId;
        private final WebSocketClient client;
        private final TranscriptCallback callback;

        public StreamingSession(String sessionId, WebSocketClient client, TranscriptCallback callback) {
            this.sessionId = sessionId;
            this.client = client;
            this.callback = callback;
        }

        public void sendAudioData(byte[] audioData) {
            if (client != null && client.isOpen()) {
                client.send(ByteBuffer.wrap(audioData));
            }
        }

        public void close() {
            if (client != null && client.isOpen()) {
                try {
                    // Send terminate message
                    Map<String, Object> terminateMessage = new HashMap<>();
                    terminateMessage.put("terminate_session", true);
                    
                    ObjectMapper mapper = new ObjectMapper();
                    String terminateJson = mapper.writeValueAsString(terminateMessage);
                    client.send(terminateJson);
                    client.close();
                } catch (Exception e) {
                    System.err.println("Error closing AssemblyAI session: " + e.getMessage());
                }
            }
        }

        public String getSessionId() {
            return sessionId;
        }
    }
}