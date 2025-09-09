package org.ton.test.gen.dsl.wrapper.basic

import org.ton.test.gen.dsl.TsContext
import org.ton.test.gen.dsl.wrapper.TsWrapperDescriptor

class TsBasicWrapperDescriptor(
    override val ctx: TsContext,
    override val name: String
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

            async initializeContract(blockchain: Blockchain, balance: bigint) {
                const contr = await blockchain.getContract(this.address);
                contr.account = createShardAccount({
                    address: this.address,
                    code: this.init.code,
                    data: this.init.data,
                    balance: balance,
                    workchain: 0
                })
            }

            async get(provider: ContractProvider, name: string, args: TupleItem[]) {
                return await provider.get(name, args)
            }
        }
        """.trimIndent()
}
