package org.ton

import org.ton.TlbStructure.Empty
import org.ton.TlbStructure.KnownTypePrefix
import org.ton.TlbStructure.LoadRef
import org.ton.TlbStructure.SwitchPrefix
import org.usvm.UExpr
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.mkSizeExpr

/**
 * [TlbLabel] is a building block of TL-B schemes.
 * This is something that can be used as a prefix in [KnownTypePrefix] structure.
 * */
sealed interface TlbLabel {
    val arity: Int
}

/**
 * Some builtin [TlbLabel].
 * It can be both [TlbAtomicLabel] or [TlbCompositeLabel].
 * */
sealed interface TlbBuiltinLabel

/**
 * TL-B primitive.
 * */
sealed class TlbAtomicLabel : TlbLabel {
    val wrapperStructureId: Int = TlbStructureIdProvider.provideId()
}

sealed interface TlbResolvedBuiltinLabel : TlbBuiltinLabel

/**
 * Named TL-B definition.
 * */
open class TlbCompositeLabel(
    val name: String, // TODO: proper id
    var definitelyHasAny: Boolean = false,
) : TlbLabel {
    // this is lateinit for supporting recursive structure
    lateinit var internalStructure: TlbStructure

    override val arity: Int = 0
}

sealed class TlbIntegerLabel :
    TlbAtomicLabel(),
    TlbBuiltinLabel {
    abstract val bitSize: (TvmContext, List<UExpr<TvmSizeSort>>) -> UExpr<TvmSizeSort>
    abstract val isSigned: Boolean
    abstract val endian: Endian
    abstract val lengthUpperBound: Int
}

sealed interface FixedSizeDataLabel {
    val concreteSize: Int
}

class TlbBitArrayOfConcreteSize(
    override val concreteSize: Int,
) : TlbAtomicLabel(),
    TlbResolvedBuiltinLabel,
    FixedSizeDataLabel {
    override val arity: Int = 0

    init {
        check(concreteSize <= TvmContext.MAX_DATA_LENGTH)
    }
}

// only for builders
class TlbBitArrayByRef(
    val sizeBits: UExpr<TvmSizeSort>,
) : TlbAtomicLabel(),
    TlbBuiltinLabel {
    override val arity: Int = 0
}

class TlbIntegerLabelOfConcreteSize(
    override val concreteSize: Int,
    override val isSigned: Boolean,
    override val endian: Endian,
) : TlbIntegerLabel(),
    TlbResolvedBuiltinLabel,
    FixedSizeDataLabel {
    override val arity: Int = 0
    override val bitSize: (TvmContext, List<UExpr<TvmSizeSort>>) -> UExpr<TvmSizeSort> = { ctx, _ ->
        ctx.mkSizeExpr(concreteSize)
    }
    override val lengthUpperBound: Int
        get() = concreteSize

    override fun toString(): String = "TlbInteger(size=$concreteSize, isSigned=$isSigned, endian=$endian)"
}

class TlbIntegerLabelOfSymbolicSize(
    override val isSigned: Boolean,
    override val endian: Endian,
    override val arity: Int,
    override val lengthUpperBound: Int = if (isSigned) 257 else 256,
    override val bitSize: (TvmContext, List<UExpr<TvmSizeSort>>) -> UExpr<TvmSizeSort>,
) : TlbIntegerLabel()

data object TlbEmptyLabel : TlbCompositeLabel("") {
    init {
        internalStructure = Empty
    }
}

sealed interface TlbMsgAddrLabel : TlbResolvedBuiltinLabel

// only for builders
class TlbAddressByRef(
    val sizeBits: UExpr<TvmSizeSort>,
) : TlbAtomicLabel(),
    TlbBuiltinLabel,
    TlbMsgAddrLabel {
    override val arity: Int = 0
}

data object TlbBasicMsgAddrLabel : TlbMsgAddrLabel, TlbCompositeLabel("MsgAddr") {
    init {
        internalStructure =
            SwitchPrefix(
                id = TlbStructureIdProvider.provideId(),
                switchSize = 11,
                mapOf(
                    "10000000000" to
                        KnownTypePrefix(
                            id = TlbStructureIdProvider.provideId(),
                            TlbInternalShortStdMsgAddrLabel,
                            typeArgIds = emptyList(),
                            rest = Empty,
                            owner = this
                        )
                ),
                owner = this
            )
    }
}

class TlbMaybeRefLabel(
    val refInfo: TvmParameterInfo.CellInfo,
) : TlbCompositeLabel("Maybe"),
    TlbResolvedBuiltinLabel {
    init {
        internalStructure =
            SwitchPrefix(
                id = TlbStructureIdProvider.provideId(),
                switchSize = 1,
                givenVariants =
                    mapOf(
                        "0" to Empty,
                        "1" to
                            LoadRef(
                                id = TlbStructureIdProvider.provideId(),
                                ref = refInfo,
                                rest = Empty,
                                owner = this
                            )
                    ),
                owner = this
            )
    }
}

private const val INTERNAL_STD_MSG_ADDR_SIZE = 8 + 256
private const val INTERNAL_SHORT_STD_MSG_ADDR_SIZE = 256

// artificial label
data object TlbInternalStdMsgAddrLabel : TlbAtomicLabel(), FixedSizeDataLabel {
    override val arity = 0
    override val concreteSize: Int = INTERNAL_STD_MSG_ADDR_SIZE
}

// artificial label
data object TlbInternalShortStdMsgAddrLabel : TlbAtomicLabel(), FixedSizeDataLabel {
    override val arity = 0
    override val concreteSize: Int = INTERNAL_SHORT_STD_MSG_ADDR_SIZE
}

data object TlbCoinsLabel : TlbResolvedBuiltinLabel, TlbCompositeLabel("Coins") {
    init {
        val coinPrefixId = TlbStructureIdProvider.provideId()

        internalStructure =
            KnownTypePrefix(
                id = coinPrefixId,
                TlbIntegerLabelOfConcreteSize(
                    concreteSize = 4,
                    isSigned = false,
                    endian = Endian.BigEndian
                ),
                typeArgIds = emptyList(),
                rest =
                    KnownTypePrefix(
                        id = TlbStructureIdProvider.provideId(),
                        TlbIntegerLabelOfSymbolicSize(
                            isSigned = false,
                            endian = Endian.BigEndian,
                            lengthUpperBound = 120,
                            arity = 1
                        ) { ctx, args ->
                            ctx.mkBvMulExpr(args.single(), ctx.mkSizeExpr(8))
                        },
                        typeArgIds = listOf(coinPrefixId),
                        rest = Empty,
                        owner = this
                    ),
                owner = this
            )
    }
}

enum class Endian {
    LittleEndian,
    BigEndian,
}
