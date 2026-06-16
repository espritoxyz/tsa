package org.ton.test.gen.dsl.wrapper.basic

import org.ton.test.gen.dsl.TsContext
import org.ton.test.gen.dsl.wrapper.TsWrapperDescriptor

class TsBasicWrapperDescriptor(
    override val ctx: TsContext,
    override val name: String,
) : TsWrapperDescriptor<TsBasicWrapper> {
    override val wrapperType: TsBasicWrapper = TsBasicWrapper(name)

    override fun renderFile(): String =
        """
        import {Address, Cell, Contract, ContractProvider, TupleItem} from '@ton/core'
        import {Blockchain, createShardAccount, internal} from "@ton/sandbox"

        export class $name implements Contract {
            readonly init: { code: Cell, data: Cell }

            constructor(readonly address: Address, code: Cell, data: Cell) {
                this.init = { code: code, data: data }
            }

            async internal(
                blockchain: Blockchain,
                sender: Address,
                body: Cell,
                value: bigint,
                bounce: boolean,
                bounced: boolean,
                ihrDisabled: boolean,
                ihrFee: bigint,
                forwardFee: bigint,
                createdLt: bigint,
                createdAt: number,
            ) {
                return await blockchain.sendMessage(internal({
                    from: sender,
                    to: this.address,
                    body: body,
                    value: value ,
                    bounce: bounce,
                    bounced: bounced,
                    ihrDisabled: ihrDisabled,
                    ihrFee: ihrFee,
                    forwardFee: forwardFee,
                    createdLt: createdLt,
                    createdAt: createdAt,
                }))
            }

            async external(
                blockchain: Blockchain,
                body: Cell,
            ) {
                return await blockchain.sendMessage({
                    info: {
                        type: 'external-in',
                        dest: this.address,
                        importFee: 0n,
                    },
                    body: body,
                })
            }

            async initializeContract(
                blockchain: Blockchain,
                balance: bigint,
                duePayment: bigint = 0n,
                storageUsedCells: bigint = 0n,
                storageUsedBits: bigint = 0n,
                storageLastPaidDelta: number = 0,
            ) {
                const contr = await blockchain.getContract(this.address);
                // When the test engineers a non-zero storage fee (via
                // `storageUsedCells/Bits` and `storageLastPaidDelta`), we must
                // ensure the contract has enough nanograms to actually pay it
                // in the storage phase. Otherwise the account would be frozen
                // and the compute phase would be skipped.
                const effectiveBalance =
                    storageUsedCells > 0n || storageUsedBits > 0n
                        ? balance + 100_000_000_000n // + 100 TON cushion for storage fee
                        : balance
                contr.account = createShardAccount({
                    address: this.address,
                    code: this.init.code,
                    data: this.init.data,
                    balance: effectiveBalance,
                    workchain: 0
                })
                if (duePayment > 0n || storageUsedCells > 0n || storageUsedBits > 0n || storageLastPaidDelta > 0) {
                    const lastPaid = (blockchain.now ?? Math.floor(Date.now() / 1000)) - storageLastPaidDelta
                    contr.account = {
                        ...contr.account,
                        account: {
                            ...contr.account.account!,
                            storageStats: {
                                ...contr.account.account!.storageStats,
                                used: { cells: storageUsedCells, bits: storageUsedBits },
                                lastPaid: lastPaid,
                                duePayment: duePayment > 0n ? duePayment : null,
                            },
                        },
                    }
                }
            }

            async get(provider: ContractProvider, name: string, args: TupleItem[]) {
                return await provider.get(name, args)
            }
        }
        """.trimIndent()
}
