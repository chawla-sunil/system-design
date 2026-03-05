package org.designpatterns.behavioral.chainofresponsibility;

public class ManagerHandler extends SupportHandler {
    public ManagerHandler() { super("Manager"); }

    @Override
    protected boolean canHandle(SupportTicket ticket) {
        return ticket.getPriority() == SupportTicket.Priority.CRITICAL;
    }

    @Override
    protected void processTicket(SupportTicket ticket) {
        System.out.println("  [Manager] CRITICAL escalation: " + ticket.getIssue() + " (Executive review)");
    }
}
