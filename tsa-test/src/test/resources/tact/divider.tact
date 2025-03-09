contract Divider {

  init() {}

  receive() {
    // Do nothing
  }

  get fun divide(value: Int, flag: Bool): Int {
    if (flag) {
      return 42 / value;
    } else {
      return value;
    }
  }
}