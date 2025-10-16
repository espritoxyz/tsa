package org.usvm.machine.fields

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import org.ton.bytecode.TvmField
import org.ton.bytecode.TvmFieldImpl
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmState
import org.usvm.machine.types.TlbInferenceManager
import org.usvm.machine.types.TvmAddressToLabelMapper
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmType
import org.usvm.memory.UWritableMemory
import org.usvm.utils.flattenReferenceIte

class TvmCellDataFieldManager(
    private val ctx: TvmContext,
    private var refsWithRequestedCellDataField: PersistentSet<UConcreteHeapAddress> = persistentHashSetOf(),
    private var refsWithAssertedCellData: PersistentSet<UConcreteHeapAddress> = persistentHashSetOf(),
    val inferenceManager: TlbInferenceManager = TlbInferenceManager(),
) {
    fun clone(): TvmCellDataFieldManager =
        TvmCellDataFieldManager(
            ctx,
            refsWithRequestedCellDataField,
            refsWithAssertedCellData,
            inferenceManager = inferenceManager.clone(),
        ).also {
            it.addressToLabelMapper = addressToLabelMapper
        }

    lateinit var addressToLabelMapper: TvmAddressToLabelMapper

    fun writeCellData(
        state: TvmState,
        cellRef: UHeapRef,
        value: UExpr<TvmContext.TvmCellDataSort>,
    ) = writeCellData(state.memory, cellRef, value)

    fun writeCellData(
        memory: UWritableMemory<TvmType>,
        cellRef: UHeapRef,
        value: UExpr<TvmContext.TvmCellDataSort>,
    ) = with(ctx) {
        memory.writeField(cellRef, cellDataField, cellDataSort, value, guard = trueExpr)
    }

    fun readCellDataForBuilderOrAllocatedCell(
        state: TvmState,
        cellRef: UConcreteHeapRef,
    ): UExpr<TvmContext.TvmCellDataSort> =
        with(ctx) {
            if (::addressToLabelMapper.isInitialized) {
                val hasStructuralConstraints =
                    addressToLabelMapper.proactiveStructuralConstraintsWereCalculated(
                        cellRef,
                    )
                check(!hasStructuralConstraints) {
                    "readCellDataForAllocatedCell cannot be used for cells with structural constraints"
                }
            }

            state.memory.readField(cellRef, cellDataField, cellDataSort)
        }

    private fun TvmContext.generatedDataConstraint(
        scope: TvmStepScopeManager,
        refs: List<UConcreteHeapRef>,
    ): UBoolExpr =
        scope.calcOnState {
            refs.fold(trueExpr as UBoolExpr) { acc, ref ->
                if (addressToLabelMapper.proactiveStructuralConstraintsWereCalculated(ref)) {
                    val curDataConstraint = addressToLabelMapper.generateLazyDataConstraints(this, ref)
                    acc and curDataConstraint
                } else {
                    // case when ref doesn't have TL-B
                    acc
                }
            }
        }

    fun readCellData(
        scope: TvmStepScopeManager,
        cellRef: UHeapRef,
    ): UExpr<TvmContext.TvmCellDataSort>? =
        with(ctx) {
            val staticRefs = flattenReferenceIte(cellRef, extractAllocated = false, extractStatic = true)

            val newRefs = staticRefs.map { it.second }.filter { it.address !in refsWithAssertedCellData }
            refsWithAssertedCellData = refsWithAssertedCellData.addAll(newRefs.map { it.address })
            refsWithRequestedCellDataField = refsWithRequestedCellDataField.addAll(newRefs.map { it.address })
            newRefs.forEach {
                inferenceManager.fixateRef(it)
            }

            val dataConstraint = generatedDataConstraint(scope, newRefs)
            scope.assert(dataConstraint)
                ?: return@with null

            scope.calcOnState {
                memory.readField(cellRef, cellDataField, cellDataSort)
            }
        }

    /**
     * This function should be used with caution.
     * [cellDataField] might be invalid (without asserted structural constraints).
     * */
    fun readCellDataWithoutAsserts(
        state: TvmState,
        cellRef: UHeapRef,
    ) = with(ctx) {
        val staticRefs = flattenReferenceIte(cellRef, extractAllocated = false, extractStatic = true)
        refsWithRequestedCellDataField =
            refsWithRequestedCellDataField.addAll(staticRefs.map { it.second.address })

        state.memory.readField(cellRef, cellDataField, cellDataSort)
    }

    fun getCellsWithRequestedCellDataField(): Set<UConcreteHeapAddress> = refsWithRequestedCellDataField

    fun getCellsWithAssertedCellData(): Set<UConcreteHeapAddress> = refsWithAssertedCellData

    companion object {
        private val cellDataField: TvmField = TvmFieldImpl(TvmCellType, "data")
    }
}
