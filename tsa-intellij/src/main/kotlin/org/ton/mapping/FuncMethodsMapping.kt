package org.ton.mapping

import com.github.snksoft.crc.CRC
import com.intellij.openapi.project.Project
import org.ton.intellij.func.psi.FuncFunction
import org.ton.intellij.func.psi.FuncPsiFactory
import org.usvm.machine.MethodId
import java.math.BigInteger
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.name

fun makeMethodsMapping(
    funcSourcesPath: Path,
    project: Project,
    mapping: MutableMap<MethodId, FuncFunction>,
    counter: AtomicInteger,
    visitedSources: MutableSet<Path>
) {
    if (funcSourcesPath in visitedSources) {
        return
    }
    visitedSources.add(funcSourcesPath)

    val sourceCode = funcSourcesPath.toFile().readText()
    val psiFile = FuncPsiFactory[project].createFile(name = funcSourcesPath.name, text = sourceCode)

    val includes = psiFile.includeDefinitions.mapNotNull { it.stringLiteral?.rawString?.text }
    val includePaths = includes.map { funcSourcesPath.parent.resolve(it) }

    includePaths.forEach { makeMethodsMapping(it, project, mapping, counter, visitedSources) }

    // Note that inline functions also receive ids, so we need to count them too (unlike functions with asm definitions)
    val functions = psiFile.functions.filter { it.asmDefinition == null }
    functions.associateByTo(mapping) { getMethodId(it, counter) }
}

fun getMethodId(funcFunction: FuncFunction, counter: AtomicInteger): MethodId {
    val methodIdDefinition = funcFunction.methodIdDefinition
    val name = funcFunction.name!!
    if (methodIdDefinition != null) {
        methodIdDefinition.integerLiteral?.let {
            return BigInteger(it.text)
        }

        val crc16 = CRC.calculateCRC(CRC.Parameters.XMODEM, name.toByteArray())
        val methodId = crc16.and(0xffff).or(0x10000)

        return BigInteger.valueOf(methodId)
    }

    return when (name) {
        "main", "recv_internal" -> 0
        "recv_external" -> -1
        "run_ticktock" -> -2
        else -> counter.getAndIncrement()
    }.let { BigInteger.valueOf(it.toLong()) }
}
