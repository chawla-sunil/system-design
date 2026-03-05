package org.designpatterns.behavioral.chainofresponsibility;

public class BasicSupportHandler extends SupportHandler {
    public BasicSupportHandler() { super("BasicSupport"); }

    @Override
    protected boolean canHandle(SupportTicket ticket) {
        return ticket.getPriority() == SupportTicket.Priority.LOW;
    }

    @Override
    protected void processTicket(SupportTicket ticket) {
        System.out.println("  [BasicSupport] Resolved: " + ticket.getIssue() + " (FAQ/Knowledge Base)");
    }
}
