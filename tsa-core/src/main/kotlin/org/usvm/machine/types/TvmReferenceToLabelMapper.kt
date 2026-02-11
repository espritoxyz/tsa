package org.usvm.machine.types

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.ton.TvmParameterInfo
import org.ton.compositeLabelOfUnknown
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.api.writeField
import org.usvm.isAllocated
import org.usvm.isStatic
import org.usvm.isTrue
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.readCellRef
import org.usvm.machine.types.dp.CalculatedTlbLabelInfo
import org.usvm.machine.types.memory.UnknownBlockField
import org.usvm.machine.types.memory.UnknownBlockLengthField
import org.usvm.mkSizeExpr
import kotlin.math.max

class TvmReferenceToLabelMapper private constructor(
    private val ctx: TvmContext,
    val calculatedTlbLabelInfo: CalculatedTlbLabelInfo,
    private val initialRefs: Set<UConcreteHeapRef>,
    private var inputAddressToLabels: PersistentMap<UConcreteHeapAddress, LabelInfo> = persistentMapOf(),
    private var grandchildrenOfRefInitialized: PersistentSet<UConcreteHeapAddress> = persistentSetOf(),
    private var proactiveConstraintsCalculatedFor: PersistentSet<UConcreteHeapAddress> = persistentSetOf(),
    private var builderLabels: PersistentMap<UConcreteHeapAddress, TlbStructureBuilder> = persistentMapOf(),
    private var allocatedAddressToCellInfo: PersistentMap<UConcreteHeapAddress, TvmParameterInfo.CellInfo> =
        persistentMapOf(),
    private var addressSlices: PersistentSet<UConcreteHeapAddress> = persistentSetOf(),
) {
    fun clone(): TvmReferenceToLabelMapper =
        TvmReferenceToLabelMapper(
            ctx,
            calculatedTlbLabelInfo,
            initialRefs,
            inputAddressToLabels,
            grandchildrenOfRefInitialized,
            proactiveConstraintsCalculatedFor,
            builderLabels,
            allocatedAddressToCellInfo,
            addressSlices,
        )

    fun proactiveStructuralConstraintsWereCalculated(ref: UConcreteHeapRef): Boolean =
        ref.address in proactiveConstraintsCalculatedFor

    fun addressWasGiven(ref: UConcreteHeapRef) = ref.address in inputAddressToLabels

    fun getLabelInfo(ref: UConcreteHeapRef) =
        if (ref.isAllocated) {
            val cellInfo = allocatedAddressToCellInfo[ref.address]
            cellInfo?.let { LabelInfo(mapOf(cellInfo to ctx.trueExpr)) }
        } else {
            inputAddressToLabels[ref.address]
        }

    private fun getChildrenAddresses(
        state: TvmState,
        ref: UConcreteHeapRef,
    ): List<UConcreteHeapRef> {
        val parentStruct = inputAddressToLabels[ref.address]
        check(parentStruct != null) {
            "Must call getChildrenAddresses only for refs that TvmAddressToLabelMapper already knows about, but got $ref"
        }
        val maxRefs =
            parentStruct.variants.keys.fold(0) { acc, cellInfo ->
                if (cellInfo is TvmParameterInfo.DataCellInfo) {
                    max(acc, calculatedTlbLabelInfo.maxRefSize(cellInfo.dataCellStructure) ?: 0)
                } else {
                    acc
                }
            }

        return List(maxRefs) {
            // resulting address is concrete because ref is concrete
            state.readCellRef(ref, state.ctx.mkSizeExpr(it), initConstraintsForChildren = false) as UConcreteHeapRef
        }
    }

    private fun generateLabelInfoForChildren(
        state: TvmState,
        ref: UConcreteHeapRef,
    ) = with(state.ctx) {
        check(ref.isStatic) {
            "Unexpected ref: $ref"
        }

        val parentStruct =
            inputAddressToLabels[ref.address]
                ?: error("CellInfo of ref $ref must be known at this point")

        check(ref.address !in grandchildrenOfRefInitialized) {
            "Grandchildren must not be known yet"
        }

        val childAddresses = getChildrenAddresses(state, ref)
        check(childAddresses.all { it.address !in inputAddressToLabels }) {
            "Structure of address must be set only once"
        }

        val childLabels = childAddresses.map { hashMapOf<TvmParameterInfo.CellInfo, UBoolExpr>() }

        parentStruct.variants.forEach { (cellInfo, guard) ->
            if (cellInfo !is TvmParameterInfo.DataCellInfo) {
                return@forEach
            }

            for (childIdx in childLabels.indices) {
                val childInfo =
                    calculatedTlbLabelInfo.getLabelChildStructure(
                        state,
                        ref,
                        cellInfo.dataCellStructure,
                        childIdx,
                    ) ?: run {
                        check(calculatedTlbLabelInfo.labelHasUnknownLeaves(cellInfo.dataCellStructure) == true) {
                            "ChildStructure can be null only for children of labels with unknown leaves, " +
                                "but it is null for ref $childIdx of ${cellInfo.dataCellStructure}"
                        }
                        // TODO: add info about known children of parents with unknown leaves
                        mapOf(TvmParameterInfo.UnknownCellInfo to trueExpr)
                    }

                childInfo.forEach { (childCellInfo, childGuard) ->
                    val oldChildGuard = childLabels[childIdx][childCellInfo] ?: falseExpr
                    childLabels[childIdx][childCellInfo] = oldChildGuard or (guard and childGuard)
                }
            }
        }

        (childAddresses zip childLabels).forEach { (childAddress, labelInfo) ->
            inputAddressToLabels = inputAddressToLabels.put(childAddress.address, LabelInfo(labelInfo))
        }
    }

    fun getLabelFromModel(
        model: TvmModel,
        ref: UConcreteHeapRef,
    ): TvmParameterInfo.CellInfo {
        check(ref.isStatic) {
            "Unexpected ref: $ref"
        }

        val labelInfo = inputAddressToLabels[ref.address]

        check(labelInfo != null) {
            "Must call this method only for refs which structures are known. No info about $ref"
        }

        return labelInfo.variants.entries
            .firstOrNull { (_, guard) ->
                model.eval(guard).isTrue
            }?.key
            ?: error("One of the guards in LabelInfo for $ref must be true in the given model.")
    }

    fun initializeConstraintsForChildren(
        state: TvmState,
        ref: UConcreteHeapRef,
    ) = with(state.ctx) {
        check(!state.isTerminated) {
            "initializeAddressChildren should be called only when the state is not terminated, but " +
                "given state's result is ${state.result}"
        }

        if (ref.address !in inputAddressToLabels || ref.address in grandchildrenOfRefInitialized) {
            return
        }

        grandchildrenOfRefInitialized = grandchildrenOfRefInitialized.add(ref.address)

        val childAddresses = getChildrenAddresses(state, ref)
        check(childAddresses.all { it.address in inputAddressToLabels }) {
            "Children's cellInfo must be known at this point"
        }

        val structuralConstraints =
            childAddresses.fold(trueExpr as UBoolExpr) { acc, childAddress ->
                // generate grandchildren of [ref]
                generateLabelInfoForChildren(state, childAddress)

                acc and generateProactiveStructuralConstraints(state, childAddress).also {
                    println(it)
                }
            }

        state.structuralConstraintsHolder = state.structuralConstraintsHolder.add(structuralConstraints)
    }

    private fun generateProactiveStructuralConstraints(
        state: TvmState,
        ref: UConcreteHeapRef,
    ): UBoolExpr {
        check(ref.address !in proactiveConstraintsCalculatedFor) {
            "Proactive structural constraints must be calculated for each address only once"
        }

        proactiveConstraintsCalculatedFor = proactiveConstraintsCalculatedFor.add(ref.address)

        return generateStructuralConstraints(
            state,
            ref,
            generateSizeConstraints = true,
            generateDataConstraints = false,
            generateTlbFieldConstraints = true,
            fillUnknownBlockField = true,
        )
    }

    fun generateLazyDataConstraints(
        state: TvmState,
        ref: UConcreteHeapRef,
    ): UBoolExpr =
        generateStructuralConstraints(
            state,
            ref,
            generateSizeConstraints = false,
            generateDataConstraints = true,
            generateTlbFieldConstraints = false,
        )

    private fun generateStructuralConstraints(
        state: TvmState,
        ref: UConcreteHeapRef,
        generateSizeConstraints: Boolean,
        generateDataConstraints: Boolean,
        generateTlbFieldConstraints: Boolean,
        fillUnknownBlockField: Boolean = false,
    ): UBoolExpr =
        with(state.ctx) {
            check(ref.isStatic) {
                "Unexpected ref: $ref"
            }

            val labelInfo = inputAddressToLabels[ref.address]
            check(labelInfo != null) {
                "At this point label info for $ref must be calculated"
            }

            labelInfo.variants.entries.fold(trueExpr as UBoolExpr) { acc, (cellInfo, guard) ->
                val label =
                    when (cellInfo) {
                        is TvmParameterInfo.DataCellInfo -> cellInfo.dataCellStructure
                        is TvmParameterInfo.DictCellInfo -> return@fold acc
                        TvmParameterInfo.UnknownCellInfo -> compositeLabelOfUnknown
                    }

                if (label.internalStructure is TlbStructure.Unknown && fillUnknownBlockField && guard.isTrue) {
                    val blockField = UnknownBlockField(TlbStructure.Unknown.id, persistentListOf())
                    val cellData = state.fieldManagers.cellDataFieldManager.readCellDataWithoutAsserts(state, ref)
                    state.memory.writeField(ref, blockField, blockField.getSort(this), cellData, guard = trueExpr)

                    val sizeField = UnknownBlockLengthField(persistentListOf())
                    val cellDataLength = state.fieldManagers.cellDataLengthFieldManager.readCellDataLength(state, ref)
                    val sort = sizeField.getSort(this)
                    state.memory.writeField(ref, sizeField, sort, cellDataLength, guard = trueExpr)
                }

                var curGuard = trueExpr as UBoolExpr
                if (generateSizeConstraints) {
                    val sizeConstraints = calculatedTlbLabelInfo.getSizeConstraints(state, ref, label)
                    curGuard = curGuard and (sizeConstraints ?: trueExpr)
                }
                if (generateDataConstraints) {
                    val dataConstraints = calculatedTlbLabelInfo.getDataConstraints(state, ref, label)
                    curGuard = curGuard and dataConstraints
                }
                if (generateTlbFieldConstraints) {
                    val tlbConstraints = calculatedTlbLabelInfo.getTlbFieldConstraints(state, ref, label)
                    curGuard = curGuard and tlbConstraints
                }

                acc and (guard implies curGuard)
            }
        }

    fun getInitialStructuralConstraints(initialState: TvmState): UBoolExpr =
        initialRefs.fold(initialState.ctx.trueExpr as UBoolExpr) { acc, ref ->
            val constraint = generateProactiveStructuralConstraints(initialState, ref)
            initialState.ctx.mkAnd(acc, constraint)
        }

    fun addTlbBuilder(
        builderRef: UConcreteHeapRef,
        tlbStructureBuilder: TlbStructureBuilder,
    ) {
        builderLabels = builderLabels.put(builderRef.address, tlbStructureBuilder)
    }

    fun getTlbBuilder(builderRef: UConcreteHeapRef): TlbStructureBuilder? = builderLabels[builderRef.address]

    fun setCellInfoFromBuilder(
        builder: UConcreteHeapRef,
        cellRef: UConcreteHeapRef,
        state: TvmState,
    ) {
        check(cellRef.isAllocated) {
            "Unexpected ref: $cellRef"
        }
        val tlbBuilder =
            builderLabels[builder.address]
                ?: return
        val newLabel = TlbCompositeLabel("artificial")
        newLabel.internalStructure = tlbBuilder.end(newLabel, state, cellRef)
        val cellInfo = TvmParameterInfo.DataCellInfo(newLabel)
        setAllocatedCellInfo(cellRef, cellInfo)
    }

    private fun setAllocatedCellInfo(
        cellRef: UConcreteHeapRef,
        cellInfo: TvmParameterInfo.CellInfo,
    ) {
        allocatedAddressToCellInfo = allocatedAddressToCellInfo.put(cellRef.address, cellInfo)
    }

    fun addAddressSlice(slice: UConcreteHeapRef) {
        addressSlices = addressSlices.add(slice.address)
    }

    fun sliceIsAddress(slice: UConcreteHeapRef): Boolean = slice.address in addressSlices

    fun addLabel(
        scope: TvmStepScopeManager,
        cellRef: UConcreteHeapRef,
        label: TlbCompositeLabel,
    ): Unit? {
        val cellInfo = TvmParameterInfo.DataCellInfo(label)
        inputAddressToLabels =
            inputAddressToLabels.put(cellRef.address, LabelInfo(mapOf(cellInfo to ctx.trueExpr)))

        val constraint =
            scope.calcOnState {
                generateProactiveStructuralConstraints(this, cellRef)
            }

        scope.assert(constraint)
            ?: return null

        scope.calcOnState {
            generateLabelInfoForChildren(this, cellRef)
        }

        return Unit
    }

    companion object {
        fun build(
            state: TvmState,
            inputInfo: InputParametersStructure,
            calculatedTlbLabelInfo: CalculatedTlbLabelInfo,
        ): TvmReferenceToLabelMapper {
            val initialRefs = hashSetOf<UConcreteHeapRef>()
            inputInfo.cellToInfo.forEach { (ref, _) ->
                initialRefs.add(ref)
            }

            val result = TvmReferenceToLabelMapper(state.ctx, calculatedTlbLabelInfo, initialRefs)

            inputInfo.cellToInfo.forEach { (ref, cellInfo) ->
                result.inputAddressToLabels =
                    result.inputAddressToLabels.put(ref.address, LabelInfo(mapOf(cellInfo to state.ctx.trueExpr)))
                result.generateLabelInfoForChildren(state, ref)
            }

            val emptyBuilder = state.emptyRefValue.emptyBuilder
            result.addTlbBuilder(emptyBuilder, TlbStructureBuilder.empty)

            return result
        }
    }
}

@JvmInline
value class LabelInfo(
    val variants: Map<TvmParameterInfo.CellInfo, UBoolExpr>,
)
