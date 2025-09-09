package org.ton.test.gen.dsl.wrapper.basic

import org.ton.test.gen.dsl.models.TsAddress
import org.ton.test.gen.dsl.models.TsBigint
import org.ton.test.gen.dsl.models.TsBlockchain
import org.ton.test.gen.dsl.models.TsBoolean
import org.ton.test.gen.dsl.models.TsCell
import org.ton.test.gen.dsl.models.TsConstructorCall
import org.ton.test.gen.dsl.models.TsExpression
import org.ton.test.gen.dsl.models.TsInt
import org.ton.test.gen.dsl.models.TsMethodCall
import org.ton.test.gen.dsl.models.TsSandboxContract
import org.ton.test.gen.dsl.models.TsSendMessageResult
import org.ton.test.gen.dsl.models.TsVoid
import org.ton.test.gen.dsl.models.TsWrapper

data class TsBasicWrapper(
    override val name: String
) : TsWrapper

fun TsBasicWrapperDescriptor.constructor(
    address: TsExpression<TsAddress>,
    code: TsExpression<TsCell>,
    data: TsExpression<TsCell>
): TsExpression<TsBasicWrapper> =
    TsConstructorCall(
        executableName = name,
        arguments = listOf(address, code, data),
        type = wrapperType
    )

fun TsExpression<TsSandboxContract<TsBasicWrapper>>.internal(
    blockchain: TsExpression<TsBlockchain>,
    sender: TsExpression<TsAddress>,
    body: TsExpression<TsCell>,
    value: TsExpression<TsBigint>,
    bounce: TsExpression<TsBoolean>,
    bounced: TsExpression<TsBoolean>,
    ihrDisabled: TsExpression<TsBoolean>,
    ihrFee: TsExpression<TsBigint>,
    forwardFee: TsExpression<TsBigint>,
    createdLt: TsExpression<TsBigint>,
    createdAt: TsExpression<TsInt>
): TsExpression<TsSendMessageResult> =
    TsMethodCall(
        caller = this,
        executableName = "internal",
        arguments =
            listOf(
                blockchain,
                sender,
                body,
                value,
                bounce,
                bounced,
                ihrDisabled,
                ihrFee,
                forwardFee,
                createdLt,
                createdAt
            ),
        async = true,
        type = TsSendMessageResult
    )

fun TsExpression<TsSandboxContract<TsBasicWrapper>>.external(
    blockchain: TsExpression<TsBlockchain>,
    body: TsExpression<TsCell>
): TsExpression<TsSendMessageResult> =
    TsMethodCall(
        caller = this,
        executableName = "external",
        arguments = listOf(blockchain, body),
        async = true,
        type = TsSendMessageResult
    )

fun TsExpression<TsSandboxContract<TsBasicWrapper>>.initializeContract(
    blockchain: TsExpression<TsBlockchain>,
    balance: TsExpression<TsBigint>
): TsExpression<TsVoid> =
    TsMethodCall(
        caller = this,
        executableName = "initializeContract",
        arguments = listOf(blockchain, balance),
        async = true,
        type = TsVoid
    )
