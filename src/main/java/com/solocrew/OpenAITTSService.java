package com.solocrew;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class OpenAITTSService {

    @Value("${openai.api.key}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final String OPENAI_TTS_URL = "https://api.openai.com/v1/audio/speech";

    public OpenAITTSService() {
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public byte[] generateSpeech(String text) throws Exception {
        try {
            System.out.println("Generating speech for text: " + text.substring(0, Math.min(text.length(), 50)) + "...");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "tts-1");
            requestBody.put("input", text);
            requestBody.put("voice", "nova"); // Nova is a soft female voice
            requestBody.put("response_format", "mp3");

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            byte[] audioData = webClient.post()
                    .uri(OPENAI_TTS_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(jsonBody)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(Duration.ofMinutes(1));

            System.out.println("Speech generation completed successfully, audio size: " + audioData.length + " bytes");
            return audioData;

        } catch (Exception e) {
            System.err.println("Failed to generate speech: " + e.getMessage());
            throw new Exception("Failed to generate speech: " + e.getMessage());
        }
    }
}