package org.usvm.utils

fun Iterable<String>.toText(separator: String = System.lineSeparator()): String = joinToString(separator)
