package org.usvm.machine.types.memory.stack

import kotlinx.collections.immutable.PersistentList
import org.ton.TlbStructure
import org.usvm.isTrue
import org.usvm.machine.TvmContext
import org.usvm.machine.state.TvmState
import org.usvm.machine.state.TvmStructuralError
import org.usvm.machine.types.SizedCellDataTypeRead
import org.usvm.machine.types.TvmCellDataTypeReadValue
import org.usvm.machine.types.TvmCellMaybeConstructorBitRead
import org.usvm.machine.types.TvmReadingOutOfSwitchBounds
import org.usvm.machine.types.TvmReadingSwitchWithUnexpectedType
import org.usvm.machine.types.memory.generateGuardForSwitch
import org.usvm.machine.types.memory.stack.TlbStackFrame.GuardedResult
import org.usvm.machine.types.memory.readFromConstant
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGtExpr
import org.usvm.mkSizeLtExpr

data class SwitchTlbStackFrame(
    private val struct: TlbStructure.SwitchPrefix,
    override val path: PersistentList<Int>,
    override val leftTlbDepth: Int,
) : TlbStackFrame {
    init {
        check(struct.variants.size > 1) {
            "SwitchTlbStackFrame should be used only for switches with several variants"
        }
    }

    override fun <ReadResult : TvmCellDataTypeReadValue> step(
        state: TvmState,
        loadData: LimitedLoadData<ReadResult>
    ): List<GuardedResult<ReadResult>> = with(state.ctx) {

        val possibleVariants =
            state.dataCellInfoStorage.mapper.calculatedTlbLabelInfo.getPossibleSwitchVariants(struct, leftTlbDepth)

        // reading long tag with `load_maybe_ref` or `load_dict` (which is actually the same instruction)
        // means that something probably went wrong --- so we report it as an error
        val isBadMaybeRead = struct.switchSize > 1 && loadData.type is TvmCellMaybeConstructorBitRead

        if (loadData.type !is SizedCellDataTypeRead || isBadMaybeRead) {
            return@with listOf(
                GuardedResult(
                    trueExpr,
                    StepError(
                        TvmStructuralError(TvmReadingSwitchWithUnexpectedType(loadData.type), state.phase)
                    ),
                    value = null,
                )
            )
        }

        val readSize = loadData.type.sizeBits
        val switchSize = mkSizeExpr(struct.switchSize)

        val result = mutableListOf<GuardedResult<ReadResult>>(
            GuardedResult(
                mkSizeGtExpr(readSize, switchSize),
                StepError(
                    TvmStructuralError(TvmReadingOutOfSwitchBounds(loadData.type), state.phase)
                ),
                value = null,
            )
        )

        possibleVariants.forEachIndexed { idx, (key, variant) ->
            val guard = generateGuardForSwitch(struct, idx, possibleVariants, state, loadData.cellAddress, path)

            // full read of switch
            val stepResult = buildFrameForStructure(
                state.ctx,
                variant,
                path,
                leftTlbDepth,
            )?.let {
                NextFrame(it)
            } ?: EndOfStackFrame

            val value = loadData.type.readFromConstant(state, zeroSizeExpr, key)

            result.add(
                GuardedResult(
                    (readSize eq switchSize) and guard,
                    stepResult,
                    value,
                )
            )

            // partial read of switch
            result.add(
                GuardedResult(
                    mkSizeLtExpr(readSize, switchSize) and guard,
                    NextFrame(ConstTlbStackFrame(key, variant, readSize, path, leftTlbDepth)),
                    value,
                )
            )
        }

        result
    }

    override fun expandNewStackFrame(ctx: TvmContext): TlbStackFrame? = null

    override val isSkippable: Boolean = false

    override fun skipLabel(ctx: TvmContext): TlbStackFrame? = null

    override fun readInModel(
        read: TlbStack.ConcreteReadInfo
    ): Triple<String, TlbStack.ConcreteReadInfo, List<TlbStackFrame>> = with(read.resolver.state.ctx) {
        check(read.leftBits >= struct.switchSize)

        val state = read.resolver.state
        val model = read.resolver.model

        val possibleVariants = if (struct.variants.size > 1) {
            state.dataCellInfoStorage.mapper.calculatedTlbLabelInfo.getPossibleSwitchVariants(struct, leftTlbDepth)
        } else {
            // case of constant
            struct.variants
        }

        possibleVariants.forEachIndexed { idx, (key, variant) ->
            val guard = generateGuardForSwitch(struct, idx, possibleVariants, state, read.address, path)
            if (model.eval(guard).isTrue) {
                val further = buildFrameForStructure(this, variant, path, leftTlbDepth)
                val newReadInfo = TlbStack.ConcreteReadInfo(
                    read.address,
                    read.resolver,
                    read.leftBits - struct.switchSize,
                )
                return@with Triple(key, newReadInfo, further?.let { listOf(it) } ?: emptyList())
            }
        }

        error("At least one switch variant must be true in model")
    }
}
