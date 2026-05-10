package org.systemdesign.splitwise.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.systemdesign.splitwise.model.Expense;

public final class InMemoryExpenseRepository implements ExpenseRepository {
    private final List<Expense> expenses = new ArrayList<>();

    @Override
    public synchronized Expense save(Expense expense) {
        expenses.add(expense);
        return expense;
    }

    @Override
    public synchronized List<Expense> findAll() {
        return new ArrayList<>(expenses);
    }

    @Override
    public synchronized List<Expense> findByGroupId(String groupId) {
        return expenses.stream()
            .filter(expense -> groupId.equals(expense.groupId()))
            .collect(Collectors.toCollection(ArrayList::new));
    }
}

