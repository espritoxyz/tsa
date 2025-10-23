package org.ton

sealed interface TlbStructure {
    sealed interface Owner

    data object UnknownStructOwner : Owner

    data class CompositeLabelOwner(
        val owner: TlbCompositeLabel,
    ) : Owner

    data object Unknown : Leaf {
        override val id: Int = 0
    }

    data object Empty : Leaf {
        override val id: Int = 1
    }

    class KnownTypePrefix(
        override val id: Int,
        val typeLabel: TlbLabel,
        val typeArgIds: List<Int>,
        val rest: TlbStructure,
        override val owner: Owner,
    ) : TlbStructure,
        CompositeNode {
        override fun equals(other: Any?): Boolean = performEquals(other)

        override fun hashCode(): Int = calculateHash()

        constructor(id: Int, typeLabel: TlbLabel, typeArgIds: List<Int>, rest: TlbStructure, owner: TlbCompositeLabel) :
            this(id, typeLabel, typeArgIds, rest, CompositeLabelOwner(owner))
    }

    class LoadRef(
        override val id: Int,
        val ref: TvmParameterInfo.CellInfo,
        val rest: TlbStructure,
        override val owner: Owner,
    ) : TlbStructure,
        CompositeNode {
        override fun equals(other: Any?): Boolean = performEquals(other)

        override fun hashCode(): Int = calculateHash()

        constructor(id: Int, ref: TvmParameterInfo.CellInfo, rest: TlbStructure, owner: TlbCompositeLabel) :
            this(id, ref, rest, CompositeLabelOwner(owner))
    }

    class SwitchPrefix(
        override val id: Int,
        val switchSize: Int,
        givenVariants: Map<String, TlbStructure>,
        override val owner: Owner,
    ) : TlbStructure,
        CompositeNode {
        override fun equals(other: Any?): Boolean = performEquals(other)

        override fun hashCode(): Int = calculateHash()

        val variants: List<SwitchVariant> = givenVariants.entries.map { SwitchVariant(it.key, it.value) }

        init {
            require(switchSize > 0) {
                "switchSize in SwitchPrefix must be > 0, but got $switchSize"
            }
            variants.forEach {
                require(it.key.length == switchSize) {
                    "Switch keys' lengths must be $switchSize, found key: $it"
                }
            }
        }

        constructor(id: Int, switchSize: Int, givenVariants: Map<String, TlbStructure>, owner: TlbCompositeLabel) :
            this(id, switchSize, givenVariants, CompositeLabelOwner(owner))

        data class SwitchVariant(
            val key: String,
            val struct: TlbStructure,
        )
    }

    sealed interface Leaf : TlbStructure

    sealed interface CompositeNode : TlbStructure {
        val owner: Owner

        fun performEquals(other: Any?): Boolean {
            if (other !is CompositeNode) {
                return false
            }
            return id == other.id
        }

        fun calculateHash() = id.hashCode()
    }

    val id: Int
}

object TlbStructureIdProvider {
    private var nextId = 2

    fun provideId(): Int = nextId++
}
