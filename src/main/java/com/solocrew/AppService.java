package com.solocrew;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class AppService {

    @Autowired
    private AssemblyAIService assemblyAIService;

    @Autowired
    private OpenAITTSService openAITTSService;

    @Autowired
    private OpenAIChatService openAIChatService;

    // Store conversations in memory (in production, use a database)
    private final ConcurrentHashMap<String, ConversationSession> conversations = new ConcurrentHashMap<>();

    public String processAudioFile(MultipartFile audioFile, String conversationUuid) throws Exception {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new Exception("No audio file provided");
        }

        if (!isValidAudioFile(audioFile)) {
            throw new Exception("Invalid audio file format");
        }

        System.out.println("Valid audio file received:");
        System.out.println("- Filename: " + audioFile.getOriginalFilename());
        System.out.println("- Size: " + audioFile.getSize() + " bytes");
        System.out.println("- Content Type: " + audioFile.getContentType());

        try {
            System.out.println("Starting transcription with AssemblyAI...");
            String transcription = assemblyAIService.transcribeAudio(audioFile);
            System.out.println("Transcription completed: " + transcription);
            
            // Get or create conversation session
            ConversationSession session = conversations.computeIfAbsent(conversationUuid, ConversationSession::new);
            
            // Add user message to conversation
            session.addMessage(new ConversationMessage("user", transcription));
            
            // Generate empathy response using OpenAI
            OpenAIChatService.EmpathyResponse empathyResponse = openAIChatService.generateEmpathyResponse(session.getMessages());
            
            // Add assistant response to conversation
            session.addMessage(new ConversationMessage("assistant", empathyResponse.getReply()));
            
            // Check if response indicates distress and update counter
            boolean needsHumanIntervention = empathyResponse.isHumanInterventionNeeded() || session.needsHumanIntervention();
            
            return "{\"status\": \"success\", \"message\": \"Audio transcribed successfully\", \"filename\": \"" + 
                   audioFile.getOriginalFilename() + "\", \"transcription\": \"" + 
                   transcription.replace("\"", "\\\"") + "\", \"needHumanIntervention\": " + needsHumanIntervention + 
                   ", \"transcriptionReply\": \"" + empathyResponse.getReply().replace("\"", "\\\"") + "\"}";
        } catch (Exception e) {
            System.err.println("Transcription failed: " + e.getMessage());
            throw new Exception("Failed to transcribe audio: " + e.getMessage());
        }
    }

    private boolean isValidAudioFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            return false;
        }

        return contentType.startsWith("audio/") || 
               contentType.equals("application/octet-stream") ||
               isValidAudioExtension(file.getOriginalFilename());
    }

    private boolean isValidAudioExtension(String filename) {
        if (filename == null) {
            return false;
        }
        
        String lowerFilename = filename.toLowerCase();
        return lowerFilename.endsWith(".mp3") || 
               lowerFilename.endsWith(".wav") || 
               lowerFilename.endsWith(".m4a") || 
               lowerFilename.endsWith(".flac") || 
               lowerFilename.endsWith(".ogg") || 
               lowerFilename.endsWith(".aac");
    }

    public byte[] convertTextToSpeech(String text) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            throw new Exception("No text provided for conversion");
        }

        if (text.length() > 4096) {
            throw new Exception("Text too long. Maximum 4096 characters allowed.");
        }

        System.out.println("Converting text to speech: " + text.substring(0, Math.min(text.length(), 100)) + "...");

        try {
            byte[] audioData = openAITTSService.generateSpeech(text);
            System.out.println("Text-to-speech conversion completed successfully");
            return audioData;
        } catch (Exception e) {
            System.err.println("Text-to-speech conversion failed: " + e.getMessage());
            throw new Exception("Failed to convert text to speech: " + e.getMessage());
        }
    }
}
