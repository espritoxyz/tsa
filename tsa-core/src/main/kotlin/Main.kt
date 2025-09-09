import org.ton.bitstring.BitString
import org.ton.boc.BagOfCells
import java.nio.file.Files
import java.nio.file.Paths

// import org.ton.boc.BagOfCells

@OptIn(ExperimentalStdlibApi::class)
fun main() {
//    val boc = BagOfCells("1D805474EFAFB2CF157BD87ED31A19E7187F7F521CB4633B1D971B93C75F9A6D".hexToByteArray(format = HexFormat.UpperCase))
//    val boc = BagOfCells("5BEEC92710103010004500201026102000E50DC612027C0019030E3DF103DE440DEF0002EF020308A28CBCF39CEF02033DF303100C10794A8CBCF39CDE4519032E00731A4313AD981ACF0014CF041600551969F739CF04166AE70614CF0416".hexToByteArray(format = HexFormat.UpperCase))
//    val boc = BagOfCells("b5ee9c72010104010061000114ff00f4a413f4bcf2c80b010201620203005ed06c2120c7009130e0d31f30ed44d0fe0020fe2030802ac8cb3fc9fe2030d33f3001c00197a4c8cb3fc9ed549130e20037a13431da89a1fc0041fc4061005591967f93fc4061a67e6041fc4061".hexToByteArray(format = HexFormat.UpperCase))
//    println(boc)
//    boc.first().beginParse()
    org.ton.hashmap.HmlLong
//    val hexBytes = Files.readAllBytes(Paths.get("/Users/damtev/work/ton/_tests/boc.txt"))
    val hexBytes = Files.readAllBytes(Paths.get("/Users/damtev/work/ton/_tests/boc.txt"))
//    val hexBytes = Files.readAllBytes(Paths.get("/Users/damtev/work/ton/out.boc"))
    val bitString = BitString(hexBytes)
    println(bitString)
    val boc = BagOfCells(hexBytes)
//    println(boc)

    val rootParsed =
        boc.first().beginParse().let {
            val setCpInstr = it.loadBits(8)
            val setCpArg = it.loadBits(8).toHex().toInt(radix = 16)
            val dictPushConst = it.loadBits(13)
            it.skipBits(1)
            val index = it.loadUInt(10)
            val dictJmp = it.loadBits(16)
            val throwInst = it.loadBits(13)
            val throwArg = it.loadUInt(11)
            listOf(setCpInstr, setCpArg, dictPushConst, index, dictJmp, throwInst, throwArg, it)
        }
    println(rootParsed)

//    val parsed = boc.first().refs.first().refs.first().beginParse().let {
//        val s = buildString {
//            it.bits.toHex().toCharArray().let { chars ->
//                for (i in chars.indices step 2) {
//                    append(chars[i + 1])
//                    append(chars[i])
//                }
//            }
//        }
//        var ctos = "CTOS" to it.loadBits(8) // CTOS
//        val dropInst = "BLKDROP2" to it.loadBits(8) // BLKDROP2 2 1
//        val dropArgFst = "ARG1" to it.loadUInt(4)
//        val dropArgSnd = "ARG2" to it.loadUInt(4)
//        var dup = "DUP" to it.loadBits(8) // DUP
//        val sempty = "SEMPTY" to it.loadBits(16) // SEMPTY
//        var pushContInst = "PUSHCONT" to it.loadBits(4) // PUSHCONT 1
//        var pushContArg1 = "ARG" to it.loadUInt(4)
//        var drop = "DROP" to it.loadBits(8) // DROP
//        val ifJmp = "IFJMP" to it.loadBits(8) // IFJMP
//        var lduInst = "LDU" to it.loadBits(8) // LDU (63 + 1)
//        var lduArg1 = "ARG" to it.loadUInt8()
//        drop = "DROP" to it.loadBits(8) // DROP
//        var pushCtrInst = "PUSHCTR" to it.loadBits(16) // PUSHCTR
//        ctos = "CTOS" to it.loadBits(8) // CTOS
//        var dumpStack = "DUMPSTK" to it.loadBits(16) // DUMPSTK
//        dup = "DUP" to it.loadBits(8) // DUP
//        var dumpInst = "DUMP" to it.loadBits(12) // DUMP 0
//        var dumArg1 = "ARG" to it.loadUInt(4)
//        drop = "DROP" to it.loadBits(8) // DROP
//        var pushIntInst = "PUSHINT" to it.loadBits(8) // PUSHINT 42
//        var pushIntInstArg = "ARG" to it.loadUInt8()
//        var newcInst = "NEWC" to it.loadBits(8) // NEWC
//        var stuInst = "STU" to it.loadBits(8) // STU (63 + 1)
//        var stuArg = "ARG" to it.loadUInt8()
//        var endc = "ENDC" to it.loadBits(8) // ENDC
//        dumpInst = "DUMP" to it.loadBits(12) // DUMP 1
//        val dumArg2 = "ARG" to it.loadUInt(4)
//        drop = "DROP" to it.loadBits(8) // DROP
//        lduInst = "LDU" to it.loadBits(8) // LDU (31 + 1)
//        val lduArg2 = "ARG" to it.loadUInt8()
//        drop = "DROP" to it.loadBits(8)
//        var swap = "SWAP" to it.loadBits(8)
//        var eqintInst = "EQINT" to it.loadBits(8)
//        var eqintArg = "ARG" to it.loadUInt8()
//        pushContInst = "PUSHCONT" to it.loadBits(4)
//        pushContArg1 = "ARG" to it.loadUInt(4)
//        var inc = "INC" to it.loadBits(8)
//        newcInst = "NEWC" to it.loadBits(8)
//        stuInst = "STU" to it.loadBits(8)
//        stuArg = "ARG" to it.loadUInt8()
//        endc = "ENDC" to it.loadBits(8)
//        var popCtr = "POPCTR" to it.loadBits(16)
//        pushContInst = "PUSHCONT" to it.loadBits(4)
//        val pushContArg2 = "ARG" to it.loadUInt(4)
//        drop = "DROP" to it.loadBits(8)
//        var ifelse = "IFELSE" to it.loadBits(8)
//
// //        println(
// //            listOf(s, it.bits.toHex(), ctos, dropInst, dropArgFst, dropArgSnd, dup, sempty, pushContInst, pushContArg1, drop, ifJmp, lduInst, lduArg1, drop, pushCtrInst, ctos, dumpStack, dup, dumpInst, dumArg1, drop, pushIntInst, pushIntInstArg, newcInst, stuInst, stuArg, endc, dumpInst, dumArg2, drop, lduInst, lduArg2, drop, swap, eqintInst, eqintArg, pushContInst, pushContArg1, inc, newcInst, stuInst, stuArg, endc, popCtr, pushContInst, pushContArg2, drop, ifelse, it.bits.slice(it.bitsPosition)).joinToString(System.lineSeparator())
// //                .replace("x", ""))
//    }

//    val counterSlice = boc.first().refs.first().refs.last().beginParse().skipBits(2 + 5).skipBits(16).let {
//        var pushCtrInst = "PUSHCTR" to it.loadBits(16) // PUSHCTR
//        var ctos = "CTOS" to it.loadBits(8) // CTOS
//        var dumpStack = "DUMPSTK" to it.loadBits(16) // DUMPSTK
//        var dup = "DUP" to it.loadBits(8) // DUP
//        var dumpInst = "DUMP" to it.loadBits(12) // DUMP 0
//        var dumArg1 = "ARG" to it.loadUInt(4)
//        var drop = "DROP" to it.loadBits(8) // DROP
//        var pushIntInst = "PUSHINT" to it.loadBits(8) // PUSHINT 42
//        var pushIntArg = "ARG" to it.loadUInt8()
//        var newcInst = "NEWC" to it.loadBits(8) // NEWC
//        var stuInst = "STU" to it.loadBits(8) // STU (63 + 1)
//        var stuArg = "ARG" to it.loadUInt8()
//        var endc = "ENDC" to it.loadBits(8) // ENDC
//        dumpInst = "DUMP" to it.loadBits(12) // DUMP 1
//        val dumArg2 = "ARG" to it.loadUInt(4)
//        drop = "DROP" to it.loadBits(8) // DROP
//        var lduInst = "LDU" to it.loadBits(8) // LDU (31 + 1)
//        val lduArg2 = "ARG" to it.loadUInt8()
//        drop = "DROP" to it.loadBits(8)
//        var swap = "SWAP" to it.loadBits(8)
//        var eqintInst = "EQINT" to it.loadBits(8)
//        var eqintArg = "ARG" to it.loadUInt8()
//        var pushContInst = "PUSHCONT" to it.loadBits(4)
//        var pushContArg1 = "ARG" to it.loadUInt(4)
//
//        listOf(pushCtrInst, ctos, dumpStack, dup, dumpInst, dumArg1, drop, pushIntInst, pushIntArg, newcInst, stuInst, stuArg, endc, dumpInst, dumArg2, drop, lduInst, lduArg2, drop, swap, eqintInst, eqintArg, pushContInst, pushContArg1, it.bits.slice(it.bitsPosition))
//    }
//    println(counterSlice.joinToString(System.lineSeparator()))

//    val firstValue = boc.first().refs.first().refs.first().beginParse().skipBits(2 + 5).skipBits(16).let {
//        var pushContInst = "PUSHCONT" to it.loadBits(4) // PUSHCONT 0
//        var pushContArg1 = "ARG" to it.loadUInt(4)
//        var blkswx = it.loadBits(8) // BLKSWX
//        var pushIntInst = "PUSHINT" to it.loadBits(8) // PUSHINT 72
//        var pushIntArg1 = "ARG" to it.loadUInt8()
//
//        var remainder = it.bits.slice(it.bitsPosition)
//        listOf(pushContInst, pushContArg1, blkswx, pushIntInst, pushIntArg1, remainder)
//    }
}
