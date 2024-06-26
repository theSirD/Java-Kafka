package ru.isaev.BankService;

import Lab1.BankAccounts.*;
import Lab1.Transactions.*;
import Lab1.src.Transactions.*;
import ru.isaev.User.BankAccountStatus;
import ru.isaev.User.User;
import ru.isaev.User.UserStatus;
import Lab1.src.BankAccounts.*;
import ru.isaev.CentralBankService.CentralBankService;
import ru.isaev.Utilities.CustomTuple;

import java.util.*;
public class BankService {
    private CentralBankService _centralBank;
    private Double _creditLimit;
    private Double _commission;
    private Double _monthlyInterestInPercent;
    private Long _daysUntilExpirationOfDepositAccount;
    private Double _limitForUntrustworthyAccounts;

    private Map<Long, User> _usersList = new HashMap<>();

    public Map<Long, BaseBankAccount> bankAccountsList = new HashMap<>();

    private BaseTransaction _currentTransaction;

    public BankService(Double creditLimit, Double commissionForNegativeCreditAccount, Double monthlyInterestInPercent, Long daysUntilExpirationOfDepositAccount, Double limitForUntrustworthyAccounts, Double commission, CentralBankService centralBank) {
        _creditLimit = creditLimit;
        _commission = commissionForNegativeCreditAccount;
        _monthlyInterestInPercent = monthlyInterestInPercent;
        _daysUntilExpirationOfDepositAccount = daysUntilExpirationOfDepositAccount;
        _limitForUntrustworthyAccounts = limitForUntrustworthyAccounts;
        _centralBank = centralBank;
    }

    public void registerUser(User user) {
        _usersList.put(Long.valueOf(_usersList.size() + 1), user);
        user.addBank(this);
    }

    public void createBankAccount(User user, BankAccountType typeOfAccount, Double initialBalance) {
        Optional<Map.Entry<Long, User>> userFromStorage = _usersList
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(user))
                .findFirst();

        Long idOfUser = userFromStorage.get().getKey();

        BankAccountStatus statusOfAccount;
        Boolean isAccountTrustworthy;
        if (user.status == UserStatus.Complete) {
            isAccountTrustworthy = true;
            statusOfAccount = BankAccountStatus.Trustworthy;
        }
        else {
            isAccountTrustworthy = false;
            statusOfAccount = BankAccountStatus.NotTrustworthy;
        }


        BaseBankAccount account = switch (typeOfAccount) {
            case BankAccountType.Credit ->
                    new CreditBankAccount(isAccountTrustworthy ? null : _limitForUntrustworthyAccounts, statusOfAccount, idOfUser, initialBalance, _creditLimit, _commission);
            case BankAccountType.Debit ->
                    new DebitBankAccount(isAccountTrustworthy ? null : _limitForUntrustworthyAccounts, statusOfAccount, idOfUser, initialBalance, _monthlyInterestInPercent);
            case BankAccountType.Deposit ->
                    new DepositBankAccount(isAccountTrustworthy ? null : _limitForUntrustworthyAccounts, statusOfAccount, idOfUser, _daysUntilExpirationOfDepositAccount, initialBalance, _monthlyInterestInPercent);
        };

        bankAccountsList.put(Long.valueOf(bankAccountsList.size() + 1), account);
    }

    public void updatePersonalInfo(User user, String address, String passport) {
        if (address == null && passport == null)
            return;

       switch (user.status) {
           case NoAddress:
               user.address = address;
               user.status = UserStatus.Complete;
               break;
           case NoPassport:
               user.passport = passport;
               user.status = UserStatus.Complete;
               break;
           case NoAddressAndPassport:
               user.address = address;
               user.passport = passport;
               user.status = UserStatus.Complete;
               break;
       }

        Optional<Map.Entry<Long, User>> userFromStorage = _usersList
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(user))
                .findFirst();

        Long idOfUser = userFromStorage.get().getKey();

        updateStatusOfBankAccountsToTrustworthy(idOfUser);
    }

    private void updateStatusOfBankAccountsToTrustworthy(Long userId) {
        for (Map.Entry<Long, BaseBankAccount> account : bankAccountsList.entrySet()) {
            if (Objects.equals(account.getValue().userId, userId)) {
                account.getValue().status = BankAccountStatus.Trustworthy;
                account.getValue().limitForUntrustworthyAccounts = 0.0;
            }
        }
    }

    public void updateMonthlyInterestInPercent(Double newMonthlyInterestInPercent) {
        _monthlyInterestInPercent = newMonthlyInterestInPercent;

        for (Map.Entry<Long, BaseBankAccount> account : bankAccountsList.entrySet()) {
            account.getValue().monthlyInterestInPercent = _monthlyInterestInPercent;
        }
    }

    public void updateCommission(Double newCommission) {
        _commission = newCommission;

        for (Map.Entry<Long, BaseBankAccount> account : bankAccountsList.entrySet()) {
            account.getValue().commission = _commission;
        }
    }

    public void updateLimitForUntrustworthyAccounts(Double newLimitForUntrustworthyAccounts) {
        _limitForUntrustworthyAccounts = newLimitForUntrustworthyAccounts;

        for (Map.Entry<Long, BaseBankAccount> account : bankAccountsList.entrySet()) {
            account.getValue().limitForUntrustworthyAccounts = _limitForUntrustworthyAccounts;
        }
    }

    public void updateCreditLimit(Double newCreditLimit) {
        _creditLimit = newCreditLimit;

        for (Map.Entry<Long, BaseBankAccount> account : bankAccountsList.entrySet()) {
            if (account.getValue().type == BankAccountType.Credit)
                account.getValue().creditLimit = newCreditLimit;
        }
    }

    public void transferMoneyBetweenAccounts(Long senderBankId, Long senderAccountId, Long receiverBankId, Long receiverAccountId, Double amountOfMoney) {
        Optional<Map.Entry<Long, BaseBankAccount>> senderAccountFromStorage = bankAccountsList
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(senderAccountId))
                .findFirst();

        BaseBankAccount senderAccount = senderAccountFromStorage.get().getValue();

        if (!Objects.equals(senderBankId, receiverBankId)) {
            _centralBank.transferMoneyBetweenAccountsOfDifferentBanks(senderAccount, receiverBankId, receiverAccountId, amountOfMoney);
            return;
        }

        Optional<Map.Entry<Long, BaseBankAccount>> receiverAccountFromStorage = bankAccountsList
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(receiverAccountId))
                .findFirst();

        BaseBankAccount receiverAccount = senderAccountFromStorage.get().getValue();

        setTransaction(new TransferMoneyBetweenAccountsTransaction(receiverAccount, senderAccount, amountOfMoney));

        executeTransaction();

    }

    public void removeMoneyFromAccount(Long accountId, Double amountOfMoney) {
        Optional<Map.Entry<Long, BaseBankAccount>> AccountFromStorage = bankAccountsList
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(accountId))
                .findFirst();

        BaseBankAccount account = AccountFromStorage.get().getValue();

        setTransaction(new RemoveMoneyTransaction(account, amountOfMoney));

        executeTransaction();
    }

    public void addMoneyToAccount(Long accountId, Double amountOfMoney) {
        Optional<Map.Entry<Long, BaseBankAccount>> AccountFromStorage = bankAccountsList
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(accountId))
                .findFirst();

        BaseBankAccount account = AccountFromStorage.get().getValue();

        setTransaction(new AddMoneyTransaction(account, amountOfMoney));

        executeTransaction();
    }

    private void setTransaction(BaseTransaction transaction) {
        _currentTransaction = transaction;
    }

    private void executeTransaction() {
        _currentTransaction.execute();
    }

    public void CancelTransaction(Long idOfBankAccount, Long idOfTransaction) {
        Optional<Map.Entry<Long, BaseBankAccount>> AccountFromStorage = bankAccountsList
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(idOfBankAccount))
                .findFirst();

        BaseBankAccount account = AccountFromStorage.get().getValue();

        Stack<CustomTuple<Long, BaseTransaction>> transactionsAfterTheCanceledOneList = new Stack<>();
        CustomTuple<Long, BaseTransaction> currentTransaction = account.transactionsHistory.peek();
        while (!Objects.equals(currentTransaction.getFirst(), idOfTransaction)) {
            currentTransaction.getSecond().undo();
            transactionsAfterTheCanceledOneList.push(currentTransaction);
            currentTransaction = account.transactionsHistory.peek();
        }

        currentTransaction.getSecond().undo();

        while (!transactionsAfterTheCanceledOneList.isEmpty()) {
            currentTransaction = transactionsAfterTheCanceledOneList.peek();
            currentTransaction.getSecond().execute();
            transactionsAfterTheCanceledOneList.pop();
        }
    }

    public void doRegularTransactionOnAccounts(TypeOfRegularTransaction type) {
        switch (type) {
            case WriteOffCommission:
                for (Map.Entry<Long, BaseBankAccount> account : bankAccountsList.entrySet()) {
                    _currentTransaction = new WriteOffCommissionTransaction(account.getValue());
                    executeTransaction();
                }
                break;
            case PayInterest:
                for (Map.Entry<Long, BaseBankAccount> account : bankAccountsList.entrySet()) {
                    _currentTransaction = new PayInterestTransaction(account.getValue());
                    executeTransaction();
                }
                break;
        }
    }

    public void update(TypeOfRegularTransaction type) {
        doRegularTransactionOnAccounts(type);
    }

    public Double calculateBalanceInTheFuture(Long accountId, Integer amountOfDays) {
        Optional<Map.Entry<Long, BaseBankAccount>> AccountFromStorage = bankAccountsList
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(accountId))
                .findFirst();

        BaseBankAccount account = AccountFromStorage.get().getValue();
        Double initialBalance = account.getBalance();

        Integer transactionsCount = 0;
        for (int i = 1; i < amountOfDays+1; i++) {
            _currentTransaction = new PayInterestTransaction(account);
            executeTransaction();
            transactionsCount++;

            if (i % 30 == 0) {
                _currentTransaction = new WriteOffCommissionTransaction(account);
                executeTransaction();
                transactionsCount++;
            }
        }

        Double newBalance = account.getBalance();

        while (transactionsCount != 0) {
            account.transactionsHistory.peek().getSecond().undo();
            transactionsCount--;
        }

        account.setBalance(initialBalance);

        return newBalance;
    }
}