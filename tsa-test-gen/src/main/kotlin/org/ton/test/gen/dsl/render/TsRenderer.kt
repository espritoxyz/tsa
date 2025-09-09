package org.ton.test.gen.dsl.render

import org.ton.test.gen.dsl.TsContext
import org.ton.test.gen.dsl.models.TsAddress
import org.ton.test.gen.dsl.models.TsArray
import org.ton.test.gen.dsl.models.TsAssignment
import org.ton.test.gen.dsl.models.TsBeforeAllBlock
import org.ton.test.gen.dsl.models.TsBeforeEachBlock
import org.ton.test.gen.dsl.models.TsBigint
import org.ton.test.gen.dsl.models.TsBigintValue
import org.ton.test.gen.dsl.models.TsBlock
import org.ton.test.gen.dsl.models.TsBlockchain
import org.ton.test.gen.dsl.models.TsBoolean
import org.ton.test.gen.dsl.models.TsBooleanValue
import org.ton.test.gen.dsl.models.TsBuilder
import org.ton.test.gen.dsl.models.TsBuilderValue
import org.ton.test.gen.dsl.models.TsCell
import org.ton.test.gen.dsl.models.TsConstructorCall
import org.ton.test.gen.dsl.models.TsDataCellValue
import org.ton.test.gen.dsl.models.TsDeclaration
import org.ton.test.gen.dsl.models.TsDictValue
import org.ton.test.gen.dsl.models.TsElement
import org.ton.test.gen.dsl.models.TsEmptyLine
import org.ton.test.gen.dsl.models.TsEquals
import org.ton.test.gen.dsl.models.TsExecutableCall
import org.ton.test.gen.dsl.models.TsExpectToEqual
import org.ton.test.gen.dsl.models.TsExpectToHaveTransaction
import org.ton.test.gen.dsl.models.TsExpression
import org.ton.test.gen.dsl.models.TsFieldAccess
import org.ton.test.gen.dsl.models.TsGreater
import org.ton.test.gen.dsl.models.TsInt
import org.ton.test.gen.dsl.models.TsIntValue
import org.ton.test.gen.dsl.models.TsLambdaPredicate
import org.ton.test.gen.dsl.models.TsMethodCall
import org.ton.test.gen.dsl.models.TsNum
import org.ton.test.gen.dsl.models.TsNumAdd
import org.ton.test.gen.dsl.models.TsNumDiv
import org.ton.test.gen.dsl.models.TsNumPow
import org.ton.test.gen.dsl.models.TsNumSub
import org.ton.test.gen.dsl.models.TsObject
import org.ton.test.gen.dsl.models.TsObjectInit
import org.ton.test.gen.dsl.models.TsPredicate
import org.ton.test.gen.dsl.models.TsSandboxContract
import org.ton.test.gen.dsl.models.TsSendMessageResult
import org.ton.test.gen.dsl.models.TsSlice
import org.ton.test.gen.dsl.models.TsSliceValue
import org.ton.test.gen.dsl.models.TsStatementExpression
import org.ton.test.gen.dsl.models.TsString
import org.ton.test.gen.dsl.models.TsStringValue
import org.ton.test.gen.dsl.models.TsTestBlock
import org.ton.test.gen.dsl.models.TsTestCase
import org.ton.test.gen.dsl.models.TsTestFile
import org.ton.test.gen.dsl.models.TsTransaction
import org.ton.test.gen.dsl.models.TsType
import org.ton.test.gen.dsl.models.TsVariable
import org.ton.test.gen.dsl.models.TsVoid
import org.ton.test.gen.dsl.models.TsWrapper
import org.ton.test.gen.dsl.wrapper.TsWrapperDescriptor
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestDictCellValue
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.TvmTestNullValue
import org.usvm.test.resolver.TvmTestSliceValue
import org.usvm.test.resolver.TvmTestValue
import org.usvm.test.resolver.truncateSliceCell

class TsRenderer(
    private val ctx: TsContext,
    private val contractType: ContractType,
) : TsVisitor<Unit> {
    enum class ContractType {
        Boc,
        Func,
    }

    private val printer = TsPrinterImpl()

    private val maxPrecedence = 18

    fun renderTests(test: TsTestFile): TsRenderedTest {
        printer.clear()

        val wrappers = test.wrappers.map { renderWrapper(it) }

        test.accept(this)

        return TsRenderedTest(
            fileName = test.name + ".spec.ts",
            wrappers = wrappers,
            code = printer.toString()
        )
    }

    private fun renderWrapper(wrapper: TsWrapperDescriptor<*>): TsRenderedWrapper =
        TsRenderedWrapper(
            fileName = wrapper.name + ".ts",
            code = wrapper.renderFile()
        )

    private fun renderCodeBlock(codeBlock: TsBlock) {
        codeBlock.statements.forEach { it.accept(this) }
    }

    override fun visit(element: TsVoid) {
        printer.print("void")
    }

    override fun visit(element: TsBoolean) {
        printer.print("boolean")
    }

    override fun visit(element: TsString) {
        printer.print("string")
    }

    override fun visit(element: TsCell) {
        printer.print("Cell")
    }

    override fun visit(element: TsSlice) {
        printer.print("Slice")
    }

    override fun visit(element: TsBuilder) {
        printer.print("Builder")
    }

    override fun visit(element: TsAddress) {
        printer.print("Address")
    }

    override fun visit(element: TsBlockchain) {
        printer.print("Blockchain")
    }

    override fun visit(element: TsSendMessageResult) {
        printer.print("SendMessageResult")
    }

    override fun visit(element: TsInt) {
        printer.print("number")
    }

    override fun visit(element: TsBigint) {
        printer.print("bigint")
    }

    override fun visit(element: TsObject) {
        printer.print("{ ")

        element.properties.forEach {
            printer.print("${it.first}: ")
            it.second.accept(this)
            printer.print("; ")
        }

        printer.print("}")
    }

    override fun visit(element: TsTransaction) {
        printer.print("Transaction")
    }

    override fun <T : TsType> visit(element: TsPredicate<T>) {
        printer.print("(_: ")
        element.argType.accept(this)
        printer.print(") => ")
        TsBoolean.accept(this)
    }

    override fun visit(element: TsWrapper) {
        printer.print(element.name)
    }

    override fun <T : TsWrapper> visit(element: TsSandboxContract<T>) {
        printer.print("SandboxContract<")
        element.wrapperType.accept(this)
        printer.print(">")
    }

    override fun <T : TsType> visit(element: TsArray<T>) {
        element.elementType.accept(this)
        printer.print("[]")
    }

    override fun visit(element: TsTestFile) {
        val wrapperImports =
            element.wrappers.joinToString(
                separator = System.lineSeparator(),
                transform = ::renderWrapperImport
            )

        // TODO optimize imports
        printer.println(TEST_FILE_IMPORTS)
        printer.println(wrapperImports)

        when (contractType) {
            ContractType.Func -> {
                printer.println(FUNC_COMPILER_IMPORT)
                printer.println()
                printer.println(COMPILE_FUNC_CONTRACT)
            }
            ContractType.Boc -> {
                printer.println()
                printer.println(COMPILE_BOC_CONTRACT)
            }
        }

        printer.println()
        printer.println(TEST_FILE_UTILS)

        element.statements.forEach { it.accept(this) }

        printer.println()
        element.testBlocks.forEach { it.accept(this) }
    }

    private fun renderWrapperImport(wrapper: TsWrapperDescriptor<*>): String =
        "import {${wrapper.name}} from \"../$WRAPPERS_DIR_NAME/${wrapper.name}\""

    override fun visit(element: TsTestBlock) {
        printer.println("describe('${element.name}', () => {")
        withIndent { renderCodeBlock(element) }
        printer.print("})")
        endStatement()
    }

    override fun visit(element: TsTestCase) {
        printer.println("it('${element.name}', async () => {")
        withIndent { renderCodeBlock(element) }
        printer.print("})")
        endStatement()
    }

    override fun visit(element: TsBeforeAllBlock) {
        printer.println("beforeAll(async () => {")
        withIndent { renderCodeBlock(element) }
        printer.print("})")
        endStatement()
    }

    override fun visit(element: TsBeforeEachBlock) {
        printer.println("beforeEach(async () => {")
        withIndent { renderCodeBlock(element) }
        printer.print("})")
        endStatement()
    }

    override fun visit(element: TsEmptyLine) {
        printer.println()
    }

    override fun <T : TsType> visit(element: TsAssignment<T>) {
        element.assigned.accept(this)
        printer.print(" = ")
        element.assignment.accept(this)
        endStatement()
    }

    override fun <T : TsType> visit(element: TsDeclaration<T>) =
        with(ctx) {
            val ref = element.reference
            val declaration = if (ref.isMutable()) "let" else "const"

            printer.print("$declaration ${element.name}: ")
            element.type.accept(this@TsRenderer)

            if (element.initializer != null) {
                printer.print(" = ")
                element.initializer.accept(this@TsRenderer)
            }

            endStatement()
        }

    override fun <T : TsType> visit(element: TsStatementExpression<T>) {
        element.expr.accept(this)
        endStatement()
    }

    override fun <T : TsType> visit(element: TsVariable<T>) {
        printer.print(element.name)
    }

    override fun <R : TsType, T : TsType> visit(element: TsFieldAccess<R, T>) {
        precedencePrint(element.receiver, element)
        printer.print(".${element.fieldName}")
    }

    private fun renderExecutableCall(
        caller: TsExpression<*>?,
        element: TsExecutableCall<*>,
    ) {
        if (element.async) {
            printer.print("await ")
        }

        if (caller != null) {
            caller.accept(this)
            printer.print(".")
        }

        printer.print(element.executableName + "(")
        element.arguments.forEachIndexed { idx, arg ->
            arg.accept(this)

            if (idx < element.arguments.lastIndex) {
                printer.print(", ")
            }
        }
        printer.print(")")
    }

    override fun <T : TsType> visit(element: TsMethodCall<T>) {
        renderExecutableCall(element.caller, element)
    }

    override fun <T : TsType> visit(element: TsConstructorCall<T>) {
        printer.print("new ")
        renderExecutableCall(caller = null, element)
    }

    override fun <T : TsType> visit(element: TsExpectToEqual<T>) {
        if (element.actual.type == TsBigint) {
            // workaround, since jest cannot serialize BigInt with --json flag

            if (element.message == null) {
                printer.print("expect(")
                TsEquals(element.actual, element.expected).accept(this)
                printer.print(").toBe(true)")
            } else {
                printer.print("assertWithMessage(")
                TsEquals(element.actual, element.expected).accept(this)
                printer.print(", \"${element.message}\")")
            }

            endStatement()

            return
        }

        if (element.message == null) {
            printer.print("expect(")
            element.actual.accept(this)
            printer.print(").toEqual(")
            element.expected.accept(this)
            printer.print(")")
        } else {
            printer.print("assertWithMessage(")
            TsEquals(element.actual, element.expected).accept(this)
            printer.print(", \"${element.message}\")")
        }

        endStatement()
    }

    override fun visit(element: TsExpectToHaveTransaction) {
        if (element.message == null) {
            printer.print("expect(")
        } else {
            printer.print("assertWithMessage(hasTransaction(")
        }
        element.sendMessageResult.accept(this)
        if (element.message == null) {
            printer.println(".transactions).toHaveTransaction({")
        } else {
            printer.println(".transactions, {")
        }

        val printProperty = { el: TsElement?, propertyName: String ->
            if (el != null) {
                printer.print("$propertyName: ")
                el.accept(this)
                printer.println(",")
            }
        }

        withIndent {
            printProperty(element.from, "from")
            printProperty(element.to, "to")
            printProperty(element.value, "value")
            printProperty(element.body, "body")
            printProperty(element.exitCode, "exitCode")
            printProperty(element.successful, "successful")
            printProperty(element.aborted, "aborted")
            printProperty(element.deploy, "deploy")
        }

        if (element.message == null) {
            printer.print("})")
        } else {
            printer.print("}), \"${element.message}\")")
        }

        endStatement()
    }

    override fun <T : TsNum> visit(element: TsNumAdd<T>) {
        precedencePrint(element.lhs, element)
        printer.print(" + ")
        precedencePrint(element.rhs, element)
    }

    override fun <T : TsNum> visit(element: TsNumSub<T>) {
        precedencePrint(element.lhs, element)
        printer.print(" - ")
        precedencePrint(element.rhs, element)
    }

    override fun <T : TsNum> visit(element: TsNumDiv<T>) {
        precedencePrint(element.lhs, element)
        printer.print(" / ")
        precedencePrint(element.rhs, element)
    }

    override fun <T : TsNum> visit(element: TsNumPow<T>) {
        precedencePrint(element.lhs, element)
        printer.print(" ** ")
        precedencePrint(element.rhs, element)
    }

    override fun visit(element: TsBooleanValue) {
        printer.print(element.value.toString())
    }

    override fun visit(element: TsIntValue) {
        printer.print(element.value.toString())
    }

    override fun visit(element: TsBigintValue) {
        printer.print(renderTestValue(element.value))
        printer.print("n")
    }

    override fun visit(element: TsStringValue) {
        printer.print("String.raw`${element.value}`")
    }

    override fun visit(element: TsDataCellValue) {
        printer.print(renderTestValue(element.value))
    }

    override fun visit(element: TsDictValue) {
        printer.print(renderTestValue(element.value))
    }

    override fun visit(element: TsSliceValue) {
        printer.print(renderTestValue(element.value))
    }

    override fun visit(element: TsBuilderValue) {
        printer.print(renderTestValue(element.value))
    }

    override fun <T : TsType> visit(element: TsEquals<T>) {
        precedencePrint(element.lhs, element)
        printer.print(" == ")
        precedencePrint(element.rhs, element)
    }

    override fun <T : TsObject> visit(element: TsObjectInit<T>) {
        printer.print("{ ")

        element.type.properties.zip(element.args).forEach { (propertyDescription, arg) ->
            printer.print("${propertyDescription.first}: ")
            arg.accept(this)
            printer.print(", ")
        }

        printer.print("}")
    }

    override fun <T : TsType> visit(element: TsGreater<T>) {
        precedencePrint(element.lhs, element)
        printer.print(" > ")
        precedencePrint(element.rhs, element)
    }

    override fun <T : TsType> visit(element: TsLambdaPredicate<T>) {
        printer.print(element.argName)
        printer.print(" => ")
        precedencePrint(element.body(element.arg), element)
    }

    private fun precedencePrint(
        element: TsExpression<*>,
        parent: TsExpression<*>,
    ) {
        // TODO support associativity

        if (element.precedence() <= parent.precedence() && element.precedence() != maxPrecedence) {
            printer.print("(")
            element.accept(this)
            printer.print(")")
        } else {
            element.accept(this)
        }
    }

    private fun TsExpression<*>.precedence(): Int =
        // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Operator_precedence#table
        when (val element = this) {
            is TsIntValue -> maxPrecedence
            is TsBigintValue -> maxPrecedence
            is TsBooleanValue -> maxPrecedence
            is TsStringValue -> maxPrecedence
            is TsBuilderValue -> maxPrecedence
            is TsDataCellValue -> maxPrecedence
            is TsSliceValue -> maxPrecedence
            is TsDictValue -> maxPrecedence
            is TsVariable -> maxPrecedence
            is TsObjectInit<*> -> maxPrecedence

            is TsFieldAccess<*, *> -> 17
            is TsConstructorCall<*> -> if (element.arguments.isEmpty()) 16 else 17
            is TsMethodCall<*> -> if (element.async) 14 else maxPrecedence
            is TsNumDiv<*> -> 12
            is TsNumAdd<*> -> 11
            is TsNumSub<*> -> 11
            is TsEquals<*> -> 8
            is TsNumPow -> 13
            is TsGreater<*> -> 9
            is TsLambdaPredicate<*> -> 2
        }

    private fun renderTestValue(arg: TvmTestValue): String =
        when (arg) {
            is TvmTestNullValue -> "null()"
            is TvmTestIntegerValue -> arg.value.toString()
            is TvmTestSliceValue -> "${renderTestValue(truncateSliceCell(arg))}.beginParse()"
            is TvmTestDictCellValue -> {
                val dictInit = "Dictionary.empty(Dictionary.Keys.BigInt(${arg.keyLength}), sliceValue)"
                val dictStores =
                    arg.entries.map { entry ->
                        ".set(${renderTestValue(entry.key)}n, ${renderTestValue(entry.value)})"
                    }

                "${dictInit}${dictStores.joinToString(separator = "")}"
            }
            is TvmTestDataCellValue -> {
                // TODO use loads

                val storeBits = { bitsToStore: String ->
                    val bitsLength = bitsToStore.length
                    val formattedBits = bitsToStore.takeIf { it.any { char -> char != '0' } } ?: "0"
                    val bitsValue = "BigInt(\"0b$formattedBits\")"

                    ".storeUint($bitsValue, $bitsLength)".takeIf { bitsLength > 0 } ?: ""
                }

                val storesBuilder = StringBuilder()
                var bitsLeft = arg.data

                arg.refs
                    .map { it to renderTestValue(it) }
                    .forEach { (ref, refValue) ->
                        when (ref) {
                            is TvmTestDataCellValue -> storesBuilder.append(".storeRef($refValue)")
                            is TvmTestDictCellValue -> {
                                val justConstructorIdx = bitsLeft.indexOfFirst { it == '1' }

                                check(justConstructorIdx != -1) {
                                    "Cell contains more dict refs than non-zero bits"
                                }

                                val bitsToStore = bitsLeft.take(justConstructorIdx)
                                bitsLeft = bitsLeft.drop(justConstructorIdx + 1)

                                storesBuilder.append(storeBits(bitsToStore))
                                storesBuilder.append(".storeDict($refValue)")
                            }
                        }
                    }

                storesBuilder.append(storeBits(bitsLeft))

                "beginCell()$storesBuilder.endCell()"
            }
            else -> TODO("Not yet implemented: $arg")
        }

    private fun endStatement() = printer.println(STATEMENT_END)

    private inline fun withIndent(block: () -> Unit) {
        try {
            printer.pushIndent()
            block()
        } finally {
            printer.popIndent()
        }
    }

    companion object {
        const val WRAPPERS_DIR_NAME: String = "wrappers"
        const val TESTS_DIR_NAME: String = "tests"

        private const val STATEMENT_END: String = ""

        private val TEST_FILE_IMPORTS =
            """
            import {Blockchain, createShardAccount, SandboxContract, SendMessageResult} from '@ton/sandbox'
            import {Address, beginCell, Builder, Cell, Dictionary, DictionaryValue, Slice, toNano, Transaction} from '@ton/core'
            import '@ton/test-utils'
            import * as fs from "node:fs"
            import {randomAddress, findTransaction} from "@ton/test-utils"
            """.trimIndent()

        private val TEST_FILE_UTILS =
            """
            const bigIntMin = (...args: bigint[]) => args.reduce((m, e) => e < m ? e : m)
            const bigIntMax = (...args: bigint[]) => args.reduce((m, e) => e > m ? e : m)
            
            async function initializeContract(
                blockchain: Blockchain, 
                address: Address, 
                code: Cell, 
                data: Cell, 
                balance: bigint = toNano(100)
            ) {
                const contr = await blockchain.getContract(address);
                contr.account = createShardAccount({
                    address: address,
                    code: code,
                    data: data,
                    balance: balance,
                    workchain: 0
                })
            }
            
            const sliceValue: DictionaryValue<Slice> = {
                serialize: (src: Slice, builder: Builder) => {
                    builder.storeSlice(src)
                },
                parse: (src: Slice) => {
                    return src.clone()
                }
            }
            
            async function cellFromHex(hex: string): Promise<Cell> {
                return Cell.fromBoc(Buffer.from(hex, 'hex'))[0];
            }

            function assertWithMessage(predicate: boolean, message: string) {
                if (!predicate) {
                    throw new Error(message)
                }
            }
            
            function hasTransaction(txs: any, match: any) {
                return findTransaction(txs, match) !== undefined
            }

            """.trimIndent()

        private const val FUNC_COMPILER_IMPORT = "import {compileFunc} from \"@ton-community/func-js\""

        private val COMPILE_FUNC_CONTRACT =
            """
            async function compileContract(target: string): Promise<Cell> {
                let compileResult = await compileFunc({
                    targets: [target],
                    sources: (x) => fs.readFileSync(x).toString("utf8"),
                })
            
                if (compileResult.status === "error") {
                    console.error("Compilation Error!")
                    console.error(`\n${'$'}{compileResult.message}`)
                    process.exit(1)
                }
            
                return Cell.fromBoc(Buffer.from(compileResult.codeBoc, "base64"))[0]
            }
            """.trimIndent()

        private val COMPILE_BOC_CONTRACT =
            """
            async function compileContract(target: string): Promise<Cell> {
                 const fileBuffer = fs.readFileSync(target);
                 return Cell.fromBoc(fileBuffer)[0]
            }
            """.trimIndent()
    }
}

data class TsRenderedWrapper(
    val fileName: String,
    val code: String,
)

data class TsRenderedTest(
    val fileName: String,
    val code: String,
    val wrappers: List<TsRenderedWrapper>,
)
