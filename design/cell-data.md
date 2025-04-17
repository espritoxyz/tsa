# Cell data 

Cell data can be stored in a bitvector in two ways:

1. Using low-order bits
2. Using high-order bits

The key difference between these approaches lies in the use of the `cellDataLength` field. When data is stored in the low-order bits, load expressions use the `cellDataLength` field, whereas store expressions do not. Conversely, when data is stored in the high-order bits, store expressions use the `cellDataLength` field, and load expressions do not.

Most of the time, bits are loaded from the input slices, where `cellDataLength` is symbolic. For the builder, it is expected that the exact value of `cellDataLength` will be often known. Therefore, it is preferable to store data in the high-order bits.