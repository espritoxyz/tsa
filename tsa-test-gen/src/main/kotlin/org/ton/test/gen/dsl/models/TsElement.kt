package org.ton.test.gen.dsl.models

import org.ton.test.gen.dsl.render.TsVisitor
import org.ton.test.gen.dsl.wrapper.TsWrapperDescriptor
import org.usvm.test.resolver.TvmTestBuilderValue
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestDictCellValue
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.TvmTestSliceValue
import java.math.BigInteger

sealed interface TsElement {
    fun <R> accept(visitor: TsVisitor<R>): R =
        visitor.run {
            when (val element = this@TsElement) {
                is TsType -> visitType(element)
                is TsStatement -> visitStatement(element)
                is TsExpression<*> -> visitExpression(element)
            }
        }

    fun <R> TsVisitor<R>.visitType(element: TsType): R =
        when (element) {
            is TsVoid -> visit(element)
            is TsBoolean -> visit(element)
            is TsString -> visit(element)
            is TsCell -> visit(element)
            is TsSlice -> visit(element)
            is TsBuilder -> visit(element)
            is TsAddress -> visit(element)
            is TsBlockchain -> visit(element)
            is TsSendMessageResult -> visit(element)
            is TsInt -> visit(element)
            is TsBigint -> visit(element)
            is TsWrapper -> visit(element)
            is TsObject -> visit(element)
            is TsSandboxContract<*> -> visit(element)
            is TsTransaction -> visit(element)
            is TsArray<*> -> visit(element)
            is TsPredicate<*> -> visit(element)
        }

    fun <R> TsVisitor<R>.visitStatement(element: TsStatement): R =
        when (element) {
            is TsTestFile -> visit(element)
            is TsTestBlock -> visit(element)
            is TsTestCase -> visit(element)
            is TsBeforeEachBlock -> visit(element)
            is TsBeforeAllBlock -> visit(element)
            is TsEmptyLine -> visit(element)
            is TsAssignment<*> -> visit(element)
            is TsDeclaration<*> -> visit(element)
            is TsStatementExpression<*> -> visit(element)
            is TsExpectToEqual<*> -> visit(element)
            is TsExpectToHaveTransaction -> visit(element)
        }

    fun <R, T : TsType> TsVisitor<R>.visitExpression(element: TsExpression<T>): R =
        when (element) {
            is TsBooleanValue -> visit(element)
            is TsIntValue -> visit(element)
            is TsBigintValue -> visit(element)
            is TsStringValue -> visit(element)
            is TsDataCellValue -> visit(element)
            is TsDictValue -> visit(element)
            is TsSliceValue -> visit(element)
            is TsBuilderValue -> visit(element)
            is TsVariable<*> -> visit(element)
            is TsNumAdd<*> -> visit(element)
            is TsNumSub<*> -> visit(element)
            is TsNumDiv<*> -> visit(element)
            is TsNumPow<*> -> visit(element)
            is TsMethodCall<*> -> visit(element)
            is TsFieldAccess<*, *> -> visit(element)
            is TsConstructorCall<*> -> visit(element)
            is TsEquals<*> -> visit(element)
            is TsObjectInit<*> -> visit(element)
            is TsGreater<*> -> visit(element)
            is TsLambdaPredicate<*> -> visit(element)
        }
}

data class TsTestFile(
    val name: String,
    val wrappers: List<TsWrapperDescriptor<*>>,
    val globalStatements: List<TsStatement>,
    val testBlocks: List<TsTestBlock>
) : TsBlock {
    override val statements: List<TsStatement>
        get() = globalStatements
}

// statements

sealed interface TsStatement : TsElement

data object TsEmptyLine : TsStatement

data class TsAssignment<T : TsType>(
    val assigned: TsLValue<T>,
    val assignment: TsExpression<T>
) : TsStatement

data class TsDeclaration<T : TsType>(
    val name: String,
    val type: T,
    val initializer: TsExpression<T>? = null
) : TsStatement {
    val reference: TsVariable<T> = TsVariable(name, type)
}

data class TsStatementExpression<T : TsType>(
    val expr: TsExpression<T>
) : TsStatement

// expressions

sealed interface TsExpression<T : TsType> : TsElement {
    val type: T
}

sealed interface TsLValue<T : TsType> : TsExpression<T>

class TsVariable<T : TsType> internal constructor(
    val name: String,
    override val type: T
) : TsLValue<T> {
    override fun equals(other: Any?): Boolean {
        if (other !is TsVariable<*>) {
            return false
        }
        return name == other.name && type == other.type
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}

data class TsFieldAccess<R : TsType, T : TsType>(
    val receiver: TsExpression<R>,
    val fieldName: String,
    override val type: T
) : TsLValue<T>

data class TsBooleanValue(
    val value: Boolean
) : TsExpression<TsBoolean> {
    override val type: TsBoolean
        get() = TsBoolean
}

data class TsIntValue(
    val value: BigInteger
) : TsExpression<TsInt> {
    override val type: TsInt
        get() = TsInt
}

data class TsBigintValue(
    val value: TvmTestIntegerValue
) : TsExpression<TsBigint> {
    override val type: TsBigint
        get() = TsBigint
}

data class TsStringValue(
    val value: String
) : TsExpression<TsString> {
    override val type: TsString
        get() = TsString
}

data class TsDataCellValue(
    val value: TvmTestDataCellValue
) : TsExpression<TsCell> {
    override val type: TsCell
        get() = TsCell
}

data class TsDictValue(
    val value: TvmTestDictCellValue
) : TsExpression<TsCell> {
    override val type: TsCell
        get() = TsCell
}

data class TsSliceValue(
    val value: TvmTestSliceValue
) : TsExpression<TsSlice> {
    override val type: TsSlice
        get() = TsSlice
}

data class TsBuilderValue(
    val value: TvmTestBuilderValue
) : TsExpression<TsBuilder> {
    override val type: TsBuilder
        get() = TsBuilder
}

data class TsEquals<T : TsType>(
    val lhs: TsExpression<T>,
    val rhs: TsExpression<T>
) : TsExpression<T> {
    override val type: T
        get() = lhs.type
}

data class TsObjectInit<T : TsObject>(
    val args: List<TsExpression<*>>,
    override val type: T
) : TsExpression<T> {
    init {
        require(args.size == type.properties.size) {
            "Less arguments provided than the type requires: ${args.size} out of ${type.properties.size}"
        }

        type.properties.zip(args).forEach { (propertyDescription, arg) ->
            require(arg.type == propertyDescription.second) {
                "Expected ${propertyDescription.second} type but got ${arg.type}"
            }
        }
    }
}

data class TsGreater<T : TsType>(
    val lhs: TsExpression<T>,
    val rhs: TsExpression<T>
) : TsExpression<TsBoolean> {
    override val type = TsBoolean
}

data class TsLambdaPredicate<T : TsType>(
    val argName: String,
    val argType: T,
    val body: (TsVariable<T>) -> TsExpression<TsBoolean>
) : TsExpression<TsPredicate<T>> {
    val arg = TsVariable(argName, argType)
    override val type: TsPredicate<T> = TsPredicate(arg.type)
}

// arithmetic

data class TsNumAdd<T : TsNum>(
    val lhs: TsExpression<T>,
    val rhs: TsExpression<T>
) : TsExpression<T> {
    override val type: T
        get() = lhs.type
}

data class TsNumSub<T : TsNum>(
    val lhs: TsExpression<T>,
    val rhs: TsExpression<T>
) : TsExpression<T> {
    override val type: T
        get() = lhs.type
}

data class TsNumDiv<T : TsNum>(
    val lhs: TsExpression<T>,
    val rhs: TsExpression<T>
) : TsExpression<T> {
    override val type: T
        get() = lhs.type
}

data class TsNumPow<T : TsNum>(
    val lhs: TsExpression<T>,
    val rhs: TsExpression<T>
) : TsExpression<T> {
    override val type: T
        get() = lhs.type
}

// test-utils

sealed class TsExpectStatement(
    open val message: String?
) : TsStatement

data class TsExpectToEqual<T : TsType>(
    val actual: TsExpression<T>,
    val expected: TsExpression<T>,
    override val message: String? = null
) : TsExpectStatement(message)

data class TsExpectToHaveTransaction(
    val sendMessageResult: TsExpression<TsSendMessageResult>,
    val from: TsExpression<TsAddress>?,
    val to: TsExpression<TsAddress>?,
    val value: TsExpression<TsBigint>?,
    val body: TsExpression<TsCell>?,
    val exitCode: TsExpression<TsInt>?,
    val successful: TsExpression<TsBoolean>?,
    val aborted: TsExpression<TsBoolean>?,
    val deploy: TsExpression<TsBoolean>?,
    override val message: String? = null
) : TsExpectStatement(message)

// executable

sealed interface TsExecutableCall<T : TsType> : TsExpression<T> {
    val executableName: String
    val arguments: List<TsExpression<*>>
    val async: Boolean
}

data class TsMethodCall<T : TsType>(
    val caller: TsExpression<*>?,
    override val executableName: String,
    override val arguments: List<TsExpression<*>>,
    override val async: Boolean,
    override val type: T
) : TsExecutableCall<T>

data class TsConstructorCall<T : TsType>(
    override val executableName: String,
    override val arguments: List<TsExpression<*>>,
    override val type: T
) : TsExecutableCall<T> {
    override val async: Boolean
        get() = false
}
