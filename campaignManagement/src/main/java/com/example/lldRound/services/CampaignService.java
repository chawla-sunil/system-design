package com.example.lldRound.services;

import com.example.lldRound.model.Campaign;
import com.example.lldRound.model.Communication;
import com.example.lldRound.model.CommunicationCampaign;

import java.util.List;

public interface CampaignService {
    void createCampaign(CommunicationCampaign campaign) throws Exception;
    Campaign getCampaign(String campaignId);
    List<Communication> evaluateCampaign(String campaignId);
}
