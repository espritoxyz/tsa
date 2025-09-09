package org.ton.test.gen.dsl.models

sealed interface TsType : TsElement

data object TsVoid : TsType

data object TsBoolean : TsType

data object TsString : TsType

data object TsCell : TsType

data object TsSlice : TsType

data object TsBuilder : TsType

data object TsAddress : TsType

data object TsBlockchain : TsType

data object TsSendMessageResult : TsType

data object TsTransaction : TsType

data class TsArray<T : TsType>(
    val elementType: T
) : TsType

data class TsPredicate<T : TsType>(
    val argType: T
) : TsType

sealed interface TsNum : TsType

data object TsInt : TsNum

data object TsBigint : TsNum

interface TsWrapper : TsType {
    val name: String
}

data class TsSandboxContract<T : TsWrapper>(
    val wrapperType: T
) : TsType

interface TsObject : TsType {
    val properties: List<Pair<String, TsType>>
}

data object TsBlockchainOpts : TsObject {
    override val properties: List<Pair<String, TsType>> = listOf("config" to TsCell)
}
