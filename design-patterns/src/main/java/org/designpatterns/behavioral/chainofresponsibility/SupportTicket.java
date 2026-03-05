package org.designpatterns.behavioral.chainofresponsibility;

public class SupportTicket {
    public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }

    private final String issue;
    private final Priority priority;

    public SupportTicket(String issue, Priority priority) {
        this.issue = issue;
        this.priority = priority;
    }

    public String getIssue() { return issue; }
    public Priority getPriority() { return priority; }
}
