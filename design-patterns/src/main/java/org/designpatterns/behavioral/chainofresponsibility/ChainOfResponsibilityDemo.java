package org.designpatterns.behavioral.chainofresponsibility;

import java.util.List;

public class ChainOfResponsibilityDemo {
    public static void run() {
        System.out.println("=== CHAIN OF RESPONSIBILITY PATTERN DEMO ===\n");

        // Build the chain
        SupportHandler basic = new BasicSupportHandler();
        SupportHandler tech = new TechnicalSupportHandler();
        SupportHandler manager = new ManagerHandler();
        basic.setNext(tech).setNext(manager);

        // Create tickets with different priorities
        List<SupportTicket> tickets = List.of(
            new SupportTicket("Password reset", SupportTicket.Priority.LOW),
            new SupportTicket("App crashes on login", SupportTicket.Priority.HIGH),
            new SupportTicket("How to export data?", SupportTicket.Priority.LOW),
            new SupportTicket("Data breach detected", SupportTicket.Priority.CRITICAL),
            new SupportTicket("Slow performance", SupportTicket.Priority.MEDIUM)
        );

        // Each ticket enters at the start of the chain
        for (SupportTicket ticket : tickets) {
            System.out.println("Ticket: \"" + ticket.getIssue() + "\" [" + ticket.getPriority() + "]");
            basic.handle(ticket);
            System.out.println();
        }
    }
}
