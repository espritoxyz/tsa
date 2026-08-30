import "../../imports/tsa_functions"


fun main() {
    val getResult = tsaCallGetterIn0Ret1<int>(1, 0x16e52);
    assert (getResult == 42) throw 100;
    throw 1000;
}
