package com.example.lldRound.model;

import java.util.List;

public class CommunicationCampaign extends Campaign {
    List<Communication> communications;
    public CommunicationCampaign(String campaignId, String name, String description, List<Communication> communications) {
        super(campaignId, name, description);
        this.communications = communications;
    }

    public void addCommunication(Communication communication) { communications.add(communication); }

    public List<Communication> getCommunications() { return communications; }




}
