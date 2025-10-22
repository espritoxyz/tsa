package org.usvm.machine.types

import kotlinx.collections.immutable.persistentListOf
import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.ton.TvmParameterInfo
import org.ton.compositeLabelOfUnknown
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.api.writeField
import org.usvm.isAllocated
import org.usvm.isTrue
import org.usvm.machine.fields.TvmCellDataLengthFieldManager.Companion.UnknownBlockLengthField
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.readCellRef
import org.usvm.machine.types.dp.CalculatedTlbLabelInfo
import org.usvm.machine.types.memory.UnknownBlockField
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import kotlin.math.max

class TvmAddressToLabelMapper(
    state: TvmState,
    inputInfo: InputParametersStructure,
    val calculatedTlbLabelInfo: CalculatedTlbLabelInfo,
) {
    private val inputAddressToLabels = hashMapOf<UConcreteHeapRef, LabelInfo>()
    private val grandchildrenOfAddressInitialized = hashSetOf<UConcreteHeapRef>()
    private val initialRefs = hashSetOf<UConcreteHeapRef>() // will be set during init
    private val proactiveConstraintsCalculatedFor = hashSetOf<UConcreteHeapRef>()
    private val builderLabels = hashMapOf<UConcreteHeapRef, TlbStructureBuilder>()
    private val allocatedAddressToCellInfo = hashMapOf<UConcreteHeapRef, TvmParameterInfo.CellInfo>()
    private val addressSlices = hashSetOf<UConcreteHeapRef>()
    private val ctx = state.ctx

    fun proactiveStructuralConstraintsWereCalculated(ref: UConcreteHeapRef): Boolean =
        ref in proactiveConstraintsCalculatedFor

    fun addressWasGiven(ref: UConcreteHeapRef) = ref in inputAddressToLabels

    fun getLabelInfo(address: UConcreteHeapRef) =
        if (address.isAllocated) {
            val cellInfo = allocatedAddressToCellInfo[address]
            cellInfo?.let { LabelInfo(mapOf(cellInfo to ctx.trueExpr)) }
        } else {
            inputAddressToLabels[address]
        }

    private fun getChildrenAddresses(
        state: TvmState,
        ref: UConcreteHeapRef,
    ): List<UConcreteHeapRef> {
        val parentStruct = inputAddressToLabels[ref]
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
        val parentStruct =
            inputAddressToLabels[ref]
                ?: error("CellInfo of ref $ref must be known at this point")
        check(ref !in grandchildrenOfAddressInitialized) {
            "Grandchildren must not be known yet"
        }

        val childAddresses = getChildrenAddresses(state, ref)
        check(childAddresses.all { it !in inputAddressToLabels }) {
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
            inputAddressToLabels[childAddress] = LabelInfo(labelInfo)
        }
    }

    fun getLabelFromModel(
        model: UModelBase<TvmType>,
        ref: UConcreteHeapRef,
    ): TvmParameterInfo.CellInfo {
        val labelInfo = inputAddressToLabels[ref]
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
                "given state's result is ${state.methodResult}"
        }

        if (ref !in inputAddressToLabels || ref in grandchildrenOfAddressInitialized) {
            return
        }

        grandchildrenOfAddressInitialized.add(ref)

        val childAddresses = getChildrenAddresses(state, ref)
        check(childAddresses.all { it in inputAddressToLabels }) {
            "Children's cellInfo must be known at this point"
        }

        val structuralConstraints =
            childAddresses.fold(trueExpr as UBoolExpr) { acc, childAddress ->
                // generate grandchildren of [ref]
                generateLabelInfoForChildren(state, childAddress)

                acc and generateProactiveStructuralConstraints(state, childAddress)
            }

        state.structuralConstraintsHolder = state.structuralConstraintsHolder.add(structuralConstraints)
    }

    private fun generateProactiveStructuralConstraints(
        state: TvmState,
        ref: UConcreteHeapRef,
        checkThatConstraintWasCalculatedOnce: Boolean = true,
    ): UBoolExpr {
        if (checkThatConstraintWasCalculatedOnce) {
            check(ref !in proactiveConstraintsCalculatedFor) {
                "Proactive structural constraints must be calculated for each address only once"
            }
        }
        proactiveConstraintsCalculatedFor.add(ref)

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
            val labelInfo = inputAddressToLabels[ref]
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

                if (label.internalStructure is TlbStructure.Unknown && fillUnknownBlockField) {
                    val blockField = UnknownBlockField(TlbStructure.Unknown.id, persistentListOf())
                    val cellData = state.fieldManagers.cellDataFieldManager.readCellDataWithoutAsserts(state, ref)
                    state.memory.writeField(ref, blockField, blockField.getSort(this), cellData, guard = trueExpr)

                    val sizeField = UnknownBlockLengthField(persistentListOf())
                    val cellDataLength = state.fieldManagers.cellDataLengthFieldManager.readCellDataLength(state, ref)
                    val sort = sizeField.getSort(this)
                    state.memory.writeField(ref, sizeField, sort, cellDataLength.extractToSort(sort), guard = trueExpr)
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
        builderLabels[builderRef] = tlbStructureBuilder
    }

    fun getTlbBuilder(builderRef: UConcreteHeapRef): TlbStructureBuilder? = builderLabels[builderRef]

    fun setCellInfoFromBuilder(
        builder: UConcreteHeapRef,
        cellRef: UConcreteHeapRef,
        state: TvmState,
    ) {
        check(cellRef.isAllocated) {
            "Unexpected ref: $cellRef"
        }
        val tlbBuilder =
            builderLabels[builder]
                ?: return
        val newLabel = TlbCompositeLabel("artificial")
        newLabel.internalStructure = tlbBuilder.end(newLabel, state, cellRef)
        val cellInfo = TvmParameterInfo.DataCellInfo(newLabel)
        setAllocatedCellInfo(cellRef, cellInfo)
    }

    fun setAllocatedCellInfo(
        cellRef: UConcreteHeapRef,
        cellInfo: TvmParameterInfo.CellInfo,
    ) {
        allocatedAddressToCellInfo[cellRef] = cellInfo
    }

    fun addAddressSlice(slice: UConcreteHeapRef) = addressSlices.add(slice)

    fun sliceIsAddress(slice: UConcreteHeapRef): Boolean = slice in addressSlices

    init {
        inputInfo.cellToInfo.forEach { (ref, cellInfo) ->
            inputAddressToLabels[ref] = LabelInfo(mapOf(cellInfo to ctx.trueExpr))
            initialRefs.add(ref)
            generateLabelInfoForChildren(state, ref)
        }
        val emptyBuilder = state.emptyRefValue.emptyBuilder
        addTlbBuilder(emptyBuilder, TlbStructureBuilder.empty)
    }
}

@JvmInline
value class LabelInfo(
    val variants: Map<TvmParameterInfo.CellInfo, UBoolExpr>,
)
