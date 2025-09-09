package org.ton.test.gen.dsl

import org.ton.test.gen.dsl.models.TsAddress
import org.ton.test.gen.dsl.models.TsAssignment
import org.ton.test.gen.dsl.models.TsBeforeAllBlock
import org.ton.test.gen.dsl.models.TsBeforeEachBlock
import org.ton.test.gen.dsl.models.TsBigint
import org.ton.test.gen.dsl.models.TsBlock
import org.ton.test.gen.dsl.models.TsBoolean
import org.ton.test.gen.dsl.models.TsCell
import org.ton.test.gen.dsl.models.TsDeclaration
import org.ton.test.gen.dsl.models.TsElement
import org.ton.test.gen.dsl.models.TsEmptyLine
import org.ton.test.gen.dsl.models.TsExpectToEqual
import org.ton.test.gen.dsl.models.TsExpectToHaveTransaction
import org.ton.test.gen.dsl.models.TsExpression
import org.ton.test.gen.dsl.models.TsInt
import org.ton.test.gen.dsl.models.TsLValue
import org.ton.test.gen.dsl.models.TsSendMessageResult
import org.ton.test.gen.dsl.models.TsStatement
import org.ton.test.gen.dsl.models.TsStatementExpression
import org.ton.test.gen.dsl.models.TsTestBlock
import org.ton.test.gen.dsl.models.TsTestCase
import org.ton.test.gen.dsl.models.TsTestFile
import org.ton.test.gen.dsl.models.TsType
import org.ton.test.gen.dsl.models.TsVariable
import org.ton.test.gen.dsl.wrapper.TsWrapperDescriptor

interface TsBuilder<T : TsElement> {
    val ctx: TsContext

    fun build(): T
}

abstract class TsBlockBuilder<T : TsBlock> : TsBuilder<T> {
    protected val statements = mutableListOf<TsStatement>()

    fun <T : TsType> newVar(
        name: String,
        init: TsExpression<T>
    ): TsVariable<T> = newVar(name, init.type, init)

    fun <T : TsType> newVar(
        name: String,
        type: T,
        init: TsExpression<T>? = null
    ): TsVariable<T> {
        val declaration = TsDeclaration(name, type, init)
        statements += declaration

        if (init == null) {
            ctx.markAsMutable(declaration.reference)
        }

        return declaration.reference
    }

    fun emptyLine() {
        statements += TsEmptyLine
    }

    infix fun <T : TsType> TsLValue<T>.assign(value: TsExpression<T>) {
        if (this is TsVariable) {
            ctx.markAsMutable(this)
        }

        statements += TsAssignment(this, value)
    }

    fun <T : TsType> TsExpression<T>.expectToEqual(
        expected: TsExpression<T>,
        message: String? = null
    ) {
        statements += TsExpectToEqual(actual = this, expected, message = message)
    }

    fun TsExpression<TsSendMessageResult>.expectToHaveTransaction(block: TsExpectToHaveTransactionBuilder.() -> Unit) {
        statements += TsExpectToHaveTransactionBuilder(ctx, sendMessageResult = this).apply(block).build()
    }

    operator fun TsExpression<*>.unaryPlus() {
        statements += TsStatementExpression(this)
    }

    fun beforeAll(block: TsBeforeAllBuilder.() -> Unit) {
        statements += TsBeforeAllBuilder(ctx).apply(block).build()
    }

    fun beforeEach(block: TsBeforeEachBuilder.() -> Unit) {
        statements += TsBeforeEachBuilder(ctx).apply(block).build()
    }
}

data class TsTestFileBuilder(
    override val ctx: TsContext,
    val name: String
) : TsBlockBuilder<TsTestFile>() {
    private val wrappers = mutableListOf<TsWrapperDescriptor<*>>()
    private val testBlocks = mutableListOf<TsTestBlock>()

    fun describe(
        name: String,
        block: TsTestBlockBuilder.() -> Unit
    ) {
        testBlocks += TsTestBlockBuilder(ctx, name).apply(block).build()
    }

    fun registerWrapper(wrapper: TsWrapperDescriptor<*>) {
        wrappers += wrapper
    }

    override fun build(): TsTestFile = TsTestFile(name, wrappers, statements, testBlocks)
}

class TsTestBlockBuilder(
    override val ctx: TsContext,
    private val name: String
) : TsBlockBuilder<TsTestBlock>() {
    fun it(
        name: String,
        block: TsTestCaseBuilder.() -> Unit
    ) {
        statements += TsTestCaseBuilder(ctx, name).apply(block).build()
    }

    override fun build(): TsTestBlock = TsTestBlock(name, statements)
}

class TsBeforeAllBuilder(
    override val ctx: TsContext
) : TsBlockBuilder<TsBeforeAllBlock>() {
    override fun build(): TsBeforeAllBlock = TsBeforeAllBlock(statements)
}

class TsBeforeEachBuilder(
    override val ctx: TsContext
) : TsBlockBuilder<TsBeforeEachBlock>() {
    override fun build(): TsBeforeEachBlock = TsBeforeEachBlock(statements)
}

class TsTestCaseBuilder(
    override val ctx: TsContext,
    private val name: String
) : TsBlockBuilder<TsTestCase>() {
    override fun build(): TsTestCase = TsTestCase(name, statements)
}

class TsExpectToHaveTransactionBuilder(
    override val ctx: TsContext,
    private val sendMessageResult: TsExpression<TsSendMessageResult>
) : TsBuilder<TsExpectToHaveTransaction> {
    var from: TsExpression<TsAddress>? = null
    var to: TsExpression<TsAddress>? = null
    var value: TsExpression<TsBigint>? = null
    var body: TsExpression<TsCell>? = null
    var exitCode: TsExpression<TsInt>? = null
    var successful: TsExpression<TsBoolean>? = null
    var aborted: TsExpression<TsBoolean>? = null
    var deploy: TsExpression<TsBoolean>? = null
    var message: String? = null

    override fun build(): TsExpectToHaveTransaction =
        TsExpectToHaveTransaction(
            sendMessageResult,
            from,
            to,
            value,
            body,
            exitCode,
            successful,
            aborted,
            deploy,
            message
        )
}

fun TsContext.testFile(
    name: String,
    block: TsTestFileBuilder.() -> Unit
) = TsTestFileBuilder(ctx = this, name).apply(block).build()
