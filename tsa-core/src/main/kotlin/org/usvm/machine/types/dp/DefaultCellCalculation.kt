package org.usvm.machine.types.dp

import org.ton.TlbAtomicLabel
import org.ton.TlbCompositeLabel
import org.ton.TlbStructure
import org.ton.TvmParameterInfo
import org.usvm.machine.TvmContext
import org.usvm.machine.types.defaultCellValue
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestDictCellValue
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.TvmTestSliceValue
import java.math.BigInteger

private data class DPParamsForDefaultCellCalculation(
    val maxRefs: Int,
    val maxTlbDepth: Int,
    val maxCellDepth: Int,
    val label: TlbCompositeLabel,
)

fun getDefaultDict(keyLength: Int): TvmTestDictCellValue { // dict mustn't be empty
    return TvmTestDictCellValue(
        keyLength,
        mapOf(
            TvmTestIntegerValue(BigInteger.ZERO) to TvmTestSliceValue(TvmTestDataCellValue(), dataPos = 0, refPos = 0),
        ),
    )
}

fun calculateDefaultCells(
    ctx: TvmContext,
    labels: Collection<TlbCompositeLabel>,
    individualMaxCellTlbDepth: Map<TlbCompositeLabel, Int>,
): Map<TlbCompositeLabel, TvmTestDataCellValue> {
    val generalMaxTlbDepth = ctx.tvmOptions.tlbOptions.maxTlbDepth
    val maxCellDepthForDefault = ctx.tvmOptions.tlbOptions.maxCellDepthForDefaultCellsConsistentWithTlb
    val calculatedValues = hashMapOf<DPParamsForDefaultCellCalculation, TvmTestDataCellValue?>()
    for (maxCellDepth in 0..maxCellDepthForDefault) {
        for (maxTlbDepth in 0..generalMaxTlbDepth) {
            for (maxRefs in 0..TvmContext.MAX_REFS_NUMBER) {
                for (label in labels) {
                    val tlbDepthBound =
                        individualMaxCellTlbDepth[label]
                            ?: error("individualMaxCellTlbDepth must be calculated for all labels")

                    val result =
                        if (maxTlbDepth <= tlbDepthBound) {
                            getDefaultCell(
                                ctx,
                                label.internalStructure,
                                maxRefs,
                                maxTlbDepth,
                                maxCellDepth,
                                generalMaxTlbDepth,
                                calculatedValues,
                            )
                        } else {
                            val params = DPParamsForDefaultCellCalculation(maxRefs, tlbDepthBound, maxCellDepth, label)
                            calculatedValues[params]
                        }

                    val params = DPParamsForDefaultCellCalculation(maxRefs, maxTlbDepth, maxCellDepth, label)
                    calculatedValues[params] = result
                }
            }
        }
    }

    val result = hashMapOf<TlbCompositeLabel, TvmTestDataCellValue>()
    labels.forEach { label ->
        val params =
            DPParamsForDefaultCellCalculation(
                maxRefs = TvmContext.MAX_REFS_NUMBER,
                maxTlbDepth = generalMaxTlbDepth,
                maxCellDepth = maxCellDepthForDefault,
                label = label,
            )
        calculatedValues[params]?.let { result[label] = it }
    }

    return result
}

private fun getDefaultCell(
    ctx: TvmContext,
    struct: TlbStructure,
    maxRefs: Int,
    maxTlbDepth: Int,
    maxCellDepth: Int,
    generalMaxTlbDepth: Int,
    calculatedValues: Map<DPParamsForDefaultCellCalculation, TvmTestDataCellValue?>,
): TvmTestDataCellValue? {
    return when (struct) {
        is TlbStructure.Unknown, is TlbStructure.Empty -> {
            TvmTestDataCellValue()
        }

        is TlbStructure.LoadRef -> {
            if (maxRefs == 0 || maxCellDepth == 0) {
                return null
            }

            val nextCell =
                when (struct.ref) {
                    is TvmParameterInfo.UnknownCellInfo -> {
                        TvmTestDataCellValue()
                    }
                    is TvmParameterInfo.DictCellInfo -> {
                        getDefaultDict(struct.ref.keySize)
                    }
                    is TvmParameterInfo.DataCellInfo -> {
                        val params =
                            DPParamsForDefaultCellCalculation(
                                maxRefs = TvmContext.MAX_REFS_NUMBER,
                                maxTlbDepth = generalMaxTlbDepth,
                                maxCellDepth = maxCellDepth - 1,
                                label = struct.ref.dataCellStructure,
                            )
                        if (params !in calculatedValues.keys) {
                            error("Needed value was not calculated when it was needed during DP process")
                        }

                        calculatedValues[params]
                            ?: return null
                    }
                }

            val furtherStruct =
                getDefaultCell(
                    ctx,
                    struct.rest,
                    maxRefs - 1,
                    maxTlbDepth,
                    maxCellDepth,
                    generalMaxTlbDepth,
                    calculatedValues,
                )
                    ?: return null

            TvmTestDataCellValue(
                data = furtherStruct.data,
                refs = listOf(nextCell) + furtherStruct.refs,
            )
        }

        is TlbStructure.KnownTypePrefix -> {
            when (struct.typeLabel) {
                is TlbAtomicLabel -> {
                    val content = struct.typeLabel.defaultCellValue(ctx)
                    val further =
                        getDefaultCell(
                            ctx,
                            struct.rest,
                            maxRefs,
                            maxTlbDepth,
                            maxCellDepth,
                            generalMaxTlbDepth,
                            calculatedValues,
                        )
                            ?: return null
                    if (content.length + further.data.length > TvmContext.MAX_DATA_LENGTH) {
                        return null
                    }
                    TvmTestDataCellValue(
                        data = content + further.data,
                        refs = further.refs,
                    )
                }

                is TlbCompositeLabel -> {
                    if (maxTlbDepth == 0) {
                        return null
                    }

                    var result: TvmTestDataCellValue? = null

                    for (innerRefs in 0..maxRefs) {
                        val params =
                            DPParamsForDefaultCellCalculation(
                                maxRefs = innerRefs,
                                maxTlbDepth = maxTlbDepth - 1,
                                maxCellDepth = maxCellDepth,
                                label = struct.typeLabel,
                            )

                        if (params !in calculatedValues.keys) {
                            error("Needed value was not calculated when it was needed during DP process")
                        }

                        val variant =
                            calculatedValues[params]
                                ?: continue

                        val further =
                            getDefaultCell(
                                ctx,
                                struct.rest,
                                maxRefs - innerRefs,
                                maxTlbDepth,
                                maxCellDepth,
                                generalMaxTlbDepth,
                                calculatedValues,
                            )
                        if (further != null &&
                            (result == null || result.data.length > variant.data.length + further.data.length)
                        ) {
                            result =
                                TvmTestDataCellValue(
                                    data = variant.data + further.data,
                                    refs = variant.refs + further.refs,
                                )
                        }
                    }
                    if (result == null || result.data.length > TvmContext.MAX_DATA_LENGTH) {
                        return null
                    }

                    result
                }
            }
        }

        is TlbStructure.SwitchPrefix -> {
            var result: TvmTestDataCellValue? = null

            for ((key, variant) in struct.variants) {
                val further =
                    getDefaultCell(
                        ctx,
                        variant,
                        maxRefs,
                        maxTlbDepth,
                        maxCellDepth,
                        generalMaxTlbDepth,
                        calculatedValues,
                    )

                if (further != null && (result == null || result.data.length > further.data.length + key.length)) {
                    result =
                        TvmTestDataCellValue(
                            data = key + further.data,
                            further.refs,
                        )
                }
            }

            result
        }
    }
}
