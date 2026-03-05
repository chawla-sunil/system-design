package org.designpatterns.behavioral.chainofresponsibility;

public class TechnicalSupportHandler extends SupportHandler {
    public TechnicalSupportHandler() { super("TechSupport"); }

    @Override
    protected boolean canHandle(SupportTicket ticket) {
        return ticket.getPriority() == SupportTicket.Priority.MEDIUM ||
               ticket.getPriority() == SupportTicket.Priority.HIGH;
    }

    @Override
    protected void processTicket(SupportTicket ticket) {
        System.out.println("  [TechSupport] Investigating: " + ticket.getIssue() + " (Technical diagnosis)");
    }
}
