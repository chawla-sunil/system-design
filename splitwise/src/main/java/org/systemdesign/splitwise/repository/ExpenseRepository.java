package org.systemdesign.splitwise.repository;

import java.util.List;
import org.systemdesign.splitwise.model.Expense;

public interface ExpenseRepository {

    Expense save(Expense expense);

    List<Expense> findAll();

    List<Expense> findByGroupId(String groupId);
}

