const gasForBurn: Int = 6700;
const gasForTransfer: Int = 10500;
const minTonsForStorage: Int = ton("0.01"); // This should use https://github.com/tact-lang/tact/issues/2336 in the future
const Basechain: Int = 0;

// This is an approximate value. Real one must be less than this, and it is checked in tests.
const walletStateInitCells: Int = 30;
const walletStateInitBits: Int = 20000;
