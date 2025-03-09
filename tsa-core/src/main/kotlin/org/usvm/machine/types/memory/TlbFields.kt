package org.usvm.machine.types.memory

import org.ton.bytecode.TvmField
import org.usvm.USort
import org.usvm.machine.TvmContext
import org.usvm.util.log2

interface TlbField : TvmField {
    override val name: String
        get() = toString()

    val structureId: Int
    val pathToStructure: List<Int>

    context(TvmContext)
    fun getSort(): USort
}

data class ConcreteSizeBlockField(
    val bitSize: Int,
    override val structureId: Int,
    override val pathToStructure: List<Int>,
) : TlbField {
    context(TvmContext)
    override fun getSort() = mkBvSort(bitSize.toUInt())
}

data class SliceRefField(
    override val structureId: Int,
    override val pathToStructure: List<Int>,
) : TlbField {
    context(TvmContext)
    override fun getSort() = addressSort
}

data class SymbolicSizeBlockField(
    val maxBitSize: Int,
    override val structureId: Int,
    override val pathToStructure: List<Int>,
) : TlbField {
    context(TvmContext)
    override fun getSort() = mkBvSort(maxBitSize.toUInt())

    init {
        check(maxBitSize >= 32)
    }
}

data class SwitchField(
    override val structureId: Int,
    override val pathToStructure: List<Int>,
    val possibleContinuations: List<Int>,
) : TlbField {
    // calculate minimum number of bits needed for storing [possibleContinuations.size] values
    private val bitSize: UInt = log2(possibleContinuations.size.toUInt() * 2u - 1u)

    context(TvmContext)
    override fun getSort() = mkBvSort(bitSize)
}

data class UnknownBlockField(
    override val structureId: Int,
    override val pathToStructure: List<Int>
) : TlbField {
    context(TvmContext)
    override fun getSort() = cellDataSort
}
