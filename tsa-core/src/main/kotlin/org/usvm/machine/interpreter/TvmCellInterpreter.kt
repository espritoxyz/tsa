package org.usvm.machine.interpreter

import org.ton.Endian
import org.ton.bytecode.TvmCell
import org.ton.bytecode.TvmCellBuildBbitsInst
import org.ton.bytecode.TvmCellBuildBdepthInst
import org.ton.bytecode.TvmCellBuildEndcInst
import org.ton.bytecode.TvmCellBuildInst
import org.ton.bytecode.TvmCellBuildNewcInst
import org.ton.bytecode.TvmCellBuildStbInst
import org.ton.bytecode.TvmCellBuildStbqInst
import org.ton.bytecode.TvmCellBuildStbrInst
import org.ton.bytecode.TvmCellBuildStbrefrInst
import org.ton.bytecode.TvmCellBuildStbrqInst
import org.ton.bytecode.TvmCellBuildStiInst
import org.ton.bytecode.TvmCellBuildStixInst
import org.ton.bytecode.TvmCellBuildStrefAltInst
import org.ton.bytecode.TvmCellBuildStrefInst
import org.ton.bytecode.TvmCellBuildStrefqInst
import org.ton.bytecode.TvmCellBuildStrefrInst
import org.ton.bytecode.TvmCellBuildStrefrqInst
import org.ton.bytecode.TvmCellBuildStsliceAltInst
import org.ton.bytecode.TvmCellBuildStsliceInst
import org.ton.bytecode.TvmCellBuildStsliceconstInst
import org.ton.bytecode.TvmCellBuildStsliceqInst
import org.ton.bytecode.TvmCellBuildStslicerInst
import org.ton.bytecode.TvmCellBuildStslicerqInst
import org.ton.bytecode.TvmCellBuildStuInst
import org.ton.bytecode.TvmCellBuildSturInst
import org.ton.bytecode.TvmCellBuildStuxInst
import org.ton.bytecode.TvmCellBuildStzeroesInst
import org.ton.bytecode.TvmCellParseCdepthInst
import org.ton.bytecode.TvmCellParseCtosInst
import org.ton.bytecode.TvmCellParseEndsInst
import org.ton.bytecode.TvmCellParseInst
import org.ton.bytecode.TvmCellParseLdiAltInst
import org.ton.bytecode.TvmCellParseLdiInst
import org.ton.bytecode.TvmCellParseLdile4Inst
import org.ton.bytecode.TvmCellParseLdile8Inst
import org.ton.bytecode.TvmCellParseLdiqInst
import org.ton.bytecode.TvmCellParseLdixInst
import org.ton.bytecode.TvmCellParseLdixqInst
import org.ton.bytecode.TvmCellParseLdrefInst
import org.ton.bytecode.TvmCellParseLdrefrtosInst
import org.ton.bytecode.TvmCellParseLdsliceAltInst
import org.ton.bytecode.TvmCellParseLdsliceInst
import org.ton.bytecode.TvmCellParseLdsliceqInst
import org.ton.bytecode.TvmCellParseLdslicexInst
import org.ton.bytecode.TvmCellParseLdslicexqInst
import org.ton.bytecode.TvmCellParseLduAltInst
import org.ton.bytecode.TvmCellParseLduInst
import org.ton.bytecode.TvmCellParseLdule4Inst
import org.ton.bytecode.TvmCellParseLdule4qInst
import org.ton.bytecode.TvmCellParseLdule8Inst
import org.ton.bytecode.TvmCellParseLduqInst
import org.ton.bytecode.TvmCellParseLduxInst
import org.ton.bytecode.TvmCellParseLduxqInst
import org.ton.bytecode.TvmCellParsePldiInst
import org.ton.bytecode.TvmCellParsePldile4Inst
import org.ton.bytecode.TvmCellParsePldile4qInst
import org.ton.bytecode.TvmCellParsePldile8Inst
import org.ton.bytecode.TvmCellParsePldile8qInst
import org.ton.bytecode.TvmCellParsePldiqInst
import org.ton.bytecode.TvmCellParsePldixInst
import org.ton.bytecode.TvmCellParsePldixqInst
import org.ton.bytecode.TvmCellParsePldrefidxInst
import org.ton.bytecode.TvmCellParsePldrefvarInst
import org.ton.bytecode.TvmCellParsePldsliceInst
import org.ton.bytecode.TvmCellParsePldsliceqInst
import org.ton.bytecode.TvmCellParsePldslicexInst
import org.ton.bytecode.TvmCellParsePldslicexqInst
import org.ton.bytecode.TvmCellParsePlduInst
import org.ton.bytecode.TvmCellParsePldule4Inst
import org.ton.bytecode.TvmCellParsePldule4qInst
import org.ton.bytecode.TvmCellParsePldule8Inst
import org.ton.bytecode.TvmCellParsePldule8qInst
import org.ton.bytecode.TvmCellParsePlduqInst
import org.ton.bytecode.TvmCellParsePlduxInst
import org.ton.bytecode.TvmCellParsePlduxqInst
import org.ton.bytecode.TvmCellParseSbitrefsInst
import org.ton.bytecode.TvmCellParseSbitsInst
import org.ton.bytecode.TvmCellParseScutlastInst
import org.ton.bytecode.TvmCellParseSdbeginsInst
import org.ton.bytecode.TvmCellParseSdbeginsqInst
import org.ton.bytecode.TvmCellParseSdbeginsxInst
import org.ton.bytecode.TvmCellParseSdbeginsxqInst
import org.ton.bytecode.TvmCellParseSdcutfirstInst
import org.ton.bytecode.TvmCellParseSdcutlastInst
import org.ton.bytecode.TvmCellParseSdepthInst
import org.ton.bytecode.TvmCellParseSdskipfirstInst
import org.ton.bytecode.TvmCellParseSdskiplastInst
import org.ton.bytecode.TvmCellParseSdsubstrInst
import org.ton.bytecode.TvmCellParseSrefsInst
import org.ton.bytecode.TvmCellParseSskiplastInst
import org.ton.bytecode.TvmCellParseXctosInst
import org.ton.bytecode.TvmInst
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.MAX_DATA_LENGTH
import org.usvm.machine.TvmContext.Companion.cellDataLengthField
import org.usvm.machine.TvmContext.Companion.cellRefsLengthField
import org.usvm.machine.TvmContext.Companion.sliceCellField
import org.usvm.machine.TvmContext.Companion.sliceDataPosField
import org.usvm.machine.TvmContext.Companion.sliceRefPosField
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.addInt
import org.usvm.machine.state.addOnStack
import org.usvm.machine.state.allocEmptyCell
import org.usvm.machine.state.allocSliceFromCell
import org.usvm.machine.state.assertDataCellType
import org.usvm.machine.state.assertDataLengthConstraintWithoutError
import org.usvm.machine.state.assertRefsLengthConstraintWithoutError
import org.usvm.machine.state.builderCopy
import org.usvm.machine.state.builderCopyFromBuilder
import org.usvm.machine.state.builderStoreDataBits
import org.usvm.machine.state.builderStoreInt
import org.usvm.machine.state.builderStoreIntTlb
import org.usvm.machine.state.builderStoreNextRef
import org.usvm.machine.state.builderStoreSlice
import org.usvm.machine.state.builderStoreSliceTlb
import org.usvm.machine.state.builderToCell
import org.usvm.machine.state.checkCellDataUnderflow
import org.usvm.machine.state.checkCellOverflow
import org.usvm.machine.state.checkCellRefsUnderflow
import org.usvm.machine.state.checkOutOfRange
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.consumeGas
import org.usvm.machine.state.doSwap
import org.usvm.machine.state.doWithStateCtx
import org.usvm.machine.state.getSliceRemainingBitsCount
import org.usvm.machine.state.getSliceRemainingRefsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.signedIntegerFitsBits
import org.usvm.machine.state.sliceCopy
import org.usvm.machine.state.sliceLoadIntTlb
import org.usvm.machine.state.sliceLoadRefTlb
import org.usvm.machine.state.sliceMoveDataPtr
import org.usvm.machine.state.sliceMoveRefPtr
import org.usvm.machine.state.slicePreloadDataBits
import org.usvm.machine.state.slicePreloadInt
import org.usvm.machine.state.slicePreloadRef
import org.usvm.machine.state.takeLastBuilder
import org.usvm.machine.state.takeLastCell
import org.usvm.machine.state.takeLastIntOrThrowTypeError
import org.usvm.machine.state.takeLastRef
import org.usvm.machine.state.takeLastSlice
import org.usvm.machine.state.unsignedIntegerFitsBits
import org.usvm.machine.types.TvmBuilderType
import org.usvm.machine.types.TvmCellType
import org.usvm.machine.types.TvmDataCellType
import org.usvm.machine.types.TvmIntegerType
import org.usvm.machine.types.TvmSliceType
import org.usvm.machine.types.TvmCellDataBitArrayRead
import org.usvm.machine.types.TvmCellDataIntegerRead
import org.usvm.machine.types.TvmRealReferenceType
import org.usvm.machine.types.assertEndOfCell
import org.usvm.machine.types.copyTlbToNewBuilder
import org.usvm.machine.types.makeCellToSlice
import org.usvm.machine.types.makeSliceTypeLoad
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeLtExpr
import org.usvm.mkSizeSubExpr
import org.usvm.sizeSort

class TvmCellInterpreter(
    private val ctx: TvmContext,
) {
    fun visitCellParseInst(
        scope: TvmStepScopeManager,
        stmt: TvmCellParseInst
    ): Unit = with(ctx) {
        when (stmt) {
            is TvmCellParseCtosInst -> visitCellToSliceInst(scope, stmt)
            is TvmCellParseXctosInst -> visitExoticCellToSliceInst(scope, stmt)
            is TvmCellParseEndsInst -> visitEndSliceInst(scope, stmt)
            is TvmCellParseLdrefInst -> visitLoadRefInst(scope, stmt)
            is TvmCellParsePldrefidxInst -> doPreloadRef(scope, stmt, refIdx = mkSizeExpr(stmt.n))
            is TvmCellParsePldrefvarInst -> {
                val refIdx = scope.calcOnState { takeLastIntOrThrowTypeError() } ?: return
                doPreloadRef(scope, stmt, refIdx = refIdx.extractToSizeSort())
            }

            is TvmCellParseLduInst -> visitLoadIntInst(
                scope,
                stmt,
                stmt.c + 1,
                isSigned = false,
                preload = false,
                quiet = false
            )

            is TvmCellParseLduqInst -> visitLoadIntInst(
                scope,
                stmt,
                stmt.c + 1,
                isSigned = false,
                preload = false,
                quiet = true
            )

            is TvmCellParseLduAltInst -> visitLoadIntInst(
                scope,
                stmt,
                stmt.c + 1,
                isSigned = false,
                preload = false,
                quiet = false
            )

            is TvmCellParseLdiInst -> visitLoadIntInst(
                scope,
                stmt,
                stmt.c + 1,
                isSigned = true,
                preload = false,
                quiet = false
            )

            is TvmCellParseLdiqInst -> visitLoadIntInst(
                scope,
                stmt,
                stmt.c + 1,
                isSigned = true,
                preload = false,
                quiet = true
            )

            is TvmCellParseLdiAltInst -> visitLoadIntInst(
                scope,
                stmt,
                stmt.c + 1,
                isSigned = true,
                preload = false,
                quiet = false
            )

            is TvmCellParsePlduInst -> visitLoadIntInst(
                scope,
                stmt,
                stmt.c + 1,
                isSigned = false,
                preload = true,
                quiet = false
            )

            is TvmCellParsePlduqInst -> visitLoadIntInst(
                scope,
                stmt,
                stmt.c + 1,
                isSigned = false,
                preload = true,
                quiet = true
            )

            is TvmCellParsePldiInst -> visitLoadIntInst(
                scope,
                stmt,
                stmt.c + 1,
                isSigned = true,
                preload = true,
                quiet = false
            )

            is TvmCellParsePldiqInst -> visitLoadIntInst(
                scope,
                stmt,
                stmt.c + 1,
                isSigned = true,
                preload = true,
                quiet = true
            )

            is TvmCellParseLdule4Inst -> visitLoadIntLEInst(
                scope,
                stmt,
                sizeBytes = 4,
                isSigned = false,
                preload = false,
                quiet = false
            )

            is TvmCellParseLdule4qInst -> visitLoadIntLEInst(
                scope,
                stmt,
                sizeBytes = 4,
                isSigned = false,
                preload = false,
                quiet = true
            )

            is TvmCellParseLdile4Inst -> visitLoadIntLEInst(
                scope,
                stmt,
                sizeBytes = 4,
                isSigned = true,
                preload = false,
                quiet = false
            )

            is TvmCellParseLdule8Inst -> visitLoadIntLEInst(
                scope,
                stmt,
                sizeBytes = 8,
                isSigned = false,
                preload = false,
                quiet = false
            )

            is TvmCellParseLdile8Inst -> visitLoadIntLEInst(
                scope,
                stmt,
                sizeBytes = 8,
                isSigned = true,
                preload = false,
                quiet = false
            )

            is TvmCellParsePldule4Inst -> visitLoadIntLEInst(
                scope,
                stmt,
                sizeBytes = 4,
                isSigned = false,
                preload = true,
                quiet = false
            )

            is TvmCellParsePldule4qInst -> visitLoadIntLEInst(
                scope,
                stmt,
                sizeBytes = 4,
                isSigned = false,
                preload = true,
                quiet = true
            )

            is TvmCellParsePldile4Inst -> visitLoadIntLEInst(
                scope,
                stmt,
                sizeBytes = 4,
                isSigned = true,
                preload = true,
                quiet = false
            )

            is TvmCellParsePldile4qInst -> visitLoadIntLEInst(
                scope,
                stmt,
                sizeBytes = 4,
                isSigned = true,
                preload = true,
                quiet = true
            )

            is TvmCellParsePldule8Inst -> visitLoadIntLEInst(
                scope,
                stmt,
                sizeBytes = 8,
                isSigned = false,
                preload = true,
                quiet = false
            )

            is TvmCellParsePldule8qInst -> visitLoadIntLEInst(
                scope,
                stmt,
                sizeBytes = 8,
                isSigned = false,
                preload = true,
                quiet = true
            )

            is TvmCellParsePldile8Inst -> visitLoadIntLEInst(
                scope,
                stmt,
                sizeBytes = 8,
                isSigned = true,
                preload = true,
                quiet = false
            )

            is TvmCellParsePldile8qInst -> visitLoadIntLEInst(
                scope,
                stmt,
                sizeBytes = 8,
                isSigned = true,
                preload = true,
                quiet = true
            )

            is TvmCellParseLduxInst -> visitLoadIntXInst(scope, stmt, isSigned = false, preload = false, quiet = false)
            is TvmCellParseLduxqInst -> visitLoadIntXInst(scope, stmt, isSigned = false, preload = false, quiet = true)
            is TvmCellParseLdixInst -> visitLoadIntXInst(scope, stmt, isSigned = true, preload = false, quiet = false)
            is TvmCellParseLdixqInst -> visitLoadIntXInst(scope, stmt, isSigned = true, preload = false, quiet = true)
            is TvmCellParsePlduxInst -> visitLoadIntXInst(scope, stmt, isSigned = false, preload = true, quiet = false)
            is TvmCellParsePlduxqInst -> visitLoadIntXInst(scope, stmt, isSigned = false, preload = true, quiet = true)
            is TvmCellParsePldixInst -> visitLoadIntXInst(scope, stmt, isSigned = true, preload = true, quiet = false)
            is TvmCellParsePldixqInst -> visitLoadIntXInst(scope, stmt, isSigned = true, preload = true, quiet = true)
            is TvmCellParseLdsliceInst -> visitLoadSliceInst(scope, stmt, stmt.c + 1, preload = false, quiet = false)
            is TvmCellParseLdsliceqInst -> visitLoadSliceInst(scope, stmt, stmt.c + 1, preload = false, quiet = true)
            is TvmCellParseLdsliceAltInst -> visitLoadSliceInst(scope, stmt, stmt.c + 1, preload = false, quiet = false)
            is TvmCellParsePldsliceInst -> visitLoadSliceInst(scope, stmt, stmt.c + 1, preload = true, quiet = false)
            is TvmCellParsePldsliceqInst -> visitLoadSliceInst(scope, stmt, stmt.c + 1, preload = true, quiet = true)
            is TvmCellParseLdslicexInst -> visitLoadSliceXInst(scope, stmt, preload = false, quiet = false) {}
            is TvmCellParseLdslicexqInst -> visitLoadSliceXInst(scope, stmt, preload = false, quiet = true) {}
            is TvmCellParsePldslicexInst, is TvmCellParseSdcutfirstInst -> visitLoadSliceXInst(
                scope,
                stmt,
                preload = true,
                quiet = false
            ) {}

            is TvmCellParsePldslicexqInst -> visitLoadSliceXInst(scope, stmt, preload = true, quiet = true) {}
            is TvmCellParseSrefsInst -> visitSizeRefsInst(scope, stmt)
            is TvmCellParseSbitsInst -> visitSizeBitsInst(scope, stmt)
            is TvmCellParseSbitrefsInst -> visitSizeBitRefsInst(scope, stmt)
            is TvmCellParseSdskipfirstInst -> {
                visitLoadSliceXInst(scope, stmt, preload = false, quiet = false, pushResultOnStack = false) {}
            }

            is TvmCellParseSdcutlastInst -> {
                visitCutLastInst(scope, stmt, skipAllRefs = true)
            }

            is TvmCellParseScutlastInst -> {
                visitCutLastInst(scope, stmt, skipAllRefs = false)
            }

            is TvmCellParseSdskiplastInst -> {
                visitSkipLastInst(scope, stmt, keepAllRefs = true)
            }

            is TvmCellParseSskiplastInst -> {
                visitSkipLastInst(scope, stmt, keepAllRefs = false)
            }

            is TvmCellParseSdbeginsInst -> {
                visitBeginsInst(scope, stmt, stmt.s, quiet = false)
            }

            is TvmCellParseSdbeginsqInst -> {
                visitBeginsInst(scope, stmt, stmt.s, quiet = true)
            }

            is TvmCellParseSdbeginsxInst -> {
                visitBeginsXInst(scope, stmt, quiet = false)
            }

            is TvmCellParseSdbeginsxqInst -> {
                visitBeginsXInst(scope, stmt, quiet = true)
            }

            is TvmCellParseCdepthInst -> visitDepthInst(scope, stmt, operandType = TvmCellType)
            is TvmCellParseSdepthInst -> visitDepthInst(scope, stmt, operandType = TvmSliceType)
            is TvmCellParseLdrefrtosInst -> visitLoadRefRtosInst(scope, stmt)
            is TvmCellParseSdsubstrInst -> visitSdsubstrInst(scope, stmt)

            else -> TODO("Unknown stmt: $stmt")
        }
    }

    fun visitSdsubstrInst(scope: TvmStepScopeManager, stmt: TvmCellParseSdsubstrInst) = with(ctx) {
        scope.consumeDefaultGas(stmt)

        val sizeBits = scope.takeLastIntOrThrowTypeError() ?: return
        val offsetBits = scope.takeLastIntOrThrowTypeError() ?: return

        val slice = scope.calcOnState { stack.takeLastSlice() }
        if (slice == null) {
            scope.doWithState(throwTypeCheckError)
            return
        }
        val isSizeWithinBounds = unsignedIntegerFitsBits(sizeBits, bits = 10u)
        checkOutOfRange(isSizeWithinBounds, scope) ?: return
        val isOffsetWithinBounds = unsignedIntegerFitsBits(offsetBits, bits = 10u)
        checkOutOfRange(isOffsetWithinBounds, scope) ?: return

        val intermediateSliceAddress =
            scope.calcOnState { memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) } }
        scope.makeSliceTypeLoad(
            slice,
            TvmCellDataBitArrayRead(offsetBits.extractToSizeSort()),
            intermediateSliceAddress
        ) { _ ->
            // hide the original [scope] from this closure
            @Suppress("NAME_SHADOWING", "UNUSED_VARIABLE")
            val scope = Unit

            val updatedSliceAddress =
                this.calcOnState { memory.allocConcrete(TvmSliceType).also { sliceCopy(intermediateSliceAddress, it) } }

            this.calcOnState { sliceMoveDataPtr(updatedSliceAddress, offsetBits.extractToSizeSort()) }
            this.makeSliceTypeLoad(
                intermediateSliceAddress,
                TvmCellDataBitArrayRead(sizeBits.extractToSizeSort()),
                updatedSliceAddress,
            ) { finalFromTlb ->
                val result = finalFromTlb?.expr ?: let {
                    val bits = slicePreloadDataBits(
                        updatedSliceAddress,
                        sizeBits.extractToSizeSort(),
                    ) ?: return@makeSliceTypeLoad

                    val cell = calcOnState { allocEmptyCell() }
                    builderStoreDataBits(cell, bits, sizeBits.extractToSizeSort())
                        ?: error("Cannot write $sizeBits bits to the empty builder")

                    calcOnState { allocSliceFromCell(cell) }
                }
                doWithState {
                    addOnStack(result, TvmSliceType)
                    newStmt(stmt.nextStmt())
                }
            }
        }
    }

    fun visitCellBuildInst(
        scope: TvmStepScopeManager,
        stmt: TvmCellBuildInst
    ) {
        when (stmt) {
            is TvmCellBuildEndcInst -> visitEndCellInst(scope, stmt)
            is TvmCellBuildNewcInst -> visitNewCellInst(scope, stmt)
            is TvmCellBuildStuInst -> visitStoreIntInst(scope, stmt, stmt.c + 1, false)
            is TvmCellBuildSturInst -> {
                doSwap(scope)
                visitStoreIntInst(scope, stmt, stmt.c + 1, false)
            }

            is TvmCellBuildStiInst -> visitStoreIntInst(scope, stmt, stmt.c + 1, true)
            is TvmCellBuildStuxInst -> visitStoreIntXInst(scope, stmt, false)
            is TvmCellBuildStixInst -> visitStoreIntXInst(scope, stmt, true)
            is TvmCellBuildStzeroesInst -> visitStoreZeroesInst(scope, stmt)
            is TvmCellBuildBbitsInst -> visitBuilderBitsInst(scope, stmt)
            is TvmCellBuildStsliceInst -> {
                scope.consumeDefaultGas(stmt)

                scope.doStoreSlice(stmt, sliceExtractor = StackSliceExtractor, quiet = false)
            }

            is TvmCellBuildStsliceAltInst -> {
                scope.consumeDefaultGas(stmt)

                scope.doStoreSlice(stmt, sliceExtractor = StackSliceExtractor, quiet = false)
            }

            is TvmCellBuildStslicerInst -> {
                scope.consumeDefaultGas(stmt)

                doSwap(scope)
                scope.doStoreSlice(stmt, sliceExtractor = StackSliceExtractor, quiet = false)
            }

            is TvmCellBuildStsliceqInst -> {
                scope.consumeDefaultGas(stmt)

                scope.doStoreSlice(stmt, sliceExtractor = StackSliceExtractor, quiet = true)
            }

            is TvmCellBuildStslicerqInst -> {
                scope.consumeDefaultGas(stmt)

                doSwap(scope)
                scope.doStoreSlice(stmt, sliceExtractor = StackSliceExtractor, quiet = true)
            }

            is TvmCellBuildStsliceconstInst -> {
                scope.consumeDefaultGas(stmt)

                val constSlice = stmt.s

                // `sss` consists of `0 <= x <= 3` references and up to `8y+2` data bits, with `0 <= y <= 7`
                check(constSlice.refs.size <= 3 && constSlice.data.bits.length <= 8 * 7 + 2) {
                    "Unexpected const slice: $constSlice"
                }

                val slice = scope.calcOnState { allocSliceFromCell(stmt.s) }
                scope.doStoreSlice(stmt, sliceExtractor = EmbeddedSliceExtractor(slice), quiet = false)
            }

            is TvmCellBuildStbInst -> {
                scope.consumeDefaultGas(stmt)

                scope.doStoreBuilder(stmt, quiet = false)
            }

            is TvmCellBuildStbrInst -> {
                scope.consumeDefaultGas(stmt)

                doSwap(scope)
                scope.doStoreBuilder(stmt, quiet = false)
            }

            is TvmCellBuildStbqInst -> {
                scope.consumeDefaultGas(stmt)

                scope.doStoreBuilder(stmt, quiet = true)
            }

            is TvmCellBuildStbrqInst -> {
                scope.consumeDefaultGas(stmt)

                doSwap(scope)
                scope.doStoreBuilder(stmt, quiet = true)
            }

            is TvmCellBuildStrefInst -> visitStoreRefInst(scope, stmt, quiet = false)
            is TvmCellBuildStrefqInst -> visitStoreRefInst(scope, stmt, quiet = true)
            is TvmCellBuildStrefAltInst -> visitStoreRefInst(scope, stmt, quiet = false)
            is TvmCellBuildStrefrInst -> {
                doSwap(scope)
                visitStoreRefInst(scope, stmt, quiet = false)
            }

            is TvmCellBuildStrefrqInst -> {
                doSwap(scope)
                visitStoreRefInst(scope, stmt, quiet = true)
            }

            is TvmCellBuildBdepthInst -> visitDepthInst(scope, stmt, operandType = TvmBuilderType)

            is TvmCellBuildStbrefrInst -> {
                scope.consumeDefaultGas(stmt)

                doEndc(scope)
                doSwap(scope)

                visitStoreRefInst(scope, stmt, quiet = false)
            }

            else -> TODO("$stmt")
        }
    }

    private fun visitLoadRefInst(scope: TvmStepScopeManager, stmt: TvmCellParseLdrefInst) {
        scope.consumeDefaultGas(stmt)

        val slice = scope.calcOnState { stack.takeLastSlice() }
        if (slice == null) {
            scope.doWithState(ctx.throwTypeCheckError)
            return
        }

        val updatedSlice = scope.calcOnState {
            memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
        }

        sliceLoadRefTlb(scope, slice, updatedSlice) { value ->
            doWithState {
                addOnStack(value, TvmCellType)
                addOnStack(updatedSlice, TvmSliceType)

                newStmt(stmt.nextStmt())
            }
        }
    }


    private fun visitLoadRefRtosInst(scope: TvmStepScopeManager, stmt: TvmCellParseLdrefrtosInst) {
        scope.doWithState {
            consumeGas(118) // assume the first time we load cell
            // TODO implement proper Complex gas semantics
        }
        val slice = scope.calcOnState { stack.takeLastSlice() }
        if (slice == null) {
            scope.doWithState(ctx.throwTypeCheckError)
            return
        }

        val updatedSlice = scope.calcOnState {
            memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
        }

        sliceLoadRefTlb(scope, slice, updatedSlice) { value ->
            // hide the original [scope] from this closure
            @Suppress("NAME_SHADOWING", "UNUSED_VARIABLE")
            val scope = Unit

            doWithState {
                addOnStack(value, TvmCellType)
                addOnStack(updatedSlice, TvmSliceType)
            }
            doSwap(this)
            doCellToSlice(this, stmt)
        }
    }

    private fun doPreloadRef(scope: TvmStepScopeManager, stmt: TvmCellParseInst, refIdx: UExpr<TvmSizeSort>) =
        with(ctx) {
            scope.consumeDefaultGas(stmt)

            val notOutOfRangeExpr = mkAnd(
                mkBvSignedLessOrEqualExpr(zeroSizeExpr, refIdx),
                mkBvSignedLessOrEqualExpr(refIdx, mkSizeExpr(3)),
            )
            checkOutOfRange(notOutOfRangeExpr, scope) ?: return@with

            val slice = scope.calcOnState { stack.takeLastSlice() }
                ?: return scope.doWithState(throwTypeCheckError)

            val ref = scope.slicePreloadRef(slice, refIdx) ?: return

            scope.doWithState {
                scope.addOnStack(ref, TvmCellType)

                newStmt(stmt.nextStmt())
            }
        }

    private fun visitEndSliceInst(scope: TvmStepScopeManager, stmt: TvmCellParseEndsInst) {
        scope.doWithState { consumeGas(18) } // complex gas

        with(ctx) {
            val slice = scope.calcOnState { stack.takeLastSlice() }
            if (slice == null) {
                scope.doWithState(throwTypeCheckError)
                return
            }

            scope.assertEndOfCell(slice) ?: return

            val cell = scope.calcOnState { memory.readField(slice, sliceCellField, addressSort) }
            val dataPos = scope.calcOnState { memory.readField(slice, sliceDataPosField, sizeSort) }
            val refsPos = scope.calcOnState { memory.readField(slice, sliceRefPosField, sizeSort) }

            checkCellDataUnderflow(scope, cell, maxSize = dataPos) ?: return
            checkCellRefsUnderflow(scope, cell, maxSize = refsPos) ?: return

            scope.doWithState {
                newStmt(stmt.nextStmt())
            }
        }
    }

    private fun TvmState.visitLoadDataInstEnd(
        stmt: TvmCellParseInst,
        preload: Boolean,
        quiet: Boolean,
        updatedSliceAddress: UConcreteHeapRef,
    ) {
        if (!preload) {
            addOnStack(updatedSliceAddress, TvmSliceType)
        }

        if (quiet) {
            addOnStack(ctx.oneValue, TvmIntegerType)
        }

        newStmt(stmt.nextStmt())
    }

    private fun visitLoadIntInst(
        scope: TvmStepScopeManager,
        stmt: TvmCellParseInst,
        sizeBits: Int,
        isSigned: Boolean,
        preload: Boolean,
        quiet: Boolean,
    ): Unit = with(ctx) {
        scope.consumeDefaultGas(stmt)

        check(sizeBits in 1..256) { "Unexpected bits size $sizeBits" }

        val slice = scope.calcOnState { stack.takeLastSlice() }
        if (slice == null) {
            scope.doWithState(throwTypeCheckError)
            return
        }

        val updatedSliceAddress = scope.calcOnState { memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) } }

        sliceLoadIntTlb(scope, slice, updatedSliceAddress, sizeBits, isSigned) { value ->
            doWithState {
                addOnStack(value, TvmIntegerType)
                visitLoadDataInstEnd(stmt, preload, quiet, updatedSliceAddress)
            }
        }
    }

    private fun visitLoadIntXInst(
        scope: TvmStepScopeManager,
        stmt: TvmCellParseInst,
        isSigned: Boolean,
        preload: Boolean,
        quiet: Boolean,
    ): Unit = with(ctx) {
        scope.consumeDefaultGas(stmt)

        val sizeBits = scope.takeLastIntOrThrowTypeError() ?: return
        val slice = scope.calcOnState { stack.takeLastSlice() }
        if (slice == null) {
            scope.doWithState(throwTypeCheckError)
            return
        }

        val bitsUpperBound = if (isSigned) TvmContext.INT_BITS else TvmContext.INT_BITS - 1u
        val notOutOfRangeExpr = mkAnd(
            mkBvSignedLessOrEqualExpr(zeroValue, sizeBits),
            mkBvSignedLessOrEqualExpr(sizeBits, bitsUpperBound.toInt().toBv257())
        )
        checkOutOfRange(notOutOfRangeExpr, scope) ?: return

        val updatedSliceAddress = scope.calcOnState { memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) } }
        scope.makeSliceTypeLoad(
            slice,
            TvmCellDataIntegerRead(sizeBits.extractToSizeSort(), isSigned, Endian.BigEndian),
            updatedSliceAddress
        ) { valueFromTlb ->
            val result = valueFromTlb?.expr ?: let {
                slicePreloadInt(slice, sizeBits, isSigned)
                    ?: return@makeSliceTypeLoad
            }

            doWithState {
                sliceMoveDataPtr(updatedSliceAddress, sizeBits.extractToSizeSort())

                addOnStack(result, TvmIntegerType)
                visitLoadDataInstEnd(stmt, preload, quiet, updatedSliceAddress)
            }
        }
    }

    private fun visitLoadIntLEInst(
        scope: TvmStepScopeManager,
        stmt: TvmCellParseInst,
        sizeBytes: Int,
        isSigned: Boolean,
        preload: Boolean,
        quiet: Boolean,
    ) = with(ctx) {
        scope.consumeDefaultGas(stmt)

        val slice = scope.calcOnState { stack.takeLastSlice() }
        if (slice == null) {
            scope.doWithState(throwTypeCheckError)
            return
        }

        val sizeBits = sizeBytes * Byte.SIZE_BITS

        val updatedSliceAddress = scope.calcOnState { memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) } }
        scope.makeSliceTypeLoad(
            slice,
            TvmCellDataIntegerRead(mkBv(sizeBits), isSigned, Endian.LittleEndian),
            updatedSliceAddress
        ) {

            // TODO: process value from TL-B (or not?). For now, we didn't encounter TL-B for little-endian

            val value = slicePreloadDataBits(slice, sizeBits) ?: return@makeSliceTypeLoad

            val bytes = List(sizeBytes) { byteIdx ->
                val high = sizeBits - 1 - byteIdx * Byte.SIZE_BITS
                val low = sizeBits - (byteIdx + 1) * Byte.SIZE_BITS

                mkBvExtractExpr(high, low, value)
            }
            val res = bytes.reduce { acc, el ->
                mkBvConcatExpr(el, acc)
            }

            val extendedRes = if (isSigned) {
                res.signedExtendToInteger()
            } else {
                res.unsignedExtendToInteger()
            }

            doWithState {
                sliceMoveDataPtr(updatedSliceAddress, mkSizeExpr(sizeBits))

                addOnStack(extendedRes, TvmIntegerType)
                visitLoadDataInstEnd(stmt, preload, quiet, updatedSliceAddress)
            }
        }
    }

    private fun visitLoadSliceInst(
        scope: TvmStepScopeManager,
        stmt: TvmCellParseInst,
        sizeBits: Int,
        preload: Boolean,
        quiet: Boolean,
    ) = with(ctx) {
        scope.consumeDefaultGas(stmt)

        check(sizeBits in 1..256) { "Unexpected bits size $sizeBits" }

        val slice = scope.calcOnState { stack.takeLastSlice() }
        if (slice == null) {
            scope.doWithState(throwTypeCheckError)
            return
        }

        val updatedSliceAddress = scope.calcOnState { memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) } }
        scope.makeSliceTypeLoad(
            slice,
            TvmCellDataBitArrayRead(mkBv(sizeBits)),
            updatedSliceAddress,
        ) { valueFromTlb ->
            val result = valueFromTlb?.expr ?: let {
                val bits = slicePreloadDataBits(slice, sizeBits) ?: return@makeSliceTypeLoad
                val cell = calcOnState { allocEmptyCell() }

                builderStoreDataBits(cell, bits, mkSizeExpr(bits.sort.sizeBits.toInt())) ?: return@makeSliceTypeLoad

                calcOnState { allocSliceFromCell(cell) }
            }

            // TODO: tlb for result

            doWithState {
                sliceMoveDataPtr(updatedSliceAddress, mkSizeExpr(sizeBits))

                addOnStack(result, TvmSliceType)
                visitLoadDataInstEnd(stmt, preload, quiet, updatedSliceAddress)
            }
        }
    }

    private fun visitLoadSliceXInst(
        scope: TvmStepScopeManager,
        stmt: TvmCellParseInst,
        preload: Boolean,
        quiet: Boolean,
        pushResultOnStack: Boolean = true,
        doAfterThisStep: TvmStepScopeManager.() -> Unit
    ): Unit = with(ctx) {
        scope.consumeDefaultGas(stmt)

        val sizeBits = scope.takeLastIntOrThrowTypeError() ?: return
        val slice = scope.calcOnState { stack.takeLastSlice() }
        if (slice == null) {
            scope.doWithState(throwTypeCheckError)
            return
        }

        val updatedSliceAddress = scope.calcOnState { memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) } }
        scope.makeSliceTypeLoad(
            slice,
            TvmCellDataBitArrayRead(sizeBits.extractToSizeSort()),
            updatedSliceAddress
        ) { valueFromTlb ->
            if (pushResultOnStack) {
                val result = valueFromTlb?.expr ?: let {
                    val notOutOfRangeExpr = unsignedIntegerFitsBits(sizeBits, bits = 10u)
                    checkOutOfRange(notOutOfRangeExpr, this)
                        ?: return@makeSliceTypeLoad

                    val quietBlock: (TvmState.() -> Unit)? = if (!quiet) null else fun TvmState.() {
                        addOnStack(slice, TvmSliceType)
                        stack.addInt(zeroValue)
                        newStmt(stmt.nextStmt())
                    }
                    val bits = slicePreloadDataBits(
                        slice,
                        sizeBits.extractToSizeSort(),
                        quietBlock = quietBlock
                    ) ?: return@makeSliceTypeLoad

                    val cell = calcOnState { allocEmptyCell() }
                    builderStoreDataBits(cell, bits, sizeBits.extractToSizeSort())
                        ?: error("Cannot write $sizeBits bits to the empty builder")

                    calcOnState { allocSliceFromCell(cell) }
                }

                doWithState {
                    addOnStack(result, TvmSliceType)
                }
            }

            doWithState {
                sliceMoveDataPtr(updatedSliceAddress, sizeBits.extractToSizeSort())

                visitLoadDataInstEnd(stmt, preload, quiet, updatedSliceAddress)
            }

            doAfterThisStep()
        }
    }

    private fun visitCutLastInst(scope: TvmStepScopeManager, stmt: TvmCellParseInst, skipAllRefs: Boolean) = with(ctx) {
        scope.consumeDefaultGas(stmt)

        val refsToRemain = if (skipAllRefs) {
            zeroSizeExpr
        } else {
            scope.takeLastIntOrThrowTypeError()?.extractToSizeSort()
                ?: return
        }

        val bitsToRemain = scope.takeLastIntOrThrowTypeError()?.extractToSizeSort()
            ?: return

        val slice = scope.calcOnState { stack.takeLastSlice() }
        if (slice == null) {
            scope.doWithState(throwTypeCheckError)
            return
        }

        val cell = scope.calcOnState { memory.readField(slice, sliceCellField, addressSort) }

        val cellDataLength = scope.calcOnState { memory.readField(cell, cellDataLengthField, sizeSort) }
        scope.assertDataLengthConstraintWithoutError(
            cellDataLength,
            unsatBlock = { error("Cannot ensure correctness for data length in cell $cell") }
        ) ?: return

        val cellRefsLength = scope.calcOnState { memory.readField(cell, cellRefsLengthField, sizeSort) }
        scope.assertRefsLengthConstraintWithoutError(
            cellRefsLength,
            unsatBlock = { error("Cannot ensure correctness for number of refs in cell $cell") }
        ) ?: return

        val dataPos = scope.calcOnState { memory.readField(slice, sliceDataPosField, sizeSort) }
        val refsPos = scope.calcOnState { memory.readField(slice, sliceRefPosField, sizeSort) }

        val requiredBitsInCell = mkSizeAddExpr(bitsToRemain, dataPos)
        checkCellDataUnderflow(scope, cell, minSize = requiredBitsInCell)
            ?: return

        if (!skipAllRefs) {
            val requiredRefsInCell = mkSizeAddExpr(refsToRemain, refsPos)
            checkCellRefsUnderflow(scope, cell, minSize = requiredRefsInCell)
                ?: return
        }

        val allExceptRemainingBits = mkSizeSubExpr(cellDataLength, bitsToRemain)
        val bitsToSkip = mkSizeSubExpr(allExceptRemainingBits, dataPos)

        val allExceptRemainingRefs = mkSizeSubExpr(cellRefsLength, refsToRemain)
        val refsToSkip = mkSizeSubExpr(allExceptRemainingRefs, refsPos)

        scope.doWithState {
            val sliceToReturn = memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
            sliceMoveDataPtr(sliceToReturn, bitsToSkip)
            sliceMoveRefPtr(sliceToReturn, refsToSkip)

            addOnStack(sliceToReturn, TvmSliceType)
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitSkipLastInst(scope: TvmStepScopeManager, stmt: TvmCellParseInst, keepAllRefs: Boolean) =
        with(ctx) {
            scope.consumeDefaultGas(stmt)

            val refsToCut = if (keepAllRefs) {
                zeroSizeExpr
            } else {
                scope.takeLastIntOrThrowTypeError()?.extractToSizeSort()
                    ?: return
            }

            val bitsToCut = scope.takeLastIntOrThrowTypeError()?.extractToSizeSort()
                ?: return

            val slice = scope.calcOnState { stack.takeLastSlice() }
            if (slice == null) {
                scope.doWithState(throwTypeCheckError)
                return
            }

            val cell = scope.calcOnState { memory.readField(slice, sliceCellField, addressSort) }

            val cellDataLength = scope.calcOnState { memory.readField(cell, cellDataLengthField, sizeSort) }
            scope.assertDataLengthConstraintWithoutError(
                cellDataLength,
                unsatBlock = { error("Cannot ensure correctness for data length in cell $cell") }
            ) ?: return

            val cellRefsLength = scope.calcOnState { memory.readField(cell, cellRefsLengthField, sizeSort) }
            scope.assertRefsLengthConstraintWithoutError(
                cellRefsLength,
                unsatBlock = { error("Cannot ensure correctness for number of refs in cell $cell") }
            ) ?: return

            val dataPos = scope.calcOnState { memory.readField(slice, sliceDataPosField, sizeSort) }
            val refsPos = scope.calcOnState { memory.readField(slice, sliceRefPosField, sizeSort) }

            val requiredBitsInCell = mkSizeAddExpr(bitsToCut, dataPos)
            checkCellDataUnderflow(scope, cell, minSize = requiredBitsInCell)
                ?: return

            if (!keepAllRefs) {
                val requiredRefsInCell = mkSizeAddExpr(refsToCut, refsPos)
                checkCellRefsUnderflow(scope, cell, minSize = requiredRefsInCell)
                    ?: return
            }

            val cutCell = scope.calcOnState {
                memory.allocConcrete(TvmDataCellType)
            }.also {
                scope.builderCopy(cell, it)
            }

            scope.doWithState {
                val cutCellDataLength = mkSizeSubExpr(cellDataLength, bitsToCut)
                val cutCellRefsLength = mkSizeSubExpr(cellRefsLength, refsToCut)

                memory.writeField(cutCell, cellDataLengthField, sizeSort, cutCellDataLength, guard = trueExpr)
                memory.writeField(cutCell, cellRefsLengthField, sizeSort, cutCellRefsLength, guard = trueExpr)

                val cutSlice = allocSliceFromCell(cutCell)

                addOnStack(cutSlice, TvmSliceType)
                newStmt(stmt.nextStmt())
            }
        }

    private fun visitBeginsInst(
        scope: TvmStepScopeManager,
        stmt: TvmCellParseInst,
        s: TvmCell,
        quiet: Boolean
    ) {
        doBeginsInst(scope, stmt, quiet) {
            check(s.refs.isEmpty()) {
                "Unexpected refs in $stmt"
            }

            scope.calcOnState { allocSliceFromCell(s) }
        }
    }

    private fun visitBeginsXInst(
        scope: TvmStepScopeManager,
        stmt: TvmCellParseInst,
        quiet: Boolean
    ) = doBeginsInst(scope, stmt, quiet) {
        scope.calcOnState { stack.takeLastSlice() }
    }

    private fun doBeginsInst(
        scope: TvmStepScopeManager,
        stmt: TvmCellParseInst,
        quiet: Boolean,
        prefixSliceLoader: () -> UHeapRef?
    ) = with(ctx) {
        scope.consumeDefaultGas(stmt)

        val prefixSlice = prefixSliceLoader()
        if (prefixSlice == null) {
            scope.doWithState(throwTypeCheckError)
            return
        }

        val slice = scope.calcOnState { stack.takeLastSlice() }
        if (slice == null) {
            scope.doWithState(throwTypeCheckError)
            return
        }

        val remainingPrefixBitsLength = scope.calcOnState { getSliceRemainingBitsCount(prefixSlice) }
        val prefixBits = scope.slicePreloadDataBits(prefixSlice, remainingPrefixBitsLength)
            ?: return@with


        val remainingDataBitsLength = scope.calcOnState { getSliceRemainingBitsCount(slice) }
        val dataBits = scope.slicePreloadDataBits(slice, remainingDataBitsLength)
            ?: return@with

        val quietBlock: TvmState.() -> Unit = {
            addOnStack(slice, TvmSliceType)
            stack.addInt(zeroValue)
            newStmt(stmt.nextStmt())
        }
        val quietBlockOrNull = if (quiet) quietBlock else null

        val dataCell = scope.calcOnState { memory.readField(slice, sliceCellField, addressSort) }
        val cellDataLength = scope.calcOnState { memory.readField(dataCell, cellDataLengthField, sizeSort) }
        scope.assertDataLengthConstraintWithoutError(
            cellDataLength,
            unsatBlock = { error("Cannot ensure correctness for data length in cell $dataCell") }
        ) ?: return

        val dataPos = scope.calcOnState { memory.readField(slice, sliceDataPosField, sizeSort) }
        val requiredBitsInDataCell = mkSizeAddExpr(dataPos, remainingPrefixBitsLength)

        // Check that we have no less than prefix length bits in the data cell
        checkCellDataUnderflow(scope, dataCell, minSize = requiredBitsInDataCell, quietBlock = quietBlockOrNull)
            ?: return@with

        // xxxxxxxxxxxxx[prefix]
        // yyyyyy[prefix|suffix]
        val prefixShift = mkBvSubExpr(mkSizeExpr(MAX_DATA_LENGTH), remainingPrefixBitsLength)
            .zeroExtendToSort(cellDataSort)
        val shiftedPrefix = mkBvShiftLeftExpr(prefixBits, prefixShift)

        val suffixShift = mkSizeSubExpr(remainingDataBitsLength, remainingPrefixBitsLength)
            .zeroExtendToSort(cellDataSort)
        // Firstly, shift data bits right to remove suffix and get 00000000000yyyyyyyy[prefix]
        val dataWithoutSuffix = mkBvLogicalShiftRightExpr(dataBits, suffixShift)
        // Then, shift it left to get only prefix [prefix]00000000000000
        val shiftedData = mkBvShiftLeftExpr(dataWithoutSuffix, prefixShift)

        scope.fork(
            shiftedPrefix eq shiftedData,
            falseStateIsExceptional = !quiet,
            blockOnFalseState = {
                quietBlockOrNull?.invoke(this)
                    ?: throwStructuralCellUnderflowError(this)
            }
        ) ?: return@with

        val suffixSlice = scope.calcOnState {
            memory.allocConcrete(TvmSliceType).also { sliceCopy(slice, it) }
        }

        scope.doWithState {
            sliceMoveDataPtr(suffixSlice, remainingPrefixBitsLength)
            addOnStack(suffixSlice, TvmSliceType)

            if (quiet) {
                stack.addInt(minusOneValue)
            }

            newStmt(stmt.nextStmt())
        }
    }

    private fun doCellToSlice(
        scope: TvmStepScopeManager,
        stmt: TvmCellParseInst
    ) {
        val cell = scope.takeLastCell()
        if (cell == null) {
            scope.doWithState(ctx.throwTypeCheckError)
            return
        }

        scope.assertDataCellType(cell)
            ?: return

        val slice = scope.calcOnState { allocSliceFromCell(cell) }

        scope.makeCellToSlice(cell, slice) {
            doWithState {
                addOnStack(slice, TvmSliceType)
                newStmt(stmt.nextStmt())
            }
        }
    }

    private fun visitCellToSliceInst(
        scope: TvmStepScopeManager,
        stmt: TvmCellParseCtosInst
    ) {
        /**
         * todo: Transforming a cell into a slice costs 100 gas units if the cell is loading
         * for the first time and 25 for subsequent loads during the same transaction
         * */
        scope.doWithState { consumeGas(118) }

        doCellToSlice(scope, stmt)
    }

    private fun visitExoticCellToSliceInst(
        scope: TvmStepScopeManager,
        stmt: TvmCellParseXctosInst
    ) {
        scope.consumeDefaultGas(stmt)

        // TODO: Exotic cells are not supported, so we handle this instruction as CTOS
        doCellToSlice(scope, stmt)

        scope.doWithStateCtx {
            stack.addInt(falseValue)
        }
    }

    private fun visitSizeRefsInst(
        scope: TvmStepScopeManager,
        stmt: TvmCellParseSrefsInst
    ) {
        scope.consumeDefaultGas(stmt)

        scope.doWithStateCtx {
            val slice = stack.takeLastSlice()
            if (slice == null) {
                throwTypeCheckError(this)
                return@doWithStateCtx
            }
            val result = getSliceRemainingRefsCount(slice)

            stack.addInt(result.signedExtendToInteger())
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitSizeBitsInst(scope: TvmStepScopeManager, stmt: TvmCellParseSbitsInst) {
        scope.consumeDefaultGas(stmt)

        scope.doWithStateCtx {
            val slice = stack.takeLastSlice()
            if (slice == null) {
                throwTypeCheckError(this)
                return@doWithStateCtx
            }

            val result = getSliceRemainingBitsCount(slice)

            stack.addInt(result.signedExtendToInteger())
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitSizeBitRefsInst(scope: TvmStepScopeManager, stmt: TvmCellParseSbitrefsInst) {
        scope.consumeDefaultGas(stmt)

        scope.doWithStateCtx {
            val slice = stack.takeLastSlice()
            if (slice == null) {
                throwTypeCheckError(this)
                return@doWithStateCtx
            }
            val sizeBits = getSliceRemainingBitsCount(slice)
            val sizeRefs = getSliceRemainingRefsCount(slice)

            stack.addInt(sizeBits.signedExtendToInteger())
            stack.addInt(sizeRefs.signedExtendToInteger())
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitDepthInst(
        scope: TvmStepScopeManager,
        stmt: TvmInst,
        operandType: TvmRealReferenceType,
    ) = with(ctx) {
        scope.consumeDefaultGas(stmt)

        scope.doWithStateCtx {
            val ref = stack.takeLastRef(operandType)
                ?: run {
                    // this operation is safe only for cells
                    if (operandType is TvmCellType) {
                        stack.addInt(zeroValue)
                        newStmt(stmt.nextStmt())
                    } else {
                        throwTypeCheckError(this)
                    }

                    return@doWithStateCtx
                }

            val depth = addressToDepth[ref] ?: run {
                makeSymbolicPrimitive(ctx.int257sort).also {
                    addressToDepth = addressToDepth.put(ref, it)
                    scope.assert(
                        mkBvSignedLessOrEqualExpr(zeroValue, it),
                        unsatBlock = {
                            error("Cannot make the depth not negative")
                        }
                    ) ?: return@doWithStateCtx
                }
            }

            stack.addInt(depth)
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitStoreIntInst(scope: TvmStepScopeManager, stmt: TvmInst, bits: Int, isSigned: Boolean) = with(ctx) {
        scope.consumeDefaultGas(stmt)

        val builder = scope.calcOnState { stack.takeLastBuilder() }
        if (builder == null) {
            scope.doWithState(throwTypeCheckError)
            return
        }

        val intValue = scope.takeLastIntOrThrowTypeError() ?: return

        val updatedBuilder = scope.calcOnState {
            memory.allocConcrete(TvmBuilderType).also { builderCopyFromBuilder(builder, it) }
        }

        val notOutOfRangeExpr = if (isSigned) {
            signedIntegerFitsBits(intValue, bits.toUInt())
        } else {
            unsignedIntegerFitsBits(intValue, bits.toUInt())
        }
        checkOutOfRange(notOutOfRangeExpr, scope) ?: return

        builderStoreIntTlb(
            scope,
            builder,
            updatedBuilder,
            intValue,
            mkSizeExpr(bits),
            isSigned,
            Endian.BigEndian
        ) ?: return@with

        scope.doWithState {
            addOnStack(updatedBuilder, TvmBuilderType)
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitStoreIntXInst(scope: TvmStepScopeManager, stmt: TvmInst, isSigned: Boolean) = with(ctx) {
        scope.consumeDefaultGas(stmt)

        val bits = scope.takeLastIntOrThrowTypeError() ?: return
        val builder = scope.calcOnState { stack.takeLastBuilder() }
        if (builder == null) {
            scope.doWithState(throwTypeCheckError)
            return
        }

        val updatedBuilder = scope.calcOnState {
            memory.allocConcrete(TvmBuilderType).also { builderCopyFromBuilder(builder, it) }
        }
        val bitsUpperBound = if (isSigned) TvmContext.INT_BITS else TvmContext.INT_BITS - 1u
        val bitsNotOutOfRangeExpr = mkAnd(
            mkBvSignedLessOrEqualExpr(zeroValue, bits),
            mkBvSignedLessOrEqualExpr(bits, bitsUpperBound.toInt().toBv257()),
        )

        checkOutOfRange(bitsNotOutOfRangeExpr, scope) ?: return

        val intValue = scope.takeLastIntOrThrowTypeError() ?: return

        val valueNotOutOfRangeExpr = if (isSigned) {
            signedIntegerFitsBits(intValue, bits)
        } else {
            unsignedIntegerFitsBits(intValue, bits)
        }
        checkOutOfRange(valueNotOutOfRangeExpr, scope) ?: return

        builderStoreIntTlb(
            scope,
            builder,
            updatedBuilder,
            intValue,
            bits.extractToSizeSort(),
            isSigned,
            Endian.BigEndian
        ) ?: return@with

        scope.doWithState {
            addOnStack(updatedBuilder, TvmBuilderType)
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitStoreZeroesInst(scope: TvmStepScopeManager, stmt: TvmCellBuildStzeroesInst) = with(ctx) {
        scope.consumeDefaultGas(stmt)

        val zeroesToStore = scope.takeLastIntOrThrowTypeError()
            ?: return@with

        checkOutOfRange(zeroesToStore, scope, min = 0, max = MAX_DATA_LENGTH)
            ?: return@with

        val builder = scope.calcOnState { stack.takeLastBuilder() }
            ?: return scope.doWithState(throwTypeCheckError)
        val updatedBuilder = scope.calcOnState {
            memory.allocConcrete(TvmBuilderType).also { builderCopyFromBuilder(builder, it) }
        }

        scope.builderStoreInt(updatedBuilder, zeroValue, zeroesToStore, isSigned = false)

        scope.doWithState {
            addOnStack(updatedBuilder, TvmBuilderType)
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitBuilderBitsInst(scope: TvmStepScopeManager, stmt: TvmCellBuildBbitsInst) = with(ctx) {
        scope.consumeDefaultGas(stmt)

        val builder = scope.calcOnState { stack.takeLastBuilder() }
            ?: return scope.doWithState(ctx.throwTypeCheckError)

        scope.doWithState {
            val dataLength = memory.readField(builder, cellDataLengthField, sizeSort)

            stack.addInt(dataLength.signedExtendToInteger())
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitNewCellInst(scope: TvmStepScopeManager, stmt: TvmCellBuildNewcInst) {
        scope.consumeDefaultGas(stmt)

        scope.doWithStateCtx {
            val builder = emptyRefValue.emptyBuilder

            scope.addOnStack(builder, TvmBuilderType)
            newStmt(stmt.nextStmt())
        }
    }

    private fun visitEndCellInst(scope: TvmStepScopeManager, stmt: TvmCellBuildEndcInst) {
        scope.consumeDefaultGas(stmt)

        doEndc(scope)

        scope.doWithState {
            newStmt(stmt.nextStmt())
        }
    }

    private fun doEndc(scope: TvmStepScopeManager) {
        val builder = scope.calcOnState { stack.takeLastBuilder() }
        if (builder == null) {
            scope.doWithState(ctx.throwTypeCheckError)
            return
        }

        val cell = scope.builderToCell(builder)

        scope.doWithState {
            addOnStack(cell, TvmCellType)
        }
    }

    private fun visitStoreRefInst(scope: TvmStepScopeManager, stmt: TvmCellBuildInst, quiet: Boolean) {
        scope.consumeDefaultGas(stmt)

        val builder = scope.calcOnState { stack.takeLastBuilder() }
        if (builder == null) {
            scope.doWithState(ctx.throwTypeCheckError)
            return
        }

        val cell = scope.takeLastCell()
        if (cell == null) {
            scope.doWithState(ctx.throwTypeCheckError)
            return
        }

        with(ctx) {
            val builderRefsLength = scope.calcOnState { memory.readField(builder, cellRefsLengthField, ctx.sizeSort) }
            val canWriteRefConstraint = mkSizeLtExpr(builderRefsLength, maxRefsLengthSizeExpr)
            val quietBlock: (TvmState.() -> Unit)? = if (!quiet) null else fun TvmState.() {
                addOnStack(cell, TvmCellType)
                addOnStack(builder, TvmBuilderType)
                stack.addInt(minusOneValue)

                newStmt(stmt.nextStmt())
            }
            checkCellOverflow(canWriteRefConstraint, scope, quietBlock)
                ?: return

            scope.doWithState {
                val updatedBuilder = memory.allocConcrete(TvmBuilderType).also { builderCopyFromBuilder(builder, it) }
                builderStoreNextRef(updatedBuilder, cell)

                // In this case, new builder has the same data structure as the old builder (only refs are changed).
                // Thus, we just copy tlb structure builder.
                copyTlbToNewBuilder(builder, updatedBuilder)

                addOnStack(updatedBuilder, TvmBuilderType)
                if (quiet) {
                    addOnStack(zeroValue, TvmIntegerType)
                }

                newStmt(stmt.nextStmt())
            }
        }
    }

    private fun TvmStepScopeManager.doStoreSlice(
        stmt: TvmCellBuildInst,
        sliceExtractor: SliceExtractor,
        quiet: Boolean
    ) = with(ctx) {
        val builder = calcOnState { stack.takeLastBuilder() }
        if (builder == null) {
            doWithState(throwTypeCheckError)
            return
        }

        val slice = sliceExtractor.slice(this@doStoreSlice)
        if (slice == null) {
            doWithState(throwTypeCheckError)
            return
        }

        val resultBuilder =
            calcOnState { memory.allocConcrete(TvmBuilderType).also { builderCopyFromBuilder(builder, it) } }

        val quietBlock: (TvmState.() -> Unit)? = if (!quiet) null else fun TvmState.() {
            addOnStack(slice, TvmSliceType)
            addOnStack(builder, TvmBuilderType)
            stack.addInt(minusOneValue)

            newStmt(stmt.nextStmt())
        }

        builderStoreSliceTlb(this@doStoreSlice, builder, resultBuilder, slice)
            ?: return@with

        doWithState {
            addOnStack(resultBuilder, TvmBuilderType)
            if (quiet) {
                addOnStack(zeroValue, TvmIntegerType)
            }

            newStmt(stmt.nextStmt())
        }
    }

    private fun TvmStepScopeManager.doStoreBuilder(stmt: TvmCellBuildInst, quiet: Boolean) = with(ctx) {
        val (toBuilder, fromBuilder) = calcOnState { stack.takeLastBuilder() to stack.takeLastBuilder() }
        if (toBuilder == null || fromBuilder == null) {
            doWithState(throwTypeCheckError)
            return
        }

        val resultBuilder =
            calcOnState { memory.allocConcrete(TvmBuilderType).also { builderCopyFromBuilder(toBuilder, it) } }

        val quietBlock: (TvmState.() -> Unit)? = if (!quiet) null else fun TvmState.() {
            addOnStack(fromBuilder, TvmBuilderType)
            addOnStack(toBuilder, TvmBuilderType)
            stack.addInt(minusOneValue)

            newStmt(stmt.nextStmt())
        }

        val fromBuilderSlice = calcOnState { allocSliceFromCell(fromBuilder) }
        builderStoreSlice(resultBuilder, fromBuilderSlice, quietBlock) ?: return

        doWithState {
            addOnStack(resultBuilder, TvmBuilderType)
            if (quiet) {
                addOnStack(zeroValue, TvmIntegerType)
            }

            newStmt(stmt.nextStmt())
        }
    }

    private sealed interface SliceExtractor {
        fun slice(scope: TvmStepScopeManager): UHeapRef?
    }

    private data class EmbeddedSliceExtractor(val ref: UHeapRef) : SliceExtractor {
        override fun slice(scope: TvmStepScopeManager): UHeapRef = ref
    }

    private data object StackSliceExtractor : SliceExtractor {
        override fun slice(scope: TvmStepScopeManager): UHeapRef? = scope.calcOnState { stack.takeLastSlice() }
    }
}
