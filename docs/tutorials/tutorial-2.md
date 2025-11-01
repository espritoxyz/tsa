---
layout: default
title: Part 2
parent: Tutorial series on TSA
nav_order: 2
---

# Part 2: Discovering bugs with TSA

In the previous [Part 1](tutorial-1.md) of these tutorial series we used TSA to discover secret information stored in the state of a live contract on the TestNet. Then we used such information to hack the contract. This simple exercise served as entry point for explaining basic features of TSA. 

In this Part 2 of the tutorial series, we delve deeper into TSA by exploring more of its features. Part 2 focuses on using TSA to search for bugs in contract implementations. To achieve this aim, we will follow the strategy of devising invariants that the implementation should satisfy, and then use TSA to look for violations of the invariants. In the process, we shall see that TSA is quite flexible for modelling complex invariants. 

## 1. Pre-requisites

To properly understand this tutorial, you need to have some background on concepts related to the TON blockchain. Make sure you understand the concepts described in the following list:

- What is the TON blockchain and smart contracts.
- How a contract stores its state.
- What are cells and slices.
- How internal messages are built and sent. Also, properties about internal messages, like message value, source and target addresses, message body (or payload).
- Basic familiarity with the [FunC](https://docs.ton.org/v3/documentation/smart-contracts/func/overview) programming language.

In addition, the following two requirements are highly recommended, otherwise some sections of the tutorial may be a bit challenging to follow:

- Read at least Section [5.2](tutorial-1.md#52-solution-2-automatically-discover-the-password-using-tsa) of [Part 1](tutorial-1.md) of these tutorial series, because Part 2 builds on top concepts explained in Part 1.
- Have some familiarity with using first-order logic for writing sentences. Also, you should know how to transform negated sentences by pushing the negation operator inside the sentence. This skill is needed because Part 2 starts delving into writing contract specifications, where some logical manipulation is inevitable.

## 2. Setting up TSA

The tutorial assumes you have a Linux-based operating system. For concreteness, we will work on Ubuntu 22.04.5 LTS (64 bits), but there shouldn't be much difference for other Linux distributions.

We will use a Docker image that is already preloaded with [Java JRE](https://www.java.com/en/download/manual.jsp), the [FunC](https://docs.ton.org/v3/documentation/smart-contracts/func/overview) and [Tact](https://tact-lang.org/) compilers, [Node.js](https://nodejs.org/en), and [TSA](https://esprito.com/tsa/), because this avoids the need to install all the dependencies manually. Follow these steps to setup the Docker image:

1. Follow the instructions for installing the [Docker Engine](https://docs.docker.com/engine/install/).

2. Open a terminal, and pull the TSA Docker image:

    ```
    sudo docker pull ghcr.io/espritoxyz/tsa:latest
    ```
    
    Wait until the image downloads. The image size is around 1.37 GB.

    > NOTE: If you already have a TSA image because you followed [Part 1](tutorial-1.md) of the tutorial series, you can skip this step. Just reuse your TSA image.

3. Run the Docker image:

    ```
    sudo docker run --entrypoint bash --workdir /home -it ghcr.io/espritoxyz/tsa
    ```

    This creates a Docker container and mounts the Docker image in the container. From here onwards, all commands executed in the terminal will execute in the created Docker container. The current container's working directory is `/home`.

    > IMPORTANT: We will refer to the terminal where the container is running as the "Docker terminal".

4. In the Docker terminal, create the directory `tutorial-2` inside `/home` and change to the newly created directory:

    ```
    mkdir tutorial-2
    cd tutorial-2
    ```

    `tutorial-2` is the directory where we will create all files in this tutorial.

5. (OPTIONAL step) It is recommended to install a command line text editor, since the Docker container does not have graphical editors. A recommended one is the `nano` text editor. Run the following in the Docker terminal to install `nano`:

    ```
    apt update
    apt install nano
    ```

## 3. The problem to solve

Our objective is to find bugs in a contract by discovering violations to invariants, with the help of TSA.

The contract we are going to analyze is a slightly modified version of the [Bank contract](https://github.com/ton-blockchain/hack-challenge-1/blob/master/2.%20bank/bank.func) from the [hack-challenge-1 repository](https://github.com/ton-blockchain/hack-challenge-1/tree/master).

The Bank contract is interesting because it has a bug that is not immediately obvious from a quick glance at the code. Also, the contract is compact enough that it allows to showcase a technique that finds bugs by looking for violations to the contract invariants. We will see that, in particular, the Bank contract violates the following invariant:

> For any existing bank account A, if the amount to withdraw does not exceed the current balance in A, we should have that, after the withdraw operation, A still exists as a bank account and `"final balance in A" = "previous balance in A" - "withdrawn amount"`.

The next section will be devoted to explaining how such invariant can be encoded in TSA, how to look for violations to the invariant, and how to interpret the TSA findings so that we can identify the bug in the source code.

But first, let us explain the source code for the Bank contract:

```c
#include "imports/stdlib.fc";

() recv_internal(msg_value, in_msg_full, in_msg_body) {
    if (in_msg_body.slice_empty?()) { ;; ignore empty messages
        return ();
    }
    slice cs = in_msg_full.begin_parse();
    int flags = cs~load_uint(4);

    if (flags & 1) { ;; ignore all bounced messages
        return ();
    }
    slice sender_address = cs~load_msg_addr();
    (int wc, int sender) = parse_std_addr(sender_address);
    throw_unless(97, wc == 0);
    
    int op = in_msg_body~load_uint(32);
    
    cell accounts = get_data();

    if (op == 0) { ;; Deposit
        int fee = 10000000;
        throw_if(98, msg_value < fee);
        int balance = msg_value - fee; 
        (_, slice old_balance_slice, int found?) = accounts.udict_delete_get?(256, sender);
        if (found?) {
            balance += old_balance_slice~load_coins();
        }
        accounts~udict_set_builder(256, sender, begin_cell().store_coins(balance));
    }

    if (op == 1) { ;; Withdraw
        (_, slice old_balance_slice, int found?) = accounts.udict_delete_get?(256, sender);
        throw_unless(99, found?);
        int balance = old_balance_slice~load_coins();
        int withdraw_amount = in_msg_body~load_coins();
        throw_unless(100, balance >= withdraw_amount);
        balance -= withdraw_amount;
        if (balance > 0) {
            accounts~udict_set_builder(256, sender, begin_cell().store_coins(balance));
        }
        var msg = begin_cell()
          .store_uint(0x18, 6)
          .store_slice(sender_address)
          .store_coins(withdraw_amount)
          .store_uint(0, 1 + 4 + 4 + 64 + 32 + 1 + 1)
        .end_cell();
        send_raw_message(msg, 64 + 2);
    }

    set_data(accounts);
}

(int, int) get_account_balance(int account) method_id(70000) {
    (slice balance_slice, int found?) = get_data().udict_get?(256, account);
    if (found?) {
        return (balance_slice~load_coins(), found?);
    } else {
        return (0, found?);
    }
}
```

The contract state consists on a dictionary `accounts`, encoded as a cell. The dictionary keys are unsigned 256-bit integers representing account ids. Each account id is obtained from a contract address by parsing the address with the function `parse_std_addr`, which returns an unsigned 256-bit integer representation of the contract address. The dictionary values are slices containing the balance of the account. The balance can be obtained from the slice by using the function `load_coins`. 

The contract can receive two kinds of messages: deposit (with opcode `0`) and withdraw (with opcode `1`).

When the contract receives any kind of message, it parses the sender address:
```c
(int wc, int sender) = parse_std_addr(sender_address);
```
and uses `sender` as the target account id for the requested operation. 

Then, the contract parses the message opcode and obtains its `accounts` dictionary:
```c
int op = in_msg_body~load_uint(32);
cell accounts = get_data();
```

If the opcode is that of a deposit message, the contract verifies that the amount included in the message (i.e., the deposit amount `msg_value`) is enough to cover the fees. Then, it computes the net amount to deposit:
```c
int balance = msg_value - fee; 
```
It looks up and deletes the `sender` account from the `accounts` dictionary: 
```c
(_, slice old_balance_slice, int found?) = accounts.udict_delete_get?(256, sender);
```
If the `sender` account exists (i.e., `found?` is true), the contract computes the final balance for the `sender` account:
```c
balance += old_balance_slice~load_coins();
```
Observe that if the `sender` account does not exist, variable `balance` remains with the net amount to deposit. Finally, the contract updates the `sender` account with the new balance:
```c
accounts~udict_set_builder(256, sender, begin_cell().store_coins(balance));
```

If the opcode is that of a withdraw message, the contract looks up and deletes the `sender` account from the `accounts` dictionary: 
```c
(_, slice old_balance_slice, int found?) = accounts.udict_delete_get?(256, sender);
```
Then, it checks that the account must exist (i.e., `found?` is true). Next, it obtains the current balance of the `sender` account:
```c
int balance = old_balance_slice~load_coins();
```
and verifies that the withdraw amount included in the message does not exceed the current balance:
```c
int withdraw_amount = in_msg_body~load_coins();
throw_unless(100, balance >= withdraw_amount); 
```
Next, it decreases the balance by the withdraw amount and updates the `sender` account with the new balance if the balance is still non-zero:
```c
balance -= withdraw_amount;
if (balance > 0) {
    accounts~udict_set_builder(256, sender, begin_cell().store_coins(balance));
}
```
Finally, the bank sends back to the sender address the withdrawn amount in toncoins.

The contract also has the method `get_account_balance` with explicit id `70000`. This method looks up the given `account` in the dictionary and returns the account balance together with a flag that indicates whether or not the account was found in the dictionary. Having an explicit method id for `get_account_balance` makes it much easier to invoke the method inside TSA checkers.

As an early point of suspicion, the fact that the contract is attempting to "delete" account ids by using `accounts.udict_delete_get?` instead of `accounts~udict_delete_get?`, should already ring a bell in the reader's mind that something could go horribly wrong. But let us not get ahead of ourselves and let us explore in the next section the invariant-based strategy for finding the bug.

## 4. Solving the problem

We would like to check that the Bank contract satisfies the following properties, also known as invariants. These invariants are properties expected of a bank implementation. In other words, these invariants are part of the implementation's **specification**.

1. Invariants related to the deposit operation:
    
    (1.a) For any non-existing account `A`, if the deposit amount covers the fees, we should have that, after the deposit operation, `A` exists as a bank account and `"final balance in A" = "deposit amount" - fees`.

    (1.b) For any existing bank account `A`, if the deposit amount covers the fees, we should have that, after the deposit operation, `A` still exists as a bank account and `"final balance in A" = "previous balance in A" + ("deposit amount" - fees)`.

    (1.c) It is not possible to deposit an amount smaller than the fees.

2. Invariants related to the withdraw operation:

    (2.a) For any existing bank account `A`, if the amount to withdraw does not exceed the current balance in `A`, we should have that, after the withdraw operation, `A` still exists as a bank account and `"final balance in A" = "previous balance in A" - "withdrawn amount"`.

    (2.b) For any existing bank account `A`, it is not possible to withdraw an amount that exceeds the current balance in `A`.
    
    (2.c) It is not possible to withdraw from a non-existent bank account `A`.

Since TSA works as an "example finder", we will use TSA to try to find examples that **violate** the invariants. If we can find an example that violates one of those invariants, then there is a bug in the source code.

The strategy is to construct a checker contract for each invariant. Each checker contract will try to find examples that satisfy the **negation** of the invariant. i.e., the checker contract will try to find violations to the invariant.

This tutorial focuses on building checkers for invariants (1.a) and (2.a) to illustrate the strategy.

### 4.1. Preparing the project

We will work entirely in the Docker terminal. In `/home/tutorial-2`, carry out the following steps to setup the TSA project:

1. Create an `/imports` directory.

    ```
    mkdir imports
    ```

    The `imports` folder will have libraries and files required for compilation and interaction with TSA from FunC.

2. Copy the TSA jar file into the project:

    ```
    cp ../tsa.jar .
    ```

    The `tsa.jar` file is the TSA executable.

3. Copy the FunC standard library into the `imports` directory:

    ```
    cd imports
    cp /usr/share/ton/smartcont/stdlib.fc .
    ```

4. Copy the Fift library into the `imports` directory:

    ```
    mkdir lib
    cp -r /usr/lib/fift/. ./lib
    ```

    This library is required for compiling into TVM bitcode.

5. Download the [FunC-TSA interface file](https://github.com/espritoxyz/tsa/blob/master/tsa-test/src/test/resources/imports/tsa_functions.fc) into `/imports`. We will need this file to talk to TSA from FunC code.

    ```
    curl -O https://raw.githubusercontent.com/espritoxyz/tsa/refs/heads/master/tsa-test/src/test/resources/imports/tsa_functions.fc
    ```

6. Create a `bank.fc` file in `/home/tutorial-2`:

    ```
    cd ..
    nano bank.fc
    ```

    Paste the code of the Bank contract in Section [3](#3-the-problem-to-solve) and save it. This will be the source code to analyze.

So far, the contents of the TSA project `/home/tutorial-2` should look like the following:

```
/tutorial-2
|-- /imports
    |-- /lib
        |--- ... A bunch of Fift files
    |-- stdlib.fc
    |-- tsa_functions.fc
|-- bank.fc
|-- tsa.jar
```

### 4.2. Writing the checker contracts

This section builds on top the explanations found in Section [5.2](tutorial-1.md#52-solution-2-automatically-discover-the-password-using-tsa) of [Part 1](tutorial-1.md). As such, it is highly recommended that you first read such section. Additionally, the current section delves into verification of specifications. As such, it is assumed that you have some familiarity with first-order logic, specially regarding the transformation of negated sentences (more specifically: pushing negations inside sentences).

#### 4.2.1. Checker for invariant (1.a)

Let us start by writing invariant (1.a) a bit more formally:

> For any account id `A` and deposit amount `d`, if `A` is not in `Accounts`, and `d >= fees`, and the deposit message successfully executes, then immediately afterwards, `A` is in `Accounts` and `balance(A) = d - fees`.

Here, `Accounts` is the accounts dictionary currently active in the bank, `fees` is the fixed amount `10000000`, and `balance(A)` is the current balance of account id `A` in `Accounts`.

The objective is to find violations to the above sentence. 

The first step is to negate the sentence. After pushing the negation inside the sentence, we obtain:

> There is an account id `A` and deposit amount `d`, such that `A` is not in `Accounts`, and `d >= fees`, and immediately after the deposit message successfully executes, either `A` is not in `Accounts` or `balance(A) != d - fees`.

The next step is to write a checker contract that encodes the negated sentence. 
In `/home/tutorial-2`, create the file `checker_1a.fc`. Paste the following code and save it. We now explain how this code encodes the negated sentence.

```c
#include "imports/stdlib.fc";
#include "imports/tsa_functions.fc";

const int fees = 10000000;

global int deposit_amount;
global int account_id;

() on_internal_message_send(int balance, int msg_value, cell msg_full, slice msg_body, int message_id) impure method_id {
    ;; Ensure the message sender corresponds to the account id.
    slice cs = msg_full.begin_parse();
    cs~load_uint(4);
    slice sender_address = cs~load_msg_addr();
    (_, int sender_addr_int) = parse_std_addr(sender_address);   
    tsa_assert(account_id == sender_addr_int);
    
    ;; The message is a deposit.
    int op = msg_body~load_uint(32);
    tsa_assert(op == 0);
    
    ;; Ensure the message value is the deposit amount.
    tsa_assert(deposit_amount == msg_value);
    
    ;; The amount to deposit is not less than the fees
    tsa_assert(deposit_amount >= fees);
}

() main() impure {
    tsa_forbid_failures();
    
    ;; Create an account id
    account_id = tsa_mk_int(256, 0);
    
    ;; Create an amount to deposit.
    deposit_amount = tsa_mk_int(100, 0);
        
    ;; Initialize the analyzed contract state.
    cell bankAccounts = tsa_get_c4(1);
    (_, int current_found?) = bankAccounts.udict_get?(256, account_id);
    tsa_assert(~ current_found?);           ;; The account does not exist in the bank
    
    ;; Send the deposit message.
    tsa_send_internal_message(1, 0);
    
    ;; Get the balance.
    (int new_balance, int new_found?) = tsa_call_2_1(account_id, 1, 70000);

    tsa_allow_failures();
    
    ;; In the report, show the deposit amount.
    tsa_fetch_value(deposit_amount, 1);
    ;; And the new balance, after the message.
    tsa_fetch_value(new_balance, 2);
    ;; Also, the culprit account id.
    tsa_fetch_value(account_id, 3);
    ;; And the found status.
    tsa_fetch_value(new_found?, 4);
    
    ;; We need to check the statement:
    ;; (NOT new_found?) OR (new_balance != deposit_amount - fees)
    int disj1 = ~ new_found?;
    int disj2 = new_balance != (deposit_amount - fees);
    
    throw_if(257, disj1 | disj2);
}
```

The entry point is the `main` procedure. As usual, we enclose between the functions `tsa_forbid_failures` and `tsa_allow_failures` all the conditions that need to successfully execute before checking the final disjunction: either `A` is not in `Accounts` or `balance(A) != d - fees`. This final disjunction is placed in a `throw_if` instruction, so that the checker will throw an exception when it finds an instantiation of the symbolic variables that satisfies the disjunction.

First, the checker uses the function `tsa_mk_int(256, 0)` to create a symbolic variable that stores an unsigned 256-bit integer. The value that TSA finds for this symbolic variable will be the account id claimed to exist by our negated invariant: "there is an account id `A`...". Account ids are 256-bit unsigned integers, according to the source code. The checker stores the value of the account id in the global variable `account_id` for later reference outside the `main` method.

Next, the checker uses the function `tsa_mk_int(100, 0)` to create another symbolic variable that stores an unsigned 100-bit integer. The value that TSA finds for this symbolic variable will be the deposit amount claimed to exist by our negated invariant: "there is a deposit amount `d`...". The bit length of the deposit amount can be anything that fits within a `VarUInteger_16` (see [here](https://docs.tact-lang.org/book/integers/#serialization-varint) for an explanation). The checker stores the value of the deposit amount in the global variable `deposit_amount` for later reference outside the `main` method.

The next step is to initialize the Bank contract state by asserting all the constraints that should be true before sending the deposit message to the Bank contract. 

We obtain the Bank state with the function `tsa_get_c4(1)`, which returns a cell encoding a dictionary with all the accounts currently stored in the bank. Function `tsa_get_c4` receives the contract id from which we want to obtain the state. Recall from Part 1 that TSA assigns contract id `0` to the checker contract, and id `1` to the analyzed contract (i.e., the Bank contract).

Now that we have the bank accounts in `bankAccounts`, we look up the account using `bankAccounts.udict_get?(256, account_id)`. Function `udict_get?` looks up the unsigned 256-bit integer key `account_id` in dictionary `bankAccounts`. The function returns the value associated with the key (if the key exists) and a flag that indicates if the key was found in the dictionary. We store the flag in the `current_found?` variable. 

Our negated invariant states that, before sending the deposit message, `account_id` should **not** be an account in the bank. Therefore, the checker tells TSA to assert `~ current_found?` using function `tsa_assert`, i.e., that flag `current_found?` should be false, or equivalently, that `account_id` should not be a key in the `bankAccounts` dictionary. In general, function `tsa_assert(assertion)` tells TSA to only keep instantiations of symbolic variables where `assertion` is true. 

Observe that the checker does not place further constraints on dictionary `bankAccounts`. This means that TSA is free to find examples where `bankAccounts` contains keys different from `account_id`.

After constraining the bank state, the checker sends an internal message to the Bank contract by using `tsa_send_internal_message(1, 0)`. Recall from Part 1 that the first argument to `tsa_send_internal_message` should be the id of the target contract (`1` for the Bank contract), and the second argument should be an integer that will serve as identifier for the message. The message identifier also functions as a name for the message in the final report. In our example, the final report will include the sent message under name `"0"`. 

How can we constraint the sent message? Recall that we need to send a **deposit** message, not just any message. TSA has a feature that allows writing constraints for specific messages: every time the function `tsa_send_internal_message` is called, the "event handler" `on_internal_message_send` executes before sending the message. Inside `on_internal_message_send` we can include further constraints about the specific message to send. 

The signature of `on_internal_message_send` should always look like this (you can rename the arguments if you want):

```c
() on_internal_message_send(int balance, int msg_value, cell msg_full, slice msg_body, int message_id) impure method_id
```

`balance` is the current balance of the checker contract; `msg_value` is the amount of coins to include in the message to send; `msg_full` is the full message to send (as a cell); `msg_body` is the message body to send (as a slice); and `message_id` is the message identifier. `message_id` is useful for restricting the constraints to a specific message when we need to send multiple messages. For example, let us suppose that we send two messages, with ids `2` and `3`:

```c
tsa_send_internal_message(1, 2);
tsa_send_internal_message(1, 3);
```

Then, inside `on_internal_message_send`, we can use conditionals to restrict the constraints for each message id:

```c
() on_internal_message_send(int balance, int msg_value, cell msg_full, slice msg_body, int message_id) impure method_id {
    if (message_id == 2) {
        ;; Specific constraints for message with id 2
    }

    if (message_id == 3) {
        ;; Specific constraints for message with id 3
    }
}
```
In our case, the checker sends a single message, which means that there is no need to check for `message_id == 0` in the `on_internal_message_send` handler, since the handler will execute only once. 

Inside the `on_internal_message_send` handler, the first lines:
```c
slice cs = msg_full.begin_parse();
cs~load_uint(4);
slice sender_address = cs~load_msg_addr();
(_, int sender_addr_int) = parse_std_addr(sender_address);   
tsa_assert(account_id == sender_addr_int);
```
ensure that the sender address corresponds to the `account_id` we want to deposit into. This is needed because once the bank receives a deposit message, the bank computes the account id from the sender address by using the function `parse_std_addr`. So, we need to ensure that when `sender_address` is transformed into an integer by using function `parse_std_addr`, this integer coincides with `account_id`, the account we want to deposit into.

The next lines:
```c
int op = msg_body~load_uint(32);
tsa_assert(op == 0);
```
assert that the message opcode should be the one corresponding to a deposit (`op == 0`).

The last lines in the `on_internal_message_send` handler:

```c
tsa_assert(deposit_amount == msg_value);

tsa_assert(deposit_amount >= fees);
```
assert that the amount of coins included in the message (`msg_value`) should be exactly the deposit amount (`deposit_amount`). And that the deposit amount should cover at least the fees (`deposit_amount >= fees`), as required by the negated invariant.

Returning to the `main` procedure, after the checker sends the deposit message, it remains to encode the disjunction: "either `A` is not in `Accounts` or `balance(A) != d - fees`".

For that matter, we invoke the `get_account_balance` method in the Bank contract:
```c
(int new_balance, int new_found?) = tsa_call_2_1(account_id, 1, 70000);
```

Recall from Part 1 that TSA has several functions of the form `tsa_call_n_m(arg_1, ..., arg_m, contract_id, method_id)`, where `n` is the number of returned values and `m` is the number of arguments to pass to the method with id `method_id` of contract `contract_id` (arguments `arg_1, ..., arg_m` will be passed to the method). In our case `tsa_call_2_1(account_id, 1, 70000)` will invoke the method with id `70000` (i.e., `get_account_balance`) of the Bank contract (id `1`). The invocation will pass the one argument `account_id` and it will return 2 values, which we assign to `new_balance` and `new_found?`, corresponding to the account new balance and the flag that indicates whether or not `account_id` was found in the bank accounts.

With `new_balance` and `new_found?` we can now state the required disjunction. The first disjunct is encoded with `~ new_found?` (i.e., `account_id` is not a bank account after the deposit message), while the second disjunct is encoded with `new_balance != (deposit_amount - fees)`.

Finally, we throw an exception if one of those disjuncts become true, as required by the negated invariant.

There is one final point in the checker code that has nothing to do with encoding the negated invariant: the calls to `tsa_fetch_value`. This function allows us to inspect variables and show their values in the final report as long as TSA finds an instantiation that throws an exception. The function `tsa_fetch_value` receives as first argument the variable to inspect, and as second argument an identifier serving as name for the inspected variable in the final report. In our case, we are inspecting the deposit amount, the account balance after the deposit message, the target account id, and the flag that indicates whether or not the account exists in the bank database after the deposit message.

#### 4.2.2. Checker for invariant (2.a)

Again, let us start by writing invariant (2.a) a bit more formally:

> For any account id `A` and withdraw amount `w`, if `A` is in `Accounts`, and `prev_balance(A) >= w`, and the withdraw message successfully executes, then immediately afterwards, `A` is in `Accounts` and `new_balance(A) = prev_balance(A) - w`.

Here, `Accounts` is the accounts dictionary currently active in the bank, `prev_balance(A)` is the balance of account id `A` before the withdraw message, and `new_balance(A)` is the balance of account id `A` after the withdraw message.

The objective is to find violations to the above sentence. As was done to invariant (1.a), negate the sentence. After pushing the negation inside the sentence, we obtain:

> There is an account id `A` and withdraw amount `w`, such that `A` is in `Accounts`, and `prev_balance(A) >= w`, and immediately after the withdraw message successfully executes, either `A` is not in `Accounts` or `new_balance(A) != prev_balance(A) - w`.

Now, write a checker contract that encodes the negated sentence. In `/home/tutorial-2`, create the file `checker_2a.fc`. Paste the following code and save it. 

```c
#include "imports/stdlib.fc";
#include "imports/tsa_functions.fc";

global int withdraw_amount;
global int account_id;

() on_internal_message_send(int balance, int msg_value, cell msg_full, slice msg_body, int message_id) impure method_id {
    ;; Ensure the message sender corresponds to the account id.
    slice cs = msg_full.begin_parse();
    cs~load_uint(4);
    slice sender_address = cs~load_msg_addr();
    (_, int sender_addr_int) = parse_std_addr(sender_address);   
    tsa_assert(account_id == sender_addr_int);
    
    ;; The message is a withdraw message.
    int op = msg_body~load_uint(32);
    tsa_assert(op == 1);
    
    ;; Ensure the message is sending the withdraw amount.
    tsa_assert(withdraw_amount == msg_body~load_coins());
    
}

() main() impure {
    tsa_forbid_failures();
    
    ;; Create an account id
    account_id = tsa_mk_int(256, 0);
    
    ;; Create an amount to withdraw.
    withdraw_amount = tsa_mk_int(100, 0);
    
    ;; Initialize the analyzed contract state.
    cell bankAccounts = tsa_get_c4(1);
    (slice balance_slice, int current_found?) = bankAccounts.udict_get?(256, account_id);
    tsa_assert(current_found?);                        ;; The account currently exists in the bank
    int prev_balance = balance_slice~load_coins();     ;; Store the current balance for later usage
    
    ;; The withdraw amount does not exceed the current balance.
    tsa_assert(prev_balance >= withdraw_amount);
    
    ;; Send the withdraw message.
    tsa_send_internal_message(1, 0);
    
    ;; Get the new balance.
    (int new_balance, int new_found?) = tsa_call_2_1(account_id, 1, 70000);

    tsa_allow_failures();
    
    ;; In the report, show the balance the account had before sending the message.
    tsa_fetch_value(prev_balance, 1);
    ;; Also show the withdraw amount.
    tsa_fetch_value(withdraw_amount, 2);
    ;; And the new balance, after the message.
    tsa_fetch_value(new_balance, 3);
    ;; Also, the culprit account id 
    tsa_fetch_value(account_id, 4);
    ;; And the found status
    tsa_fetch_value(new_found?, 5);
    
    ;; We need to check the statement:
    ;; (NOT new_found?) OR (new_balance != prev_balance - withdraw_amount)
    int disj1 = ~ new_found?;
    int disj2 = new_balance != (prev_balance - withdraw_amount);
    
    throw_if(257, disj1 | disj2);
}
```

The checker is almost identical to the checker for invariant (1.a). There are only a couple of differences. We will only explain the differences.

First, the negated invariant requires that `account_id` already exist in the bank before sending the withdraw message. As such, during initialization of the Bank contract state, the line:

```c
tsa_assert(current_found?);
```
ensures that `account_id` exists in the bank dictionary before sending the withdraw message.

Immediately afterwards, line:
```c
int prev_balance = balance_slice~load_coins();
```
obtains the balance before the withdraw message is sent. `prev_balance` then gets constrained at line:

```c
tsa_assert(prev_balance >= withdraw_amount);
```
which states that the withdraw amount cannot exceed the balance in the account, as required by the negated invariant.

Notice that the checker does not impose further constraints on the bank dictionary nor on variable `prev_balance`. This means that TSA could find a dictionary containing further accounts in addition to `account_id`, and `prev_balance` could have some arbitrary value bigger or equal to `withdraw_amount`.

The checker then proceeds to send the internal message. Again, further constraints are added in the event handler `on_internal_message_send`. These constraints are fairly similar to the ones for the deposit message. The only difference is that now we are sending a withdraw message (opcode `1`), and we require that the withdraw amount be included in the message body:

```c
tsa_assert(withdraw_amount == msg_body~load_coins())
```

After sending the withdraw message, the checker obtains the new balance (`new_balance`) and the found flag (`new_found?`). Then, it encodes the final disjunction of the negated invariant: "either `A` is not in `Accounts` or `new_balance(A) != prev_balance(A) - w`" as the disjuncts `~ new_found?` and `new_balance != (prev_balance - withdraw_amount)`, respectively.

By using the `tsa_fetch_value` function, the checker contract also inspects the balance before the withdraw message, the withdraw amount, the balance after the withdraw message, the target account id, and the flag that indicates whether or not the account exists in the bank database after the withdraw message.

### 4.3. Running TSA from the command line

At `/home/tutorial-2` run the following command for checking invariant (1.a):

```
java -jar tsa.jar custom-checker --checker checker_1a.fc --contract func bank.fc --fift-std imports/lib -o report_1a.sarif
```

Let us explain each argument in the command:

- `java -jar tsa.jar custom-checker` executes TSA in custom checker mode.
- `--checker checker_1a.fc` tells TSA to use the code in `checker_1a.fc` as the checker contract.
- `--contract func bank.fc` tells TSA to use the code in `bank.fc` as the contract to verify. The `func` option tells TSA that `bank.fc` is written in FunC. To see further details for other language options, execute the command `java -jar tsa.jar custom-checker --help`.
- `--fift-std imports/lib` tells TSA to use the Fift library `imports/lib` during compilation of the checker contract.
- `-o report_1a.sarif` tells TSA to write the final report in `report_1a.sarif`. The report will be a plaintext file containing the TSA findings.

Similarly, run the following command for checking invariant (2.a):

```
java -jar tsa.jar custom-checker --checker checker_2a.fc --contract func bank.fc --fift-std imports/lib -o report_2a.sarif
```

After executing the above commands, the report files `report_1a.sarif` and `report_2a.sarif` should exist in `/home/tutorial-2`.

### 4.4. Interpreting the TSA output report

Open `report_1a.sarif`: 

```
nano report_1a.sarif
```

It should show something similar to this:

```
"runs": [
    {
        "results": [
        ],
        "tool": {
            "driver": {
                "name": "TSA",
                "organization": "Explyt"
            }
        }
    }
]
```
which describes an execution that could not find instantiations that reach  exception `257`, because the `"results"` list is empty. In other words, TSA could not find violations to invariant (1.a). 

Just because TSA could not find violations, it does **not** mean that invariant (1.a) is true. It only means that it is likely that invariant (1.a) is true. If we want to truly be sure that invariant (1.a) is true, we would need to construct a formal proof of its correctness, which is not a simple task. 

Instead of attempting a formal proof for invariant (1.a), we can build further evidence of its correctness by **injecting** bugs in the source code, and then check if TSA is able to find violations to (1.a).

For example, change this line in `bank.fc`, which occurs at the end of the Deposit case (`op == 0`):
```c
accounts~udict_set_builder(256, sender, begin_cell().store_coins(balance));
```
for this line, which uses `.` instead of `~`:
```c
accounts.udict_set_builder(256, sender, begin_cell().store_coins(balance));
```
and save the file as `deposit_bug_bank.fc`.

The change has the effect that the `accounts` dictionary does not get updated. This means that if a deposit message arrives for an account id that initially does not exist in the dictionary, then after the message gets processed, the account would still not exist in the dictionary. 

Indeed, if we run TSA again on the modified source code:

```
java -jar tsa.jar custom-checker --checker checker_1a.fc --contract func deposit_bug_bank.fc --fift-std imports/lib -o report_bug_1a.sarif
```

We should see a non-empty `"results"` list in `report_bug_1a.sarif`, with a `"message"` property:

```
"message": {
    "text": "TvmFailure(exit=TVM user defined error with exit code 257, phase=COMPUTE_PHASE)"
}
```
that indicates that TSA found an instantiation that threw the exception at the end of `checker_1a.fc`, i.e., TSA found a violation to invariant (1.a).

Looking further down in `report_bug_1a.sarif`, at the `"fetchedValues"` property, we should see the variables that were inspected with function `tsa_fetch_value` inside the checker (comments added for convenience):
```
"fetchedValues": {
    "1": "73786976294838206464",     [Deposit amount]
    "2": "0",                        [Balance after deposit message]
    "3": "0",                        [Account id]
    "4": "0"                         [Found flag after deposit message]
}
```

We see that, even though the deposit amount is higher than the fees (`10000000`), the account id still does not exist in the dictionary, because the found flag after the deposit message is `0`, i.e., `false`.

In case that TSA is still not able to find violations after manually injecting bugs in the source code, it is recommended to do the following sanity check. 

It may be the case that, in the checker, the constraints between the functions `tsa_forbid_failures` and `tsa_allow_failures` are contradictory or always throw exceptions. If the constraints are contradictory or always fail, TSA will not be able to find an instantiation that reaches `tsa_allow_failures`, because it is impossible, producing an empty report as a result. 

Therefore, it is important to sanity check your constraints by writing a checker that always throws an exception immediately after the call to function `tsa_allow_failures`, like so:
```c
() main() impure {
    tsa_forbid_failures();

    ;; All your constraints

    tsa_allow_failures();

    throw(1000);  ;; Unconditionally throw an exception
}
```
If TSA does not report the exception `1000` when running the above checker, then there is something wrong with your constraints, and you need to revise them: they may be contradictory or always throw an exception.

Having explored the results for invariant (1.a), let us now switch to the report for invariant (2.a). 

Open `report_2a.sarif`: 
```
nano report_2a.sarif
```

This time, TSA was able to find a violation to the invariant, since TSA threw the exception at the end of `checker_2a.fc`:
```
"message": {
    "text": "TvmFailure(exit=TVM user defined error with exit code 257, phase=COMPUTE_PHASE)"
}
```

Looking further down in the `"fetchedValues"` property, we see something interesting (added comments for convenience):
```
"fetchedValues": {
  "1": "633825300114114700748351602688",      [Balance before withdraw message]
  "2": "633825300114114700748351602688",      [Withdraw amount]
  "3": "633825300114114700748351602688",      [Balance after withdraw message]
  "4": "0",                                   [Account id]
  "5": "-1"                                   [Found flag after withdraw message]
}
```
We see that when the withdraw amount is equal to the balance before the message, a violation occurs because after the withdraw message was processed, the balance is not `0`, but actually equal to the initial balance.

The question we should ask is: Is it a coincidence that the initial balance should be equal to the withdraw amount for the violation to occur? Let us modify the checker `checker_2a.fc` by constraining the initial balance to a fixed amount, say `12345`. Immediately after line `int curr_balance = balance_slice~load_coins();`, add the following line:
```c
tsa_assert(curr_balance == 12345);
```

Save the checker file as `checker_2a_alt1.fc` and run TSA again:
```
java -jar tsa.jar custom-checker --checker checker_2a_alt1.fc --contract func bank.fc --fift-std imports/lib -o report_2a_alt1.sarif
```

Open `report_2a_alt1.sarif`. At the `"fetchedValues"` property, you should see something similar to this (comments added for convenience):
```
"fetchedValues": {
  "1": "12345",     [Balance before withdraw message]
  "2": "12345",     [Withdraw amount]
  "3": "12345",     [Balance after withdraw message]
  "4": "0",         [Account id]
  "5": "-1"         [Found flag after withdraw message]
}
```
where we see again that TSA finds the violation when the withdraw amount is equal to the initial balance. We also see that the final balance does not get updated to `0`, but remains equal to the initial balance.

Inspecting the source code `bank.fc`, we see that the following line inside the Withdraw case (`op == 1`):
```c
(_, slice old_balance_slice, int found?) = accounts.udict_delete_get?(256, sender);
```
deletes the `sender` account id from the dictionary `accounts` but it does **not** update the `accounts` variable, because it uses `.` instead of `~`. This means that, after this line executes, the `sender` account remains in `accounts` with its initial balance.

The above is not problematic by itself, but it causes a bug when combined with the computation that follows. **When the balance is equal to the withdraw amount**, the final value of the `balance` variable is `0`. This means that the following condition guarding the update to the balance is false:
```c
if (balance > 0) {
  accounts~udict_set_builder(256, sender, begin_cell().store_coins(balance));
}
```
Therefore, the `sender` account does not get updated, and it keeps the balance originally found in the `accounts` dictionary. Note that this bug happens only when the withdraw amount is exactly equal to the balance, as suggested by the TSA findings.

Indeed, we can confirm that the bug occurs only when the withdraw amount equals the initial balance. Open checker `checker_2a_alt1.fc` and replace this line:
```c
tsa_assert(curr_balance == 12345);
```
with this line:
```c
tsa_assert(curr_balance != withdraw_amount);
```
The new line tells TSA to find instantiations where the withdraw amount is **different** from the balance before the message. If you run TSA with the modified checker, you should get a report with empty results, i.e., TSA could not find violations.

In summary, we can use TSA to search for bugs in our implementation by doing the following strategy:

1. Clearly state the invariants that your implementation should satisfy.
2. For each invariant, negate the invariant and write a checker that implements the negated invariant. Consider inspecting the variables mentioned in the invariant (by using `tsa_fetch_value`), because this will help in detecting patterns among the variables when violations to the invariant are found.
3. If TSA cannot find violations to a particular invariant, test the corresponding checker by injecting bugs in the code of the analyzed contract. Then, see if TSA can find the violations. This should provide further evidence that the checker is working and that the invariant is probably true. If TSA still cannot find violations after injecting bugs into the source code, sanity check your constraints, as suggested in this section during the discussion of results for invariant (1.a).
4. If TSA finds violations to a particular invariant, add constraints in the corresponding checker that set specific values to the variables in the invariant. This will be useful for confirming patterns among the variables.

## 5. Closing thoughts

Congratulations! You have successfully used TSA to search for bugs in a contract implementation. Along the way, we learned:

- An invariant-based strategy to look for bugs in an implementation.
- How to prepare invariants and encode them in checkers.
- How to look for violations of the invariants and inspect variables occurring in the invariant.
- How to constraint the initial state of a contract.
- How to write constraints for specific internal messages.

In the next part, we will use TSA to analyze situations where there are multiple contracts that need to communicate among themselves. Stay tuned!
