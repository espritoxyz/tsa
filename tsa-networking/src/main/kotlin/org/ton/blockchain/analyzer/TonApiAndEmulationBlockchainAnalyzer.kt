package org.ton.blockchain.analyzer

import org.ton.blockchain.info.TonBlockchainInfoExtractor

class TonApiAndEmulationBlockchainAnalyzer(
    emulatorLibPath: String,
    override val infoExtractor: TonBlockchainInfoExtractor,
) : TvmBlockchainAnalyzerBase(
        emulatorLibPath = emulatorLibPath,
    )
