package com.example.lldRound.model;

import com.example.lldRound.Enum.CommunicationChannel;
import com.example.lldRound.utils.CommunicationDependency;

import java.util.ArrayList;
import java.util.List;

public class Communication {
    private  int rank;
    private String communicationId;
    private CommunicationChannel channel;
    private String subject;
    private String message;

    private List<CommunicationDependency> dependencyList;

    public List<CommunicationDependency> getDependencyList() {
        return dependencyList;
    }

    public Communication(String communicationId, CommunicationChannel channel, String subject, String message) {
        this.communicationId = communicationId;
        this.channel = channel;
        this.subject = subject;
        this.message = message;
        dependencyList = new ArrayList<>();
    }

    public String getCommunicationId() {
        return communicationId;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public void setCommunicationId(String communicationId) {
        this.communicationId = communicationId;
    }

    public CommunicationChannel getChannel() {
        return channel;
    }

    public void setChannel(CommunicationChannel channel) {
        this.channel = channel;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "Communication{" +
                "rank=" + rank +
                ", communicationId='" + communicationId + '\'' +
                ", channel=" + channel +
                ", subject='" + subject + '\'' +
                ", message='" + message + '\'' +
                '}';
    }

    public void addDependency(Communication c) {
        dependencyList.add(new CommunicationDependency(c));
    }
}
