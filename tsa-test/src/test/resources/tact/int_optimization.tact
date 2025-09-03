message(296627) Guess {
  guess: Bool;
}

fun computeNumber(a: Int, b: Int): Int {
    return (a * b) + (a * (b / 2));
}

contract GuessGame {

  consecutiveWins: Int = 0;
  triesCount: Int = 0;

  init() { }

  receive(msg: Guess) {
    let n = computeNumber(self.triesCount, self.consecutiveWins) % 2 == 0;
    self.consecutiveWins = msg.guess == n ? self.consecutiveWins + 1 : 0;
    self.triesCount += 1;
  }

  get(-10) fun consecutiveWins(): Int {
    return self.consecutiveWins;
  }
}
