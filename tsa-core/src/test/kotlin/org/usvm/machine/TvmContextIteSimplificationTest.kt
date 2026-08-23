package org.usvm.machine

import io.ksmt.expr.KExpr
import io.ksmt.expr.KInterpretedValue
import io.ksmt.expr.KIteExpr
import io.ksmt.sort.KBv32Sort

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TvmContextIteSimplificationTest {
    private val options = TvmOptions()
    private val components = TvmComponents(options)
    private val ctx = TvmContext(options, components)

    @AfterTest
    fun closeResources() {
        ctx.close()
        components.close()
    }

    @Test
    fun `merges ITE branches with the same leaves`() = with(ctx) {
        val outerCondition = mkConst("outer", boolSort)
        val trueCondition = mkConst("trueCondition", boolSort)
        val falseCondition = mkConst("falseCondition", boolSort)
        val trueLeaf = mkBv(1)
        val falseLeaf = mkBv(0)

        val expression =
            mkIte(
                outerCondition,
                mkIte(trueCondition, trueLeaf, falseLeaf),
                mkIte(falseCondition, trueLeaf, falseLeaf),
            )

        val expectedCondition =
            mkOr(
                mkAnd(outerCondition, trueCondition),
                mkAnd(outerCondition.not(), falseCondition),
                flat = false,
            )
        val mergedIte = assertIs<KIteExpr<*>>(expression)
        assertEquals(expectedCondition, mergedIte.condition)
        assertEquals(trueLeaf, mergedIte.trueBranch)
        assertEquals(falseLeaf, mergedIte.falseBranch)
    }

    @Test
    fun `merges repeated concrete leaves across a deep ITE tree`() = with(ctx) {
        val leaves = listOf(mkBv(0x2f4), mkBv(0x3ff), mkBv(0x3af), mkBv(0x387))
        var expression: KExpr<KBv32Sort> = leaves.first()
        repeat(1_000) { index ->
            expression =
                mkIte(
                    mkConst("condition$index", boolSort),
                    leaves[(index + 1) % leaves.size],
                    expression,
                )
        }

        val remainingLeaves = mutableListOf<KInterpretedValue<*>>()
        var current: KExpr<*> = expression
        var iteCount = 0
        while (current is KIteExpr<*>) {
            iteCount++
            assertTrue(current.trueBranch is KInterpretedValue<*>)
            remainingLeaves += current.trueBranch as KInterpretedValue<*>
            current = current.falseBranch
        }
        assertTrue(current is KInterpretedValue<*>)
        remainingLeaves += current as KInterpretedValue<*>

        assertEquals(leaves.size - 1, iteCount)
        assertEquals(leaves.toSet(), remainingLeaves.toSet())
        assertEquals(leaves.size, remainingLeaves.size)
    }

    @Test
    fun `distributes equality over ITE arguments`() = with(ctx) {
        val condition = mkConst("eqCondition", boolSort)
        val lhsTrue = mkConst("lhsTrue", bv32Sort)
        val lhsFalse = mkConst("lhsFalse", bv32Sort)
        val rhs = mkConst("rhs", bv32Sort)

        val expression = mkEq(mkIte(condition, lhsTrue, lhsFalse), rhs)
        val expected = mkIte(condition, mkEq(lhsTrue, rhs), mkEq(lhsFalse, rhs))

        assertEquals(expected, expression)
    }

    @Test
    fun `merges ITE branches with swapped leaves`() = with(ctx) {
        val outerCondition = mkConst("outer", boolSort)
        val trueCondition = mkConst("trueCondition", boolSort)
        val falseCondition = mkConst("falseCondition", boolSort)
        val trueLeaf = mkBv(1)
        val falseLeaf = mkBv(0)

        val expression =
            mkIte(
                outerCondition,
                mkIte(trueCondition, trueLeaf, falseLeaf),
                mkIte(falseCondition, falseLeaf, trueLeaf),
            )

        val expectedCondition =
            mkOr(
                mkAnd(outerCondition, trueCondition),
                mkAnd(outerCondition.not(), falseCondition.not()),
                flat = false,
            )
        val mergedIte = assertIs<KIteExpr<*>>(expression)
        assertEquals(expectedCondition, mergedIte.condition)
        assertEquals(trueLeaf, mergedIte.trueBranch)
        assertEquals(falseLeaf, mergedIte.falseBranch)
    }
}
