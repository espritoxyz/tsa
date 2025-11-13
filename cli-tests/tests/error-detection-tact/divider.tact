contract Divider {
  init() {}
  receive() {}

  get fun subtractAndDivide(subtrahend: Int, dividend: Int, flag: Bool): Int {
    let divider: Int = 42;

    if (flag) {
      divider -= subtrahend;
    } else {
      divider = 2;
    }

    if (dividend == 100) {
      throw(100);
    }

    return dividend / divider;
  }
}
