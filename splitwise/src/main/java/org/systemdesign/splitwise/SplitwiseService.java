package org.systemdesign.splitwise;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.systemdesign.splitwise.model.BalanceEntry;
import org.systemdesign.splitwise.model.CreateExpenseRequest;
import org.systemdesign.splitwise.model.Expense;
import org.systemdesign.splitwise.model.Group;
import org.systemdesign.splitwise.model.Settlement;
import org.systemdesign.splitwise.model.User;
import org.systemdesign.splitwise.repository.ExpenseRepository;
import org.systemdesign.splitwise.repository.GroupRepository;
import org.systemdesign.splitwise.repository.InMemoryExpenseRepository;
import org.systemdesign.splitwise.repository.InMemoryGroupRepository;
import org.systemdesign.splitwise.repository.InMemoryUserRepository;
import org.systemdesign.splitwise.repository.UserRepository;
import org.systemdesign.splitwise.service.BalanceService;
import org.systemdesign.splitwise.service.ExpenseService;
import org.systemdesign.splitwise.service.GroupService;
import org.systemdesign.splitwise.service.SettlementService;
import org.systemdesign.splitwise.service.UserService;
import org.systemdesign.splitwise.strategy.SplitStrategyFactory;

public final class SplitwiseService {
    private final UserService userService;
    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final BalanceService balanceService;
    private final SettlementService settlementService;

    public SplitwiseService() {
        UserRepository userRepository = new InMemoryUserRepository();
        GroupRepository groupRepository = new InMemoryGroupRepository();
        ExpenseRepository expenseRepository = new InMemoryExpenseRepository();

        this.userService = new UserService(userRepository);
        this.groupService = new GroupService(groupRepository, userService);
        this.balanceService = new BalanceService();
        this.settlementService = new SettlementService(balanceService);
        this.expenseService = new ExpenseService(expenseRepository, userService, groupService, new SplitStrategyFactory());
    }

    public User addUser(String id, String name, String email) {
        return userService.registerUser(id, name, email);
    }

    public Group createGroup(String id, String name, Collection<String> memberIds) {
        return groupService.createGroup(id, name, memberIds);
    }

    public void addMemberToGroup(String groupId, String userId) {
        groupService.addMember(groupId, userId);
    }

    public Expense addExpense(CreateExpenseRequest request) {
        return expenseService.addExpense(request);
    }

    public List<BalanceEntry> getAllBalances() {
        return balanceService.calculateBalances(expenseService.getAllExpenses());
    }

    public List<BalanceEntry> getBalancesForGroup(String groupId) {
        return balanceService.calculateBalances(expenseService.getExpensesByGroup(groupId));
    }

    public List<BalanceEntry> getBalancesForUser(String userId) {
        userService.getRequiredUser(userId);
        return getAllBalances().stream()
            .filter(balance -> balance.debtorUserId().equals(userId) || balance.creditorUserId().equals(userId))
            .collect(Collectors.toList());
    }

    public List<Settlement> simplifyAllBalances() {
        return settlementService.simplify(getAllBalances());
    }

    public List<Settlement> simplifyGroupBalances(String groupId) {
        return settlementService.simplify(getBalancesForGroup(groupId));
    }

    public List<String> describeAllBalances() {
        return describeBalances(getAllBalances());
    }

    public List<String> describeBalancesForUser(String userId) {
        return describeBalances(getBalancesForUser(userId));
    }

    public List<String> describeGroupBalances(String groupId) {
        groupService.getRequiredGroup(groupId);
        return describeBalances(getBalancesForGroup(groupId));
    }

    public List<String> describeSettlementPlan() {
        return describeSettlements(simplifyAllBalances());
    }

    public List<String> describeGroupSettlementPlan(String groupId) {
        groupService.getRequiredGroup(groupId);
        return describeSettlements(simplifyGroupBalances(groupId));
    }

    public String displayName(String userId) {
        return userService.getRequiredUser(userId).name();
    }

    private List<String> describeBalances(List<BalanceEntry> balances) {
        if (balances.isEmpty()) {
            return List.of("No balances");
        }
        return balances.stream()
            .map(balance -> displayName(balance.debtorUserId()) + " owes "
                + displayName(balance.creditorUserId()) + " " + formatMoney(balance.amountInCents()))
            .collect(Collectors.toList());
    }

    private List<String> describeSettlements(List<Settlement> settlements) {
        if (settlements.isEmpty()) {
            return List.of("No settlements required");
        }
        return settlements.stream()
            .map(settlement -> displayName(settlement.fromUserId()) + " pays "
                + displayName(settlement.toUserId()) + " " + formatMoney(settlement.amountInCents()))
            .collect(Collectors.toList());
    }

    private String formatMoney(long amountInCents) {
        return String.format("₹%.2f", amountInCents / 100.0);
    }
}

