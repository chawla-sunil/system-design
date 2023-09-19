package com.example.lldRound.model;

public class Campaign {
    String campaignId;
    String name;
    String description;

    public Campaign(String campaignId, String name, String description) {
        this.campaignId = campaignId;
        this.name = name;
        this.description = description;
    }

    public Campaign() {
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
