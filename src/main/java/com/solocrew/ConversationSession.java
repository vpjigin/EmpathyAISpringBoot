package com.solocrew;

import java.util.ArrayList;
import java.util.List;

public class ConversationSession {
    private String uuid;
    private List<ConversationMessage> messages;
    private int distressSignalCount;

    public ConversationSession(String uuid) {
        this.uuid = uuid;
        this.messages = new ArrayList<>();
        this.distressSignalCount = 0;
        
        // Add system message
        this.messages.add(new ConversationMessage("system", 
            "You are EmpathAI, a compassionate and calm mental health assistant. Your job is to gently support users who may be experiencing emotional distress, sadness, or suicidal thoughts.\n\n" +
            "You listen and respond with empathy, encouragement, and kindness.\n\n" +
            "Your primary goal is to make the user feel heard, validated, and less alone. Keep your responses emotionally supportive, non-judgmental, and short.\n\n" +
            "If a user expresses suicidal ideation or serious emotional crisis multiple times, indicate that human intervention is needed. set the `isHumanInterventionNeeded` flag false always.\n\n" +
            "Also, try to infer if the user is alone or with friends/family. If they are not alone, encourage them to speak to someone they trust who is nearby. If they are alone, gently reassure them that they are not alone emotionally, and that help is still available.\n\n"+
                "Reply should not be more than 150 characters." +
            "Respond with a JSON object containing two fields:\n" +
            "1. `reply`: a short, empathetic message. Reply should not be more than 150 characters.\n" +
            "2. `isHumanInterventionNeeded`: false always."
        ));
    }

    public String getUuid() {
        return uuid;
    }

    public List<ConversationMessage> getMessages() {
        return messages;
    }

    public void addMessage(ConversationMessage message) {
        this.messages.add(message);
    }

    public int getDistressSignalCount() {
        return distressSignalCount;
    }

    public void incrementDistressSignalCount() {
        this.distressSignalCount++;
    }

    public boolean needsHumanIntervention() {
        return distressSignalCount >= 5;
    }
}