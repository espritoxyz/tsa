package org.ton.test.gen.dsl.render

interface TsPrinter {
    fun print(text: String)
    fun println(text: String = "")

    fun pushIndent()
    fun popIndent()

    fun clear()

    override fun toString(): String
    var printedLength: Int
}

class TsPrinterImpl(
    private var tabsAmount: Int = 0,
    private val builder: StringBuilder = StringBuilder()
) : TsPrinter, Appendable by builder {

    private var atLineStart: Boolean = true

    private val indent: String get() = TAB * tabsAmount

    override fun pushIndent() {
        tabsAmount++
    }

    override fun popIndent() {
        tabsAmount--
    }

    override fun toString(): String = builder.toString()

    override var printedLength: Int = builder.length

    override fun print(text: String) {
        if (atLineStart) {
            printIndent()
            atLineStart = false
        }
        append(text)
    }

    override fun println(text: String) {
        if (atLineStart) {
            printIndent()
            atLineStart = false
        }
        print(text)
        appendLine()
        atLineStart = true
    }

    override fun clear() {
        builder.clear()
    }

    private fun printIndent() {
        append(indent)
    }

    private operator fun String.times(n: Int): String = repeat(n)

    companion object {
        private const val TAB = "    "
    }
}