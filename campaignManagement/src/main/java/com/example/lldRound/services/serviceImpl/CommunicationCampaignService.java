package com.example.lldRound.services.serviceImpl;

import com.example.lldRound.model.Campaign;
import com.example.lldRound.model.Communication;
import com.example.lldRound.model.CommunicationCampaign;
import com.example.lldRound.services.CampaignService;
import com.example.lldRound.utils.CommunicationDependency;

import java.util.*;

public class CommunicationCampaignService implements CampaignService {

    Map<String, CommunicationCampaign> campaigns = new HashMap<>();

    @Override
    public void createCampaign(CommunicationCampaign campaign) throws Exception {
        validateCampaign(campaign);
        campaigns.put(campaign.getCampaignId(), campaign);
    }

    @Override
    public Campaign getCampaign(String campaignId) {
        if (campaigns.containsKey(campaignId)) {
            return campaigns.get(campaignId);
        } else {
            throw new NoSuchElementException("Campaign not found.");
        }
    }

    @Override
    public List<Communication> evaluateCampaign(String campaignId) {
        int rank = 1;
        CommunicationCampaign campaign = campaigns.get(campaignId);
        if (campaign == null) {
            throw new IllegalArgumentException("Campaign not found");
        }

        List<Communication> orderedCommunications = new ArrayList<>();
        List<Communication> unprocessed = new ArrayList<>(campaign.getCommunications());

        while (!unprocessed.isEmpty()) {
            boolean added = false;
            Iterator<Communication> iterator = unprocessed.iterator();
            while (iterator.hasNext()) {
                Communication communication = iterator.next();
                boolean canAdd = true;
                for (CommunicationDependency dependency : communication.getDependencyList()) {
                    if (!orderedCommunications.contains(dependency.getCommunication())) {
                        canAdd = false;
                        break;
                    }
                }
                if (canAdd) {
                    communication.setRank(rank++);
                    orderedCommunications.add(communication);
                    iterator.remove();
                    added = true;
                }
            }
            if (!added) {
                throw new IllegalStateException("Communication order cannot be placed due to dependencies.");
            }
        }
        return orderedCommunications;
    }


    private void validateCampaign(CommunicationCampaign campaign) throws Exception {
        if (campaign == null || campaign.getCampaignId() == null || campaign.getName() == null || campaign.getDescription() == null) {
            throw new IllegalArgumentException("Invalid campaign data");
        }

        List<Communication> communications = campaign.getCommunications();
        for (int i = 0; i < communications.size(); i++) {
            Communication communication = communications.get(i);
            List<CommunicationDependency> dependencies = communication.getDependencyList();
            for (CommunicationDependency dependency : dependencies) {
                if (!communications.contains(dependency.getCommunication())) {
                    throw new IllegalArgumentException("Invalid communication order: " + communication.getCommunicationId() +
                            " depends on " + dependency.getCommunication().getCommunicationId() + " which is not defined earlier.");
                }
            }
        }
    }
}
