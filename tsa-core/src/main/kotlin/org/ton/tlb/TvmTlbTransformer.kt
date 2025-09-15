package org.ton.tlb

import org.ton.Endian.BigEndian
import org.ton.TlbBasicMsgAddrLabel
import org.ton.TlbCoinsLabel
import org.ton.TlbCompositeLabel
import org.ton.TlbIntegerLabelOfConcreteSize
import org.ton.TlbLabel
import org.ton.TlbMaybeRefLabel
import org.ton.TlbStructure
import org.ton.TlbStructure.Empty
import org.ton.TlbStructure.KnownTypePrefix
import org.ton.TlbStructure.LoadRef
import org.ton.TlbStructure.SwitchPrefix
import org.ton.TlbStructure.Unknown
import org.ton.TlbStructureIdProvider
import org.ton.TvmParameterInfo
import org.ton.TvmParameterInfo.DataCellInfo

class TvmTlbTransformer(
    definitions: List<TvmTlbTypeDefinition>,
) {
    private val typeDefinitions = definitions.associateBy { it.id }
    private val transformed = hashMapOf<Pair<TvmTlbTypeDefinition, List<TvmTlbTypeExpr>>, TlbLabel>()
    private val cellTypeId = definitions.first { it.name == "Cell" }.id
    private val anyTypeId = definitions.first { it.name == "Any" }.id

    private fun getTypeDef(id: Int): TvmTlbTypeDefinition =
        typeDefinitions[id]
            ?: error("Unknown type id: $id")

    fun transformTypeDefinition(
        def: TvmTlbTypeDefinition,
        args: List<TvmTlbTypeExpr> = emptyList(),
    ): TlbLabel? {
        return transformed.getOrPut(def to args) {
            if (def.isBuiltin) {
                transformBuiltins(def, args) ?: return null
            } else {
                transformComplexType(def, args)
            }
        }
    }

    private fun sequenceOfExprsHasAny(sequence: Iterable<TvmTlbTypeExpr>): Boolean =
        sequence.any {
            it is TvmTlbType && it.id in listOf(cellTypeId, anyTypeId)
        }

    private fun transformBuiltins(
        def: TvmTlbTypeDefinition,
        args: List<TvmTlbTypeExpr>,
    ): TlbLabel? {
        val name = def.name

        // TODO check `isSigned` and `endian` fields
        return when {
            name == "#" -> TlbIntegerLabelOfConcreteSize(32, isSigned = false, endian = BigEndian)
            name == "##" -> TlbIntegerLabelOfConcreteSize(args.toIntConst(), isSigned = true, endian = BigEndian)
            name == "bits" -> TODO()
            name == "uint" -> TlbIntegerLabelOfConcreteSize(args.toIntConst(), isSigned = false, endian = BigEndian)
            name == "int" -> TlbIntegerLabelOfConcreteSize(args.toIntConst(), isSigned = true, endian = BigEndian)
            name.startsWith("bits") -> TODO()
            name.startsWith("int") -> {
                val bits = name.removePrefix("int").toInt()
                TlbIntegerLabelOfConcreteSize(bits, isSigned = true, endian = BigEndian)
            }
            name.startsWith("uint") -> {
                val bits = name.removePrefix("uint").toInt()
                TlbIntegerLabelOfConcreteSize(bits, isSigned = false, endian = BigEndian)
            }
            name == "Cell" || name == "Any" -> null
            else -> TODO()
        }
    }

    private fun transformComplexType(
        def: TvmTlbTypeDefinition,
        args: List<TvmTlbTypeExpr>,
    ): TlbLabel {
        // special cases
        when (def.name) {
            "Maybe" -> {
                require(args.size == 1)
                when (val arg = args.single()) {
                    is TvmTlbReference -> {
                        val label = TlbCompositeLabel("<anonymous-label>")
                        val (internal, hasAny) = transformSequenceOfExprs(listOf(arg.ref), label)
                        label.internalStructure = internal
                        label.definitelyHasAny = hasAny
                        return TlbMaybeRefLabel(DataCellInfo(label))
                    }
                    else -> {
                        val label = TlbCompositeLabel("Maybe")
                        val (internal, hasAny) = transformSequenceOfExprs(listOf(arg), label)
                        val structure =
                            SwitchPrefix(
                                id = TlbStructureIdProvider.provideId(),
                                switchSize = 1,
                                mapOf(
                                    "0" to Empty,
                                    "1" to internal,
                                ),
                                owner = label,
                            )
                        label.internalStructure = structure
                        label.definitelyHasAny = hasAny
                        return label
                    }
                }
            }
            "Either" -> {
                require(args.size == 2)
                val label = TlbCompositeLabel("Either")
                val (left, leftHasAny) = transformSequenceOfExprs(listOf(args[0]), label)
                val (right, rightHasAny) = transformSequenceOfExprs(listOf(args[1]), label)
                val structure =
                    SwitchPrefix(
                        id = TlbStructureIdProvider.provideId(),
                        switchSize = 1,
                        mapOf(
                            "0" to left,
                            "1" to right,
                        ),
                        owner = label,
                    )
                label.internalStructure = structure
                label.definitelyHasAny = leftHasAny || rightHasAny
                return label
            }
            "MsgAddress" -> {
                return TlbBasicMsgAddrLabel
            }
            "Grams", "Coins" -> { // TODO: add variant for `VarUInteger`
                return TlbCoinsLabel
            }
        }

        if (args.isNotEmpty()) {
            TODO()
        }

        val someConstructorHasAny =
            def.constructors.any { c ->
                val typeExprs = c.fields.map { it.typeExpr }
                sequenceOfExprsHasAny(typeExprs)
            }
        if (someConstructorHasAny) {
            // this one must not be recursive (if it is, it will lead to infinite recursion)
            val result = TlbCompositeLabel(def.name)
            val structure =
                transformConstructors(
                    def.constructors.map { ConstructorTagSuffix(it, it.tag) },
                    owner = result,
                )
            result.internalStructure = structure
            result.definitelyHasAny = true
            return result
        }

        return TlbCompositeLabel(
            name = def.name,
        ).also { label ->
            transformed[def to args] = label
            val structure =
                transformConstructors(
                    def.constructors.map { ConstructorTagSuffix(it, it.tag) },
                    owner = label,
                )
            label.internalStructure = structure
        }
    }

    private fun transformConstructors(
        constructors: List<ConstructorTagSuffix>,
        owner: TlbCompositeLabel,
    ): TlbStructure {
        if (constructors.size == 1 && constructors.single().tagSuffix.isEmpty()) {
            return transformConstructor(constructors.single().constructor, owner)
        }

        val minLen = constructors.minOf { it.tagSuffix.length }
        val groupedConstructors = hashMapOf<String, MutableList<ConstructorTagSuffix>>()

        constructors.forEach { constructor ->
            val prefix = constructor.tagSuffix.substring(0 until minLen)

            constructor.tagSuffix = constructor.tagSuffix.substring(minLen)
            groupedConstructors.getOrPut(prefix) { mutableListOf() }.add(constructor)
        }

        val variants = groupedConstructors.mapValues { transformConstructors(it.value, owner) }

        return SwitchPrefix(
            id = TlbStructureIdProvider.provideId(),
            minLen,
            variants,
            owner,
        )
    }

    private fun transformSequenceOfExprs(
        sequence: List<TvmTlbTypeExpr>,
        owner: TlbCompositeLabel,
    ): Pair<TlbStructure, Boolean> {
        var last: TlbStructure = Empty
        var foundAny = false
        sequence.asReversed().forEach { expr ->
            last =
                when (expr) {
                    is TvmTlbReference -> {
                        val ref = expr.ref
                        require(ref is TvmTlbType) {
                            "Unexpected reference: $ref"
                        }

                        val dataInfo =
                            transformTypeDefinition(getTypeDef(ref.id), ref.args)?.let {
                                (it as? TlbCompositeLabel)?.let { casted -> DataCellInfo(casted) }
                                    ?: error("Unexpected atomic label in ref: $it")
                            } ?: TvmParameterInfo.UnknownCellInfo

                        LoadRef(TlbStructureIdProvider.provideId(), dataInfo, last, owner)
                    }

                    is TvmTlbType -> {
                        val typeDef = getTypeDef(expr.id)

                        transformTypeDefinition(typeDef, expr.args)?.let { label ->
                            // unfold last label, if it has `Any`
                            if (last is Empty && label is TlbCompositeLabel && label.definitelyHasAny) {
                                foundAny = true
                                label.internalStructure
                            } else {
                                check(label.arity == 0)
                                KnownTypePrefix(
                                    TlbStructureIdProvider.provideId(),
                                    label,
                                    typeArgIds = emptyList(),
                                    last,
                                    owner,
                                )
                            }
                        } ?: let {
                            foundAny = true
                            Unknown
                        }
                    }

                    else -> TODO()
                }
        }

        return last to foundAny
    }

    private fun transformConstructor(
        constructor: TvmTlbTypeConstructor,
        owner: TlbCompositeLabel,
    ): TlbStructure {
        val exprs = constructor.fields.map { it.typeExpr }
        return transformSequenceOfExprs(exprs, owner).first
    }

    private data class ConstructorTagSuffix(
        val constructor: TvmTlbTypeConstructor,
        var tagSuffix: String,
    )

    private fun List<TvmTlbTypeExpr>.toIntConst(): Int =
        (singleOrNull() as? TvmTlbIntConst)?.value ?: error("Unexpected args: $this")
}
