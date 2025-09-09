package org.ton.test.gen.dsl.models

sealed interface TsBlock : TsStatement {
    val statements: List<TsStatement>
}

data class TsTestBlock(
    val name: String,
    override val statements: List<TsStatement>
) : TsBlock

data class TsTestCase(
    val name: String,
    override val statements: List<TsStatement>
) : TsBlock

data class TsBeforeAllBlock(
    override val statements: List<TsStatement>
) : TsBlock

data class TsBeforeEachBlock(
    override val statements: List<TsStatement>
) : TsBlock
