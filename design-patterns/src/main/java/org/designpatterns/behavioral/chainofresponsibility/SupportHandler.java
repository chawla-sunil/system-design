package org.designpatterns.behavioral.chainofresponsibility;

public abstract class SupportHandler {
    private SupportHandler nextHandler;
    protected final String handlerName;

    public SupportHandler(String handlerName) {
        this.handlerName = handlerName;
    }

    public SupportHandler setNext(SupportHandler next) {
        this.nextHandler = next;
        return next; // enables chaining
    }

    public void handle(SupportTicket ticket) {
        if (canHandle(ticket)) {
            processTicket(ticket);
        } else if (nextHandler != null) {
            System.out.println("  [" + handlerName + "] Cannot handle '" + ticket.getIssue() + "' (priority: " + ticket.getPriority() + "). Escalating...");
            nextHandler.handle(ticket);
        } else {
            System.out.println("  [" + handlerName + "] No handler available for: " + ticket.getIssue());
        }
    }

    protected abstract boolean canHandle(SupportTicket ticket);
    protected abstract void processTicket(SupportTicket ticket);
}
