// Manually created file not to be used in the actually good tests

// auxiliary functions

@pure
fun return_1<A>(): (A)
    asm "NOP";
@pure
fun return_2<A, B>(): (A, B)
    asm "NOP";
@pure
fun return_3<A, B, C>(): (A, B, C)
    asm "NOP";
@pure
fun return_4<A, B, C, D>(): (A, B, C, D)
    asm "NOP";
@pure
fun return_5<A, B, C, D, E>(): (A, B, C, D, E)
    asm "NOP";
@pure
fun return_6<A, B, C, D, E, F>(): (A, B, C, D, E, F)
    asm "NOP";
@pure
fun return_7<A, B, C, D, E, F, G>(): (A, B, C, D, E, F, G)
    asm "NOP";
@pure
fun return_8<A, B, C, D, E, F, G, H>(): (A, B, C, D, E, F, G, H)
    asm "NOP";
@pure
fun return_9<A, B, C, D, E, F, G, H, I>(): (A, B, C, D, E, F, G, H, I)
    asm "NOP";
@pure
fun return_10<A, B, C, D, E, F, G, H, I, J>(): (A, B, C, D, E, F, G, H, I, J)
    asm "NOP";

// API functions

@method_id(1)
fun tsa_forbid_failures() {
    // do nothing
}

@method_id(2)
fun tsa_allow_failures() {
    // do nothing
}

@method_id(3)
fun tsa_assert(condition: int) {
    // do nothing
}

@method_id(4)
fun tsa_assert_not(condition: int) {
    // do nothing
}

// @method_id(5)
fun tsa_fetch_value<A>(value: A, valueId: int) {
    // do nothing
}

@method_id(6)
fun tsa_send_internal_message(contractId: int, inputId: int) {
    // do nothing
}

@method_id(7)
fun tsa_get_c4(contractId: int): cell {
    return return_1();
}

@method_id(8)
fun tsa_send_external_message(contractId: int, inputId: int) {
    // do nothing
}

@method_id(9)
fun tsa_get_balance(contractId: int): int {
    return return_1();
}

// this function shouldn't be used while executing contract [contract_id] (inside checker handlers)
@method_id(10)
fun tsa_set_c4(contractId: int, value: cell) {
    // do nothing
}

@method_id(11)
fun tsa_make_address_random(address: address) {
    // do nothing
}

@method_id(12)
fun tsa_make_slice_independent_from_random_addresses(independentSlice: slice) {
    // do nothing
}

@method_id(13)
fun tsa_input_was_accepted(inputId: int): int {
    return return_1();
}

@method_id(14)
fun tsa_send_internal_message_with_body(body: slice, contractId: int, inputId: int) {
    // do nothing
}

@method_id(15)
fun tsa_send_external_message_with_body(body: slice, contractId: int, inputId: int) {
    // do nothing
}

// making symbolic values API functions

@method_id(100)
fun tsa_mk_int(bits: int, signed: int): int {
    return return_1();
}

// calling methods functions

// @method_id(10000)
fun tsa_call_0_0<A>(idContract: int, idMethod: int) {
    // do nothing
}

// @method_id(10001)
fun tsa_call_0_1<A>(p0: A, idContract: int, idMethod: int) {
    // do nothing
}

// @method_id(10002)
fun tsa_call_0_2<A, B>(p0: A, p1: B, idContract: int, idMethod: int) {
    // do nothing
}

// @method_id(10003)
fun tsa_call_0_3<A, B, C>(p0: A, p1: B, p2: C, idContract: int, idMethod: int) {
    // do nothing
}

// @method_id(10004)
fun tsa_call_0_4<A, B, C, D>(p0: A, p1: B, p2: C, p3: D, idContract: int, idMethod: int) {
    // do nothing
}

// @method_id(10005)
fun tsa_call_0_5<A, B, C, D, E>(p0: A, p1: B, p2: C, p3: D, p4: E, idContract: int, idMethod: int) {
    // do nothing
}

// @method_id(10006)
fun tsa_call_0_6<A, B, C, D, E, F>(p0: A, p1: B, p2: C, p3: D, p4: E, p5: F, idContract: int, idMethod: int) {
    // do nothing
}

// @method_id(10007)
fun tsa_call_0_7<A, B, C, D, E, F, G>(p0: A, p1: B, p2: C, p3: D, p4: E, p5: F, p6: G, idContract: int, idMethod: int) {
    // do nothing
}

// @method_id(10008)
fun tsa_call_0_8<A, B, C, D, E, F, G, H>(p0: A, p1: B, p2: C, p3: D, p4: E, p5: F, p6: G, p7: H, idContract: int, idMethod: int) {
    // do nothing
}

// @method_id(10009)
fun tsa_call_0_9<A, B, C, D, E, F, G, H, I>(p0: A, p1: B, p2: C, p3: D, p4: E, p5: F, p6: G, p7: H, p8: I, idContract: int, idMethod: int) {
    // do nothing
}

// @method_id(10010)
fun tsa_call_0_10<A, B, C, D, E, F, G, H, I, J>(p0: A, p1: B, p2: C, p3: D, p4: E, p5: F, p6: G, p7: H, p8: I, p9: J, idContract: int, idMethod: int) {
    // do nothing
}

// @method_id(10100)
fun tsa_call_1_0<A>(idContract: int, idMethod: int): (A) {
    return return_1();
}

// @method_id(10101)
fun tsa_call_1_1<A, B>(p0: B, idContract: int, idMethod: int): (A) {
    return return_1();
}

// @method_id(10102)
fun tsa_call_1_2<A, B, C>(p0: B, p1: C, idContract: int, idMethod: int): (A) {
    return return_1();
}

// @method_id(10103)
fun tsa_call_1_3<A, B, C, D>(p0: B, p1: C, p2: D, idContract: int, idMethod: int): (A) {
    return return_1();
}

// @method_id(10104)
fun tsa_call_1_4<A, B, C, D, E>(p0: B, p1: C, p2: D, p3: E, idContract: int, idMethod: int): (A) {
    return return_1();
}

// @method_id(10105)
fun tsa_call_1_5<A, B, C, D, E, F>(p0: B, p1: C, p2: D, p3: E, p4: F, idContract: int, idMethod: int): (A) {
    return return_1();
}

// @method_id(10106)
fun tsa_call_1_6<A, B, C, D, E, F, G>(p0: B, p1: C, p2: D, p3: E, p4: F, p5: G, idContract: int, idMethod: int): (A) {
    return return_1();
}

@method_id(10107)
fun tsa_call_1_7<A, B, C, D, E, F, G, H>(p0: B, p1: C, p2: D, p3: E, p4: F, p5: G, p6: H, idContract: int, idMethod: int): (A) {
    return return_1();
}

// @method_id(10108)
fun tsa_call_1_8<A, B, C, D, E, F, G, H, I>(p0: B, p1: C, p2: D, p3: E, p4: F, p5: G, p6: H, p7: I, idContract: int, idMethod: int): (A) {
    return return_1();
}

// @method_id(10109)
fun tsa_call_1_9<A, B, C, D, E, F, G, H, I, J>(p0: B, p1: C, p2: D, p3: E, p4: F, p5: G, p6: H, p7: I, p8: J, idContract: int, idMethod: int): (A) {
    return return_1();
}

// @method_id(10110)
fun tsa_call_1_10<A, B, C, D, E, F, G, H, I, J, K>(p0: B, p1: C, p2: D, p3: E, p4: F, p5: G, p6: H, p7: I, p8: J, p9: K, idContract: int, idMethod: int): (A) {
    return return_1();
}

// @method_id(10200)
fun tsa_call_2_0<A, B>(idContract: int, idMethod: int): (A, B) {
    return return_2();
}

// @method_id(10201)
fun tsa_call_2_1<A, B, C>(p0: C, idContract: int, idMethod: int): (A, B) {
    return return_2();
}

// @method_id(10202)
fun tsa_call_2_2<A, B, C, D>(p0: C, p1: D, idContract: int, idMethod: int): (A, B) {
    return return_2();
}

// @method_id(10203)
fun tsa_call_2_3<A, B, C, D, E>(p0: C, p1: D, p2: E, idContract: int, idMethod: int): (A, B) {
    return return_2();
}

// @method_id(10204)
fun tsa_call_2_4<A, B, C, D, E, F>(p0: C, p1: D, p2: E, p3: F, idContract: int, idMethod: int): (A, B) {
    return return_2();
}

