package org.ton.examples.args

import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.utils.TvmTestExecutor
import org.ton.test.utils.checkInvariants
import org.ton.test.utils.exitCode
import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import org.ton.test.utils.propertiesFound
import org.usvm.machine.ConcreteOpcode
import org.usvm.machine.MessageConcreteData
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmConcreteGeneralData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmOptions
import org.usvm.machine.getResourcePath
import org.usvm.machine.state.input.ReceiverInput.Companion.NANOTONS_BOUND_2
import org.usvm.test.resolver.TvmSuccessfulExecution
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTestFailure
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.TvmTestNullValue
import org.usvm.test.resolver.TvmTestSliceValue
import org.usvm.test.resolver.TvmTestTupleValue
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArgsConstraintsTest {
    private val consistentMsgValuePath = "/args/consistent_msg_value.fc"
    private val consistentFlagsPath = "/args/consistent_flags.fc"
    private val fwdFeePath = "/args/fwd_fee.fc"
    private val createdLtPath = "/args/created_lt.fc"
    private val createdAtPath = "/args/created_at.fc"
    private val myAddressPath = "/args/my_address.fc"
    private val balancePath = "/args/balance.fc"
    private val senderAddressPath = "/args/sender_address.fc"
    private val opcodePath = "/args/opcode.fc"
    private val recvExternalPath = "/args/recv_external.fc"
    private val allC7ParamsPath = "/args/all_c7_params.fc"
    private val storageFeesPath = "/args/storage_fees.fc"
    private val duePaymentPath = "/args/due_payment.fc"

    /**
     * Sentinel exit code thrown from `all_c7_params.fc` after every C7 parameter
     * has been read.
     */
    private val allC7ParamsSuccessCode = 200

    /** Indices of C7 parameters per TON docs. */
    private object C7Idx {
        const val TAG = 0
        const val ACTIONS = 1
        const val MSGS_SENT = 2
        const val NOW = 3
        const val BLOCK_LTIME = 4
        const val LTIME = 5
        const val RAND_SEED = 6
        const val BALANCE = 7
        const val INCOMING_VALUE = 11
        const val STORAGE_FEES = 12
        const val PREV_BLOCKS = 13
        const val UNPACKED_CONFIG = 14
        const val DUE_PAYMENT = 15
    }

    /** The constant SmartContractInfo magic value from c7[0]. */
    private val smartContractInfoTag = BigInteger("076ef1ea", 16)

    private val nowPath = "/args/now.fc"

    // Concrete unix time that makes the [nowPath] contract throw with exit code 1000.
    // Must match the literal hardcoded in `now.fc` and stay within [TvmContext.UNIX_TIME_MIN]..[TvmContext.UNIX_TIME_MAX].
    private val concreteNow = 1735689600L.toBigInteger()

    @Test
    fun testConsistentMessageValue() {
        val path = getResourcePath<ArgsConstraintsTest>(consistentMsgValuePath)
        val result = funcCompileAndAnalyzeAllMethods(path)
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testConsistentFlags() {
        val path = getResourcePath<ArgsConstraintsTest>(consistentFlagsPath)
        val result =
            funcCompileAndAnalyzeAllMethods(
                path,
                tvmOptions = TvmOptions(analyzeBouncedMessaged = true),
            )
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testFwdFee() {
        val path = getResourcePath<ArgsConstraintsTest>(fwdFeePath)
        val result = funcCompileAndAnalyzeAllMethods(path)
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testCreatedLt() {
        val path = getResourcePath<ArgsConstraintsTest>(createdLtPath)
        val result = funcCompileAndAnalyzeAllMethods(path)
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testCreatedAt() {
        val path = getResourcePath<ArgsConstraintsTest>(createdAtPath)
        val result = funcCompileAndAnalyzeAllMethods(path)
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testMyAddress() {
        val path = getResourcePath<ArgsConstraintsTest>(myAddressPath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        propertiesFound(
            result.testSuites.single(),
            listOf(
                { test -> test.result is TvmSuccessfulExecution },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
            ),
        )

        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    private val stonfiAddressBits =
        "10000000000" +
            BigInteger("779dcc815138d9500e449c5291e7f12738c23d575b5310000f6a253bd607384e", 16)
                .toString(2)
                .padStart(256, '0')

    @Test
    fun testConcreteMyAddress() {
        val path = getResourcePath<ArgsConstraintsTest>(myAddressPath)
        val result =
            funcCompileAndAnalyzeAllMethods(
                path,
                concreteContractData =
                    TvmConcreteContractData(
                        addressBits = stonfiAddressBits,
                    ),
            )

        val tests = result.testSuites.single()

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
        )

        checkInvariants(
            tests,
            listOf { test -> test.result !is TvmSuccessfulExecution },
        )
    }

    @Test
    fun testBalance() {
        val path = getResourcePath<ArgsConstraintsTest>(balancePath)

        val options =
            TvmOptions(
                useSoftConstraints = true,
            )

        val result = funcCompileAndAnalyzeAllMethods(path, tvmOptions = options)

        val tests = result.testSuites.single()

        propertiesFound(
            tests,
            listOf(
                { test -> test.result is TvmSuccessfulExecution },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 1001 },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 1002 },
            ),
        )

        checkInvariants(
            tests,
            listOf(
                { test -> (test.result as? TvmTestFailure)?.exitCode != 1000 },
                { test ->
                    // soft constraint test
                    if (test.exitCode() != 1002) {
                        return@listOf true
                    }
                    test.contractStatesBefore[0]!!.balance.value <= NANOTONS_BOUND_2.toBigInteger()
                },
            ),
        )

        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testConcreteBalance() {
        val path = getResourcePath<ArgsConstraintsTest>(balancePath)
        val result =
            funcCompileAndAnalyzeAllMethods(
                path,
                concreteContractData = TvmConcreteContractData(initialBalance = 12345.toBigInteger()),
            )

        val tests = result.testSuites.single()
        assertTrue { tests.isNotEmpty() }

        checkInvariants(
            tests,
            listOf { test -> test.result !is TvmSuccessfulExecution },
        )
    }

    @Test
    fun testSenderAddress() {
        val path = getResourcePath<ArgsConstraintsTest>(senderAddressPath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        propertiesFound(
            result.testSuites.single(),
            listOf(
                { test -> test.result is TvmSuccessfulExecution },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
            ),
        )

        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testConcreteSenderAddress() {
        val path = getResourcePath<ArgsConstraintsTest>(senderAddressPath)
        val result =
            funcCompileAndAnalyzeAllMethods(
                path,
                concreteGeneralData =
                    TvmConcreteGeneralData(
                        initialInputConcreteData = MessageConcreteData(senderBits = stonfiAddressBits),
                    ),
            )

        propertiesFound(
            result.testSuites.single(),
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
        )

        checkInvariants(
            result.testSuites.single(),
            listOf { test -> test.result !is TvmSuccessfulExecution },
        )
    }

    @Test
    fun testOpcode() {
        val path = getResourcePath<ArgsConstraintsTest>(opcodePath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        propertiesFound(
            result.testSuites.single(),
            listOf(
                { test -> test.result is TvmSuccessfulExecution },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
            ),
        )

        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testConcreteOpcode() {
        val path = getResourcePath<ArgsConstraintsTest>(opcodePath)
        val result =
            funcCompileAndAnalyzeAllMethods(
                path,
                concreteGeneralData =
                    TvmConcreteGeneralData(
                        initialInputConcreteData =
                            MessageConcreteData(
                                opcodeInfo = ConcreteOpcode(0x12345678L.toBigInteger()),
                            ),
                    ),
            )

        propertiesFound(
            result.testSuites.single(),
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
        )

        checkInvariants(
            result.testSuites.single(),
            listOf { test -> test.result !is TvmSuccessfulExecution },
        )
    }

    /**
     * Asserts on every C7 register parameter that is initialized to a fixed
     * concrete value by [initContractInfo]. Symbolic parameters (NOW, RAND_SEED,
     * BALANCE, MYADDR, GLOBAL_CONFIG, MYCODE) are checked via type/structure
     * constraints. INCOMINGVALUE is checked to be a (Int, Maybe Cell) tuple
     * (Maybe Tuple in TVM terms), PREVBLOCKSINFOTUPLE is currently always null,
     * and UNPACKEDCONFIGTUPLE is a tuple of slices over global config params.
     */
    private fun checkC7Invariants(test: TvmSymbolicTest) {
        val c7 = test.initialRootContractState.c7.elements

        // c7[0] TAG is the fixed magic value 0x076ef1ea.
        assertEquals(smartContractInfoTag, (c7[C7Idx.TAG] as TvmTestIntegerValue).value)
        // c7[1] ACTIONS - initialized to 0.
        assertEquals(BigInteger.ZERO, (c7[C7Idx.ACTIONS] as TvmTestIntegerValue).value)
        // c7[2] MSGS_SENT - initialized to 0.
        assertEquals(BigInteger.ZERO, (c7[C7Idx.MSGS_SENT] as TvmTestIntegerValue).value)
        // c7[3] NOW is symbolic; just ensure it is a non-negative integer.
        val now = c7[C7Idx.NOW] as TvmTestIntegerValue
        assertTrue(now.value.signum() >= 0)
        // c7[4] BLOCK_LTIME - initialized to 0.
        assertEquals(BigInteger.ZERO, (c7[C7Idx.BLOCK_LTIME] as TvmTestIntegerValue).value)
        // c7[5] LTIME - initialized to 0.
        assertEquals(BigInteger.ZERO, (c7[C7Idx.LTIME] as TvmTestIntegerValue).value)
        // c7[7] BALANCE - tuple of [int balance, cell extra-currencies] where extra is null.
        val balanceTuple = c7[C7Idx.BALANCE] as TvmTestTupleValue
        assertEquals(2, balanceTuple.elements.size)
        assertTrue(balanceTuple.elements[0] is TvmTestIntegerValue)
        assertEquals(TvmTestNullValue, balanceTuple.elements[1])
        // c7[11] INCOMINGVALUE - tuple of [int msg_value, cell extra-currencies].
        val incoming = c7[C7Idx.INCOMING_VALUE] as TvmTestTupleValue
        assertEquals(2, incoming.elements.size)
        assertTrue(incoming.elements[0] is TvmTestIntegerValue)
        assertEquals(TvmTestNullValue, incoming.elements[1])
        // c7[12] STORAGE_FEES - symbolic in [0, MAX_STORAGE_PHASE_FEES).
        val storageFees = (c7[C7Idx.STORAGE_FEES] as TvmTestIntegerValue).value
        assertTrue(storageFees.signum() >= 0, "storage_fees must be non-negative: $storageFees")
        assertTrue(
            storageFees < TvmContext.MAX_STORAGE_PHASE_FEES.toBigInteger(),
            "storage_fees must be less than MAX_STORAGE_PHASE_FEES: $storageFees",
        )
        // c7[13] PREV_BLOCKS_INFO - currently not modeled, must be null.
        assertEquals(TvmTestNullValue, c7[C7Idx.PREV_BLOCKS])
        // c7[14] UNPACKED_CONFIG_TUPLE - tuple of 7 slices over global config params
        // (current storage prices, global id, gas/fwd prices, size limits); each element is a
        // slice when the config param is present or null when it is absent.
        val unpackedConfig = c7[C7Idx.UNPACKED_CONFIG] as TvmTestTupleValue
        assertEquals(7, unpackedConfig.elements.size)
        assertTrue(
            unpackedConfig.elements.all { it is TvmTestSliceValue || it == TvmTestNullValue },
            "unpacked config tuple elements must be slices or null: ${unpackedConfig.elements}",
        )
        // c7[15] DUE_PAYMENT - symbolic in [0, MAX_DUE_PAYMENT).
        val duePayment = (c7[C7Idx.DUE_PAYMENT] as TvmTestIntegerValue).value
        assertTrue(duePayment.signum() >= 0, "due_payment must be non-negative: $duePayment")
        assertTrue(
            duePayment < TvmContext.MAX_DUE_PAYMENT.toBigInteger(),
            "due_payment must be less than MAX_DUE_PAYMENT: $duePayment",
        )
    }

    @Test
    fun testAllC7Params() {
        val path = getResourcePath<ArgsConstraintsTest>(allC7ParamsPath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        val tests = result.testSuites.single()

        // The contract reaches the sentinel exit code 200 since it only reads
        // each C7 parameter without throwing.
        propertiesFound(
            tests,
            listOf { test -> test.exitCode() == allC7ParamsSuccessCode },
        )

        checkInvariants(
            tests,
            listOf { test ->
                checkC7Invariants(test)
                true
            },
        )

        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testConcreteAllC7Params() {
        val path = getResourcePath<ArgsConstraintsTest>(allC7ParamsPath)
        val concreteBalance = 1_234_567.toBigInteger()
        val result =
            funcCompileAndAnalyzeAllMethods(
                path,
                concreteContractData =
                    TvmConcreteContractData(
                        initialBalance = concreteBalance,
                        addressBits = stonfiAddressBits,
                    ),
                concreteGeneralData = TvmConcreteGeneralData(initialSeed = 42.toBigInteger()),
            )

        val tests = result.testSuites.single()
        assertTrue { tests.isNotEmpty() }

        propertiesFound(
            tests,
            listOf { test -> test.exitCode() == allC7ParamsSuccessCode },
        )

        checkInvariants(
            tests,
            listOf { test ->
                checkC7Invariants(test)
                val c7 = test.initialRootContractState.c7.elements
                // Concrete balance was passed in as `concreteContractData`.
                val balance = (c7[C7Idx.BALANCE] as TvmTestTupleValue).elements[0] as TvmTestIntegerValue
                assertEquals(concreteBalance, balance.value)
                // Concrete random seed was passed in via `concreteGeneralData.initialSeed`.
                assertEquals(42.toBigInteger(), (c7[C7Idx.RAND_SEED] as TvmTestIntegerValue).value)
                true
            },
        )

        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testStorageFees() {
        val path = getResourcePath<ArgsConstraintsTest>(storageFeesPath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        val tests = result.testSuites.single()

        // The symbolic engine must explore all three branches: zero, mid range
        // (>=5 TON, valid since the upper bound is 10 TON exclusive), and
        // "non-zero and less than 5 TON".
        propertiesFound(
            tests,
            listOf(
                { test -> test.exitCode() == 101 }, // sf == 0
                { test -> test.exitCode() == 102 }, // 5 TON <= sf < 10 TON
                { test -> test.exitCode() == 103 }, // 0 < sf < 5 TON
            ),
        )

        // Check the resolved c7[12] value matches the branch path.
        checkInvariants(
            tests,
            listOf { test ->
                val sf =
                    (test.initialRootContractState.c7.elements[C7Idx.STORAGE_FEES] as TvmTestIntegerValue).value
                when (test.exitCode()) {
                    101 -> sf == BigInteger.ZERO
                    102 ->
                        sf >= 5L.times(TvmContext.NANOTONS_IN_TON).toBigInteger() &&
                            sf < TvmContext.MAX_STORAGE_PHASE_FEES.toBigInteger()
                    103 -> sf > BigInteger.ZERO && sf < 5L.times(TvmContext.NANOTONS_IN_TON).toBigInteger()
                    else -> false
                }
            },
        )

        // Concrete sandbox execution: test generation engineers
        // `account.storageStats.used` and `lastPaid` so that the real TVM
        // storage phase produces a storage fee falling into the correct
        // bucket (zero / in `(0, 5 TON)` / `>= 5 TON`) for each branch.
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testDuePayment() {
        val path = getResourcePath<ArgsConstraintsTest>(duePaymentPath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        val tests = result.testSuites.single()

        // The symbolic engine must explore all three branches: zero, mid range
        // (>=0.05 TON, valid since the upper bound is 0.1 TON exclusive), and
        // "non-zero and less than 0.05 TON".
        propertiesFound(
            tests,
            listOf(
                { test -> test.exitCode() == 201 }, // due == 0
                { test -> test.exitCode() == 202 }, // 0.05 TON <= due < 0.1 TON
                { test -> test.exitCode() == 203 }, // 0 < due < 0.05 TON
            ),
        )

        // Check the resolved c7[15] value matches the branch path.
        checkInvariants(
            tests,
            listOf { test ->
                val due =
                    (test.initialRootContractState.c7.elements[C7Idx.DUE_PAYMENT] as TvmTestIntegerValue).value
                val halfBound = TvmContext.MAX_DUE_PAYMENT.toBigInteger().divide(2.toBigInteger())
                when (test.exitCode()) {
                    201 -> due == BigInteger.ZERO
                    202 -> due >= halfBound && due < TvmContext.MAX_DUE_PAYMENT.toBigInteger()
                    203 -> due > BigInteger.ZERO && due < halfBound
                    else -> false
                }
            },
        )

        // Concrete sandbox execution: test generation patches
        // `account.storageStats.duePayment` per test case, so all three branches
        // are reachable in the real TVM as well.
        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testNow() {
        val path = getResourcePath<ArgsConstraintsTest>(nowPath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        propertiesFound(
            result.testSuites.single(),
            listOf(
                { test -> test.result is TvmSuccessfulExecution },
                { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
            ),
        )

        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testConcreteNow() {
        val path = getResourcePath<ArgsConstraintsTest>(nowPath)
        val result =
            funcCompileAndAnalyzeAllMethods(
                path,
                concreteGeneralData =
                    TvmConcreteGeneralData(
                        startTransactionUnixTime = concreteNow,
                    ),
            )

        propertiesFound(
            result.testSuites.single(),
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == 1000 },
        )

        checkInvariants(
            result.testSuites.single(),
            listOf { test -> test.result !is TvmSuccessfulExecution },
        )

        TvmTestExecutor.executeGeneratedTests(result, path, TsRenderer.ContractType.Func)
    }

    @Test
    fun testRecvExternal() {
        val path = extractResource(recvExternalPath)
        val result = funcCompileAndAnalyzeAllMethods(path)

        val tests = result.first { it.methodId == TvmContext.RECEIVE_EXTERNAL_ID }

        checkInvariants(
            tests,
            listOf(
                { test -> (test.result as? TvmTestFailure)?.exitCode != 1000 },
                { test -> (test.result as? TvmTestFailure)?.exitCode != 1001 },
                { test -> (test.result as? TvmTestFailure)?.exitCode != 1002 },
            ),
        )

        propertiesFound(
            tests,
            listOf { test -> (test.result as? TvmTestFailure)?.exitCode == 1003 },
        )
    }
}
