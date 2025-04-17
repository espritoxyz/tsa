# TL-B schemes support

We introduce support to TL-B schemes for 2 reasons:

1. To be able to eliminate inputs that do not satisfy given TL-B schemes.
2. To find errors in parsing TL-B schemes.

These 2 goals are achieved by different mechanisms, both of which work with intermediate API for storing TL-B schemes.

## API for storing TL-B schemes

For storing TL-B schemes we introduce two core classes: `TlbStructure` and `TlbLabel`.

`TlbLabel` might be atomic or composite. Each composite `TlbLabel` has an internal structure of type `TlbStructure`. It corresponds to a TL-B definition with applied arguments.

Some `TlbLabel`s are built-in. That means that they have corresponding TVM read instruction.

## Eliminating inputs with wrong format

For this we need to generate several structural constraints.

The first idea was to generate all those constraints at the beginning, before executing anything. The problem with this approach is that there might be arbitrarily many addresses with structural constraints. This is why it was decided to generate those constraints lazily.

First, we generate structural constraints only for root input addresses. Then, the first time a child of the cell `c` is requested, we generate constraints for all children of `c`.

The component that is responsible for these actions is `GlobalStructuralConstraintsHolder`.

A corner case of this approach is a situation when a child is requested for the first time only during test resolving. This case is processed separately.

## Finding parsing errors

We introduced several handlers for interpreter that should be called when performing parsing operations. Examples of such actions:

- Load reference from slice `s`.
- Assert `end_of_parse` of slice `s`.
- Load `data` from slice `s` (`data` can be integer, bit array, maybe constructor bit).

Each action might end up with parsing error. Our goal is to infer constraints when this happens, and fork on them.

We store a position of each slice as `TlbStack`. Some of the conflicts are detected by analyzing it. Others rely only on some precalculated TL-B characteristics.

## Auxiliary TL-B characteristics

All described operations (generating structural constraints, detecting TL-B conflicts) use some precalculated values for instances of `TlbLabel` like symbolic data/refs length, maximum number of children, default cell content. All of these are contained within `CalculatedTlbLabelInfo`. They are calculated at the very beginning of the analysis using dynamic programming approach.

## TL-B for builders

Class for storing TL-B info for builders is `TlbStructureBuilder`. It produces `TlbStructure` with `end` method.

TL-B structures for allocated cells are based on used `store` operation. Usually `store_x` adds the corresponding `TlbLabel` in the building structure. A special case is `store_(u)int` for constant value. That produces `switch` with one variant, the stored constant.

## Some TL-B parsing rules

- `skip_bits` is allowed only for integers and switches of size 1.

## What's not supported

- After `load_bits`, TL-B scheme in the read slice is lost
- `store_slice`
- Comparing produced TL-B scheme for a builded cell with the expected one
- `bits` in TL-B scheme
- Type arguments are supported only for `int` (though not supported by TL-B parser yet)
- Some other complex constructions in TL-B definitions
