package org.systemdesign.splitwise.service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.systemdesign.splitwise.exception.ValidationException;
import org.systemdesign.splitwise.model.CreateExpenseRequest;
import org.systemdesign.splitwise.model.Expense;
import org.systemdesign.splitwise.model.ExpenseShare;
import org.systemdesign.splitwise.model.Group;
import org.systemdesign.splitwise.repository.ExpenseRepository;
import org.systemdesign.splitwise.strategy.SplitStrategyFactory;

public final class ExpenseService {
    private final ExpenseRepository expenseRepository;
    private final UserService userService;
    private final GroupService groupService;
    private final SplitStrategyFactory splitStrategyFactory;
    private final AtomicInteger expenseSequence = new AtomicInteger(1);

    public ExpenseService(
        ExpenseRepository expenseRepository,
        UserService userService,
        GroupService groupService,
        SplitStrategyFactory splitStrategyFactory
    ) {
        this.expenseRepository = expenseRepository;
        this.userService = userService;
        this.groupService = groupService;
        this.splitStrategyFactory = splitStrategyFactory;
    }

    public Expense addExpense(CreateExpenseRequest request) {
        userService.getRequiredUser(request.paidByUserId());
        LinkedHashSet<String> distinctParticipants = normalizeParticipants(request.participants());
        distinctParticipants.forEach(userService::getRequiredUser);

        if (request.groupId() != null) {
            validateGroupExpense(request, distinctParticipants, groupService.getRequiredGroup(request.groupId()));
        }

        List<ExpenseShare> shares = splitStrategyFactory.getStrategy(request.splitType())
            .split(request.totalAmountInCents(), List.copyOf(distinctParticipants), request.splitInputs());

        Expense expense = new Expense(
            nextExpenseId(),
            request.description(),
            request.paidByUserId(),
            request.totalAmountInCents(),
            shares,
            request.splitType(),
            request.groupId(),
            Instant.now()
        );
        return expenseRepository.save(expense);
    }

    public List<Expense> getAllExpenses() {
        return expenseRepository.findAll();
    }

    public List<Expense> getExpensesByGroup(String groupId) {
        groupService.getRequiredGroup(groupId);
        return expenseRepository.findByGroupId(groupId);
    }

    private LinkedHashSet<String> normalizeParticipants(List<String> participants) {
        LinkedHashSet<String> distinctParticipants = new LinkedHashSet<>();
        for (String participant : participants) {
            if (participant == null || participant.isBlank()) {
                throw new ValidationException("participant id cannot be blank");
            }
            if (!distinctParticipants.add(participant.trim())) {
                throw new ValidationException("duplicate participant: " + participant);
            }
        }
        return distinctParticipants;
    }

    private void validateGroupExpense(CreateExpenseRequest request, LinkedHashSet<String> participants, Group group) {
        if (!group.hasMember(request.paidByUserId())) {
            throw new ValidationException("payer must be part of the group");
        }

        for (String participant : participants) {
            if (!group.hasMember(participant)) {
                throw new ValidationException("participant " + participant + " is not in group " + group.id());
            }
        }
    }

    private String nextExpenseId() {
        return "EXP-" + expenseSequence.getAndIncrement();
    }
}

