package org.usvm.machine

import io.ksmt.expr.KBitVecValue
import io.ksmt.utils.BvUtils.toBigIntegerSigned
import io.ksmt.utils.powerOfTwo
import org.ton.bytecode.MethodId
import org.ton.bytecode.TvmCell
import org.ton.bytecode.TvmCellData
import org.ton.bytecode.TvmCodeBlock
import org.ton.bytecode.TvmInst
import org.ton.bytecode.TvmInstLambdaLocation
import org.ton.bytecode.TvmInstLocation
import org.ton.bytecode.TvmMethod
import org.ton.cell.Cell
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.machine.TvmContext.Companion.tctx
import org.usvm.machine.TvmContext.TvmInt257Sort
import java.io.File
import java.math.BigInteger
import java.nio.file.Path
import java.nio.file.Paths

@Suppress("NOTHING_TO_INLINE")
inline fun UExpr<out UBvSort>.bigIntValue() = (this as KBitVecValue<*>).toBigIntegerSigned()

@Suppress("NOTHING_TO_INLINE")
inline fun UExpr<out UBvSort>.intValue() = bigIntValue().toInt()

fun TvmCodeBlock.extractMethodIdOrNull(): MethodId? = (this as? TvmMethod)?.id

inline fun <T> tryCatchIf(
    condition: Boolean,
    body: () -> T,
    exceptionHandler: (Throwable) -> T,
): T {
    if (!condition) {
        return body()
    }

    return runCatching {
        body()
    }.getOrElse(exceptionHandler)
}

inline fun <reified T> getResourcePath(path: String): Path = getResourcePath(T::class.java, path)

fun getResourcePathEasy(path: String): Path = getResourcePath(object {}.javaClass, path)

fun getResourcePath(
    cls: Class<*>,
    path: String,
): Path =
    cls.getResource(path)?.let {
        // This way paths are parsed correctly on Windows.
        // String fields like [path] or [file] of [URL] class
        // shouldn't be used here.
        File(it.toURI()).toPath()
    } ?: error("Resource $path was not found")

fun TvmInst.getRootLocation(): TvmInstLocation {
    var curLoc = location
    while (curLoc is TvmInstLambdaLocation) {
        curLoc = curLoc.parent
    }
    return curLoc
}

fun Cell.toTvmCell(): TvmCell {
    val children = refs.map { it.toTvmCell() }
    val data = TvmCellData(bits.toBinary())
    return TvmCell(data, children)
}

fun UExpr<UBoolSort>.asIntValue(): UExpr<TvmInt257Sort> =
    with(ctx.tctx()) {
        mkIte(this@asIntValue, oneValue, zeroValue)
    }

fun maxUnsignedValue(bits: UInt): BigInteger = powerOfTwo(bits).minus(BigInteger.ONE)

fun Path.getParentNonNullAbsolutePath(): Path = (parent ?: Paths.get("")).toAbsolutePath()

fun <T> List<T>.splitHeadTail(): Pair<T, List<T>>? = if (isEmpty()) null else first() to subList(1, size)
