const MyWorkchain: Bool = false;
// Storage constants

// This is enough for 1 year of storage
// And more than 8 years more until freeze
const minTonsForStorage: Int = ton("0.01");
const gasForTransfer: Int = 10200;
const gasForBurn: Int = 7500;

// This is an approximate value. Real one must be less than this, and it is checked in tests.
const walletStateInitCells: Int = 30;
const walletStateInitBits: Int = 20000;
