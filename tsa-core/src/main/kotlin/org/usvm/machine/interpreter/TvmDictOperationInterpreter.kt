package org.usvm.machine.interpreter

import io.ksmt.expr.KBitVecValue
import io.ksmt.expr.KExpr
import io.ksmt.sort.KBvSort
import io.ksmt.utils.asExpr
import kotlinx.collections.immutable.persistentSetOf
import org.ton.bytecode.TvmDictDeleteDictdelInst
import org.ton.bytecode.TvmDictDeleteDictdelgetInst
import org.ton.bytecode.TvmDictDeleteDictdelgetrefInst
import org.ton.bytecode.TvmDictDeleteDictidelInst
import org.ton.bytecode.TvmDictDeleteDictidelgetInst
import org.ton.bytecode.TvmDictDeleteDictidelgetrefInst
import org.ton.bytecode.TvmDictDeleteDictudelInst
import org.ton.bytecode.TvmDictDeleteDictudelgetInst
import org.ton.bytecode.TvmDictDeleteDictudelgetrefInst
import org.ton.bytecode.TvmDictDeleteInst
import org.ton.bytecode.TvmDictGetDictgetInst
import org.ton.bytecode.TvmDictGetDictgetrefInst
import org.ton.bytecode.TvmDictGetDictigetInst
import org.ton.bytecode.TvmDictGetDictigetrefInst
import org.ton.bytecode.TvmDictGetDictugetInst
import org.ton.bytecode.TvmDictGetDictugetrefInst
import org.ton.bytecode.TvmDictGetInst
import org.ton.bytecode.TvmDictInst
import org.ton.bytecode.TvmDictMayberefDictgetoptrefInst
import org.ton.bytecode.TvmDictMayberefDictigetoptrefInst
import org.ton.bytecode.TvmDictMayberefDictugetoptrefInst
import org.ton.bytecode.TvmDictMayberefInst
import org.ton.bytecode.TvmDictMinDictimaxInst
import org.ton.bytecode.TvmDictMinDictimaxrefInst
import org.ton.bytecode.TvmDictMinDictiminInst
import org.ton.bytecode.TvmDictMinDictiminrefInst
import org.ton.bytecode.TvmDictMinDictiremmaxInst
import org.ton.bytecode.TvmDictMinDictiremmaxrefInst
import org.ton.bytecode.TvmDictMinDictiremminInst
import org.ton.bytecode.TvmDictMinDictiremminrefInst
import org.ton.bytecode.TvmDictMinDictmaxInst
import org.ton.bytecode.TvmDictMinDictmaxrefInst
import org.ton.bytecode.TvmDictMinDictminInst
import org.ton.bytecode.TvmDictMinDictminrefInst
import org.ton.bytecode.TvmDictMinDictremmaxInst
import org.ton.bytecode.TvmDictMinDictremmaxrefInst
import org.ton.bytecode.TvmDictMinDictremminInst
import org.ton.bytecode.TvmDictMinDictremminrefInst
import org.ton.bytecode.TvmDictMinDictumaxInst
import org.ton.bytecode.TvmDictMinDictumaxrefInst
import org.ton.bytecode.TvmDictMinDictuminInst
import org.ton.bytecode.TvmDictMinDictuminrefInst
import org.ton.bytecode.TvmDictMinDicturemmaxInst
import org.ton.bytecode.TvmDictMinDicturemmaxrefInst
import org.ton.bytecode.TvmDictMinDicturemminInst
import org.ton.bytecode.TvmDictMinDicturemminrefInst
import org.ton.bytecode.TvmDictMinInst
import org.ton.bytecode.TvmDictNextDictgetnextInst
import org.ton.bytecode.TvmDictNextDictgetnexteqInst
import org.ton.bytecode.TvmDictNextDictgetprevInst
import org.ton.bytecode.TvmDictNextDictgetpreveqInst
import org.ton.bytecode.TvmDictNextDictigetnextInst
import org.ton.bytecode.TvmDictNextDictigetnexteqInst
import org.ton.bytecode.TvmDictNextDictigetprevInst
import org.ton.bytecode.TvmDictNextDictigetpreveqInst
import org.ton.bytecode.TvmDictNextDictugetnextInst
import org.ton.bytecode.TvmDictNextDictugetnexteqInst
import org.ton.bytecode.TvmDictNextDictugetprevInst
import org.ton.bytecode.TvmDictNextDictugetpreveqInst
import org.ton.bytecode.TvmDictNextInst
import org.ton.bytecode.TvmDictPrefixInst
import org.ton.bytecode.TvmDictSerialInst
import org.ton.bytecode.TvmDictSerialLddictInst
import org.ton.bytecode.TvmDictSerialLddictqInst
import org.ton.bytecode.TvmDictSerialLddictsInst
import org.ton.bytecode.TvmDictSerialPlddictInst
import org.ton.bytecode.TvmDictSerialPlddictqInst
import org.ton.bytecode.TvmDictSerialPlddictsInst
import org.ton.bytecode.TvmDictSerialSkipdictInst
import org.ton.bytecode.TvmDictSerialStdictInst
import org.ton.bytecode.TvmDictSetBuilderDictaddbInst
import org.ton.bytecode.TvmDictSetBuilderDictaddgetbInst
import org.ton.bytecode.TvmDictSetBuilderDictiaddbInst
import org.ton.bytecode.TvmDictSetBuilderDictiaddgetbInst
import org.ton.bytecode.TvmDictSetBuilderDictireplacebInst
import org.ton.bytecode.TvmDictSetBuilderDictireplacegetbInst
import org.ton.bytecode.TvmDictSetBuilderDictisetbInst
import org.ton.bytecode.TvmDictSetBuilderDictisetgetbInst
import org.ton.bytecode.TvmDictSetBuilderDictreplacebInst
import org.ton.bytecode.TvmDictSetBuilderDictreplacegetbInst
import org.ton.bytecode.TvmDictSetBuilderDictsetbInst
import org.ton.bytecode.TvmDictSetBuilderDictsetgetbInst
import org.ton.bytecode.TvmDictSetBuilderDictuaddbInst
import org.ton.bytecode.TvmDictSetBuilderDictuaddgetbInst
import org.ton.bytecode.TvmDictSetBuilderDictureplacebInst
import org.ton.bytecode.TvmDictSetBuilderDictureplacegetbInst
import org.ton.bytecode.TvmDictSetBuilderDictusetbInst
import org.ton.bytecode.TvmDictSetBuilderDictusetgetbInst
import org.ton.bytecode.TvmDictSetBuilderInst
import org.ton.bytecode.TvmDictSetDictaddInst
import org.ton.bytecode.TvmDictSetDictaddgetInst
import org.ton.bytecode.TvmDictSetDictaddgetrefInst
import org.ton.bytecode.TvmDictSetDictaddrefInst
import org.ton.bytecode.TvmDictSetDictiaddInst
import org.ton.bytecode.TvmDictSetDictiaddgetInst
import org.ton.bytecode.TvmDictSetDictiaddgetrefInst
import org.ton.bytecode.TvmDictSetDictiaddrefInst
import org.ton.bytecode.TvmDictSetDictireplaceInst
import org.ton.bytecode.TvmDictSetDictireplacegetInst
import org.ton.bytecode.TvmDictSetDictireplacegetrefInst
import org.ton.bytecode.TvmDictSetDictireplacerefInst
import org.ton.bytecode.TvmDictSetDictisetInst
import org.ton.bytecode.TvmDictSetDictisetgetInst
import org.ton.bytecode.TvmDictSetDictisetgetrefInst
import org.ton.bytecode.TvmDictSetDictisetrefInst
import org.ton.bytecode.TvmDictSetDictreplaceInst
import org.ton.bytecode.TvmDictSetDictreplacegetInst
import org.ton.bytecode.TvmDictSetDictreplacegetrefInst
import org.ton.bytecode.TvmDictSetDictreplacerefInst
import org.ton.bytecode.TvmDictSetDictsetInst
import org.ton.bytecode.TvmDictSetDictsetgetInst
import org.ton.bytecode.TvmDictSetDictsetgetrefInst
import org.ton.bytecode.TvmDictSetDictsetrefInst
import org.ton.bytecode.TvmDictSetDictuaddInst
import org.ton.bytecode.TvmDictSetDictuaddgetInst
import org.ton.bytecode.TvmDictSetDictuaddgetrefInst
import org.ton.bytecode.TvmDictSetDictuaddrefInst
import org.ton.bytecode.TvmDictSetDictureplaceInst
import org.ton.bytecode.TvmDictSetDictureplacegetInst
import org.ton.bytecode.TvmDictSetDictureplacegetrefInst
import org.ton.bytecode.TvmDictSetDictureplacerefInst
import org.ton.bytecode.TvmDictSetDictusetInst
import org.ton.bytecode.TvmDictSetDictusetgetInst
import org.ton.bytecode.TvmDictSetDictusetgetrefInst
import org.ton.bytecode.TvmDictSetDictusetrefInst
import org.ton.bytecode.TvmDictSetInst
import org.ton.bytecode.TvmDictSpecialInst
import org.ton.bytecode.TvmDictSubInst
import org.ton.bytecode.TvmInst
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.collection.set.primitive.setEntries
import org.usvm.isTrue
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.dictKeyLengthField
import org.usvm.machine.TvmContext.TvmCellDataSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.intValue
import org.usvm.machine.interpreter.TvmInterpreter.Companion.logger
import org.usvm.machine.interpreter.inputdict.DictKeyKind
import org.usvm.machine.interpreter.inputdict.InputDict
import org.usvm.machine.interpreter.inputdict.InputDictRootInformation
import org.usvm.machine.interpreter.inputdict.InputDictionaryStorage
import org.usvm.machine.interpreter.inputdict.Modification
import org.usvm.machine.interpreter.inputdict.TypedDictKey
import org.usvm.machine.interpreter.inputdict.assertInputDictIsEmpty
import org.usvm.machine.interpreter.inputdict.doInputDictHasKey
import org.usvm.machine.interpreter.inputdict.doInputDictMinMax
import org.usvm.machine.interpreter.inputdict.doInputDictNextPrev
import org.usvm.machine.interpreter.inputdict.extendDictKey
import org.usvm.machine.interpreter.inputdict.makeFreshKeyConstant
import org.usvm.machine.state.DictId
import org.usvm.machine.state.DictKeyInfo
import org.usvm.machine.state.TvmDictValueRegionId
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.addInt
import org.usvm.machine.state.addOnStack
import org.usvm.machine.state.allocEmptyCell
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.allocSliceFromData
import org.usvm.machine.state.allocatedDictContainsKey
import org.usvm.machine.state.assertDictType
import org.usvm.machine.state.assertIfSat
import org.usvm.machine.state.builderCopyFromBuilder
import org.usvm.machine.state.builderStoreDataBits
import org.usvm.machine.state.builderStoreNextRefNoOverflowCheck
import org.usvm.machine.state.calcOnStateCtx
import org.usvm.machine.state.checkCellOverflow
import org.usvm.machine.state.checkOutOfRange
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.copyDict
import org.usvm.machine.state.dictAddKeyValue
import org.usvm.machine.state.dictGetValue
import org.usvm.machine.state.dictRemoveKey
import org.usvm.machine.state.dictValueRegion
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.generateSymbolicSlice
import org.usvm.machine.state.getSliceRemainingBitsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.sliceCopy
import org.usvm.machine.state.sliceLoadRefTlb
import org.usvm.machine.state.sliceMoveDataPtr
import org.usvm.machine.state.sliceMoveRefPtr
import org.usvm.machine.state.slicePreloadDataBits
import org.usvm.machine.state.slicePreloadNextRef
import org.usvm.machine.state.takeLastBuilder
import org.usvm.machine.state.takeLastCell
import org.usvm.machine.state.takeLastIntOrThrowTypeError
import org.usvm.machine.state.takeLastSlice
import org.usvm.machine.types.TvmBuilderType
import org.usvm.machine.types.TvmCellMaybeConstructorBitRead
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmDataCellType
import org.usvm.machine.types.TvmDictCellType
import org.usvm.machine.types.TvmIntegerType
import org.usvm.machine.types.TvmNullType
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.makeSliceTypeLoad
import org.usvm.mkSizeExpr
import org.usvm.sizeSort
import org.usvm.utils.flattenReferenceIte

class TvmDictOperationInterpreter(
    private val ctx: TvmContext,
) {
    fun visitTvmDictInst(
        scope: TvmStepScopeManager,
        inst: TvmDictInst,
    ) {
        scope.consumeDefaultGas(inst)

        when (inst) {
            is TvmDictGetInst -> visitDictGet(scope, inst)
            is TvmDictSetInst -> visitDictSet(scope, inst)
            is TvmDictSetBuilderInst -> visitDictSetBuilder(scope, inst)
            is TvmDictDeleteInst -> visitDictDelete(scope, inst)
            is TvmDictSerialInst -> visitDictSerial(scope, inst)
            is TvmDictMinInst -> visitDictMin(scope, inst)
            is TvmDictNextInst -> visitDictNext(scope, inst)
            is TvmDictMayberefInst -> visitDictMaybeRef(scope, inst)
            is TvmDictSubInst -> TODO("Unknown stmt: $inst")
            is TvmDictPrefixInst -> TODO("Unknown stmt: $inst")
            is TvmDictSpecialInst -> error("Dict special inst should not be handled there: $inst")
        }
    }

    private fun visitDictSet(
        scope: TvmStepScopeManager,
        inst: TvmDictSetInst,
    ) {
        when (inst) {
            is TvmDictSetDictaddInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    getOldValue = false,
                    DictSetMode.ADD,
                )

            is TvmDictSetDictaddgetInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    getOldValue = true,
                    DictSetMode.ADD,
                )

            is TvmDictSetDictaddrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.CELL,
                    getOldValue = false,
                    DictSetMode.ADD,
                )

            is TvmDictSetDictaddgetrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.CELL,
                    getOldValue = true,
                    DictSetMode.ADD,
                )

            is TvmDictSetDictreplaceInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    getOldValue = false,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetDictreplacegetInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    getOldValue = true,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetDictreplacerefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.CELL,
                    getOldValue = false,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetDictreplacegetrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.CELL,
                    getOldValue = true,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetDictsetInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    getOldValue = false,
                    DictSetMode.SET,
                )

            is TvmDictSetDictsetgetInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    getOldValue = true,
                    DictSetMode.SET,
                )

            is TvmDictSetDictsetrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.CELL,
                    getOldValue = false,
                    DictSetMode.SET,
                )

            is TvmDictSetDictsetgetrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.CELL,
                    getOldValue = true,
                    DictSetMode.SET,
                )

            is TvmDictSetDictiaddInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = false,
                    DictSetMode.ADD,
                )

            is TvmDictSetDictiaddgetInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = true,
                    DictSetMode.ADD,
                )

            is TvmDictSetDictiaddrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = false,
                    DictSetMode.ADD,
                )

            is TvmDictSetDictiaddgetrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = true,
                    DictSetMode.ADD,
                )

            is TvmDictSetDictireplaceInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = false,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetDictireplacegetInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = true,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetDictireplacerefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = false,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetDictireplacegetrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = true,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetDictisetInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = false,
                    DictSetMode.SET,
                )

            is TvmDictSetDictisetgetInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = true,
                    DictSetMode.SET,
                )

            is TvmDictSetDictisetrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = false,
                    DictSetMode.SET,
                )

            is TvmDictSetDictisetgetrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = true,
                    DictSetMode.SET,
                )

            is TvmDictSetDictuaddInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = false,
                    DictSetMode.ADD,
                )

            is TvmDictSetDictuaddgetInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = true,
                    DictSetMode.ADD,
                )

            is TvmDictSetDictuaddrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = false,
                    DictSetMode.ADD,
                )

            is TvmDictSetDictuaddgetrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = true,
                    DictSetMode.ADD,
                )

            is TvmDictSetDictureplaceInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = false,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetDictureplacegetInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = true,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetDictureplacerefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = false,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetDictureplacegetrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = true,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetDictusetInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = false,
                    DictSetMode.SET,
                )

            is TvmDictSetDictusetgetInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = true,
                    DictSetMode.SET,
                )

            is TvmDictSetDictusetrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = false,
                    DictSetMode.SET,
                )

            is TvmDictSetDictusetgetrefInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = true,
                    DictSetMode.SET,
                )
        }
    }

    private fun visitDictSetBuilder(
        scope: TvmStepScopeManager,
        inst: TvmDictSetBuilderInst,
    ) {
        when (inst) {
            is TvmDictSetBuilderDictaddbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.BUILDER,
                    getOldValue = false,
                    DictSetMode.ADD,
                )

            is TvmDictSetBuilderDictaddgetbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.BUILDER,
                    getOldValue = true,
                    DictSetMode.ADD,
                )

            is TvmDictSetBuilderDictreplacebInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.BUILDER,
                    getOldValue = false,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetBuilderDictreplacegetbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.BUILDER,
                    getOldValue = true,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetBuilderDictsetbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.BUILDER,
                    getOldValue = false,
                    DictSetMode.SET,
                )

            is TvmDictSetBuilderDictsetgetbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.BUILDER,
                    getOldValue = true,
                    DictSetMode.SET,
                )

            is TvmDictSetBuilderDictiaddbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.BUILDER,
                    getOldValue = false,
                    DictSetMode.ADD,
                )

            is TvmDictSetBuilderDictiaddgetbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.BUILDER,
                    getOldValue = true,
                    DictSetMode.ADD,
                )

            is TvmDictSetBuilderDictireplacebInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.BUILDER,
                    getOldValue = false,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetBuilderDictireplacegetbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.BUILDER,
                    getOldValue = true,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetBuilderDictisetbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.BUILDER,
                    getOldValue = false,
                    DictSetMode.SET,
                )

            is TvmDictSetBuilderDictisetgetbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.BUILDER,
                    getOldValue = true,
                    DictSetMode.SET,
                )

            is TvmDictSetBuilderDictuaddbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.BUILDER,
                    getOldValue = false,
                    DictSetMode.ADD,
                )

            is TvmDictSetBuilderDictuaddgetbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.BUILDER,
                    getOldValue = true,
                    DictSetMode.ADD,
                )

            is TvmDictSetBuilderDictureplacebInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.BUILDER,
                    getOldValue = false,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetBuilderDictureplacegetbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.BUILDER,
                    getOldValue = true,
                    DictSetMode.REPLACE,
                )

            is TvmDictSetBuilderDictusetbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.BUILDER,
                    getOldValue = false,
                    DictSetMode.SET,
                )

            is TvmDictSetBuilderDictusetgetbInst ->
                doDictSet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.BUILDER,
                    getOldValue = true,
                    DictSetMode.SET,
                )
        }
    }

    private fun visitDictGet(
        scope: TvmStepScopeManager,
        inst: TvmDictGetInst,
    ) {
        when (inst) {
            is TvmDictGetDictgetInst ->
                doDictGet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    nullDefaultValue = false,
                )

            is TvmDictGetDictgetrefInst ->
                doDictGet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.CELL,
                    nullDefaultValue = false,
                )

            is TvmDictGetDictigetInst ->
                doDictGet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    nullDefaultValue = false,
                )

            is TvmDictGetDictigetrefInst ->
                doDictGet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.CELL,
                    nullDefaultValue = false,
                )

            is TvmDictGetDictugetInst ->
                doDictGet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    nullDefaultValue = false,
                )

            is TvmDictGetDictugetrefInst ->
                doDictGet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.CELL,
                    nullDefaultValue = false,
                )
        }
    }

    private fun visitDictMaybeRef(
        scope: TvmStepScopeManager,
        inst: TvmDictMayberefInst,
    ) {
        when (inst) {
            is TvmDictMayberefDictgetoptrefInst ->
                doDictGet(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.CELL,
                    nullDefaultValue = true,
                )

            is TvmDictMayberefDictigetoptrefInst ->
                doDictGet(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.CELL,
                    nullDefaultValue = true,
                )

            is TvmDictMayberefDictugetoptrefInst ->
                doDictGet(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.CELL,
                    nullDefaultValue = true,
                )

            else -> TODO("Unknown stmt: $inst")
        }
    }

    private fun visitDictDelete(
        scope: TvmStepScopeManager,
        inst: TvmDictDeleteInst,
    ) {
        when (inst) {
            is TvmDictDeleteDictdelInst ->
                doDictDelete(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    getOldValue = false,
                )

            is TvmDictDeleteDictdelgetInst ->
                doDictDelete(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    getOldValue = true,
                )

            is TvmDictDeleteDictdelgetrefInst ->
                doDictDelete(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.CELL,
                    getOldValue = true,
                )

            is TvmDictDeleteDictidelInst ->
                doDictDelete(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = false,
                )

            is TvmDictDeleteDictidelgetInst ->
                doDictDelete(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = true,
                )

            is TvmDictDeleteDictidelgetrefInst ->
                doDictDelete(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = true,
                )

            is TvmDictDeleteDictudelInst ->
                doDictDelete(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = false,
                )

            is TvmDictDeleteDictudelgetInst ->
                doDictDelete(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    getOldValue = true,
                )

            is TvmDictDeleteDictudelgetrefInst ->
                doDictDelete(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.CELL,
                    getOldValue = true,
                )
        }
    }

    private fun visitDictSerial(
        scope: TvmStepScopeManager,
        inst: TvmDictSerialInst,
    ) {
        when (inst) {
            is TvmDictSerialLddictInst -> doLoadDict(inst, scope, returnUpdatedSlice = true)
            is TvmDictSerialLddictqInst -> TODO()
            is TvmDictSerialLddictsInst -> TODO()
            is TvmDictSerialPlddictInst -> doLoadDict(inst, scope, returnUpdatedSlice = false)
            is TvmDictSerialPlddictqInst -> TODO()
            is TvmDictSerialPlddictsInst -> TODO()
            is TvmDictSerialSkipdictInst -> doLoadDict(inst, scope, returnUpdatedSlice = true, putDictOnStack = false)
            is TvmDictSerialStdictInst -> doStoreDictToBuilder(inst, scope)
        }
    }

    private fun visitDictMin(
        scope: TvmStepScopeManager,
        inst: TvmDictMinInst,
    ) {
        when (inst) {
            is TvmDictMinDictimaxInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    DictMinMaxMode.MAX,
                    removeKey = false,
                )

            is TvmDictMinDictimaxrefInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.CELL,
                    DictMinMaxMode.MAX,
                    removeKey = false,
                )

            is TvmDictMinDictiminInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    DictMinMaxMode.MIN,
                    removeKey = false,
                )

            is TvmDictMinDictiminrefInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.CELL,
                    DictMinMaxMode.MIN,
                    removeKey = false,
                )

            is TvmDictMinDictiremmaxInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    DictMinMaxMode.MAX,
                    removeKey = true,
                )

            is TvmDictMinDictiremmaxrefInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.CELL,
                    DictMinMaxMode.MAX,
                    removeKey = true,
                )

            is TvmDictMinDictiremminInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    DictMinMaxMode.MIN,
                    removeKey = true,
                )

            is TvmDictMinDictiremminrefInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.CELL,
                    DictMinMaxMode.MIN,
                    removeKey = true,
                )

            is TvmDictMinDictmaxInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    DictMinMaxMode.MAX,
                    removeKey = false,
                )

            is TvmDictMinDictmaxrefInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.CELL,
                    DictMinMaxMode.MAX,
                    removeKey = false,
                )

            is TvmDictMinDictminInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    DictMinMaxMode.MIN,
                    removeKey = false,
                )

            is TvmDictMinDictminrefInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.CELL,
                    DictMinMaxMode.MIN,
                    removeKey = false,
                )

            is TvmDictMinDictremmaxInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    DictMinMaxMode.MAX,
                    removeKey = true,
                )

            is TvmDictMinDictremmaxrefInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.CELL,
                    DictMinMaxMode.MAX,
                    removeKey = true,
                )

            is TvmDictMinDictremminInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    DictMinMaxMode.MIN,
                    removeKey = true,
                )

            is TvmDictMinDictremminrefInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.CELL,
                    DictMinMaxMode.MIN,
                    removeKey = true,
                )

            is TvmDictMinDictumaxInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    DictMinMaxMode.MAX,
                    removeKey = false,
                )

            is TvmDictMinDictumaxrefInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.CELL,
                    DictMinMaxMode.MAX,
                    removeKey = false,
                )

            is TvmDictMinDictuminInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    DictMinMaxMode.MIN,
                    removeKey = false,
                )

            is TvmDictMinDictuminrefInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.CELL,
                    DictMinMaxMode.MIN,
                    removeKey = false,
                )

            is TvmDictMinDicturemmaxInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    DictMinMaxMode.MAX,
                    removeKey = true,
                )

            is TvmDictMinDicturemmaxrefInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.CELL,
                    DictMinMaxMode.MAX,
                    removeKey = true,
                )

            is TvmDictMinDicturemminInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    DictMinMaxMode.MIN,
                    removeKey = true,
                )

            is TvmDictMinDicturemminrefInst ->
                doDictMinMax(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.CELL,
                    DictMinMaxMode.MIN,
                    removeKey = true,
                )
        }
    }

    private fun visitDictNext(
        scope: TvmStepScopeManager,
        inst: TvmDictNextInst,
    ) {
        when (inst) {
            is TvmDictNextDictgetnextInst ->
                doDictNextPrev(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    DictNextPrevMode.NEXT,
                    allowEq = false,
                )

            is TvmDictNextDictgetnexteqInst ->
                doDictNextPrev(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    DictNextPrevMode.NEXT,
                    allowEq = true,
                )

            is TvmDictNextDictgetprevInst ->
                doDictNextPrev(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    DictNextPrevMode.PREV,
                    allowEq = false,
                )

            is TvmDictNextDictgetpreveqInst ->
                doDictNextPrev(
                    inst,
                    scope,
                    DictKeyKind.SLICE,
                    DictValueType.SLICE,
                    DictNextPrevMode.PREV,
                    allowEq = true,
                )

            is TvmDictNextDictigetnextInst ->
                doDictNextPrev(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    DictNextPrevMode.NEXT,
                    allowEq = false,
                )

            is TvmDictNextDictigetnexteqInst ->
                doDictNextPrev(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    DictNextPrevMode.NEXT,
                    allowEq = true,
                )

            is TvmDictNextDictigetprevInst ->
                doDictNextPrev(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    DictNextPrevMode.PREV,
                    allowEq = false,
                )

            is TvmDictNextDictigetpreveqInst ->
                doDictNextPrev(
                    inst,
                    scope,
                    DictKeyKind.SIGNED_INT,
                    DictValueType.SLICE,
                    DictNextPrevMode.PREV,
                    allowEq = true,
                )

            is TvmDictNextDictugetnextInst ->
                doDictNextPrev(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    DictNextPrevMode.NEXT,
                    allowEq = false,
                )

            is TvmDictNextDictugetnexteqInst ->
                doDictNextPrev(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    DictNextPrevMode.NEXT,
                    allowEq = true,
                )

            is TvmDictNextDictugetprevInst ->
                doDictNextPrev(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    DictNextPrevMode.PREV,
                    allowEq = false,
                )

            is TvmDictNextDictugetpreveqInst ->
                doDictNextPrev(
                    inst,
                    scope,
                    DictKeyKind.UNSIGNED_INT,
                    DictValueType.SLICE,
                    DictNextPrevMode.PREV,
                    allowEq = true,
                )
        }
    }

    // this is actually load_maybe_ref, not necessarily load_dict
    private fun doLoadDict(
        inst: TvmDictSerialInst,
        scope: TvmStepScopeManager,
        returnUpdatedSlice: Boolean,
        putDictOnStack: Boolean = true,
    ) {
        val slice = scope.calcOnStateCtx { takeLastSlice() }
        if (slice == null) {
            scope.doWithState(ctx.throwTypeCheckError)
            return
        }

        val updatedSlice = scope.calcOnState { memory.allocConcrete(TvmSliceType) }
        scope.makeSliceTypeLoad(
            slice,
            TvmCellMaybeConstructorBitRead(ctx),
            updatedSlice,
            badCellSizeIsExceptional = true,
            onBadCellSize = ctx.throwCellUnderflowErrorBasedOnContext,
        ) { isNotEmptyValueFromTlb ->
            val isNotEmpty =
                isNotEmptyValueFromTlb?.expr ?: let {
                    val maybeConstructorTypeBit =
                        slicePreloadDataBits(slice, bits = 1)
                            ?: return@makeSliceTypeLoad
                    calcOnStateCtx { mkEq(maybeConstructorTypeBit, mkBv(value = 1, sizeBits = 1u)) }
                }

            fork(
                isNotEmpty,
                falseStateIsExceptional = false,
                blockOnFalseState = {
                    if (putDictOnStack) {
                        addOnStack(ctx.nullValue, TvmNullType)
                    }

                    if (returnUpdatedSlice) {
                        updatedSlice.also { sliceCopy(slice, it) }
                        sliceMoveDataPtr(updatedSlice, bits = 1)
                        addOnStack(updatedSlice, TvmSliceType)
                    }

                    newStmt(inst.nextStmt())
                },
            ) ?: return@makeSliceTypeLoad

            // Hack: here newSlice is just to copy tlb stack from the old one.
            // We mustn't copy it in this specific case, so we just pass a dummy new slice instead.
            sliceLoadRefTlb(this, slice, calcOnState { generateSymbolicSlice() }) { dictCellRef ->
                doWithStateCtx {
                    if (putDictOnStack) {
                        addOnStack(dictCellRef, TvmCellType)
                    }

                    if (returnUpdatedSlice) {
                        updatedSlice.also { sliceCopy(slice, it) }
                        sliceMoveDataPtr(updatedSlice, bits = 1)
                        sliceMoveRefPtr(updatedSlice)
                        addOnStack(updatedSlice, TvmSliceType)
                    }

                    newStmt(inst.nextStmt())
                }
            }
        }
    }

    private fun doStoreDictToBuilder(
        inst: TvmDictSerialInst,
        scope: TvmStepScopeManager,
    ) = with(scope.ctx) {
        val builder = scope.calcOnStateCtx { takeLastBuilder() }
        if (builder == null) {
            scope.doWithState(ctx.throwTypeCheckError)
            return
        }

        // this instruction can be used to store data cells, not just dict cells
        val (dictCellRef, status) = popDictFromStack(scope, keyLengthForAssertingDictType = null)
        status ?: return

        val resultBuilder = scope.calcOnStateCtx { memory.allocConcrete(TvmBuilderType) }
        scope.doWithStateCtx { builderCopyFromBuilder(builder, resultBuilder) }

        if (dictCellRef == null) {
            scope.builderStoreDataBits(resultBuilder, mkBv(value = 0, sizeBits = 1u))
                ?: return
        } else {
            scope.builderStoreDataBits(resultBuilder, mkBv(value = 1, sizeBits = 1u))
                ?: return

            val refs =
                scope.calcOnState {
                    fieldManagers.cellRefsLengthFieldManager.readCellRefLength(this, resultBuilder)
                }

            checkCellOverflow(
                mkBvSignedLessOrEqualExpr(refs, mkSizeExpr(TvmContext.MAX_REFS_NUMBER)),
                scope,
            ) ?: return

            scope.calcOnState {
                builderStoreNextRefNoOverflowCheck(resultBuilder, dictCellRef)
            }
        }

        scope.doWithStateCtx {
            addOnStack(resultBuilder, TvmBuilderType)
            newStmt(inst.nextStmt())
        }
    }

    private fun doDictSet(
        inst: TvmDictInst,
        scope: TvmStepScopeManager,
        keyKind: DictKeyKind,
        valueType: DictValueType,
        getOldValue: Boolean,
        mode: DictSetMode,
    ) {
        val keyLength =
            loadKeyLength(scope)
                ?: return
        val (dictCellRef, status) = popDictFromStack(scope, keyLength)
        status ?: return
        val key = loadKey(scope, keyKind, keyLength) ?: return
        val value = loadValue(scope, valueType)

        if (value == null) {
            scope.doWithState(ctx.throwTypeCheckError)
            return
        }

        val dictId = DictId(keyLength)
        val resultDict = scope.calcOnState { memory.allocConcrete(TvmDictCellType) }

        val keySort = ctx.mkBvSort(keyLength.toUInt())
        val isInput = checkDictIsInput(scope, dictCellRef, dictId, keySort)
        if (isInput) {
            doSetInstOnInputDict(
                scope = scope,
                getOldValue = getOldValue,
                dictCellRef = dictCellRef,
                keySort = keySort,
                keyKind = keyKind,
                mode = mode,
                key = key,
                resultDict = resultDict,
                keyLength = keyLength,
                dictId = dictId,
                value = value,
                inst = inst,
            )
            return
        }

        doSetInstOnAllocatedDict(
            scope,
            dictCellRef,
            key,
            keyLength,
            resultDict,
            value,
            valueType,
            mode,
            getOldValue,
            inst,
        )
    }

    private fun doSetInstOnAllocatedDict(
        scope: TvmStepScopeManager,
        dictCellRef: UHeapRef?,
        key: UExpr<UBvSort>,
        keyLength: Int,
        resultDict: UConcreteHeapRef,
        value: KExpr<UAddressSort>,
        valueType: DictValueType,
        mode: DictSetMode,
        getOldValue: Boolean,
        inst: TvmDictInst,
    ) {
        val dictContainsKey =
            dictCellRef?.let {
                scope.calcOnState { allocatedDictContainsKey(dictCellRef, keyLength.asDictId(), key) }
            } ?: ctx.falseExpr

        val oldValue =
            dictCellRef?.let {
                scope.calcOnState { dictGetValue(dictCellRef, keyLength.asDictId(), key) }
            }

        if (dictCellRef != null) {
            assertDictKeyLength(scope, dictCellRef, keyLength)
                ?: return
            assertDictIsNotEmpty(scope, dictCellRef, keyLength.asDictId())
                ?: return

            scope.doWithState { copyDict(dictCellRef, resultDict, keyLength.asDictId(), key.sort) }
        } else {
            scope.doWithStateCtx {
                memory.writeField(resultDict, dictKeyLengthField, sizeSort, mkSizeExpr(keyLength), guard = trueExpr)
            }
        }

        assertDictValueDoesNotOverflow(scope, keyLength.asDictId(), value)
            ?: return

        scope.doWithState { dictAddKeyValue(resultDict, keyLength.asDictId(), key, value) }

        scope.fork(
            dictContainsKey,
            falseStateIsExceptional = false,
            blockOnFalseState = {
                dictSetResultStack(
                    dictCellRef,
                    resultDict,
                    oldValue = null,
                    valueType,
                    mode,
                    getOldValue,
                    keyContains = false,
                )
                newStmt(inst.nextStmt())
            },
        ) ?: return

        require(oldValue != null) {
            "Unexpected null dict that contains key $key"
        }

        assertDictValueDoesNotOverflow(scope, keyLength.asDictId(), oldValue)
            ?: return

        val unwrappedOldValue =
            oldValue.takeIf { getOldValue }?.let {
                unwrapDictValue(scope, it, valueType)
                    ?: return
            }
        scope.doWithState {
            dictSetResultStack(
                dictCellRef,
                resultDict,
                unwrappedOldValue,
                valueType,
                mode,
                getOldValue,
                keyContains = true,
            )
            newStmt(inst.nextStmt())
        }
    }

    private fun doSetInstOnInputDict(
        scope: TvmStepScopeManager,
        getOldValue: Boolean,
        dictCellRef: UHeapRef?,
        keySort: KBvSort,
        keyKind: DictKeyKind,
        mode: DictSetMode,
        key: UExpr<UBvSort>,
        resultDict: UConcreteHeapRef,
        keyLength: Int,
        dictId: DictId,
        value: KExpr<UAddressSort>,
        inst: TvmDictInst,
    ) {
        if (getOldValue) {
            TODO("unsupported getting old value in input dicts `set` operation")
        }
        val dictOriginalConcrete =
            dictCellRef as? UConcreteHeapRef
                ?: TODO("ites are not supported yet")
        val baseInputDict =
            scope.calcOnState {
                readInputDictionary(dictOriginalConcrete, keySort, keyKind)
            }
        val conditionToStore =
            createStoreConditionFromMode(scope, mode, keyKind, baseInputDict, key)
                ?: return
        val appliedModification = Modification.Store(TypedDictKey(key, keyKind), conditionToStore)
        val newInputDict = baseInputDict.withModification(appliedModification)
        scope.calcOnState {
            inputDictionaryStorage = inputDictionaryStorage.createDictEntry(resultDict, newInputDict)
        }

        scope.calcOnStateCtx {
            val dictKeyLength = memory.readField(dictOriginalConcrete, dictKeyLengthField, sizeSort)
            scope.assert(with(ctx) { mkEq(dictKeyLength.zeroExtendToSort(sizeSort), keyLength.toBv(sizeSort)) })
                ?: return@calcOnStateCtx
            memory.writeField(resultDict, dictKeyLengthField, sizeSort, dictKeyLength, guard = trueExpr)

            val dictValueRegionId = TvmDictValueRegionId(dictId, keySort)
            val dictValueRegion = memory.dictValueRegion(dictValueRegionId)
            val updatedValues =
                dictValueRegion
                    .copyRefValues(dictOriginalConcrete, resultDict)
                    .writeRefValue(resultDict, key, value.asExpr(ctx.addressSort), guard = conditionToStore)
            memory.setRegion(dictValueRegionId, updatedValues)
        }
        scope.calcOnStateCtx {
            addOnStack(resultDict, TvmCellType)
            if (mode != DictSetMode.SET) {
                val status = ctx.mkIte(conditionToStore, ctx.trueValue, ctx.falseValue)
                addOnStack(status, TvmIntegerType)
            }
            newStmt(inst.nextStmt())
        }
        return
    }

    /**
     * @return null if scope is dead
     */
    private fun createStoreConditionFromMode(
        scope: TvmStepScopeManager,
        mode: DictSetMode,
        keyKind: DictKeyKind,
        baseInputDict: InputDict,
        key: UExpr<UBvSort>,
    ): UBoolExpr? =
        with(ctx) {
            if (mode == DictSetMode.SET) {
                trueExpr
            } else {
                val keyExists =
                    doInputDictHasKey(scope, baseInputDict, TypedDictKey(key, keyKind))?.exists ?: return null
                if (mode == DictSetMode.REPLACE) {
                    keyExists
                } else {
                    keyExists.not()
                }
            }
        }

    private fun TvmState.dictSetResultStack(
        initialDictRef: UHeapRef?,
        result: UHeapRef,
        oldValue: UHeapRef?,
        oldValueType: DictValueType,
        mode: DictSetMode,
        getOldValue: Boolean,
        keyContains: Boolean,
    ) {
        val returnOldDict = keyContains && mode == DictSetMode.ADD || !keyContains && mode == DictSetMode.REPLACE
        if (returnOldDict) {
            if (initialDictRef == null) {
                addOnStack(ctx.nullValue, TvmNullType)
            } else {
                addOnStack(initialDictRef, TvmCellType)
            }
        } else {
            addOnStack(result, TvmCellType)
        }

        if (keyContains && getOldValue) {
            require(oldValue != null) {
                "Unexpected null previous dict value to store"
            }
            addValueOnStack(oldValue, oldValueType)
        }

        val status =
            when (mode) {
                DictSetMode.SET -> if (getOldValue) keyContains else null
                DictSetMode.ADD -> !keyContains
                DictSetMode.REPLACE -> keyContains
            }

        if (status != null) {
            val statusValue = if (status) ctx.trueValue else ctx.falseValue
            stack.addInt(statusValue)
        }
    }

    private fun doDictGet(
        inst: TvmInst,
        scope: TvmStepScopeManager,
        keyKind: DictKeyKind,
        valueType: DictValueType,
        nullDefaultValue: Boolean,
    ) = with(ctx) {
        val keyLength =
            loadKeyLength(scope)
                ?: return
        val (dictCellRef, status) = popDictFromStack(scope, keyLength)
        status ?: return
        val key = loadKey(scope, keyKind, keyLength) ?: return

        if (dictCellRef == null) {
            scope.doWithState {
                addOnStack(falseValue, TvmIntegerType)
                newStmt(inst.nextStmt())
            }
            return
        }
        /*
        To properly avoid forking, the code in `doDictGetImpl` must be clearly separated into interpreter part and
        logical part. After making such a division, the logical part must be rewritten with the use of guards
        and dictionary type (input dictionary or non-input dictionary).
         */
        val dicts = flattenReferenceIte(dictCellRef, extractAllocated = true)
        val actions = dicts.map { (cond, dict) -> TvmStepScopeManager.ActionOnCondition({}, false, cond, dict) }
        scope.doWithConditions(actions) { dictCellRef ->
            doDictGetImpl(this, keyLength, dictCellRef, keyKind, key, nullDefaultValue, inst, valueType)
        }
    }

    private fun doDictGetImpl(
        scope: TvmStepScopeManager,
        keyLength: Int,
        dictCellRef: UConcreteHeapRef,
        keyKind: DictKeyKind,
        key: UExpr<UBvSort>,
        nullDefaultValue: Boolean,
        inst: TvmInst,
        valueType: DictValueType,
    ) {
        val ctx = scope.ctx
        val dictId = DictId(keyLength)

        assertDictKeyLength(scope, dictCellRef, keyLength)
            ?: return

        val keySort = scope.ctx.mkBvSort(keyLength.toUInt())
        val isInput = checkDictIsInput(scope, dictCellRef, dictId, keySort)
        val dictContainsKey =
            if (isInput) {
                val inputDict = scope.calcOnState { readInputDictionary(dictCellRef, keySort, keyKind) }
                val keyExists =
                    doInputDictHasKey(scope, inputDict, TypedDictKey(key, keyKind))?.exists
                        ?: return
                keyExists
            } else {
                scope.calcOnState {
                    allocatedDictContainsKey(dictCellRef, dictId, key)
                }
            }

        scope.fork(
            dictContainsKey,
            falseStateIsExceptional = false,
            blockOnFalseState = {
                if (nullDefaultValue) {
                    addOnStack(ctx.nullValue, TvmNullType)
                } else {
                    addOnStack(ctx.falseValue, TvmIntegerType)
                }
                newStmt(inst.nextStmt())
            },
        ) ?: return

        val sliceValue = scope.calcOnStateCtx { dictGetValue(dictCellRef, dictId, key) }

        val unwrappedValue =
            unwrapDictValue(scope, sliceValue, valueType)
                ?: return

        assertDictValueDoesNotOverflow(scope, dictId, sliceValue)
            ?: return

        scope.doWithState {
            addValueOnStack(unwrappedValue, valueType)
            if (!nullDefaultValue) {
                addOnStack(ctx.trueValue, TvmIntegerType)
            }
            newStmt(inst.nextStmt())
        }
    }

    private fun doDictDelete(
        inst: TvmDictDeleteInst,
        scope: TvmStepScopeManager,
        keyKind: DictKeyKind,
        valueType: DictValueType,
        getOldValue: Boolean,
    ) {
        val keyLength =
            loadKeyLength(scope)
                ?: return
        val (dictCellRef, status) = popDictFromStack(scope, keyLength)
        status ?: return
        val key = loadKey(scope, keyKind, keyLength) ?: return

        if (dictCellRef == null) {
            scope.doWithStateCtx {
                addOnStack(nullValue, TvmNullType)
                addOnStack(falseValue, TvmIntegerType)
                newStmt(inst.nextStmt())
            }
            return
        }

        val dictId = DictId(keyLength)

        assertDictKeyLength(scope, dictCellRef, keyLength)
            ?: return

        val keySort = ctx.mkBvSort(keyLength.toUInt())
        val isInput =
            checkDictIsInput(scope, dictCellRef, dictId, keySort)
        if (isInput) {
            doDeleteInstOnInputDict(
                scope = scope,
                dictCellRef = dictCellRef,
                keySort = keySort,
                keyKind = keyKind,
                key = key,
                dictId = dictId,
                valueType = valueType,
                getOldValue = getOldValue,
                inst = inst,
            )
        } else {
            doDeleteInstOnAllocatedDict(
                scope = scope,
                dictCellRef = dictCellRef,
                dictId = dictId,
                key = key,
                inst = inst,
                valueType = valueType,
                getOldValue = getOldValue,
            )
        }
    }

    private fun doDeleteInstOnAllocatedDict(
        scope: TvmStepScopeManager,
        dictCellRef: UHeapRef,
        dictId: DictId,
        key: UExpr<UBvSort>,
        inst: TvmDictDeleteInst,
        valueType: DictValueType,
        getOldValue: Boolean,
    ) {
        val dictContainsKey = scope.calcOnState { allocatedDictContainsKey(dictCellRef, dictId, key) }

        scope.fork(
            condition = dictContainsKey,
            falseStateIsExceptional = false,
            blockOnFalseState = {
                addOnStack(dictCellRef, TvmCellType)
                addOnStack(ctx.falseValue, TvmIntegerType)
                newStmt(inst.nextStmt())
            },
        ) ?: return

        val value = scope.calcOnState { dictGetValue(dictCellRef, dictId, key) }
        val unwrappedValue =
            unwrapDictValue(scope, value, valueType)
                ?: return

        assertDictValueDoesNotOverflow(scope, dictId, value)
            ?: return

        handleDictRemoveKey(
            scope,
            dictCellRef,
            dictId,
            key,
            originalDictContainsKeyEmptyResult = {
                addOnStack(ctx.nullValue, TvmNullType)

                if (getOldValue) {
                    addValueOnStack(unwrappedValue, valueType)
                }

                addOnStack(ctx.trueValue, TvmIntegerType)
                newStmt(inst.nextStmt())
            },
            originalDictContainsKeyNonEmptyResult = { resultDict ->
                addOnStack(resultDict, TvmCellType)

                if (getOldValue) {
                    addValueOnStack(unwrappedValue, valueType)
                }

                addOnStack(ctx.trueValue, TvmIntegerType)
                newStmt(inst.nextStmt())
            },
        )
    }

    private fun doDeleteInstOnInputDict(
        scope: TvmStepScopeManager,
        dictCellRef: UHeapRef,
        keySort: KBvSort,
        keyKind: DictKeyKind,
        key: UExpr<UBvSort>,
        dictId: DictId,
        valueType: DictValueType,
        getOldValue: Boolean,
        inst: TvmDictDeleteInst,
    ) {
        val resultDictRef = scope.calcOnState { memory.allocConcrete(TvmDictCellType) }
        val dictOriginalConcreteRef =
            dictCellRef as? UConcreteHeapRef
                ?: TODO("unsupported")
        val initInputDict =
            scope.calcOnState {
                readInputDictionary(dictOriginalConcreteRef, keySort, keyKind)
            }

        val queryKeyExists =
            doInputDictHasKey(scope, initInputDict, TypedDictKey(key, keyKind))?.exists
                ?: return
        val appliedModification = Modification.Remove(TypedDictKey(key, keyKind))
        val newInputDict = initInputDict.withModification(appliedModification)
        scope.calcOnStateCtx {
            copyInputDictMemoryRepresentation(dictOriginalConcreteRef, resultDictRef, dictId, keySort)
            inputDictionaryStorage = inputDictionaryStorage.createDictEntry(resultDictRef, newInputDict)
        }

        // assert not empty
        val someKeyExistsAfterDeletion =
            doInputDictHasKey(
                scope,
                newInputDict,
                scope.calcOnState { makeFreshKeyConstant(keySort, keyKind) },
            )?.exists
                ?: return
        assertInputDictIsEmpty(
            scope = scope,
            inputDict = newInputDict,
            guard = ctx.mkNot(someKeyExistsAfterDeletion),
        ) ?: return

        // TODO: once the ite operations on the dictionary are fully supported,
        // replace this with ITE reference
        val actions =
            listOf(
                TvmStepScopeManager.ActionOnCondition(
                    action = {},
                    caseIsExceptional = false,
                    condition = someKeyExistsAfterDeletion,
                    paramForDoForAllBlock = resultDictRef to TvmCellType,
                ),
                TvmStepScopeManager.ActionOnCondition(
                    action = {},
                    caseIsExceptional = false,
                    condition = ctx.mkNot(someKeyExistsAfterDeletion),
                    paramForDoForAllBlock = ctx.nullValue to TvmNullType,
                ),
            )
        scope.doWithConditions(actions) { (resultDictRef, cellType) ->
            if (!getOldValue) {
                this.calcOnStateCtx {
                    addOnStack(resultDictRef, cellType)
                    addOnStack(mkIte(queryKeyExists, trueValue, falseValue), TvmIntegerType)
                    newStmt(inst.nextStmt())
                }
            } else {
                val oldValue = this.calcOnState { dictGetValue(dictCellRef, dictId, key) }
                assertDictValueDoesNotOverflow(this, dictId, oldValue)
                    ?: return@doWithConditions
                val unwrappedValue =
                    unwrapDictValue(this, oldValue, valueType)
                        ?: return@doWithConditions
                this.doWithConditions(
                    listOf(
                        TvmStepScopeManager.ActionOnCondition(
                            action = {
                                addOnStack(resultDictRef, cellType)
                                addValueOnStack(unwrappedValue, valueType)
                                addOnStack(ctx.trueValue, TvmIntegerType)
                                newStmt(inst.nextStmt())
                            },
                            caseIsExceptional = false,
                            condition = queryKeyExists,
                            paramForDoForAllBlock = Unit,
                        ),
                        TvmStepScopeManager.ActionOnCondition(
                            action = {
                                addOnStack(resultDictRef, cellType)
                                addOnStack(ctx.falseValue, TvmIntegerType)
                                newStmt(inst.nextStmt())
                            },
                            caseIsExceptional = false,
                            condition = ctx.mkNot(queryKeyExists),
                            paramForDoForAllBlock = Unit,
                        ),
                    ),
                ) { _: Unit -> }
            }
        }
        return
    }

    private fun checkDictIsInput(
        scope: TvmStepScopeManager,
        dictCellRef: UHeapRef?,
        dictId: DictId,
        keySort: UBvSort,
    ): Boolean {
        val allSetEntries =
            dictCellRef?.let {
                scope.calcOnStateCtx {
                    memory.setEntries(it, dictId, keySort, DictKeyInfo)
                }
            }
        return (allSetEntries != null && allSetEntries.isInput) ||
            scope.calcOnState { inputDictionaryStorage.hasInputDictEntryAtRef(dictCellRef) }
    }

    private fun TvmState.copyInputDictMemoryRepresentation(
        dictOriginalConcrete: UConcreteHeapRef,
        resultDict: UConcreteHeapRef,
        dictId: DictId,
        keySort: KBvSort,
    ) {
        val dictKeyLength = memory.readField(dictOriginalConcrete, dictKeyLengthField, ctx.sizeSort)
        memory.writeField(resultDict, dictKeyLengthField, ctx.sizeSort, dictKeyLength, guard = ctx.trueExpr)
        val dictValueRegionId = TvmDictValueRegionId(dictId, keySort)
        val dictValueRegion = memory.dictValueRegion(dictValueRegionId)
        val updatedValues =
            dictValueRegion
                .copyRefValues(dictOriginalConcrete, resultDict)
        memory.setRegion(dictValueRegionId, updatedValues)
    }

    private fun doDictMinMax(
        inst: TvmDictMinInst,
        scope: TvmStepScopeManager,
        keyKind: DictKeyKind,
        valueType: DictValueType,
        mode: DictMinMaxMode,
        removeKey: Boolean,
    ): Unit =
        with(ctx) {
            val keyLength =
                loadKeyLength(scope, rangeOpMaxKeyLength(keyKind))
                    ?: return
            val (dictCellRef, status) = popDictFromStack(scope, keyLength)
            status ?: return

            if (dictCellRef == null) {
                scope.doWithState {
                    if (removeKey) {
                        addOnStack(nullValue, TvmNullType)
                    }

                    addOnStack(falseValue, TvmIntegerType)
                    newStmt(inst.nextStmt())
                }
                return
            }

            assertDictKeyLength(scope, dictCellRef, keyLength)
                ?: return

            val dictId = DictId(keyLength)
            val keySort = ctx.mkBvSort(keyLength.toUInt())

            val isInput = checkDictIsInput(scope, dictCellRef, dictId, keySort)
            if (isInput) {
                doMinMaxInstOnInputDict(
                    scope = scope,
                    dictCellRef = dictCellRef,
                    keySort = keySort,
                    keyKind = keyKind,
                    mode = mode,
                    dictId = dictId,
                    valueType = valueType,
                    removeKey = removeKey,
                    inst = inst,
                )
            } else {
                doMinMaxInstOnAllocatedDict(
                    scope = scope,
                    dictCellRef = dictCellRef,
                    keyKind = keyKind,
                    mode = mode,
                    valueType = valueType,
                    removeKey = removeKey,
                    inst = inst,
                    keyLength = keyLength,
                )
            }
        }

    private fun TvmContext.doMinMaxInstOnAllocatedDict(
        scope: TvmStepScopeManager,
        dictCellRef: UHeapRef,
        keyKind: DictKeyKind,
        mode: DictMinMaxMode,
        valueType: DictValueType,
        removeKey: Boolean,
        inst: TvmDictMinInst,
        keyLength: Int,
    ): Boolean {
        // since these entries were stored during execution, value overflow constraints have already been asserted
        val allSetEntries =
            scope.calcOnStateCtx {
                memory.setEntries(dictCellRef, keyLength.asDictId(), keyLength.asDictSort(scope.ctx), DictKeyInfo)
            }
        val storedKeys =
            scope.calcOnState {
                allSetEntries.entries.map { entry ->
                    val setContainsEntry = allocatedDictContainsKey(dictCellRef, keyLength.asDictId(), entry.setElement)
                    entry.setElement to setContainsEntry
                }
            }

        val resultElement = scope.calcOnState { makeSymbolicPrimitive(keyLength.asDictSort(scope.ctx)) }
        val resultElementExtended = extendDictKey(resultElement, keyKind)
        val dictContainsResultElement =
            scope.calcOnState {
                allocatedDictContainsKey(dictCellRef, keyLength.asDictId(), resultElement)
            }

        val resultIsMinMax =
            scope.calcOnState {
                storedKeys
                    .map { (storeKey, storedKeyContains) ->
                        val compareLessThan =
                            when (mode) {
                                DictMinMaxMode.MIN -> true
                                DictMinMaxMode.MAX -> false
                            }
                        val cmp =
                            compareKeys(
                                keyKind,
                                compareLessThan,
                                allowEq = true,
                                resultElementExtended,
                                extendDictKey(storeKey, keyKind),
                            )
                        mkImplies(storedKeyContains, cmp)
                    }.let { mkAnd(it) }
            }

        scope.assert(
            ctx.mkAnd(dictContainsResultElement, resultIsMinMax),
            unsatBlock = { error("Dict min/max element is not in the dict") },
        ) ?: return true

        val value = scope.calcOnState { dictGetValue(dictCellRef, keyLength.asDictId(), resultElement) }
        val unwrappedValue =
            unwrapDictValue(scope, value, valueType)
                ?: return true

        assertDictValueDoesNotOverflow(scope, keyLength.asDictId(), value)
            ?: return true

        if (!removeKey) {
            composeStackAfterDictMinMaxOp(scope, valueType, unwrappedValue, keyKind, resultElement, inst)
            return true
        }

        handleDictRemoveKey(
            scope,
            dictCellRef,
            keyLength.asDictId(),
            resultElement,
            originalDictContainsKeyEmptyResult = {
                addOnStack(ctx.nullValue, TvmNullType)

                addValueOnStack(unwrappedValue, valueType)
                addKeyOnStack(resultElement, keyKind)
                addOnStack(ctx.trueValue, TvmIntegerType)
                newStmt(inst.nextStmt())
            },
            originalDictContainsKeyNonEmptyResult = { resultDict ->
                addOnStack(resultDict, TvmCellType)

                addValueOnStack(unwrappedValue, valueType)
                addKeyOnStack(resultElement, keyKind)
                addOnStack(ctx.trueValue, TvmIntegerType)
                newStmt(inst.nextStmt())
            },
        )
        return false
    }

    private fun doMinMaxInstOnInputDict(
        scope: TvmStepScopeManager,
        dictCellRef: UHeapRef,
        keySort: KBvSort,
        keyKind: DictKeyKind,
        mode: DictMinMaxMode,
        dictId: DictId,
        valueType: DictValueType,
        removeKey: Boolean,
        inst: TvmDictMinInst,
    ) {
        val dictConcreteRef =
            dictCellRef as? UConcreteHeapRef
                ?: TODO("ite refs are not supported yet")
        val inputDict =
            scope.calcOnState { readInputDictionary(dictConcreteRef, keySort, keyKind) }
        val keyResultSymbol =
            doInputDictMinMax(
                scope,
                inputDict,
                mode == DictMinMaxMode.MAX,
                keySort,
                keyKind,
            )?.expr ?: return
        val value = scope.calcOnState { dictGetValue(dictCellRef, dictId, keyResultSymbol.expr) }
        val unwrappedValue =
            unwrapDictValue(scope, value, valueType)
                ?: return

        assertDictValueDoesNotOverflow(scope, dictId, value)
            ?: return

        if (!removeKey) {
            composeStackAfterDictMinMaxOp(scope, valueType, unwrappedValue, keyKind, keyResultSymbol.expr, inst)
        } else {
            val resultDict = scope.calcOnState { memory.allocConcrete(TvmDictCellType) }
            scope.calcOnState {
                copyInputDictMemoryRepresentation(
                    dictConcreteRef,
                    resultDict,
                    dictId,
                    keySort,
                )
            }
            val appliedModification = Modification.Remove(keyResultSymbol)
            val newInputDict =
                InputDict(
                    modifications = inputDict.modifications.add(0, appliedModification),
                    rootInputDictId = inputDict.rootInputDictId,
                )
            scope.calcOnState {
                inputDictionaryStorage =
                    inputDictionaryStorage.createDictEntry(resultDict, newInputDict)
                addOnStack(resultDict, TvmCellType)

                addValueOnStack(unwrappedValue, valueType)
                addKeyOnStack(keyResultSymbol.expr, keyKind)
                addOnStack(ctx.trueValue, TvmIntegerType)
                newStmt(inst.nextStmt())
            }
        }
        return
    }

    private fun composeStackAfterDictMinMaxOp(
        scope: TvmStepScopeManager,
        valueType: DictValueType,
        unwrappedValue: UHeapRef,
        keyType: DictKeyKind,
        resultElement: UExpr<KBvSort>,
        inst: TvmDictMinInst,
    ) {
        scope.doWithStateCtx {
            addValueOnStack(unwrappedValue, valueType)
            addKeyOnStack(resultElement, keyType)
            addOnStack(trueValue, TvmIntegerType)
            newStmt(inst.nextStmt())
        }
    }

    /**
     * Does not return `null` as it is doing all the work and returns the execution straight to the main loop
     */
    private fun doDictNextPrev(
        inst: TvmDictNextInst,
        scope: TvmStepScopeManager,
        keyKind: DictKeyKind,
        valueType: DictValueType,
        mode: DictNextPrevMode,
        allowEq: Boolean,
    ) = with(ctx) {
        val keyLength =
            loadKeyLength(scope, rangeOpMaxKeyLength(keyKind))
                ?: return
        val (dictCellRef, status) = popDictFromStack(scope, keyLength)
        status ?: return

        val key =
            when (keyKind) {
                DictKeyKind.SIGNED_INT,
                DictKeyKind.UNSIGNED_INT,
                -> {
                    // key does not necessarily fit into [keyLength] bits
                    // and in case of unsigned key is not necessarily non-negative
                    scope.takeLastIntOrThrowTypeError()?.signExtendToSort(cellDataSort)
                        ?: return
                }

                DictKeyKind.SLICE -> {
                    val slice =
                        scope.calcOnState { takeLastSlice() }
                            ?: return scope.doWithState(throwTypeCheckError)

                    scope.slicePreloadDataBits(slice, keyLength)?.zeroExtendToSort(cellDataSort)
                        ?: return
                }
            }

        if (dictCellRef == null) {
            scope.doWithStateCtx {
                addOnStack(falseValue, TvmIntegerType)
                newStmt(inst.nextStmt())
            }
            return
        }

        val dictId = DictId(keyLength)

        assertDictKeyLength(scope, dictCellRef, keyLength)
            ?: return

        val keySort = ctx.mkBvSort(keyLength.toUInt())

        // since these entries were stored during execution, value overflow constraints have already been asserted
        val isInput = checkDictIsInput(scope, dictCellRef, dictId, keySort)
        if (isInput) {
            doNextPrevInstOnInputDict(
                dictCellRef = dictCellRef,
                scope = scope,
                keySort = keySort,
                keyKind = keyKind,
                key = key,
                mode = mode,
                allowEq = allowEq,
                dictId = dictId,
                valueType = valueType,
                inst = inst,
            )
            return
        }

        doNextPrevInstOnAllocatedDict(
            scope,
            dictCellRef,
            keyKind,
            mode,
            allowEq,
            key,
            inst,
            valueType,
            keyLength,
        )
    }

    private fun Int.asDictId() = DictId(this)

    private fun Int.asDictSort(ctx: TvmContext) = ctx.mkBvSort(this.toUInt())

    private fun TvmContext.doNextPrevInstOnAllocatedDict(
        scope: TvmStepScopeManager,
        dictCellRef: UHeapRef,
        keyKind: DictKeyKind,
        mode: DictNextPrevMode,
        allowEq: Boolean,
        key: UExpr<TvmCellDataSort>,
        inst: TvmDictNextInst,
        valueType: DictValueType,
        keyLength: Int,
    ) {
        val dictId = keyLength.asDictId()
        val keySort = keyLength.asDictSort(scope.ctx)
        val allSetEntries =
            scope.calcOnStateCtx {
                memory.setEntries(dictCellRef, dictId, keySort, DictKeyInfo)
            }
        val storedKeys =
            scope.calcOnStateCtx {
                allSetEntries.entries.map { entry ->
                    val setContainsEntry = allocatedDictContainsKey(dictCellRef, dictId, entry.setElement)
                    entry.setElement to setContainsEntry
                }
            }

        val resultElement = scope.calcOnStateCtx { makeSymbolicPrimitive(keySort) }
        val dictContainsResultElement =
            scope.calcOnStateCtx {
                allocatedDictContainsKey(dictCellRef, dictId, resultElement)
            }

        val resultElementExtended = extendDictKey(resultElement, keyKind)
        val resultIsNextPrev =
            scope.calcOnStateCtx {
                val compareLessThan =
                    when (mode) {
                        DictNextPrevMode.NEXT -> false
                        DictNextPrevMode.PREV -> true
                    }
                compareKeys(keyKind, compareLessThan, allowEq, resultElementExtended, key)
            }

        val resultIsClosest =
            scope.calcOnStateCtx {
                storedKeys
                    .map { (storeKey, storedKeyContains) ->
                        val storeKeyExtended = extendDictKey(storeKey, keyKind)
                        val compareLessThan =
                            when (mode) {
                                DictNextPrevMode.NEXT -> false
                                DictNextPrevMode.PREV -> true
                            }
                        val storedKeyRelevant = compareKeys(keyKind, compareLessThan, allowEq, storeKeyExtended, key)

                        val compareClosestLessThan =
                            when (mode) {
                                DictNextPrevMode.NEXT -> true
                                DictNextPrevMode.PREV -> false
                            }
                        val resultIsClosest =
                            compareKeys(
                                keyKind,
                                compareClosestLessThan,
                                allowEq = true,
                                resultElementExtended,
                                storeKeyExtended,
                            )

                        mkImplies(storedKeyContains and storedKeyRelevant, resultIsClosest)
                    }.let { mkAnd(it) }
            }

        val dictHasNextKeyConstraint = ctx.mkAnd(dictContainsResultElement, resultIsNextPrev, resultIsClosest)

        if (!scope.assertIfSat(dictHasNextKeyConstraint)) {
            // TODO handle UNKNOWN
            // There is no next key in the dict
            scope.doWithStateCtx {
                addOnStack(falseValue, TvmIntegerType)
                newStmt(inst.nextStmt())
            }
            return
        }

        scope.doWithStateCtx {
            // explicitly store key
            val setContainsElemLValue = USetEntryLValue(keySort, dictCellRef, resultElement, dictId, DictKeyInfo)
            memory.write(setContainsElemLValue, rvalue = trueExpr, guard = trueExpr)
        }

        val value = scope.calcOnState { dictGetValue(dictCellRef, dictId, resultElement) }
        val unwrappedValue =
            unwrapDictValue(scope, value, valueType)
                ?: return

        assertDictValueDoesNotOverflow(scope, dictId, value)
            ?: return

        scope.doWithStateCtx {
            addValueOnStack(unwrappedValue, valueType)
            addKeyOnStack(resultElement, keyKind)
            addOnStack(trueValue, TvmIntegerType)
            newStmt(inst.nextStmt())
        }
    }

    private fun doNextPrevInstOnInputDict(
        dictCellRef: UHeapRef,
        scope: TvmStepScopeManager,
        keySort: KBvSort,
        keyKind: DictKeyKind,
        key: UExpr<TvmCellDataSort>,
        mode: DictNextPrevMode,
        allowEq: Boolean,
        dictId: DictId,
        valueType: DictValueType,
        inst: TvmDictNextInst,
    ) {
        val dictConcreteRef =
            dictCellRef as? UConcreteHeapRef
                ?: TODO("not concrete refs are not supported yet")
        val inputDict =
            scope.calcOnState {
                readInputDictionary(dictConcreteRef, keySort, keyKind)
            }
        doInputDictNextPrev(
            scope,
            inputDict,
            key,
            keySort,
            keyKind,
            mode == DictNextPrevMode.NEXT,
            allowEq,
        ) { updated ->
            val resultSymbol = updated.answer
            if (resultSymbol != null) {
                val value = this.calcOnState { dictGetValue(dictCellRef, dictId, resultSymbol.expr) }
                val unwrappedValue =
                    unwrapDictValue(this, value, valueType)
                        ?: return@doInputDictNextPrev

                assertDictValueDoesNotOverflow(this, dictId, value)
                    ?: return@doInputDictNextPrev

                this.doWithStateCtx {
                    addValueOnStack(unwrappedValue, valueType)
                    addKeyOnStack(resultSymbol.expr, keyKind)
                    addOnStack(trueValue, TvmIntegerType)
                    newStmt(inst.nextStmt())
                }
            } else {
                this.doWithStateCtx {
                    addOnStack(falseValue, TvmIntegerType)
                    newStmt(inst.nextStmt())
                }
            }
        }
        return
    }

    /**
     * Reads the input dictionary and ensures that root information is initialized
     */
    private fun TvmState.readInputDictionary(
        dictConcreteRef: UConcreteHeapRef,
        dictKeySort: UBvSort,
        dictKeyKind: DictKeyKind,
    ): InputDict {
        val inputDict = inputDictionaryStorage.memory[dictConcreteRef] ?: InputDict()
        val rootInputDictInfo =
            inputDictionaryStorage.rootInformation[inputDict.rootInputDictId] ?: run {
                // we must ensure that the dictionary is not empty
                val symbol = makeFreshKeyConstant(dictKeySort, dictKeyKind)
                InputDictRootInformation(symbols = persistentSetOf(symbol))
            }
        inputDictionaryStorage =
            InputDictionaryStorage(
                inputDictionaryStorage.memory.put(dictConcreteRef, inputDict),
                inputDictionaryStorage.rootInformation.put(inputDict.rootInputDictId, rootInputDictInfo),
            )
        return inputDict
    }

    /**
     * Should be used only for min/max and nearest operations
     *
     * @see [doDictMinMax]
     * @see [doDictNextPrev]
     */
    private fun rangeOpMaxKeyLength(keyType: DictKeyKind): Int =
        when (keyType) {
            DictKeyKind.UNSIGNED_INT -> 256
            DictKeyKind.SIGNED_INT -> 257
            DictKeyKind.SLICE -> TvmContext.MAX_DATA_LENGTH
        }

    private fun TvmContext.compareKeys(
        keyType: DictKeyKind,
        compareLessThan: Boolean,
        allowEq: Boolean,
        left: UExpr<TvmCellDataSort>,
        right: UExpr<TvmCellDataSort>,
    ): UBoolExpr =
        when (keyType) {
            DictKeyKind.UNSIGNED_INT,
            DictKeyKind.SIGNED_INT,
            ->
                when {
                    compareLessThan && allowEq -> mkBvSignedLessOrEqualExpr(left, right)
                    compareLessThan && !allowEq -> mkBvSignedLessExpr(left, right)
                    !compareLessThan && allowEq -> mkBvSignedGreaterOrEqualExpr(left, right)
                    else -> mkBvSignedGreaterExpr(left, right)
                }

            DictKeyKind.SLICE ->
                when {
                    compareLessThan && allowEq -> mkBvUnsignedLessOrEqualExpr(left, right)
                    compareLessThan && !allowEq -> mkBvUnsignedLessExpr(left, right)
                    !compareLessThan && allowEq -> mkBvUnsignedGreaterOrEqualExpr(left, right)
                    else -> mkBvUnsignedGreaterExpr(left, right)
                }
        }

    private fun assertDictKeyLength(
        scope: TvmStepScopeManager,
        dict: UHeapRef,
        keyLength: Int,
    ): Unit? =
        scope.calcOnStateCtx {
            val dictKeyLength = scope.calcOnState { memory.readField(dict, dictKeyLengthField, sizeSort) }

            scope.assert(
                constraint = dictKeyLength eq mkSizeExpr(keyLength),
                unsatBlock = { throwRealDictError(this) },
            ) ?: run {
                logger.warn { "Cannot assert dict key length constraint" }
                null
            }
        }

    private fun loadKeyLength(
        scope: TvmStepScopeManager,
        maxKeyLength: Int = TvmContext.MAX_DATA_LENGTH,
    ): Int? {
        val keyLengthExpr =
            scope.takeLastIntOrThrowTypeError()
                ?: return null

        if (keyLengthExpr !is KBitVecValue<*>) {
            TODO("Non-concrete key length: $keyLengthExpr")
        }

        val keyLength = keyLengthExpr.intValue()

        checkOutOfRange(
            keyLengthExpr,
            scope,
            min = 0,
            max = maxKeyLength,
        ) ?: return null

        return keyLength
    }

    // todo: verify key length
    private fun popDictFromStack(
        scope: TvmStepScopeManager,
        keyLengthForAssertingDictType: Int?,
    ): Pair<UHeapRef?, Unit?> =
        scope.calcOnState {
            if (stack.lastIsNull()) {
                stack.pop(0)
                null to Unit
            } else {
                takeLastCell()?.let {
                    val status =
                        if (keyLengthForAssertingDictType != null) {
                            scope.assertDictType(it, keyLengthForAssertingDictType)
                        } else {
                            Unit
                        }
                    it to status
                } ?: run {
                    scope.doWithState {
                        ctx.throwTypeCheckError(this)
                    }
                    null to null
                }
            }
        }

    private fun assertDictIsNotEmpty(
        scope: TvmStepScopeManager,
        dict: UHeapRef,
        dictId: DictId,
    ): Unit? {
        val entries =
            scope.calcOnStateCtx {
                val keySort = mkBvSort(dictId.keyLength.toUInt())
                memory.setEntries(dict, dictId, keySort, DictKeyInfo).entries
            }
        val definitelyNotEmpty =
            entries.any { entry ->
                val contains =
                    scope.calcOnStateCtx {
                        allocatedDictContainsKey(dict, dictId, entry.setElement)
                    }
                contains.isTrue
            }
        if (definitelyNotEmpty) {
            return Unit
        }

        val constraint =
            scope.calcOnStateCtx {
                val symbolicKey = makeSymbolicPrimitive(mkBvSort(dictId.keyLength.toUInt()))

                allocatedDictContainsKey(dict, dictId, symbolicKey)
            }

        return scope.assert(constraint)
            ?: run {
                logger.debug { "Cannot assert non-empty dict" }
                null
            }
    }

    private fun loadKey(
        scope: TvmStepScopeManager,
        keyType: DictKeyKind,
        keyLength: Int,
    ): UExpr<UBvSort>? =
        with(ctx) {
            // todo: handle keyLength errors
            when (keyType) {
                DictKeyKind.SIGNED_INT ->
                    scope
                        .takeLastIntOrThrowTypeError()
                        ?.let { mkBvExtractExpr(high = keyLength - 1, low = 0, it) }

                DictKeyKind.UNSIGNED_INT ->
                    scope
                        .takeLastIntOrThrowTypeError()
                        ?.let { mkBvExtractExpr(high = keyLength - 1, low = 0, it) }

                DictKeyKind.SLICE -> {
                    val slice = scope.calcOnState { takeLastSlice() }
                    if (slice == null) {
                        scope.doWithState(throwTypeCheckError)
                        return null
                    }

                    scope.slicePreloadDataBits(slice, keyLength)
                }
            }
        }

    private fun TvmState.addKeyOnStack(
        key: UExpr<UBvSort>,
        keyType: DictKeyKind,
    ) = with(ctx) {
        when (keyType) {
            DictKeyKind.SIGNED_INT -> {
                val keyValue = key.signedExtendToInteger()
                addOnStack(keyValue, TvmIntegerType)
            }

            DictKeyKind.UNSIGNED_INT -> {
                val keyValue = key.unsignedExtendToInteger()
                addOnStack(keyValue, TvmIntegerType)
            }

            DictKeyKind.SLICE -> {
                val resultSlice = this@addKeyOnStack.allocSliceFromData(key)
                addOnStack(resultSlice, TvmSliceType)
            }
        }
    }

    private fun loadValue(
        scope: TvmStepScopeManager,
        valueType: DictValueType,
    ) = scope.calcOnState {
        when (valueType) {
            DictValueType.SLICE -> takeLastSlice()
            DictValueType.CELL -> {
                val cell = takeLastCell() ?: return@calcOnState null
                val builder = allocEmptyCell()

                builderStoreNextRefNoOverflowCheck(builder, cell)
                allocSliceFromCell(builder)
            }

            DictValueType.BUILDER -> {
                val builder = takeLastBuilder() ?: return@calcOnState null
                val cell = memory.allocConcrete(TvmDataCellType).also { builderCopyFromBuilder(builder, it) }

                scope.calcOnState { allocSliceFromCell(cell) }
            }
        }
    }

    private fun unwrapDictValue(
        scope: TvmStepScopeManager,
        sliceValue: UHeapRef,
        valueType: DictValueType,
    ): UHeapRef? =
        when (valueType) {
            DictValueType.SLICE -> sliceValue
            DictValueType.CELL -> scope.slicePreloadNextRef(sliceValue)
            DictValueType.BUILDER -> error("Unexpected dict value type: $valueType")
        }

    private fun TvmState.addValueOnStack(
        value: UHeapRef,
        valueType: DictValueType,
    ) {
        when (valueType) {
            DictValueType.SLICE -> addOnStack(value, TvmSliceType)
            DictValueType.CELL -> addOnStack(value, TvmCellType)
            DictValueType.BUILDER -> error("Unexpected value type to store: $valueType")
        }
    }

    private fun handleDictRemoveKey(
        scope: TvmStepScopeManager,
        dictCellRef: UHeapRef,
        dictId: DictId,
        key: UExpr<UBvSort>,
        originalDictContainsKeyEmptyResult: TvmState.() -> Unit,
        originalDictContainsKeyNonEmptyResult: TvmState.(UHeapRef) -> Unit,
    ) {
        val resultDict = scope.calcOnState { memory.allocConcrete(TvmDictCellType) }

        scope.doWithStateCtx {
            copyDict(dictCellRef, resultDict, dictId, key.sort)
            dictRemoveKey(resultDict, dictId, key)
        }

        val resultSetEntries =
            scope.calcOnStateCtx {
                memory.setEntries(resultDict, dictId, key.sort, DictKeyInfo)
            }

        val resultSetContainsAnyStoredKey =
            scope.calcOnStateCtx {
                resultSetEntries.entries
                    .map { entry ->
                        allocatedDictContainsKey(resultDict, dictId, entry.setElement)
                    }.let { mkOr(it) }
            }

        val resultSetContainsNoStoredKey = ctx.mkNot(resultSetContainsAnyStoredKey)

        scope.doWithConditions(
            listOf(
                TvmStepScopeManager.ActionOnCondition(
                    caseIsExceptional = false,
                    condition = resultSetContainsNoStoredKey,
                    paramForDoForAllBlock = 1,
                    action = {},
                ),
                TvmStepScopeManager.ActionOnCondition(
                    caseIsExceptional = false,
                    condition = ctx.mkNot(resultSetContainsNoStoredKey),
                    paramForDoForAllBlock = 2,
                    action = {},
                ),
            ),
        ) { caseId ->
            when (caseId) {
                1 -> {
                    if (!resultSetEntries.isInput) {
                        doWithState {
                            originalDictContainsKeyEmptyResult()
                        }
                    } else {
                        // todo: empty input dict
                        val leftKey =
                            calcOnStateCtx {
                                makeSymbolicPrimitive(mkBvSort(dictId.keyLength.toUInt()))
                            }

                        val condition =
                            calcOnStateCtx {
                                (leftKey neq key) and allocatedDictContainsKey(dictCellRef, dictId, leftKey)
                            }
                        assert(condition)
                            ?: return@doWithConditions

                        // No need to [assertDictValueDoesNotOverflow] on value of leftKey:
                        // at this point, this value didn't appear in path constraints
                        // (if it did, we have already made this assertion).
                        // Cells that are not in path constraints are resolved as empty cells.

                        doWithState {
                            originalDictContainsKeyNonEmptyResult(resultDict)
                        }
                    }
                }

                2 -> {
                    doWithState {
                        originalDictContainsKeyNonEmptyResult(resultDict)
                    }
                }

                else -> {
                    error("Unexpected case id: $caseId")
                }
            }
        }
    }

    private fun TvmState.dictValueDoesNotOverflowConstraint(
        keyLength: Int,
        value: UHeapRef,
    ): UBoolExpr =
        with(ctx) {
            // hml_short$0 {m:#} {n:#} len:(Unary ~n) {n <= m} s:(n * Bit) = HmLabel ~n m;
            val maxLabelBits = 1 + (keyLength + 1) + keyLength
            val valueBitsLeft = getSliceRemainingBitsCount(value)
            val maxDictLeafBits = mkBvAddExpr(valueBitsLeft, mkSizeExpr(maxLabelBits))

            mkBvSignedLessOrEqualExpr(maxDictLeafBits, maxDataLengthSizeExpr)
        }

    private fun assertDictValueDoesNotOverflow(
        scope: TvmStepScopeManager,
        dictId: DictId,
        value: UHeapRef,
    ): Unit? {
        val constraint = scope.calcOnState { dictValueDoesNotOverflowConstraint(dictId.keyLength, value) }

        return scope.assert(constraint)
            ?: run {
                logger.warn { "Cannot assert dict value to not overflow" }
                null
            }
    }

    private enum class DictSetMode {
        SET, // always set
        ADD, // set only if absent
        REPLACE, // set only if present
    }

    private enum class DictMinMaxMode {
        MIN,
        MAX,
    }

    internal enum class DictNextPrevMode {
        NEXT,
        PREV,
    }

    private enum class DictValueType {
        SLICE,
        CELL,
        BUILDER,
    }
}
