package com.solocrew;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;

@Service
public class AssemblyAIService {

    @Value("${assemblyai.api.key}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final String ASSEMBLYAI_UPLOAD_URL = "https://api.assemblyai.com/v2/upload";
    private static final String ASSEMBLYAI_TRANSCRIPT_URL = "https://api.assemblyai.com/v2/transcript";

    public AssemblyAIService() {
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String transcribeAudio(MultipartFile audioFile) throws Exception {
        String audioUrl = uploadAudioFile(audioFile);
        
        String transcriptId = requestTranscription(audioUrl);
        
        return pollForTranscription(transcriptId);
    }

    private String uploadAudioFile(MultipartFile audioFile) throws Exception {
        try {
            ByteArrayResource resource = new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return audioFile.getOriginalFilename();
                }
            };

            String response = webClient.post()
                    .uri(ASSEMBLYAI_UPLOAD_URL)
                    .header("authorization", apiKey)
                    .body(BodyInserters.fromResource(resource))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMinutes(2));

            JsonNode jsonResponse = objectMapper.readTree(response);
            System.out.println("Response from AssemplyAI "+jsonResponse);
            return jsonResponse.get("upload_url").asText();
        } catch (Exception e) {
            throw new Exception("Failed to upload audio file to AssemblyAI: " + e.getMessage());
        }
    }

    private String requestTranscription(String audioUrl) throws Exception {
        try {
            String requestBody = objectMapper.writeValueAsString(new TranscriptRequest(audioUrl));

            String response = webClient.post()
                    .uri(ASSEMBLYAI_TRANSCRIPT_URL)
                    .header("authorization", apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));

            JsonNode jsonResponse = objectMapper.readTree(response);
            return jsonResponse.get("id").asText();
        } catch (Exception e) {
            throw new Exception("Failed to request transcription: " + e.getMessage());
        }
    }

    private String pollForTranscription(String transcriptId) throws Exception {
        int maxAttempts = 60;
        int attemptCount = 0;

        while (attemptCount < maxAttempts) {
            try {
                String response = webClient.get()
                        .uri(ASSEMBLYAI_TRANSCRIPT_URL + "/" + transcriptId)
                        .header("authorization", apiKey)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(30));

                JsonNode jsonResponse = objectMapper.readTree(response);
                String status = jsonResponse.get("status").asText();

                System.out.println("Transcription status: " + status);

                if ("completed".equals(status)) {
                    String text = jsonResponse.get("text").asText();
                    System.out.println("Transcription completed successfully");
                    return text;
                } else if ("error".equals(status)) {
                    String error = jsonResponse.get("error").asText();
                    throw new Exception("Transcription failed: " + error);
                }

                Thread.sleep(2000);
                attemptCount++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Exception("Transcription polling interrupted");
            } catch (Exception e) {
                throw new Exception("Failed to poll transcription status: " + e.getMessage());
            }
        }

        throw new Exception("Transcription timed out after " + (maxAttempts * 2) + " seconds");
    }

    private static class TranscriptRequest {
        public String audio_url;

        public TranscriptRequest(String audioUrl) {
            this.audio_url = audioUrl;
        }
    }
}