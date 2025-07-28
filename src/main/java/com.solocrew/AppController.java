package com.solocrew;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class AppController {

    @Autowired
    AppService service;

    @PostMapping("/audio")
    public ResponseEntity<String> processAudio(@RequestParam("file") MultipartFile audioFile, 
                                             @RequestParam("uuid") String conversationUuid) {
        try {
            String result = service.processAudioFile(audioFile, conversationUuid);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error processing audio file: " + e.getMessage());
        }
    }

    @GetMapping("/text-to-speech")
    public ResponseEntity<byte[]> textToSpeech(@RequestParam("text") String text) {
        try {
            byte[] audioData = service.convertTextToSpeech(text);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
            headers.setContentDispositionFormData("attachment", "speech.mp3");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(audioData);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

}
