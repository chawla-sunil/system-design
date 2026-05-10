package org.systemdesign.splitwise;

import java.util.List;
import org.systemdesign.splitwise.model.CreateExpenseRequest;
import org.systemdesign.splitwise.model.SplitInput;
import org.systemdesign.splitwise.model.SplitType;

public class Main {

    public static void main(String[] args) {
        SplitwiseService splitwiseService = new SplitwiseService();

        splitwiseService.addUser("u1", "Aman", "aman@example.com");
        splitwiseService.addUser("u2", "Bhavna", "bhavna@example.com");
        splitwiseService.addUser("u3", "Charu", "charu@example.com");
        splitwiseService.addUser("u4", "Deepak", "deepak@example.com");

        splitwiseService.createGroup("g1", "Goa Trip", List.of("u1", "u2", "u3", "u4"));

        splitwiseService.addExpense(new CreateExpenseRequest(
            "Dinner on the first night",
            "u1",
            4_000,
            List.of("u1", "u2", "u3", "u4"),
            SplitType.EQUAL,
            List.of(),
            "g1"
        ));

        splitwiseService.addExpense(new CreateExpenseRequest(
            "Airport cab",
            "u2",
            1_250,
            List.of("u1", "u2", "u3"),
            SplitType.EXACT,
            List.of(
                new SplitInput("u1", 300),
                new SplitInput("u2", 450),
                new SplitInput("u3", 500)
            ),
            "g1"
        ));

        splitwiseService.addExpense(new CreateExpenseRequest(
            "Resort booking",
            "u3",
            5_000,
            List.of("u1", "u2", "u3", "u4"),
            SplitType.PERCENTAGE,
            List.of(
                new SplitInput("u1", 40),
                new SplitInput("u2", 20),
                new SplitInput("u3", 20),
                new SplitInput("u4", 20)
            ),
            "g1"
        ));

        splitwiseService.addExpense(new CreateExpenseRequest(
            "Coffee outside the group",
            "u4",
            450,
            List.of("u1", "u4"),
            SplitType.EQUAL,
            List.of(),
            null
        ));

        printSection("Overall balances", splitwiseService.describeAllBalances());
        printSection("Balances for Goa Trip group", splitwiseService.describeGroupBalances("g1"));
        printSection("Balances for Aman", splitwiseService.describeBalancesForUser("u1"));
        printSection("Simplified settlement plan", splitwiseService.describeSettlementPlan());
    }

    private static void printSection(String title, List<String> lines) {
        System.out.println("\n=== " + title + " ===");
        lines.forEach(System.out::println);
    }
}