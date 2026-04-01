package org.usvm.machine.state

import org.usvm.TrackedLiteral

// Two literals with the same name might be different literals, so this shouldn't be data class
open class TvmTrackedLiteral(
    override val name: String,
) : TrackedLiteral

class TvmCheckerSymbolicPrimitive : TvmTrackedLiteral("checker_primitive")

class TvmSignatureCheckLiteral : TvmTrackedLiteral("signature_check")

class TvmCreatedAt : TvmTrackedLiteral("created_at")

class TvmCreatedLt : TvmTrackedLiteral("created_lt")
