package org.usvm.machine.types

import org.usvm.types.USingleTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class TvmTypeSystem : UTypeSystem<TvmType> {
    override val typeOperationsTimeout: Duration
        get() = Duration.INFINITE // TODO: better timeout?

    override fun findSubtypes(type: TvmType): Sequence<TvmType> =
        allFinalTypes.asSequence().filter { isSupertype(type, it) }

    override fun hasCommonSubtype(
        type: TvmType,
        types: Collection<TvmType>,
    ): Boolean = TODO()

    override fun isFinal(type: TvmType): Boolean = type in allFinalTypes

    override fun isInstantiable(type: TvmType): Boolean = isFinal(type)

    override fun isSupertype(
        supertype: TvmType,
        type: TvmType,
    ): Boolean =
        foldOnTvmType(type, false) { acc, curType ->
            acc || curType == supertype
        }

    private val topTypeStream by lazy { USingleTypeStream(this, TvmAnyType) }

    override fun topTypeStream(): UTypeStream<TvmType> = topTypeStream

    companion object {
        val allFinalTypes =
            setOf(
                TvmNullType,
                TvmIntegerType,
                TvmTupleType,
                TvmContinuationType,
                TvmSliceType,
                TvmBuilderType,
                TvmDataCellType,
                TvmDictCellType,
            )
    }
}
