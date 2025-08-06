package org.ton.test.gen.dsl.wrapper

import org.ton.test.gen.dsl.TsContext
import org.ton.test.gen.dsl.models.TsWrapper

interface TsWrapperDescriptor<T : TsWrapper> {
    val name: String
    val wrapperType: T
    val ctx: TsContext

    fun renderFile(): String
}