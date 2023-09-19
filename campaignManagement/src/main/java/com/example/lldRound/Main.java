package com.example.lldRound;

import com.example.lldRound.Enum.CommunicationChannel;
import com.example.lldRound.model.Communication;
import com.example.lldRound.model.CommunicationCampaign;
import com.example.lldRound.services.serviceImpl.CommunicationCampaignService;

import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        try {
            CommunicationCampaign campaign = getCommunicationCampaign();

            CommunicationCampaignService communicationCampaignService = new CommunicationCampaignService();

            // Create the campaign
            communicationCampaignService.createCampaign(campaign);

            // Evaluate the campaign and get the execution order
            List<Communication> executionOrder = communicationCampaignService.evaluateCampaign("1");

            // Print the execution order
            for (Communication communicationInfo : executionOrder) {
                System.out.println(communicationInfo);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }

    }

    private static CommunicationCampaign getCommunicationCampaign() {
        Communication c1 = new Communication("A", CommunicationChannel.EMAIL, "Subject A", "Message A");
        Communication c2 = new Communication("B", CommunicationChannel.SMS, null, "Message B");
        Communication c3 = new Communication("C", CommunicationChannel.SMS, null, "Message C");
        Communication c4 = new Communication("D", CommunicationChannel.SMS, null, "Message D");

        // A -> B in example here it means this
        // B.addDependency(A);  // which are all the communications which will be executed before me.
        // a- b
        // b -c
        // a -c
        // b -a
        c2.addDependency(c1);  // a- b
        c3.addDependency(c2);  // b -c
        c3.addDependency(c1);  // a -c
//        c1.addDependency(c2);  // b -a


        CommunicationCampaign campaign = new CommunicationCampaign("1",
                "Sample Communication Campaign", "This is a sample campaign.",
                Arrays.asList(c1, c2, c3, c4));
        return campaign;
    }
}
