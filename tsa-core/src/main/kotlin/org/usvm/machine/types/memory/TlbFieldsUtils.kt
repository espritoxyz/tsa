package org.usvm.machine.types.memory

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.ton.FixedSizeDataLabel
import org.ton.TlbAddressByRef
import org.ton.TlbBitArrayByRef
import org.ton.TlbCompositeLabel
import org.ton.TlbIntegerLabelOfConcreteSize
import org.ton.TlbIntegerLabelOfSymbolicSize
import org.ton.TlbStructure
import org.ton.TlbStructure.KnownTypePrefix
import org.ton.TlbStructure.LoadRef
import org.ton.TlbStructure.SwitchPrefix
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.intValue
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.bvMaxValueSignedExtended
import org.usvm.machine.state.bvMinValueSignedExtended
import org.usvm.machine.state.loadIntFromCellWithoutChecksAndStructuralAsserts
import org.usvm.machine.state.preloadDataBitsFromCellWithoutStructuralAsserts
import org.usvm.machine.types.dp.AbstractionForUExprWithCellDataPrefix
import org.usvm.machine.types.memory.stack.TlbStack
import org.usvm.mkSizeExpr
import org.usvm.sizeSort
import org.usvm.test.resolver.TvmTestStateResolver

fun TvmContext.generateCellDataConstraint(
    struct: KnownTypePrefix,
    param: AbstractionForUExprWithCellDataPrefix
): UBoolExpr =
    when (struct.typeLabel) {
        is TlbCompositeLabel -> {
            trueExpr
        }

        is FixedSizeDataLabel -> {
            val (addr, prefixSize, path, state) = param
            val field = ConcreteSizeBlockField(struct.typeLabel.concreteSize, struct.id, path)
            val sort = field.getSort(this)
            val symbol = state.memory.readField(addr, field, sort)
            val data =
                state.preloadDataBitsFromCellWithoutStructuralAsserts(addr, prefixSize, struct.typeLabel.concreteSize)
            check(symbol.sort.sizeBits == data.sort.sizeBits)
            symbol eq data
        }

        is TlbIntegerLabelOfSymbolicSize -> {
            val (addr, prefixSize, path, state) = param

            val typeArgs = struct.typeArgs(state, addr, path)
            val intSize = struct.typeLabel.bitSize(state.ctx, typeArgs)

            val intFromData =
                state.loadIntFromCellWithoutChecksAndStructuralAsserts(
                    addr,
                    prefixSize,
                    intSize.zeroExtendToSort(int257sort),
                    struct.typeLabel.isSigned
                )

            val field = SymbolicSizeBlockField(struct.typeLabel.lengthUpperBound, struct.id, path)
            val sort = field.getSort(this)

            val intFromTlbField = state.memory.readField(addr, field, sort)
            val extendedIntFromTlbInt =
                if (struct.typeLabel.isSigned) {
                    intFromTlbField.signedExtendToInteger()
                } else {
                    intFromTlbField.unsignedExtendToInteger()
                }

            intFromData eq extendedIntFromTlbInt
        }

        is TlbBitArrayByRef, is TlbAddressByRef -> {
            error("Cannot generate cell data constraints for TlbBitArrayByRef and TlbAddressByRef")
        }
    }

private fun TlbCompositeLabel.getStructureById(id: Int): TlbStructure =
    getStructureById(id, internalStructure)
        ?: error("Id $id not found in structure of $this")

private fun getStructureById(
    id: Int,
    structure: TlbStructure
): TlbStructure? {
    if (structure is TlbStructure.CompositeNode && structure.id == id) {
        return structure
    }
    return when (structure) {
        is TlbStructure.Leaf -> null
        is SwitchPrefix -> structure.variants.firstNotNullOfOrNull { getStructureById(id, it.struct) }
        is KnownTypePrefix -> getStructureById(id, structure.rest)
        is LoadRef -> getStructureById(id, structure.rest)
    }
}

fun KnownTypePrefix.typeArgs(
    state: TvmState,
    address: UConcreteHeapRef,
    path: List<Int>
): List<UExpr<TvmSizeSort>> =
    with(state.ctx) {
        typeArgIds.map {
            val typeArgStruct = owner.getStructureById(it)
            check(typeArgStruct is KnownTypePrefix) {
                "Only KnownTypePrefix can be used as type argument, but found $typeArgStruct"
            }
            val label = typeArgStruct.typeLabel
            check(label is TlbIntegerLabelOfConcreteSize && !label.isSigned) {
                "Only unsigned integer of concrete size can be used as type argument, but found $label"
            }
            val bitSize = label.concreteSize
            check(bitSize <= 31) {
                "Only integers of size <= 31 can be used as type argument, but found $label of size $bitSize"
            }
            val field = ConcreteSizeBlockField(bitSize, it, path)
            val sort = field.getSort(this)
            val intValue = state.memory.readField(address, field, sort)
            intValue.zeroExtendToSort(sizeSort)
        }
    }

fun TvmContext.generateGuardForSwitch(
    switch: TlbStructure,
    variantId: Int,
    possibleVariants: List<SwitchPrefix.SwitchVariant>,
    state: TvmState,
    address: UHeapRef,
    path: List<Int>
): UBoolExpr {
    require(variantId in 0..possibleVariants.size)
    val field = SwitchField(switch.id, path, possibleVariants.map { it.struct.id })
    val sort = field.getSort(this)
    val value = state.memory.readField(address, field, sort)
    val variantIdExpr = mkBv(variantId, sort)
    return if (variantId == possibleVariants.size - 1) {
        mkBvUnsignedGreaterOrEqualExpr(value, variantIdExpr)
    } else {
        value eq variantIdExpr
    }
}

fun readInModelFromTlbFields(
    address: UHeapRef,
    resolver: TvmTestStateResolver,
    label: TlbCompositeLabel
): String {
    val state = resolver.state
    val model = resolver.model
    var stack = TlbStack.new(state.ctx, label)
    var result = ""
    val sizeSymbolic = state.fieldManagers.cellDataLengthFieldManager.readCellDataLength(state, address)
    val size = model.eval(sizeSymbolic).intValue()
    var readInfo = TlbStack.ConcreteReadInfo(model.eval(address) as UConcreteHeapRef, resolver, size)

    while (!stack.isEmpty) {
        val (readValue, leftToRead, newStack) = stack.readInModel(readInfo)
        result += readValue
        readInfo = leftToRead
        stack = newStack
    }

    return result
}

fun generateTlbFieldConstraints(
    state: TvmState,
    ref: UConcreteHeapRef,
    label: TlbCompositeLabel,
    possibleSwitchVariants: List<Map<SwitchPrefix, List<SwitchPrefix.SwitchVariant>>>,
    maxTlbDepth: Int
) = generateTlbFieldConstraints(
    state,
    ref,
    label.internalStructure,
    persistentListOf(),
    possibleSwitchVariants,
    maxTlbDepth
)

fun generateTlbFieldConstraints(
    state: TvmState,
    ref: UConcreteHeapRef,
    structure: TlbStructure,
    path: PersistentList<Int>,
    possibleSwitchVariants: List<Map<SwitchPrefix, List<SwitchPrefix.SwitchVariant>>>,
    maxTlbDepth: Int
): UBoolExpr =
    with(state.ctx) {
        when (structure) {
            is TlbStructure.Unknown, is TlbStructure.Empty -> {
                trueExpr
            }

            is LoadRef -> {
                generateTlbFieldConstraints(state, ref, structure.rest, path, possibleSwitchVariants, maxTlbDepth)
            }

            is SwitchPrefix -> {
                val possibleVariants =
                    possibleSwitchVariants[maxTlbDepth][structure]
                        ?: error("Possible variants for switch $structure not found")
                possibleVariants.fold(trueExpr as UBoolExpr) { acc, (_, variant) ->
                    acc and generateTlbFieldConstraints(state, ref, variant, path, possibleSwitchVariants, maxTlbDepth)
                }
            }

            is KnownTypePrefix -> {
                when (structure.typeLabel) {
                    is FixedSizeDataLabel -> {
                        generateTlbFieldConstraints(
                            state,
                            ref,
                            structure.rest,
                            path,
                            possibleSwitchVariants,
                            maxTlbDepth
                        )
                    }

                    is TlbCompositeLabel -> {
                        val internal =
                            if (maxTlbDepth > 0) {
                                generateTlbFieldConstraints(
                                    state,
                                    ref,
                                    structure.typeLabel.internalStructure,
                                    path.add(structure.id),
                                    possibleSwitchVariants,
                                    maxTlbDepth - 1
                                )
                            } else {
                                trueExpr
                            }
                        internal and
                            generateTlbFieldConstraints(
                                state,
                                ref,
                                structure.rest,
                                path,
                                possibleSwitchVariants,
                                maxTlbDepth
                            )
                    }

                    is TlbIntegerLabelOfSymbolicSize -> {
                        val typeArgs = structure.typeArgs(state, ref, path)
                        val bitSize = structure.typeLabel.bitSize(this, typeArgs)

                        val field = SymbolicSizeBlockField(structure.typeLabel.lengthUpperBound, structure.id, path)
                        val fieldValue = state.memory.readField(ref, field, field.getSort(this))
                        val bits = field.getSort(this).sizeBits

                        val oneValue = mkBv(1, bits)

                        val valueConstraint =
                            if (structure.typeLabel.isSigned) {
                                val sort = mkBvSort(bits)
                                val minValue = bvMinValueSignedExtended(bitSize.zeroExtendToSort(sort))
                                val maxValue = bvMaxValueSignedExtended(bitSize.zeroExtendToSort(sort))

                                mkBvSignedLessOrEqualExpr(fieldValue, maxValue) and
                                    mkBvSignedGreaterOrEqualExpr(fieldValue, minValue)
                            } else {
                                val shift = bitSize.zeroExtendToSort(mkBvSort(bits))
                                val maxValue = mkBvSubExpr(mkBvShiftLeftExpr(oneValue, shift), oneValue)

                                mkBvUnsignedLessOrEqualExpr(fieldValue, maxValue)
                            }

                        val cur =
                            mkBvUnsignedLessOrEqualExpr(bitSize, mkSizeExpr(structure.typeLabel.lengthUpperBound)) and
                                valueConstraint
                        cur and
                            generateTlbFieldConstraints(
                                state,
                                ref,
                                structure.rest,
                                path,
                                possibleSwitchVariants,
                                maxTlbDepth
                            )
                    }

                    is TlbBitArrayByRef, is TlbAddressByRef -> {
                        error("Cannot generate tlb field constraints for TlbBitArrayByRef and TlbAddressByRef")
                    }
                }
            }
        }
    }
