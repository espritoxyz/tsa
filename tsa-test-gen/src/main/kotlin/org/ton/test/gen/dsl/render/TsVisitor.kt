package org.ton.test.gen.dsl.render

import org.ton.test.gen.dsl.models.TsAddress
import org.ton.test.gen.dsl.models.TsArray
import org.ton.test.gen.dsl.models.TsAssignment
import org.ton.test.gen.dsl.models.TsBeforeAllBlock
import org.ton.test.gen.dsl.models.TsBeforeEachBlock
import org.ton.test.gen.dsl.models.TsBigint
import org.ton.test.gen.dsl.models.TsBigintValue
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
import org.ton.test.gen.dsl.models.TsEmptyLine
import org.ton.test.gen.dsl.models.TsEquals
import org.ton.test.gen.dsl.models.TsExpectToEqual
import org.ton.test.gen.dsl.models.TsExpectToHaveTransaction
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

interface TsVisitor<R> {
    // types
    fun visit(element: TsVoid): R

    fun visit(element: TsBoolean): R

    fun visit(element: TsString): R

    fun visit(element: TsCell): R

    fun visit(element: TsSlice): R

    fun visit(element: TsBuilder): R

    fun visit(element: TsAddress): R

    fun visit(element: TsBlockchain): R

    fun visit(element: TsSendMessageResult): R

    fun visit(element: TsInt): R

    fun visit(element: TsBigint): R

    fun visit(element: TsWrapper): R

    fun visit(element: TsObject): R

    fun visit(element: TsTransaction): R

    fun <T : TsType> visit(element: TsPredicate<T>): R

    fun <T : TsWrapper> visit(element: TsSandboxContract<T>): R

    fun <T : TsType> visit(element: TsArray<T>): R

    // blocks
    fun visit(element: TsTestFile): R

    fun visit(element: TsTestBlock): R

    fun visit(element: TsTestCase): R

    fun visit(element: TsBeforeAllBlock): R

    fun visit(element: TsBeforeEachBlock): R

    // statements
    fun visit(element: TsEmptyLine): R

    fun <T : TsType> visit(element: TsAssignment<T>): R

    fun <T : TsType> visit(element: TsDeclaration<T>): R

    fun <T : TsType> visit(element: TsStatementExpression<T>): R

    // executable
    fun <T : TsType> visit(element: TsMethodCall<T>): R

    fun <T : TsType> visit(element: TsConstructorCall<T>): R

    // test-utils
    fun <T : TsType> visit(element: TsExpectToEqual<T>): R

    fun visit(element: TsExpectToHaveTransaction): R

    // expressions
    fun <T : TsType> visit(element: TsVariable<T>): R

    fun <P : TsType, T : TsType> visit(element: TsFieldAccess<P, T>): R

    fun visit(element: TsBooleanValue): R

    fun visit(element: TsIntValue): R

    fun visit(element: TsBigintValue): R

    fun visit(element: TsStringValue): R

    fun visit(element: TsDataCellValue): R

    fun visit(element: TsDictValue): R

    fun visit(element: TsSliceValue): R

    fun visit(element: TsBuilderValue): R

    fun <T : TsType> visit(element: TsEquals<T>): R

    fun <T : TsObject> visit(element: TsObjectInit<T>): R

    fun <T : TsType> visit(element: TsGreater<T>): R

    fun <T : TsType> visit(element: TsLambdaPredicate<T>): R

    // arithmetic
    fun <T : TsNum> visit(element: TsNumAdd<T>): R

    fun <T : TsNum> visit(element: TsNumSub<T>): R

    fun <T : TsNum> visit(element: TsNumDiv<T>): R

    fun <T : TsNum> visit(element: TsNumPow<T>): R
}
