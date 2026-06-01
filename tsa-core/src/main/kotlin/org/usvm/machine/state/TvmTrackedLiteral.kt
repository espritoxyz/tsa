package org.usvm.machine.state

import org.usvm.TrackedLiteral
import org.usvm.UConcreteHeapRef

// Two literals with the same name might be different literals, so this shouldn't be data class
open class TvmTrackedLiteral(
    override val name: String,
) : TrackedLiteral

class TvmCheckerSymbolicPrimitive : TvmTrackedLiteral("checker_primitive")

class TvmSignatureCheckLiteral : TvmTrackedLiteral("signature_check")

class TvmCreatedAt : TvmTrackedLiteral("created_at")

class TvmCreatedLt : TvmTrackedLiteral("created_lt")

class TvmTime : TvmTrackedLiteral("time")

class TvmFwdFee : TvmTrackedLiteral("tvm_fwd_fee")

class TvmMsgValue : TvmTrackedLiteral("tvm_msg_value")

class TvmBalance : TvmTrackedLiteral("tvm_balance")

class TvmMessageSender : TvmTrackedLiteral("tvm_sender")

class TvmContractAddress : TvmTrackedLiteral("tvm_contr_addr")

class TvmHash(
    val ref: UConcreteHeapRef,
) : TvmTrackedLiteral("tvm_hash")

class TvmDepth(
    val ref: UConcreteHeapRef,
) : TvmTrackedLiteral("tvm_depth")

class TvmSha256(
    val ref: UConcreteHeapRef,
) : TvmTrackedLiteral("tvm_sha")

class CDataSizeDistinctCells : TvmTrackedLiteral("cdatasize-distinct-cells")

class CDataSizeDataBits : TvmTrackedLiteral("cdatasize-data-bits")

class CDataSizeCellRefs : TvmTrackedLiteral("cdatasize-cell-refs")
