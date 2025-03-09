trait MinterExitcodes {
    const IncorrectSender: Int = 73;
    const UnauthorizedBurn: Int = 74;
    const InsufficientGasForDiscovery: Int = 75;
}

trait WalletExitcodes {
    const IncorrectSender: Int = 705;
    const IncorrectBalanceAfrerSend: Int = 706;
    const IncorrectSenderInternal: Int = 707; // Yeah, two same exit codes are used for different purposes
    //IncorrectSenderInternal - https://github.com/ton-blockchain/token-contract/blob/main/ft/jetton-wallet.fc#L117
    const UnsufficientAmountOfTonForBurn: Int = 707;
    //UsufficientAmountOfTonForBurn - https://github.com/ton-blockchain/token-contract/blob/main/ft/jetton-wallet.fc#L175
    const UnsufficientAmountOfTonAttached: Int = 709;
    const InvalidDestinationWorkchain: Int = 333;
}

trait GasConstants {
    const gasForBurn: Int = ton("0.011408799");
    const gasForDiscovery: Int = ton("0.0067056");
}