package com.solocrew;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AudioStreamingWebSocketHandler implements WebSocketHandler {

    @Autowired
    private AssemblyAIStreamingServiceV2 assemblyAIStreamingService;

    @Autowired
    private OpenAIChatService openAIChatService;

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, WebSocketSession> clientSessions;
    private final ConcurrentHashMap<String, AssemblyAIStreamingServiceV2.StreamingSessionV2> assemblyAISessions;
    private final ConcurrentHashMap<String, ConversationSession> conversations;

    public AudioStreamingWebSocketHandler() {
        this.objectMapper = new ObjectMapper();
        this.clientSessions = new ConcurrentHashMap<>();
        this.assemblyAISessions = new ConcurrentHashMap<>();
        this.conversations = new ConcurrentHashMap<>();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("WebSocket connection established: " + session.getId());
        clientSessions.put(session.getId(), session);
        
        // Send connection established message
        sendMessage(session, createJsonResponse("connection_established", "WebSocket connection established", null));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message instanceof TextMessage) {
            handleTextMessage(session, (TextMessage) message);
        } else if (message instanceof BinaryMessage) {
            handleBinaryMessage(session, (BinaryMessage) message);
        }
    }

    private void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode jsonMessage = objectMapper.readTree(message.getPayload());
            String type = jsonMessage.get("type").asText();
            
            switch (type) {
                case "start_streaming":
                    String conversationUuid = jsonMessage.get("conversation_uuid").asText();
                    startStreaming(session, conversationUuid);
                    break;
                    
                case "stop_streaming":
                    stopStreaming(session);
                    break;
                    
                default:
                    System.out.println("Unknown message type: " + type);
            }
            
        } catch (Exception e) {
            System.err.println("Error handling text message: " + e.getMessage());
            sendMessage(session, createJsonResponse("error", "Failed to process message: " + e.getMessage(), null));
        }
    }

    private void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        AssemblyAIStreamingServiceV2.StreamingSessionV2 assemblySession = assemblyAISessions.get(session.getId());
        
        if (assemblySession != null) {
            // Forward audio data to AssemblyAI
            ByteBuffer audioData = message.getPayload();
            byte[] audioBytes = new byte[audioData.remaining()];
            audioData.get(audioBytes);
            
            assemblySession.sendAudioData(audioBytes);
//            System.out.println("Forwarded " + audioBytes.length + " bytes to AssemblyAI for session: " + session.getId());
        } else {
            System.err.println("No AssemblyAI session found for WebSocket session: " + session.getId());
        }
    }

    private void startStreaming(WebSocketSession session, String conversationUuid) {
        System.out.println("Starting streaming for session: " + session.getId() + ", conversation: " + conversationUuid);
        
        // Get or create conversation session
        ConversationSession conversation = conversations.computeIfAbsent(conversationUuid, ConversationSession::new);
        
        // Create AssemblyAI streaming session
        assemblyAIStreamingService.createStreamingSession(session.getId(), new AssemblyAIStreamingServiceV2.TranscriptCallback() {
            @Override
            public void onTranscript(String text, boolean isFinal) {
                if (isFinal) {
                    handleFinalTranscript(session, conversation, text);
                }
            }

            @Override
            public void onClose() {
                System.out.println("AssemblyAI session closed for: " + session.getId());
            }

            @Override
            public void onError(Exception ex) {
                System.err.println("AssemblyAI error for session " + session.getId() + ": " + ex.getMessage());
                try {
                    sendMessage(session, createJsonResponse("error", "AssemblyAI error: " + ex.getMessage(), null));
                } catch (Exception e) {
                    System.err.println("Failed to send error message to client: " + e.getMessage());
                }
            }
        }).thenAccept(assemblySession -> {
            assemblyAISessions.put(session.getId(), assemblySession);
            try {
                sendMessage(session, createJsonResponse("streaming_started", "Audio streaming started", conversationUuid));
            } catch (Exception e) {
                System.err.println("Failed to send streaming started message: " + e.getMessage());
            }
        }).exceptionally(throwable -> {
            System.err.println("Failed to create AssemblyAI session: " + throwable.getMessage());
            try {
                sendMessage(session, createJsonResponse("error", "Failed to start streaming: " + throwable.getMessage(), null));
            } catch (Exception e) {
                System.err.println("Failed to send error message: " + e.getMessage());
            }
            return null;
        });
    }

    private void stopStreaming(WebSocketSession session) {
        System.out.println("Stopping streaming for session: " + session.getId());
        
        AssemblyAIStreamingServiceV2.StreamingSessionV2 assemblySession = assemblyAISessions.remove(session.getId());
        if (assemblySession != null) {
            assemblySession.close();
        }
        
        try {
            sendMessage(session, createJsonResponse("streaming_stopped", "Audio streaming stopped", null));
        } catch (Exception e) {
            System.err.println("Failed to send streaming stopped message: " + e.getMessage());
        }
    }

    private void handleFinalTranscript(WebSocketSession session, ConversationSession conversation, String transcriptText) {
        try {
            System.out.println("=== WEBSOCKET TRANSCRIPT PROCESSING ===");
            System.out.println("Session ID: " + session.getId());
            System.out.println("Conversation UUID: " + conversation.getUuid());
            System.out.println("Final transcript: " + transcriptText);
            System.out.println("=======================================");
            
            // Add user message to conversation
            conversation.addMessage(new ConversationMessage("user", transcriptText));
            System.out.println("Added user message to conversation");
            
            // Generate empathy response using OpenAI
            System.out.println("Calling OpenAI for empathy response...");
            OpenAIChatService.EmpathyResponse empathyResponse = openAIChatService.generateEmpathyResponse(conversation.getMessages());
            System.out.println("Received empathy response from OpenAI");
            
            // Add assistant response to conversation
            conversation.addMessage(new ConversationMessage("assistant", empathyResponse.getReply()));
            System.out.println("Added assistant response to conversation");
            
            // Check if response indicates distress and update counter
            boolean needsHumanIntervention = empathyResponse.isHumanInterventionNeeded() || conversation.needsHumanIntervention();
            
            // Send response to client
            String responseJson = createTranscriptResponse(
                transcriptText, 
                empathyResponse.getReply(), 
                needsHumanIntervention,
                conversation.getUuid()
            );
            
            System.out.println("=== SENDING TO CLIENT ===");
            System.out.println("Response JSON: " + responseJson);
            System.out.println("========================");
            
            sendMessage(session, responseJson);
            System.out.println("Response sent to client successfully");
            
        } catch (Exception e) {
            System.err.println("=== ERROR PROCESSING TRANSCRIPT ===");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("==================================");
            try {
                sendMessage(session, createJsonResponse("error", "Failed to process transcript: " + e.getMessage(), null));
            } catch (Exception ex) {
                System.err.println("Failed to send error message: " + ex.getMessage());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("WebSocket transport error for session " + session.getId() + ": " + exception.getMessage());
        cleanupSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        System.out.println("WebSocket connection closed for session " + session.getId() + ": " + closeStatus.toString());
        cleanupSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void cleanupSession(WebSocketSession session) {
        clientSessions.remove(session.getId());
        
        // Close AssemblyAI session
        AssemblyAIStreamingServiceV2.StreamingSessionV2 assemblySession = assemblyAISessions.remove(session.getId());
        if (assemblySession != null) {
            assemblySession.close();
        }
    }

    private void sendMessage(WebSocketSession session, String message) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(message));
        }
    }

    private String createJsonResponse(String type, String message, String conversationUuid) {
        try {
            return objectMapper.writeValueAsString(new WebSocketResponse(type, message, conversationUuid));
        } catch (Exception e) {
            System.err.println("Failed to create JSON response: " + e.getMessage());
            return "{\"type\":\"error\",\"message\":\"Failed to create response\"}";
        }
    }

    private String createTranscriptResponse(String transcript, String reply, boolean needsHumanIntervention, String conversationUuid) {
        try {
            return objectMapper.writeValueAsString(new TranscriptResponse(
                "transcript", 
                "Transcript processed successfully", 
                conversationUuid,
                transcript,
                reply,
                needsHumanIntervention
            ));
        } catch (Exception e) {
            System.err.println("Failed to create transcript response: " + e.getMessage());
            return "{\"type\":\"error\",\"message\":\"Failed to create transcript response\"}";
        }
    }

    // Response classes
    public static class WebSocketResponse {
        public String type;
        public String message;
        public String conversation_uuid;

        public WebSocketResponse(String type, String message, String conversationUuid) {
            this.type = type;
            this.message = message;
            this.conversation_uuid = conversationUuid;
        }
    }

    public static class TranscriptResponse extends WebSocketResponse {
        public String transcript;
        public String reply;
        public boolean needsHumanIntervention;

        public TranscriptResponse(String type, String message, String conversationUuid, 
                                String transcript, String reply, boolean needsHumanIntervention) {
            super(type, message, conversationUuid);
            this.transcript = transcript;
            this.reply = reply;
            this.needsHumanIntervention = needsHumanIntervention;
        }
    }
}