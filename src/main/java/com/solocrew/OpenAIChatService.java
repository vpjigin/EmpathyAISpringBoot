package com.solocrew;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OpenAIChatService {

    @Value("${openai.api.key}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";

    public OpenAIChatService() {
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public EmpathyResponse generateEmpathyResponse(List<ConversationMessage> conversationHistory) throws Exception {
        try {
            System.out.println("=== OPENAI REQUEST ===");
            System.out.println("Conversation history size: " + conversationHistory.size() + " messages");
            
            // Print conversation history
            for (int i = 0; i < conversationHistory.size(); i++) {
                ConversationMessage msg = conversationHistory.get(i);
                System.out.println("Message " + i + " [" + msg.getRole() + "]: " + msg.getContent());
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4");
            requestBody.put("messages", conversationHistory.stream()
                    .map(msg -> {
                        Map<String, String> message = new HashMap<>();
                        message.put("role", msg.getRole());
                        message.put("content", msg.getContent());
                        return message;
                    })
                    .collect(Collectors.toList()));
            requestBody.put("max_tokens", 200);
            requestBody.put("temperature", 0.7);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            System.out.println("OpenAI Request Body: " + jsonBody);
            System.out.println("=====================");

            String response = webClient.post()
                    .uri(OPENAI_CHAT_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(jsonBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));

            System.out.println("=== OPENAI RAW RESPONSE ===");
            System.out.println("Full response: " + response);
            System.out.println("===========================");

            JsonNode jsonResponse = objectMapper.readTree(response);
            String aiResponse = jsonResponse.get("choices").get(0).get("message").get("content").asText();

            System.out.println("=== OPENAI AI CONTENT ===");
            System.out.println("AI Content: " + aiResponse);
            System.out.println("========================");

            // Parse the JSON response from the AI
            try {
                JsonNode aiJsonResponse = objectMapper.readTree(aiResponse);
                String reply = aiJsonResponse.get("reply").asText();
                boolean isHumanInterventionNeeded = aiJsonResponse.get("isHumanInterventionNeeded").asBoolean();
                
                System.out.println("=== PARSED AI RESPONSE ===");
                System.out.println("Reply: " + reply);
                System.out.println("Human Intervention Needed: " + isHumanInterventionNeeded);
                System.out.println("=========================");
                
                return new EmpathyResponse(reply, isHumanInterventionNeeded);
            } catch (Exception e) {
                // Fallback if AI doesn't return proper JSON
                System.err.println("=== AI RESPONSE PARSING FAILED ===");
                System.err.println("Error: " + e.getMessage());
                System.err.println("Raw AI response that failed to parse: " + aiResponse);
                System.err.println("Using fallback response");
                System.err.println("=================================");
                return new EmpathyResponse("I'm here to listen and support you. Please tell me more about how you're feeling.", false);
            }

        } catch (Exception e) {
            System.err.println("=== OPENAI REQUEST FAILED ===");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("============================");
            throw new Exception("Failed to generate empathy response: " + e.getMessage());
        }
    }

    public static class EmpathyResponse {
        private String reply;
        private boolean isHumanInterventionNeeded;

        public EmpathyResponse(String reply, boolean isHumanInterventionNeeded) {
            this.reply = reply;
            this.isHumanInterventionNeeded = isHumanInterventionNeeded;
        }

        public String getReply() {
            return reply;
        }

        public boolean isHumanInterventionNeeded() {
            return isHumanInterventionNeeded;
        }
    }
}