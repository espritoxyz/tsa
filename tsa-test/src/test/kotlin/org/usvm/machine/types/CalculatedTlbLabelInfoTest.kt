package org.usvm.machine.types

import io.ksmt.expr.KInterpretedValue
import org.ton.TlbStructure
import org.ton.TvmInputInfo
import org.ton.TvmParameterInfo
import org.ton.examples.types.customVarInteger
import org.ton.examples.types.doubleCustomVarInteger
import org.ton.examples.types.intSwitchStructure
import org.ton.examples.types.longDataStructure
import org.ton.examples.types.maybeStructure
import org.ton.examples.types.prefixInt64Structure
import org.ton.examples.types.recursiveStructure
import org.ton.examples.types.recursiveWithRefStructure
import org.ton.examples.types.refAfterRecursiveStructure
import org.ton.examples.types.structureX
import org.ton.examples.types.structureY
import org.ton.examples.types.wrappedMsgStructure
import org.ton.test.utils.extractResource
import org.usvm.UExpr
import org.usvm.machine.BocAnalyzer
import org.usvm.machine.TvmComponents
import org.usvm.machine.TvmConcreteContractData
import org.usvm.machine.TvmConcreteGeneralData
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmMachine
import org.usvm.machine.TvmOptions
import org.usvm.machine.TvmSizeSort
import org.usvm.machine.intValue
import org.usvm.machine.interpreter.TvmInterpreter
import org.usvm.machine.state.generateSymbolicCell
import org.usvm.machine.types.dp.CalculatedTlbLabelInfo
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalculatedTlbLabelInfoTest {
    private val bytecodePath: String = "/counter.txt"
    private val bytecodeResourcePath = extractResource(bytecodePath)

    private val someCode = BocAnalyzer.loadContractFromBoc(bytecodeResourcePath)

    private val dummyComponents = TvmComponents(TvmOptions())
    private val ctx = TvmContext(TvmOptions(), dummyComponents)

    private val dummyInterpreter = TvmInterpreter(
        ctx,
        listOf(someCode),
        dummyComponents.typeSystem,
        TvmInputInfo(),
    )
    private val dummyState = dummyInterpreter.getInitialState(
        startContractId = 0,
        concreteGeneralData = TvmConcreteGeneralData(),
        concreteContractData = listOf(TvmConcreteContractData()),
        methodId = BigInteger.ZERO
    )
    private val cellDataFieldManager = dummyState.fieldManagers.cellDataFieldManager

    val info = CalculatedTlbLabelInfo(
        ctx,
        listOf(
            maybeStructure,
            prefixInt64Structure,
            intSwitchStructure,
            structureX,
            structureY,
            recursiveStructure,
            recursiveWithRefStructure,
            refAfterRecursiveStructure,
            longDataStructure,
            wrappedMsgStructure,
            customVarInteger,
            doubleCustomVarInteger,
        )
    )
    
    private val maxTlbDepth = ctx.tvmOptions.tlbOptions.maxTlbDepth

    @Test
    fun testMaybeStructureValues() {
        assertTrue(info.labelHasUnknownLeaves(maybeStructure) == false)
        assertEquals(0, info.minimalLabelDepth(maybeStructure))
        assertEquals(1, info.maxRefSize(maybeStructure))
        assertEquals(maxTlbDepth, info.getIndividualTlbDepthBound(maybeStructure))

        val address = dummyState.generateSymbolicCell()

        val size = info.getDataCellSize(dummyState, address, maybeStructure)
        assertEquals(1, size?.intValue())

        val child = info.getLabelChildStructure(dummyState, address, maybeStructure,  childIdx = 0)!!
        assertEquals(1, child.size)

        val guard = child.values.first()
        val struct = child.keys.first()
        assertTrue(guard !is KInterpretedValue)
        assertTrue(struct is TvmParameterInfo.UnknownCellInfo)

        val cell = info.getDefaultCell(maybeStructure)
        assertTrue(cell?.data == "0" && cell.refs.isEmpty())

        assertTrue { address.address !in cellDataFieldManager.getCellsWithRequestedCellDataField() }
    }

    @Test
    fun testIntSwitchValues() {
        assertTrue(info.labelHasUnknownLeaves(intSwitchStructure) == false)
        assertEquals(0, info.minimalLabelDepth(intSwitchStructure))
        assertEquals(0, info.maxRefSize(intSwitchStructure))
        assertEquals(maxTlbDepth, info.getIndividualTlbDepthBound(intSwitchStructure))

        val address = dummyState.generateSymbolicCell()

        val size = info.getDataCellSize(dummyState, address, intSwitchStructure)
        assertTrue(size !is KInterpretedValue<*>)
        val evaluated = dummyState.models.first().eval(size as UExpr<TvmSizeSort>).intValue()
        assertTrue(evaluated == 34 || evaluated == 66)

        val child = info.getLabelChildStructure(dummyState, address, intSwitchStructure,  childIdx = 0)!!
        assertEquals(0, child.size)

        val cell = info.getDefaultCell(intSwitchStructure)
        require(cell != null && cell.data.length == 34 && cell.data.startsWith("01") && cell.refs.isEmpty())

        assertTrue { address.address !in cellDataFieldManager.getCellsWithRequestedCellDataField() }

        val switchConstraint = info.getDataConstraints(dummyState, address, intSwitchStructure)
        assertTrue(switchConstraint !is KInterpretedValue)

        assertTrue { address.address in cellDataFieldManager.getCellsWithRequestedCellDataField() }
    }

    @Test
    fun testHasUnknownLeaves() {
        assertEquals(maxTlbDepth, info.getIndividualTlbDepthBound(prefixInt64Structure))
        assertTrue(info.labelHasUnknownLeaves(prefixInt64Structure) == true)
        assertEquals(TvmContext.MAX_REFS_NUMBER, info.maxRefSize(prefixInt64Structure))
        val address = dummyState.generateSymbolicCell()
        assertEquals(
            null,
            info.getDataCellSize(dummyState, address, prefixInt64Structure)
        )

        val cell = info.getDefaultCell(prefixInt64Structure)
        assertTrue(cell != null && cell.data.length == 64 && cell.refs.isEmpty())

        assertTrue { address.address !in cellDataFieldManager.getCellsWithRequestedCellDataField() }

        val dataConstraints = info.getDataConstraints(dummyState, address, prefixInt64Structure)

        assertTrue { dataConstraints !is KInterpretedValue }
    }

    @Test
    fun testStructureX() {
        assertTrue(info.labelHasUnknownLeaves(structureX) == false)
        assertEquals(0, info.minimalLabelDepth(structureX))
        assertEquals(0, info.maxRefSize(structureX))
        assertEquals(maxTlbDepth, info.getIndividualTlbDepthBound(structureX))

        val cell = info.getDefaultCell(structureX)
        assertTrue(cell != null && cell.data.length == 16 && cell.refs.isEmpty())

        val address = dummyState.generateSymbolicCell()

        val size = info.getDataCellSize(dummyState, address, structureX)
        assertEquals(16, size?.intValue())

        val child = info.getLabelChildStructure(dummyState, address, structureX,  childIdx = 0)!!
        assertEquals(0, child.size)

        assertTrue { address.address !in cellDataFieldManager.getCellsWithRequestedCellDataField() }
    }

    @Test
    fun testStructureY() {
        assertTrue(info.labelHasUnknownLeaves(structureY) == false)
        assertEquals(1, info.minimalLabelDepth(structureY))
        assertEquals(0, info.maxRefSize(structureY))
        assertEquals(maxTlbDepth, info.getIndividualTlbDepthBound(structureY))

        val cell = info.getDefaultCell(structureY)
        assertTrue(cell != null && cell.data.length == 16 * 3 && cell.refs.isEmpty())

        val address = dummyState.generateSymbolicCell()

        val size = info.getDataCellSize(dummyState, address, structureY)
        assertEquals(16 * 3, size?.intValue())

        val child = info.getLabelChildStructure(dummyState, address, structureY,  childIdx = 0)!!
        assertEquals(0, child.size)

        assertTrue { address.address !in cellDataFieldManager.getCellsWithRequestedCellDataField() }
    }

    @Test
    fun testRecursiveStructure() {
        assertTrue(info.labelHasUnknownLeaves(recursiveStructure) == false)
        assertEquals(0, info.minimalLabelDepth(recursiveStructure))
        assertEquals(0, info.maxRefSize(recursiveStructure))
        assertEquals(maxTlbDepth, info.getIndividualTlbDepthBound(recursiveStructure))

        val switchStructure = recursiveStructure.internalStructure as TlbStructure.SwitchPrefix

        val cell = info.getDefaultCell(recursiveStructure)
        assertTrue(cell != null && cell.data == "1" && cell.refs.isEmpty())

        // content of this address in model should consist of only zeros => thus the evaluated sizes
        val address = dummyState.generateSymbolicCell()

        val size0 = info.getDataCellSize(dummyState, address, recursiveStructure, maxDepth = 0)
        assertEquals(1, size0?.intValue())
        val switchVariants1 = info.getPossibleSwitchVariants(switchStructure, maxDepth = 0)
        assertTrue { switchVariants1.single().key == "1" }

        val size1 = info.getDataCellSize(dummyState, address, recursiveStructure, maxDepth = 1)
        assertTrue(size1 !is KInterpretedValue)
        val evaluated1 = dummyState.models.first().eval(size1 as UExpr<TvmSizeSort>).intValue()
        assertEquals(10, evaluated1)
        val switchVariants2 = info.getPossibleSwitchVariants(switchStructure, maxDepth = 1)
        assertTrue { switchVariants2.size == 2 }

        val size3 = info.getDataCellSize(dummyState, address, recursiveStructure, maxDepth = 3)
        assertTrue(size3 !is KInterpretedValue)
        val evaluated3 = dummyState.models.first().eval(size3 as UExpr<TvmSizeSort>).intValue()
        assertEquals(28, evaluated3)

        val child = info.getLabelChildStructure(dummyState, address, recursiveStructure,  childIdx = 0)!!
        assertEquals(0, child.size)

        assertTrue { address.address !in cellDataFieldManager.getCellsWithRequestedCellDataField() }
    }

    @Test
    fun testRecursiveWithRefStructure() {
        assertTrue(info.labelHasUnknownLeaves(recursiveWithRefStructure) == false)
        assertEquals(0, info.minimalLabelDepth(recursiveWithRefStructure))
        assertEquals(4, info.maxRefSize(recursiveWithRefStructure))
        assertEquals(0, info.maxRefSize(recursiveWithRefStructure, maxDepth = 0))
        assertEquals(1, info.maxRefSize(recursiveWithRefStructure, maxDepth = 1))

        val cell = info.getDefaultCell(recursiveWithRefStructure)
        assertTrue(cell != null && cell.data == "1" && cell.refs.isEmpty())

        // content of this address in model should consist of only zeros => thus the evaluated sizes
        val address = dummyState.generateSymbolicCell()

        val size0 = info.getDataCellSize(dummyState, address, recursiveWithRefStructure, maxDepth = 0)
        assertEquals(1, size0?.intValue())

        val size1 = info.getDataCellSize(dummyState, address, recursiveWithRefStructure, maxDepth = 1)
        assertTrue(size1 !is KInterpretedValue)
        val evaluated1 = dummyState.models.first().eval(size1 as UExpr<TvmSizeSort>).intValue()
        assertEquals(2, evaluated1)

        val size3 = info.getDataCellSize(dummyState, address, recursiveWithRefStructure, maxDepth = 3)
        assertTrue(size3 !is KInterpretedValue)
        val evaluated3 = dummyState.models.first().eval(size3 as UExpr<TvmSizeSort>).intValue()
        assertEquals(4, evaluated3)

        val child0 = info.getLabelChildStructure(dummyState, address, recursiveWithRefStructure,  childIdx = 0)!!
        assertEquals(1, child0.size)
        assertTrue(child0.values.first() !is KInterpretedValue)

        val child1 = info.getLabelChildStructure(dummyState, address, recursiveWithRefStructure,  childIdx = 1)!!
        assertEquals(1, child1.size)
        assertTrue(child1.values.first() !is KInterpretedValue)

        val child3 = info.getLabelChildStructure(dummyState, address, recursiveWithRefStructure,  childIdx = 3)!!
        assertEquals(1, child1.size)
        assertTrue(child3.values.first() !is KInterpretedValue)

        val numberOfChildrenExceeded =
            info.getConditionForNumberOfChildrenExceeded(dummyState, address, recursiveWithRefStructure)
        assertTrue(numberOfChildrenExceeded !is KInterpretedValue)

        assertTrue { address.address !in cellDataFieldManager.getCellsWithRequestedCellDataField() }
    }

    @Test
    fun testRefAfterRecursiveStructure() {
        assertEquals(false, info.labelHasUnknownLeaves(refAfterRecursiveStructure))
        assertEquals(1, info.minimalLabelDepth(refAfterRecursiveStructure))
        assertEquals(4, info.maxRefSize(refAfterRecursiveStructure))
        assertEquals(null, info.maxRefSize(refAfterRecursiveStructure, maxDepth = 0))
        assertEquals(1, info.maxRefSize(refAfterRecursiveStructure, maxDepth = 1))

        val cell = info.getDefaultCell(refAfterRecursiveStructure)
        assertTrue(cell != null && cell.data == "1" && cell.refs.size == 1)

        val address = dummyState.generateSymbolicCell()

        val child0 = info.getLabelChildStructure(dummyState, address, refAfterRecursiveStructure,  childIdx = 0)!!
        assertEquals(1, child0.size)
        assertEquals(ctx.trueExpr, child0.values.first())

        val child1 = info.getLabelChildStructure(dummyState, address, refAfterRecursiveStructure,  childIdx = 1)!!
        assertEquals(1, child1.size)
        assertTrue(child1.values.first() !is KInterpretedValue)

        val numberOfChildrenExceeded =
            info.getConditionForNumberOfChildrenExceeded(dummyState, address, refAfterRecursiveStructure)!!
        assertTrue(numberOfChildrenExceeded !is KInterpretedValue)

        assertTrue { address.address !in cellDataFieldManager.getCellsWithRequestedCellDataField() }
    }

    @Test
    fun testLongData() {
        assertEquals(false, info.labelHasUnknownLeaves(longDataStructure))
        assertEquals(0, info.minimalLabelDepth(longDataStructure))
        assertEquals(0, info.maxRefSize(longDataStructure))
        assertEquals(1, info.getIndividualTlbDepthBound(longDataStructure))

        val address = dummyState.generateSymbolicCell()
        val size = info.getDataCellSize(dummyState, address, longDataStructure)
        assertTrue(size !is KInterpretedValue)

        assertTrue { address.address !in cellDataFieldManager.getCellsWithRequestedCellDataField() }
    }

    @Test
    fun testWrappedMsg() {
        val address = dummyState.generateSymbolicCell()

        val switchConstraints = info.getDataConstraints(dummyState, address, wrappedMsgStructure)
        assertTrue(switchConstraints !is KInterpretedValue)
    }

    @Test
    fun testCustomVarInteger() {
        assertEquals(false, info.labelHasUnknownLeaves(customVarInteger))
        assertEquals(0, info.minimalLabelDepth(customVarInteger))
        assertEquals(0, info.maxRefSize(customVarInteger))

        val address = dummyState.generateSymbolicCell()
        val size = info.getDataCellSize(dummyState, address, customVarInteger)
        assertTrue(size !is KInterpretedValue)

        assertTrue { address.address !in cellDataFieldManager.getCellsWithRequestedCellDataField() }

        val dataConstraint = info.getDataConstraints(dummyState, address, customVarInteger)
        assertTrue(dataConstraint !is KInterpretedValue)
    }

    @Test
    fun testDoubleCustomVarInteger() {
        assertEquals(false, info.labelHasUnknownLeaves(doubleCustomVarInteger))
        assertEquals(1, info.minimalLabelDepth(doubleCustomVarInteger))
        assertEquals(0, info.maxRefSize(doubleCustomVarInteger))

        val address = dummyState.generateSymbolicCell()
        val size = info.getDataCellSize(dummyState, address, doubleCustomVarInteger)
        assertTrue(size !is KInterpretedValue)

        assertTrue { address.address !in cellDataFieldManager.getCellsWithRequestedCellDataField() }

        val dataConstraint = info.getDataConstraints(dummyState, address, doubleCustomVarInteger)
        assertTrue(dataConstraint !is KInterpretedValue)
    }
}
