// generated

// auxiliary functions

@pure
fun return1<A>(): (A) asm "NOP";

@pure
fun return2<A, B>(): (A, B) asm "NOP";

@pure
fun return3<A, B, C>(): (A, B, C) asm "NOP";

@pure
fun return4<A, B, C, D>(): (A, B, C, D) asm "NOP";

@pure
fun return5<A, B, C, D, E>(): (A, B, C, D, E) asm "NOP";

@pure
fun return6<A, B, C, D, E, F>(): (A, B, C, D, E, F) asm "NOP";

@pure
fun return7<A, B, C, D, E, F, G>(): (A, B, C, D, E, F, G) asm "NOP";

@pure
fun return8<A, B, C, D, E, F, G, H>(): (A, B, C, D, E, F, G, H) asm "NOP";

@pure
fun return9<A, B, C, D, E, F, G, H, I>(): (A, B, C, D, E, F, G, H, I) asm "NOP";

@pure
fun return10<A, B, C, D, E, F, G, H, I, J>(): (A, B, C, D, E, F, G, H, I, J) asm "NOP";

struct TsaAny{ dummy: int; };

fun wrap<T>(x: T): TsaAny asm "NOP";
fun unwrap<T>(x: TsaAny): T asm "NOP";

// API functions

@method_id(1)
fun tsaForbidFailures() {
    // do nothing
}

@method_id(2)
fun tsaAllowFailures() {
    // do nothing
}

@method_id(3)
fun tsaAssert(condition: int) {
    // do nothing
}

@method_id(4)
fun tsaAssertNot(condition: int) {
    // do nothing
}

// @method_id(5)
fun tsaFetchValue<A>(value: A, valueId: int) {
    // do nothing
}

@method_id(6)
fun tsaSendInternalMessage(contractId: int, inputId: int) {
    // do nothing
}

@method_id(7)
fun tsaGetC4(contractId: int): cell {
    return return1();
}

@method_id(8)
fun tsaSendExternalMessage(contractId: int, inputId: int) {
    // do nothing
}

@method_id(9)
fun tsaGetBalance(contractId: int): int {
    return return1();
}

// this function shouldn't be used while executing contract [contractId] (inside checker handlers)
@method_id(10)
fun tsaSetC4(contractId: int, value: cell) {
    // do nothing
}

@method_id(11)
fun tsaMakeAddressRandom(address: address) {
    // do nothing
}

@method_id(12)
fun tsaMakeSliceIndependentFromRandomAddresses(independentSlice: slice) {
    // do nothing
}

@method_id(13)
fun tsaInputWasAccepted(inputId: int): int {
    return return1();
}

@method_id(14)
fun tsaSendImplMessageWithBody(body: slice, contractId: int, inputId: int) {
    // do nothing
}

@method_id(15)
fun tsaSendExternalMessageWithBody(body: slice, contractId: int, inputId: int) {
    // do nothing
}

// making symbolic values API functions

@method_id(100)
fun tsaMkInt(bits: int, signed: int): int {
    return return1();
}

// calling methods functions

@method_id(10000)
fun tsaCallGetterIn0Ret0Impl(idContract: int, idMethod: int) {
    // do nothing
}

fun tsaCallGetterIn0Ret0(idContract: int, idMethod: int) {
    tsaCallGetterIn0Ret0Impl(idContract, idMethod)
}

@method_id(10001)
fun tsaCallGetterIn1Ret0Impl(p0: TsaAny, idContract: int, idMethod: int) {
    // do nothing
}

fun tsaCallGetterIn1Ret0<A>(p0: A, idContract: int, idMethod: int) {
    tsaCallGetterIn1Ret0Impl(wrap(p0), idContract, idMethod)
}

@method_id(10002)
fun tsaCallGetterIn2Ret0Impl(p0: TsaAny, p1: TsaAny, idContract: int, idMethod: int) {
    // do nothing
}

fun tsaCallGetterIn2Ret0<A, B>(p0: A, p1: B, idContract: int, idMethod: int) {
    tsaCallGetterIn2Ret0Impl(wrap(p0), wrap(p1), idContract, idMethod)
}

@method_id(10003)
fun tsaCallGetterIn3Ret0Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, idContract: int, idMethod: int) {
    // do nothing
}

fun tsaCallGetterIn3Ret0<A, B, C>(p0: A, p1: B, p2: C, idContract: int, idMethod: int) {
    tsaCallGetterIn3Ret0Impl(wrap(p0), wrap(p1), wrap(p2), idContract, idMethod)
}

@method_id(10004)
fun tsaCallGetterIn4Ret0Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, idContract: int, idMethod: int) {
    // do nothing
}

fun tsaCallGetterIn4Ret0<A, B, C, D>(p0: A, p1: B, p2: C, p3: D, idContract: int, idMethod: int) {
    tsaCallGetterIn4Ret0Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), idContract, idMethod)
}

@method_id(10005)
fun tsaCallGetterIn5Ret0Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, idContract: int, idMethod: int) {
    // do nothing
}

fun tsaCallGetterIn5Ret0<A, B, C, D, E>(p0: A, p1: B, p2: C, p3: D, p4: E, idContract: int, idMethod: int) {
    tsaCallGetterIn5Ret0Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), idContract, idMethod)
}

@method_id(10006)
fun tsaCallGetterIn6Ret0Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, idContract: int, idMethod: int) {
    // do nothing
}

fun tsaCallGetterIn6Ret0<A, B, C, D, E, F>(p0: A, p1: B, p2: C, p3: D, p4: E, p5: F, idContract: int, idMethod: int) {
    tsaCallGetterIn6Ret0Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), idContract, idMethod)
}

@method_id(10007)
fun tsaCallGetterIn7Ret0Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, idContract: int, idMethod: int) {
    // do nothing
}

fun tsaCallGetterIn7Ret0<A, B, C, D, E, F, G>(p0: A, p1: B, p2: C, p3: D, p4: E, p5: F, p6: G, idContract: int, idMethod: int) {
    tsaCallGetterIn7Ret0Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), idContract, idMethod)
}

@method_id(10008)
fun tsaCallGetterIn8Ret0Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, idContract: int, idMethod: int) {
    // do nothing
}

fun tsaCallGetterIn8Ret0<A, B, C, D, E, F, G, H>(p0: A, p1: B, p2: C, p3: D, p4: E, p5: F, p6: G, p7: H, idContract: int, idMethod: int) {
    tsaCallGetterIn8Ret0Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), idContract, idMethod)
}

@method_id(10009)
fun tsaCallGetterIn9Ret0Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, idContract: int, idMethod: int) {
    // do nothing
}

fun tsaCallGetterIn9Ret0<A, B, C, D, E, F, G, H, I>(p0: A, p1: B, p2: C, p3: D, p4: E, p5: F, p6: G, p7: H, p8: I, idContract: int, idMethod: int) {
    tsaCallGetterIn9Ret0Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), idContract, idMethod)
}

@method_id(10010)
fun tsaCallGetterIn10Ret0Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, p9: TsaAny, idContract: int, idMethod: int) {
    // do nothing
}

fun tsaCallGetterIn10Ret0<A, B, C, D, E, F, G, H, I, J>(p0: A, p1: B, p2: C, p3: D, p4: E, p5: F, p6: G, p7: H, p8: I, p9: J, idContract: int, idMethod: int) {
    tsaCallGetterIn10Ret0Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), wrap(p9), idContract, idMethod)
}

@method_id(10100)
fun tsaCallGetterIn0Ret1Impl(idContract: int, idMethod: int): (TsaAny) {
    return return1();
}

fun tsaCallGetterIn0Ret1<A>(idContract: int, idMethod: int): (A) {
    val result = tsaCallGetterIn0Ret1Impl(idContract, idMethod);
    return (unwrap<A>(result));

}

@method_id(10101)
fun tsaCallGetterIn1Ret1Impl(p0: TsaAny, idContract: int, idMethod: int): (TsaAny) {
    return return1();
}

fun tsaCallGetterIn1Ret1<A, B>(p0: B, idContract: int, idMethod: int): (A) {
    val result = tsaCallGetterIn1Ret1Impl(wrap(p0), idContract, idMethod);
    return (unwrap<A>(result));

}

@method_id(10102)
fun tsaCallGetterIn2Ret1Impl(p0: TsaAny, p1: TsaAny, idContract: int, idMethod: int): (TsaAny) {
    return return1();
}

fun tsaCallGetterIn2Ret1<A, B, C>(p0: B, p1: C, idContract: int, idMethod: int): (A) {
    val result = tsaCallGetterIn2Ret1Impl(wrap(p0), wrap(p1), idContract, idMethod);
    return (unwrap<A>(result));

}

@method_id(10103)
fun tsaCallGetterIn3Ret1Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, idContract: int, idMethod: int): (TsaAny) {
    return return1();
}

fun tsaCallGetterIn3Ret1<A, B, C, D>(p0: B, p1: C, p2: D, idContract: int, idMethod: int): (A) {
    val result = tsaCallGetterIn3Ret1Impl(wrap(p0), wrap(p1), wrap(p2), idContract, idMethod);
    return (unwrap<A>(result));

}

@method_id(10104)
fun tsaCallGetterIn4Ret1Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, idContract: int, idMethod: int): (TsaAny) {
    return return1();
}

fun tsaCallGetterIn4Ret1<A, B, C, D, E>(p0: B, p1: C, p2: D, p3: E, idContract: int, idMethod: int): (A) {
    val result = tsaCallGetterIn4Ret1Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), idContract, idMethod);
    return (unwrap<A>(result));

}

@method_id(10105)
fun tsaCallGetterIn5Ret1Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, idContract: int, idMethod: int): (TsaAny) {
    return return1();
}

fun tsaCallGetterIn5Ret1<A, B, C, D, E, F>(p0: B, p1: C, p2: D, p3: E, p4: F, idContract: int, idMethod: int): (A) {
    val result = tsaCallGetterIn5Ret1Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), idContract, idMethod);
    return (unwrap<A>(result));

}

@method_id(10106)
fun tsaCallGetterIn6Ret1Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, idContract: int, idMethod: int): (TsaAny) {
    return return1();
}

fun tsaCallGetterIn6Ret1<A, B, C, D, E, F, G>(p0: B, p1: C, p2: D, p3: E, p4: F, p5: G, idContract: int, idMethod: int): (A) {
    val result = tsaCallGetterIn6Ret1Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), idContract, idMethod);
    return (unwrap<A>(result));

}

@method_id(10107)
fun tsaCallGetterIn7Ret1Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, idContract: int, idMethod: int): (TsaAny) {
    return return1();
}

fun tsaCallGetterIn7Ret1<A, B, C, D, E, F, G, H>(p0: B, p1: C, p2: D, p3: E, p4: F, p5: G, p6: H, idContract: int, idMethod: int): (A) {
    val result = tsaCallGetterIn7Ret1Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), idContract, idMethod);
    return (unwrap<A>(result));

}

@method_id(10108)
fun tsaCallGetterIn8Ret1Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, idContract: int, idMethod: int): (TsaAny) {
    return return1();
}

fun tsaCallGetterIn8Ret1<A, B, C, D, E, F, G, H, I>(p0: B, p1: C, p2: D, p3: E, p4: F, p5: G, p6: H, p7: I, idContract: int, idMethod: int): (A) {
    val result = tsaCallGetterIn8Ret1Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), idContract, idMethod);
    return (unwrap<A>(result));

}

@method_id(10109)
fun tsaCallGetterIn9Ret1Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, idContract: int, idMethod: int): (TsaAny) {
    return return1();
}

fun tsaCallGetterIn9Ret1<A, B, C, D, E, F, G, H, I, J>(p0: B, p1: C, p2: D, p3: E, p4: F, p5: G, p6: H, p7: I, p8: J, idContract: int, idMethod: int): (A) {
    val result = tsaCallGetterIn9Ret1Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), idContract, idMethod);
    return (unwrap<A>(result));

}

@method_id(10110)
fun tsaCallGetterIn10Ret1Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, p9: TsaAny, idContract: int, idMethod: int): (TsaAny) {
    return return1();
}

fun tsaCallGetterIn10Ret1<A, B, C, D, E, F, G, H, I, J, K>(p0: B, p1: C, p2: D, p3: E, p4: F, p5: G, p6: H, p7: I, p8: J, p9: K, idContract: int, idMethod: int): (A) {
    val result = tsaCallGetterIn10Ret1Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), wrap(p9), idContract, idMethod);
    return (unwrap<A>(result));

}

@method_id(10200)
fun tsaCallGetterIn0Ret2Impl(idContract: int, idMethod: int): (TsaAny, TsaAny) {
    return return2();
}

fun tsaCallGetterIn0Ret2<A, B>(idContract: int, idMethod: int): (A, B) {
    val result = tsaCallGetterIn0Ret2Impl(idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1));

}

@method_id(10201)
fun tsaCallGetterIn1Ret2Impl(p0: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny) {
    return return2();
}

fun tsaCallGetterIn1Ret2<A, B, C>(p0: C, idContract: int, idMethod: int): (A, B) {
    val result = tsaCallGetterIn1Ret2Impl(wrap(p0), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1));

}

@method_id(10202)
fun tsaCallGetterIn2Ret2Impl(p0: TsaAny, p1: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny) {
    return return2();
}

fun tsaCallGetterIn2Ret2<A, B, C, D>(p0: C, p1: D, idContract: int, idMethod: int): (A, B) {
    val result = tsaCallGetterIn2Ret2Impl(wrap(p0), wrap(p1), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1));

}

@method_id(10203)
fun tsaCallGetterIn3Ret2Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny) {
    return return2();
}

fun tsaCallGetterIn3Ret2<A, B, C, D, E>(p0: C, p1: D, p2: E, idContract: int, idMethod: int): (A, B) {
    val result = tsaCallGetterIn3Ret2Impl(wrap(p0), wrap(p1), wrap(p2), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1));

}

@method_id(10204)
fun tsaCallGetterIn4Ret2Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny) {
    return return2();
}

fun tsaCallGetterIn4Ret2<A, B, C, D, E, F>(p0: C, p1: D, p2: E, p3: F, idContract: int, idMethod: int): (A, B) {
    val result = tsaCallGetterIn4Ret2Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1));

}

@method_id(10205)
fun tsaCallGetterIn5Ret2Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny) {
    return return2();
}

fun tsaCallGetterIn5Ret2<A, B, C, D, E, F, G>(p0: C, p1: D, p2: E, p3: F, p4: G, idContract: int, idMethod: int): (A, B) {
    val result = tsaCallGetterIn5Ret2Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1));

}

@method_id(10206)
fun tsaCallGetterIn6Ret2Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny) {
    return return2();
}

fun tsaCallGetterIn6Ret2<A, B, C, D, E, F, G, H>(p0: C, p1: D, p2: E, p3: F, p4: G, p5: H, idContract: int, idMethod: int): (A, B) {
    val result = tsaCallGetterIn6Ret2Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1));

}

@method_id(10207)
fun tsaCallGetterIn7Ret2Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny) {
    return return2();
}

fun tsaCallGetterIn7Ret2<A, B, C, D, E, F, G, H, I>(p0: C, p1: D, p2: E, p3: F, p4: G, p5: H, p6: I, idContract: int, idMethod: int): (A, B) {
    val result = tsaCallGetterIn7Ret2Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1));

}

@method_id(10208)
fun tsaCallGetterIn8Ret2Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny) {
    return return2();
}

fun tsaCallGetterIn8Ret2<A, B, C, D, E, F, G, H, I, J>(p0: C, p1: D, p2: E, p3: F, p4: G, p5: H, p6: I, p7: J, idContract: int, idMethod: int): (A, B) {
    val result = tsaCallGetterIn8Ret2Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1));

}

@method_id(10209)
fun tsaCallGetterIn9Ret2Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny) {
    return return2();
}

fun tsaCallGetterIn9Ret2<A, B, C, D, E, F, G, H, I, J, K>(p0: C, p1: D, p2: E, p3: F, p4: G, p5: H, p6: I, p7: J, p8: K, idContract: int, idMethod: int): (A, B) {
    val result = tsaCallGetterIn9Ret2Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1));

}

@method_id(10210)
fun tsaCallGetterIn10Ret2Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, p9: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny) {
    return return2();
}

fun tsaCallGetterIn10Ret2<A, B, C, D, E, F, G, H, I, J, K, L>(p0: C, p1: D, p2: E, p3: F, p4: G, p5: H, p6: I, p7: J, p8: K, p9: L, idContract: int, idMethod: int): (A, B) {
    val result = tsaCallGetterIn10Ret2Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), wrap(p9), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1));

}

@method_id(10300)
fun tsaCallGetterIn0Ret3Impl(idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny) {
    return return3();
}

fun tsaCallGetterIn0Ret3<A, B, C>(idContract: int, idMethod: int): (A, B, C) {
    val result = tsaCallGetterIn0Ret3Impl(idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2));

}

@method_id(10301)
fun tsaCallGetterIn1Ret3Impl(p0: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny) {
    return return3();
}

fun tsaCallGetterIn1Ret3<A, B, C, D>(p0: D, idContract: int, idMethod: int): (A, B, C) {
    val result = tsaCallGetterIn1Ret3Impl(wrap(p0), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2));

}

@method_id(10302)
fun tsaCallGetterIn2Ret3Impl(p0: TsaAny, p1: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny) {
    return return3();
}

fun tsaCallGetterIn2Ret3<A, B, C, D, E>(p0: D, p1: E, idContract: int, idMethod: int): (A, B, C) {
    val result = tsaCallGetterIn2Ret3Impl(wrap(p0), wrap(p1), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2));

}

@method_id(10303)
fun tsaCallGetterIn3Ret3Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny) {
    return return3();
}

fun tsaCallGetterIn3Ret3<A, B, C, D, E, F>(p0: D, p1: E, p2: F, idContract: int, idMethod: int): (A, B, C) {
    val result = tsaCallGetterIn3Ret3Impl(wrap(p0), wrap(p1), wrap(p2), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2));

}

@method_id(10304)
fun tsaCallGetterIn4Ret3Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny) {
    return return3();
}

fun tsaCallGetterIn4Ret3<A, B, C, D, E, F, G>(p0: D, p1: E, p2: F, p3: G, idContract: int, idMethod: int): (A, B, C) {
    val result = tsaCallGetterIn4Ret3Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2));

}

@method_id(10305)
fun tsaCallGetterIn5Ret3Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny) {
    return return3();
}

fun tsaCallGetterIn5Ret3<A, B, C, D, E, F, G, H>(p0: D, p1: E, p2: F, p3: G, p4: H, idContract: int, idMethod: int): (A, B, C) {
    val result = tsaCallGetterIn5Ret3Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2));

}

@method_id(10306)
fun tsaCallGetterIn6Ret3Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny) {
    return return3();
}

fun tsaCallGetterIn6Ret3<A, B, C, D, E, F, G, H, I>(p0: D, p1: E, p2: F, p3: G, p4: H, p5: I, idContract: int, idMethod: int): (A, B, C) {
    val result = tsaCallGetterIn6Ret3Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2));

}

@method_id(10307)
fun tsaCallGetterIn7Ret3Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny) {
    return return3();
}

fun tsaCallGetterIn7Ret3<A, B, C, D, E, F, G, H, I, J>(p0: D, p1: E, p2: F, p3: G, p4: H, p5: I, p6: J, idContract: int, idMethod: int): (A, B, C) {
    val result = tsaCallGetterIn7Ret3Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2));

}

@method_id(10308)
fun tsaCallGetterIn8Ret3Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny) {
    return return3();
}

fun tsaCallGetterIn8Ret3<A, B, C, D, E, F, G, H, I, J, K>(p0: D, p1: E, p2: F, p3: G, p4: H, p5: I, p6: J, p7: K, idContract: int, idMethod: int): (A, B, C) {
    val result = tsaCallGetterIn8Ret3Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2));

}

@method_id(10309)
fun tsaCallGetterIn9Ret3Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny) {
    return return3();
}

fun tsaCallGetterIn9Ret3<A, B, C, D, E, F, G, H, I, J, K, L>(p0: D, p1: E, p2: F, p3: G, p4: H, p5: I, p6: J, p7: K, p8: L, idContract: int, idMethod: int): (A, B, C) {
    val result = tsaCallGetterIn9Ret3Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2));

}

@method_id(10310)
fun tsaCallGetterIn10Ret3Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, p9: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny) {
    return return3();
}

fun tsaCallGetterIn10Ret3<A, B, C, D, E, F, G, H, I, J, K, L, M>(p0: D, p1: E, p2: F, p3: G, p4: H, p5: I, p6: J, p7: K, p8: L, p9: M, idContract: int, idMethod: int): (A, B, C) {
    val result = tsaCallGetterIn10Ret3Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), wrap(p9), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2));

}

@method_id(10400)
fun tsaCallGetterIn0Ret4Impl(idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny) {
    return return4();
}

fun tsaCallGetterIn0Ret4<A, B, C, D>(idContract: int, idMethod: int): (A, B, C, D) {
    val result = tsaCallGetterIn0Ret4Impl(idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3));

}

@method_id(10401)
fun tsaCallGetterIn1Ret4Impl(p0: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny) {
    return return4();
}

fun tsaCallGetterIn1Ret4<A, B, C, D, E>(p0: E, idContract: int, idMethod: int): (A, B, C, D) {
    val result = tsaCallGetterIn1Ret4Impl(wrap(p0), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3));

}

@method_id(10402)
fun tsaCallGetterIn2Ret4Impl(p0: TsaAny, p1: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny) {
    return return4();
}

fun tsaCallGetterIn2Ret4<A, B, C, D, E, F>(p0: E, p1: F, idContract: int, idMethod: int): (A, B, C, D) {
    val result = tsaCallGetterIn2Ret4Impl(wrap(p0), wrap(p1), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3));

}

@method_id(10403)
fun tsaCallGetterIn3Ret4Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny) {
    return return4();
}

fun tsaCallGetterIn3Ret4<A, B, C, D, E, F, G>(p0: E, p1: F, p2: G, idContract: int, idMethod: int): (A, B, C, D) {
    val result = tsaCallGetterIn3Ret4Impl(wrap(p0), wrap(p1), wrap(p2), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3));

}

@method_id(10404)
fun tsaCallGetterIn4Ret4Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny) {
    return return4();
}

fun tsaCallGetterIn4Ret4<A, B, C, D, E, F, G, H>(p0: E, p1: F, p2: G, p3: H, idContract: int, idMethod: int): (A, B, C, D) {
    val result = tsaCallGetterIn4Ret4Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3));

}

@method_id(10405)
fun tsaCallGetterIn5Ret4Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny) {
    return return4();
}

fun tsaCallGetterIn5Ret4<A, B, C, D, E, F, G, H, I>(p0: E, p1: F, p2: G, p3: H, p4: I, idContract: int, idMethod: int): (A, B, C, D) {
    val result = tsaCallGetterIn5Ret4Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3));

}

@method_id(10406)
fun tsaCallGetterIn6Ret4Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny) {
    return return4();
}

fun tsaCallGetterIn6Ret4<A, B, C, D, E, F, G, H, I, J>(p0: E, p1: F, p2: G, p3: H, p4: I, p5: J, idContract: int, idMethod: int): (A, B, C, D) {
    val result = tsaCallGetterIn6Ret4Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3));

}

@method_id(10407)
fun tsaCallGetterIn7Ret4Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny) {
    return return4();
}

fun tsaCallGetterIn7Ret4<A, B, C, D, E, F, G, H, I, J, K>(p0: E, p1: F, p2: G, p3: H, p4: I, p5: J, p6: K, idContract: int, idMethod: int): (A, B, C, D) {
    val result = tsaCallGetterIn7Ret4Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3));

}

@method_id(10408)
fun tsaCallGetterIn8Ret4Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny) {
    return return4();
}

fun tsaCallGetterIn8Ret4<A, B, C, D, E, F, G, H, I, J, K, L>(p0: E, p1: F, p2: G, p3: H, p4: I, p5: J, p6: K, p7: L, idContract: int, idMethod: int): (A, B, C, D) {
    val result = tsaCallGetterIn8Ret4Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3));

}

@method_id(10409)
fun tsaCallGetterIn9Ret4Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny) {
    return return4();
}

fun tsaCallGetterIn9Ret4<A, B, C, D, E, F, G, H, I, J, K, L, M>(p0: E, p1: F, p2: G, p3: H, p4: I, p5: J, p6: K, p7: L, p8: M, idContract: int, idMethod: int): (A, B, C, D) {
    val result = tsaCallGetterIn9Ret4Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3));

}

@method_id(10410)
fun tsaCallGetterIn10Ret4Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, p9: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny) {
    return return4();
}

fun tsaCallGetterIn10Ret4<A, B, C, D, E, F, G, H, I, J, K, L, M, N>(p0: E, p1: F, p2: G, p3: H, p4: I, p5: J, p6: K, p7: L, p8: M, p9: N, idContract: int, idMethod: int): (A, B, C, D) {
    val result = tsaCallGetterIn10Ret4Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), wrap(p9), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3));

}

@method_id(10500)
fun tsaCallGetterIn0Ret5Impl(idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return5();
}

fun tsaCallGetterIn0Ret5<A, B, C, D, E>(idContract: int, idMethod: int): (A, B, C, D, E) {
    val result = tsaCallGetterIn0Ret5Impl(idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4));

}

@method_id(10501)
fun tsaCallGetterIn1Ret5Impl(p0: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return5();
}

fun tsaCallGetterIn1Ret5<A, B, C, D, E, F>(p0: F, idContract: int, idMethod: int): (A, B, C, D, E) {
    val result = tsaCallGetterIn1Ret5Impl(wrap(p0), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4));

}

@method_id(10502)
fun tsaCallGetterIn2Ret5Impl(p0: TsaAny, p1: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return5();
}

fun tsaCallGetterIn2Ret5<A, B, C, D, E, F, G>(p0: F, p1: G, idContract: int, idMethod: int): (A, B, C, D, E) {
    val result = tsaCallGetterIn2Ret5Impl(wrap(p0), wrap(p1), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4));

}

@method_id(10503)
fun tsaCallGetterIn3Ret5Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return5();
}

fun tsaCallGetterIn3Ret5<A, B, C, D, E, F, G, H>(p0: F, p1: G, p2: H, idContract: int, idMethod: int): (A, B, C, D, E) {
    val result = tsaCallGetterIn3Ret5Impl(wrap(p0), wrap(p1), wrap(p2), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4));

}

@method_id(10504)
fun tsaCallGetterIn4Ret5Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return5();
}

fun tsaCallGetterIn4Ret5<A, B, C, D, E, F, G, H, I>(p0: F, p1: G, p2: H, p3: I, idContract: int, idMethod: int): (A, B, C, D, E) {
    val result = tsaCallGetterIn4Ret5Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4));

}

@method_id(10505)
fun tsaCallGetterIn5Ret5Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return5();
}

fun tsaCallGetterIn5Ret5<A, B, C, D, E, F, G, H, I, J>(p0: F, p1: G, p2: H, p3: I, p4: J, idContract: int, idMethod: int): (A, B, C, D, E) {
    val result = tsaCallGetterIn5Ret5Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4));

}

@method_id(10506)
fun tsaCallGetterIn6Ret5Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return5();
}

fun tsaCallGetterIn6Ret5<A, B, C, D, E, F, G, H, I, J, K>(p0: F, p1: G, p2: H, p3: I, p4: J, p5: K, idContract: int, idMethod: int): (A, B, C, D, E) {
    val result = tsaCallGetterIn6Ret5Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4));

}

@method_id(10507)
fun tsaCallGetterIn7Ret5Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return5();
}

fun tsaCallGetterIn7Ret5<A, B, C, D, E, F, G, H, I, J, K, L>(p0: F, p1: G, p2: H, p3: I, p4: J, p5: K, p6: L, idContract: int, idMethod: int): (A, B, C, D, E) {
    val result = tsaCallGetterIn7Ret5Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4));

}

@method_id(10508)
fun tsaCallGetterIn8Ret5Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return5();
}

fun tsaCallGetterIn8Ret5<A, B, C, D, E, F, G, H, I, J, K, L, M>(p0: F, p1: G, p2: H, p3: I, p4: J, p5: K, p6: L, p7: M, idContract: int, idMethod: int): (A, B, C, D, E) {
    val result = tsaCallGetterIn8Ret5Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4));

}

@method_id(10509)
fun tsaCallGetterIn9Ret5Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return5();
}

fun tsaCallGetterIn9Ret5<A, B, C, D, E, F, G, H, I, J, K, L, M, N>(p0: F, p1: G, p2: H, p3: I, p4: J, p5: K, p6: L, p7: M, p8: N, idContract: int, idMethod: int): (A, B, C, D, E) {
    val result = tsaCallGetterIn9Ret5Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4));

}

@method_id(10510)
fun tsaCallGetterIn10Ret5Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, p9: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return5();
}

fun tsaCallGetterIn10Ret5<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O>(p0: F, p1: G, p2: H, p3: I, p4: J, p5: K, p6: L, p7: M, p8: N, p9: O, idContract: int, idMethod: int): (A, B, C, D, E) {
    val result = tsaCallGetterIn10Ret5Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), wrap(p9), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4));

}

@method_id(10600)
fun tsaCallGetterIn0Ret6Impl(idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return6();
}

fun tsaCallGetterIn0Ret6<A, B, C, D, E, F>(idContract: int, idMethod: int): (A, B, C, D, E, F) {
    val result = tsaCallGetterIn0Ret6Impl(idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5));

}

@method_id(10601)
fun tsaCallGetterIn1Ret6Impl(p0: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return6();
}

fun tsaCallGetterIn1Ret6<A, B, C, D, E, F, G>(p0: G, idContract: int, idMethod: int): (A, B, C, D, E, F) {
    val result = tsaCallGetterIn1Ret6Impl(wrap(p0), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5));

}

@method_id(10602)
fun tsaCallGetterIn2Ret6Impl(p0: TsaAny, p1: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return6();
}

fun tsaCallGetterIn2Ret6<A, B, C, D, E, F, G, H>(p0: G, p1: H, idContract: int, idMethod: int): (A, B, C, D, E, F) {
    val result = tsaCallGetterIn2Ret6Impl(wrap(p0), wrap(p1), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5));

}

@method_id(10603)
fun tsaCallGetterIn3Ret6Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return6();
}

fun tsaCallGetterIn3Ret6<A, B, C, D, E, F, G, H, I>(p0: G, p1: H, p2: I, idContract: int, idMethod: int): (A, B, C, D, E, F) {
    val result = tsaCallGetterIn3Ret6Impl(wrap(p0), wrap(p1), wrap(p2), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5));

}

@method_id(10604)
fun tsaCallGetterIn4Ret6Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return6();
}

fun tsaCallGetterIn4Ret6<A, B, C, D, E, F, G, H, I, J>(p0: G, p1: H, p2: I, p3: J, idContract: int, idMethod: int): (A, B, C, D, E, F) {
    val result = tsaCallGetterIn4Ret6Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5));

}

@method_id(10605)
fun tsaCallGetterIn5Ret6Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return6();
}

fun tsaCallGetterIn5Ret6<A, B, C, D, E, F, G, H, I, J, K>(p0: G, p1: H, p2: I, p3: J, p4: K, idContract: int, idMethod: int): (A, B, C, D, E, F) {
    val result = tsaCallGetterIn5Ret6Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5));

}

@method_id(10606)
fun tsaCallGetterIn6Ret6Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return6();
}

fun tsaCallGetterIn6Ret6<A, B, C, D, E, F, G, H, I, J, K, L>(p0: G, p1: H, p2: I, p3: J, p4: K, p5: L, idContract: int, idMethod: int): (A, B, C, D, E, F) {
    val result = tsaCallGetterIn6Ret6Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5));

}

@method_id(10607)
fun tsaCallGetterIn7Ret6Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return6();
}

fun tsaCallGetterIn7Ret6<A, B, C, D, E, F, G, H, I, J, K, L, M>(p0: G, p1: H, p2: I, p3: J, p4: K, p5: L, p6: M, idContract: int, idMethod: int): (A, B, C, D, E, F) {
    val result = tsaCallGetterIn7Ret6Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5));

}

@method_id(10608)
fun tsaCallGetterIn8Ret6Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return6();
}

fun tsaCallGetterIn8Ret6<A, B, C, D, E, F, G, H, I, J, K, L, M, N>(p0: G, p1: H, p2: I, p3: J, p4: K, p5: L, p6: M, p7: N, idContract: int, idMethod: int): (A, B, C, D, E, F) {
    val result = tsaCallGetterIn8Ret6Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5));

}

@method_id(10609)
fun tsaCallGetterIn9Ret6Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return6();
}

fun tsaCallGetterIn9Ret6<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O>(p0: G, p1: H, p2: I, p3: J, p4: K, p5: L, p6: M, p7: N, p8: O, idContract: int, idMethod: int): (A, B, C, D, E, F) {
    val result = tsaCallGetterIn9Ret6Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5));

}

@method_id(10610)
fun tsaCallGetterIn10Ret6Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, p9: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return6();
}

fun tsaCallGetterIn10Ret6<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P>(p0: G, p1: H, p2: I, p3: J, p4: K, p5: L, p6: M, p7: N, p8: O, p9: P, idContract: int, idMethod: int): (A, B, C, D, E, F) {
    val result = tsaCallGetterIn10Ret6Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), wrap(p9), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5));

}

@method_id(10700)
fun tsaCallGetterIn0Ret7Impl(idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return7();
}

fun tsaCallGetterIn0Ret7<A, B, C, D, E, F, G>(idContract: int, idMethod: int): (A, B, C, D, E, F, G) {
    val result = tsaCallGetterIn0Ret7Impl(idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6));

}

@method_id(10701)
fun tsaCallGetterIn1Ret7Impl(p0: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return7();
}

fun tsaCallGetterIn1Ret7<A, B, C, D, E, F, G, H>(p0: H, idContract: int, idMethod: int): (A, B, C, D, E, F, G) {
    val result = tsaCallGetterIn1Ret7Impl(wrap(p0), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6));

}

@method_id(10702)
fun tsaCallGetterIn2Ret7Impl(p0: TsaAny, p1: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return7();
}

fun tsaCallGetterIn2Ret7<A, B, C, D, E, F, G, H, I>(p0: H, p1: I, idContract: int, idMethod: int): (A, B, C, D, E, F, G) {
    val result = tsaCallGetterIn2Ret7Impl(wrap(p0), wrap(p1), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6));

}

@method_id(10703)
fun tsaCallGetterIn3Ret7Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return7();
}

fun tsaCallGetterIn3Ret7<A, B, C, D, E, F, G, H, I, J>(p0: H, p1: I, p2: J, idContract: int, idMethod: int): (A, B, C, D, E, F, G) {
    val result = tsaCallGetterIn3Ret7Impl(wrap(p0), wrap(p1), wrap(p2), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6));

}

@method_id(10704)
fun tsaCallGetterIn4Ret7Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return7();
}

fun tsaCallGetterIn4Ret7<A, B, C, D, E, F, G, H, I, J, K>(p0: H, p1: I, p2: J, p3: K, idContract: int, idMethod: int): (A, B, C, D, E, F, G) {
    val result = tsaCallGetterIn4Ret7Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6));

}

@method_id(10705)
fun tsaCallGetterIn5Ret7Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return7();
}

fun tsaCallGetterIn5Ret7<A, B, C, D, E, F, G, H, I, J, K, L>(p0: H, p1: I, p2: J, p3: K, p4: L, idContract: int, idMethod: int): (A, B, C, D, E, F, G) {
    val result = tsaCallGetterIn5Ret7Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6));

}

@method_id(10706)
fun tsaCallGetterIn6Ret7Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return7();
}

fun tsaCallGetterIn6Ret7<A, B, C, D, E, F, G, H, I, J, K, L, M>(p0: H, p1: I, p2: J, p3: K, p4: L, p5: M, idContract: int, idMethod: int): (A, B, C, D, E, F, G) {
    val result = tsaCallGetterIn6Ret7Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6));

}

@method_id(10707)
fun tsaCallGetterIn7Ret7Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return7();
}

fun tsaCallGetterIn7Ret7<A, B, C, D, E, F, G, H, I, J, K, L, M, N>(p0: H, p1: I, p2: J, p3: K, p4: L, p5: M, p6: N, idContract: int, idMethod: int): (A, B, C, D, E, F, G) {
    val result = tsaCallGetterIn7Ret7Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6));

}

@method_id(10708)
fun tsaCallGetterIn8Ret7Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return7();
}

fun tsaCallGetterIn8Ret7<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O>(p0: H, p1: I, p2: J, p3: K, p4: L, p5: M, p6: N, p7: O, idContract: int, idMethod: int): (A, B, C, D, E, F, G) {
    val result = tsaCallGetterIn8Ret7Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6));

}

@method_id(10709)
fun tsaCallGetterIn9Ret7Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return7();
}

fun tsaCallGetterIn9Ret7<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P>(p0: H, p1: I, p2: J, p3: K, p4: L, p5: M, p6: N, p7: O, p8: P, idContract: int, idMethod: int): (A, B, C, D, E, F, G) {
    val result = tsaCallGetterIn9Ret7Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6));

}

@method_id(10710)
fun tsaCallGetterIn10Ret7Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, p9: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return7();
}

fun tsaCallGetterIn10Ret7<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q>(p0: H, p1: I, p2: J, p3: K, p4: L, p5: M, p6: N, p7: O, p8: P, p9: Q, idContract: int, idMethod: int): (A, B, C, D, E, F, G) {
    val result = tsaCallGetterIn10Ret7Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), wrap(p9), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6));

}

@method_id(10800)
fun tsaCallGetterIn0Ret8Impl(idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return8();
}

fun tsaCallGetterIn0Ret8<A, B, C, D, E, F, G, H>(idContract: int, idMethod: int): (A, B, C, D, E, F, G, H) {
    val result = tsaCallGetterIn0Ret8Impl(idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7));

}

@method_id(10801)
fun tsaCallGetterIn1Ret8Impl(p0: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return8();
}

fun tsaCallGetterIn1Ret8<A, B, C, D, E, F, G, H, I>(p0: I, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H) {
    val result = tsaCallGetterIn1Ret8Impl(wrap(p0), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7));

}

@method_id(10802)
fun tsaCallGetterIn2Ret8Impl(p0: TsaAny, p1: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return8();
}

fun tsaCallGetterIn2Ret8<A, B, C, D, E, F, G, H, I, J>(p0: I, p1: J, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H) {
    val result = tsaCallGetterIn2Ret8Impl(wrap(p0), wrap(p1), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7));

}

@method_id(10803)
fun tsaCallGetterIn3Ret8Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return8();
}

fun tsaCallGetterIn3Ret8<A, B, C, D, E, F, G, H, I, J, K>(p0: I, p1: J, p2: K, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H) {
    val result = tsaCallGetterIn3Ret8Impl(wrap(p0), wrap(p1), wrap(p2), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7));

}

@method_id(10804)
fun tsaCallGetterIn4Ret8Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return8();
}

fun tsaCallGetterIn4Ret8<A, B, C, D, E, F, G, H, I, J, K, L>(p0: I, p1: J, p2: K, p3: L, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H) {
    val result = tsaCallGetterIn4Ret8Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7));

}

@method_id(10805)
fun tsaCallGetterIn5Ret8Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return8();
}

fun tsaCallGetterIn5Ret8<A, B, C, D, E, F, G, H, I, J, K, L, M>(p0: I, p1: J, p2: K, p3: L, p4: M, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H) {
    val result = tsaCallGetterIn5Ret8Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7));

}

@method_id(10806)
fun tsaCallGetterIn6Ret8Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return8();
}

fun tsaCallGetterIn6Ret8<A, B, C, D, E, F, G, H, I, J, K, L, M, N>(p0: I, p1: J, p2: K, p3: L, p4: M, p5: N, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H) {
    val result = tsaCallGetterIn6Ret8Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7));

}

@method_id(10807)
fun tsaCallGetterIn7Ret8Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return8();
}

fun tsaCallGetterIn7Ret8<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O>(p0: I, p1: J, p2: K, p3: L, p4: M, p5: N, p6: O, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H) {
    val result = tsaCallGetterIn7Ret8Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7));

}

@method_id(10808)
fun tsaCallGetterIn8Ret8Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return8();
}

fun tsaCallGetterIn8Ret8<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P>(p0: I, p1: J, p2: K, p3: L, p4: M, p5: N, p6: O, p7: P, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H) {
    val result = tsaCallGetterIn8Ret8Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7));

}

@method_id(10809)
fun tsaCallGetterIn9Ret8Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return8();
}

fun tsaCallGetterIn9Ret8<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q>(p0: I, p1: J, p2: K, p3: L, p4: M, p5: N, p6: O, p7: P, p8: Q, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H) {
    val result = tsaCallGetterIn9Ret8Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7));

}

@method_id(10810)
fun tsaCallGetterIn10Ret8Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, p9: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return8();
}

fun tsaCallGetterIn10Ret8<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R>(p0: I, p1: J, p2: K, p3: L, p4: M, p5: N, p6: O, p7: P, p8: Q, p9: R, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H) {
    val result = tsaCallGetterIn10Ret8Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), wrap(p9), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7));

}

@method_id(10900)
fun tsaCallGetterIn0Ret9Impl(idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return9();
}

fun tsaCallGetterIn0Ret9<A, B, C, D, E, F, G, H, I>(idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I) {
    val result = tsaCallGetterIn0Ret9Impl(idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8));

}

@method_id(10901)
fun tsaCallGetterIn1Ret9Impl(p0: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return9();
}

fun tsaCallGetterIn1Ret9<A, B, C, D, E, F, G, H, I, J>(p0: J, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I) {
    val result = tsaCallGetterIn1Ret9Impl(wrap(p0), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8));

}

@method_id(10902)
fun tsaCallGetterIn2Ret9Impl(p0: TsaAny, p1: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return9();
}

fun tsaCallGetterIn2Ret9<A, B, C, D, E, F, G, H, I, J, K>(p0: J, p1: K, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I) {
    val result = tsaCallGetterIn2Ret9Impl(wrap(p0), wrap(p1), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8));

}

@method_id(10903)
fun tsaCallGetterIn3Ret9Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return9();
}

fun tsaCallGetterIn3Ret9<A, B, C, D, E, F, G, H, I, J, K, L>(p0: J, p1: K, p2: L, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I) {
    val result = tsaCallGetterIn3Ret9Impl(wrap(p0), wrap(p1), wrap(p2), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8));

}

@method_id(10904)
fun tsaCallGetterIn4Ret9Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return9();
}

fun tsaCallGetterIn4Ret9<A, B, C, D, E, F, G, H, I, J, K, L, M>(p0: J, p1: K, p2: L, p3: M, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I) {
    val result = tsaCallGetterIn4Ret9Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8));

}

@method_id(10905)
fun tsaCallGetterIn5Ret9Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return9();
}

fun tsaCallGetterIn5Ret9<A, B, C, D, E, F, G, H, I, J, K, L, M, N>(p0: J, p1: K, p2: L, p3: M, p4: N, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I) {
    val result = tsaCallGetterIn5Ret9Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8));

}

@method_id(10906)
fun tsaCallGetterIn6Ret9Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return9();
}

fun tsaCallGetterIn6Ret9<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O>(p0: J, p1: K, p2: L, p3: M, p4: N, p5: O, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I) {
    val result = tsaCallGetterIn6Ret9Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8));

}

@method_id(10907)
fun tsaCallGetterIn7Ret9Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return9();
}

fun tsaCallGetterIn7Ret9<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P>(p0: J, p1: K, p2: L, p3: M, p4: N, p5: O, p6: P, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I) {
    val result = tsaCallGetterIn7Ret9Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8));

}

@method_id(10908)
fun tsaCallGetterIn8Ret9Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return9();
}

fun tsaCallGetterIn8Ret9<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q>(p0: J, p1: K, p2: L, p3: M, p4: N, p5: O, p6: P, p7: Q, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I) {
    val result = tsaCallGetterIn8Ret9Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8));

}

@method_id(10909)
fun tsaCallGetterIn9Ret9Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return9();
}

fun tsaCallGetterIn9Ret9<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R>(p0: J, p1: K, p2: L, p3: M, p4: N, p5: O, p6: P, p7: Q, p8: R, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I) {
    val result = tsaCallGetterIn9Ret9Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8));

}

@method_id(10910)
fun tsaCallGetterIn10Ret9Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, p9: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return9();
}

fun tsaCallGetterIn10Ret9<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S>(p0: J, p1: K, p2: L, p3: M, p4: N, p5: O, p6: P, p7: Q, p8: R, p9: S, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I) {
    val result = tsaCallGetterIn10Ret9Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), wrap(p9), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8));

}

@method_id(11000)
fun tsaCallGetterIn0Ret10Impl(idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return10();
}

fun tsaCallGetterIn0Ret10<A, B, C, D, E, F, G, H, I, J>(idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I, J) {
    val result = tsaCallGetterIn0Ret10Impl(idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8), unwrap<J>(result.9));

}

@method_id(11001)
fun tsaCallGetterIn1Ret10Impl(p0: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return10();
}

fun tsaCallGetterIn1Ret10<A, B, C, D, E, F, G, H, I, J, K>(p0: K, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I, J) {
    val result = tsaCallGetterIn1Ret10Impl(wrap(p0), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8), unwrap<J>(result.9));

}

@method_id(11002)
fun tsaCallGetterIn2Ret10Impl(p0: TsaAny, p1: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return10();
}

fun tsaCallGetterIn2Ret10<A, B, C, D, E, F, G, H, I, J, K, L>(p0: K, p1: L, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I, J) {
    val result = tsaCallGetterIn2Ret10Impl(wrap(p0), wrap(p1), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8), unwrap<J>(result.9));

}

@method_id(11003)
fun tsaCallGetterIn3Ret10Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return10();
}

fun tsaCallGetterIn3Ret10<A, B, C, D, E, F, G, H, I, J, K, L, M>(p0: K, p1: L, p2: M, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I, J) {
    val result = tsaCallGetterIn3Ret10Impl(wrap(p0), wrap(p1), wrap(p2), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8), unwrap<J>(result.9));

}

@method_id(11004)
fun tsaCallGetterIn4Ret10Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return10();
}

fun tsaCallGetterIn4Ret10<A, B, C, D, E, F, G, H, I, J, K, L, M, N>(p0: K, p1: L, p2: M, p3: N, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I, J) {
    val result = tsaCallGetterIn4Ret10Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8), unwrap<J>(result.9));

}

@method_id(11005)
fun tsaCallGetterIn5Ret10Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return10();
}

fun tsaCallGetterIn5Ret10<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O>(p0: K, p1: L, p2: M, p3: N, p4: O, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I, J) {
    val result = tsaCallGetterIn5Ret10Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8), unwrap<J>(result.9));

}

@method_id(11006)
fun tsaCallGetterIn6Ret10Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return10();
}

fun tsaCallGetterIn6Ret10<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P>(p0: K, p1: L, p2: M, p3: N, p4: O, p5: P, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I, J) {
    val result = tsaCallGetterIn6Ret10Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8), unwrap<J>(result.9));

}

@method_id(11007)
fun tsaCallGetterIn7Ret10Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return10();
}

fun tsaCallGetterIn7Ret10<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q>(p0: K, p1: L, p2: M, p3: N, p4: O, p5: P, p6: Q, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I, J) {
    val result = tsaCallGetterIn7Ret10Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8), unwrap<J>(result.9));

}

@method_id(11008)
fun tsaCallGetterIn8Ret10Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return10();
}

fun tsaCallGetterIn8Ret10<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R>(p0: K, p1: L, p2: M, p3: N, p4: O, p5: P, p6: Q, p7: R, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I, J) {
    val result = tsaCallGetterIn8Ret10Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8), unwrap<J>(result.9));

}

@method_id(11009)
fun tsaCallGetterIn9Ret10Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return10();
}

fun tsaCallGetterIn9Ret10<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S>(p0: K, p1: L, p2: M, p3: N, p4: O, p5: P, p6: Q, p7: R, p8: S, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I, J) {
    val result = tsaCallGetterIn9Ret10Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8), unwrap<J>(result.9));

}

@method_id(11010)
fun tsaCallGetterIn10Ret10Impl(p0: TsaAny, p1: TsaAny, p2: TsaAny, p3: TsaAny, p4: TsaAny, p5: TsaAny, p6: TsaAny, p7: TsaAny, p8: TsaAny, p9: TsaAny, idContract: int, idMethod: int): (TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny, TsaAny) {
    return return10();
}

fun tsaCallGetterIn10Ret10<A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T>(p0: K, p1: L, p2: M, p3: N, p4: O, p5: P, p6: Q, p7: R, p8: S, p9: T, idContract: int, idMethod: int): (A, B, C, D, E, F, G, H, I, J) {
    val result = tsaCallGetterIn10Ret10Impl(wrap(p0), wrap(p1), wrap(p2), wrap(p3), wrap(p4), wrap(p5), wrap(p6), wrap(p7), wrap(p8), wrap(p9), idContract, idMethod);
    return (unwrap<A>(result.0), unwrap<B>(result.1), unwrap<C>(result.2), unwrap<D>(result.3), unwrap<E>(result.4), unwrap<F>(result.5), unwrap<G>(result.6), unwrap<H>(result.7), unwrap<I>(result.8), unwrap<J>(result.9));

}