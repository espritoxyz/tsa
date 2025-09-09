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
        instListRaw = instListNew
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
        instListRaw = instListNew
    ).also {
        retStmt.location.codeBlock = it
    }
}

// An artificial entity representing instructions in continuation
data class TvmLambda(
    private val instListRaw: MutableList<TvmInst>,
    // must be given if lambda is empty
    private val givenParent: TvmInst? = null
) : TvmCodeBlock() {
    override val instList: List<TvmInst>
        get() = instListRaw

    init {
        check(instListRaw.isNotEmpty() || givenParent != null) {
            "If instList is empty, parent must be given"
        }

        initLocationsCodeBlock()
        instListRaw += returnStmt()
    }

    private fun returnStmt(): TsaArtificialImplicitRetInst {
        if (instList.isNotEmpty()) {
            val lastStmt = instList.last()
            return TsaArtificialImplicitRetInst(lastStmt.location.increment())
        } else {
            val loc =
                TvmInstLambdaLocation(0).also {
                    it.parent = givenParent?.location
                        ?: error("Parent must be given if TvmLambda is empty")
                    it.codeBlock = this
                }
            return TsaArtificialImplicitRetInst(loc)
        }
    }
}
