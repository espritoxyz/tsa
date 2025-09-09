package org.ton.targets

import org.ton.bytecode.TvmInst
import org.usvm.targets.UTarget

abstract class TvmTarget(
    location: TvmInst? = null
) : UTarget<TvmInst, TvmTarget>(location)
