contract Divider {

  init() {}

  receive() {
    // Do nothing
  }

  get fun divide(subtrahend: Int, dividend: Int, flag: Bool): Int {
    let divider: Int = 42;

    if (flag) {
      divider -= subtrahend;
    } else {
      divider = 2;
    }

    return dividend / divider;
  }
}