package com.example.lldRound.utils;

import com.example.lldRound.model.Communication;

public class CommunicationDependency {
    private final Communication communication;

    public CommunicationDependency(Communication communication) {
        this.communication = communication;
    }

    public Communication getCommunication() {
        return communication;
    }
}