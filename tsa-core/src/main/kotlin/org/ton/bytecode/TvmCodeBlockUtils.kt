package org.ton.bytecode

/**
 * Note: after this old [TvmMethod] becomes invalid.
 */
fun TvmMethod.addReturnStmt(): TvmMethod {
    val retStmt = TsaArtificialImplicitRetInst(TvmInstMethodLocation(id, instList.size))
    val instListNew = instList.toMutableList()
    instListNew.add(retStmt)
    return TvmMethod(
        id = id,
        instListRaw = instListNew,
    ).also {
        retStmt.location.codeBlock = it
    }
}

/**
 * Note: after this old [TvmMainMethod] becomes invalid.
 */
fun TvmMainMethod.addReturnStmt(): TvmMainMethod {
    val retStmt = TsaArtificialImplicitRetInst(TvmMainMethodLocation(instList.size))
    val instListNew = instList.toMutableList()
    instListNew.add(retStmt)
    return TvmMainMethod(
        instListRaw = instListNew,
    ).also {
        retStmt.location.codeBlock = it
    }
}

// An artificial entity representing instructions in continuation
data class TvmLambda(
    private val instListRaw: MutableList<TvmInst>
) : TvmCodeBlock() {
    override val instList: List<TvmInst>
        get() = instListRaw

    init {
        initLocationsCodeBlock()
        instListRaw += returnStmt()
    }

    private fun returnStmt(): TsaArtificialImplicitRetInst {
        check(instList.isNotEmpty()) {
            "TvmLambda must not be empty"
        }
        val lastStmt = instList.last()
        return TsaArtificialImplicitRetInst(lastStmt.location.increment())
    }
}
