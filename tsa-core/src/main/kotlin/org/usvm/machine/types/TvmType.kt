package org.usvm.machine.types

sealed class TvmType(
    val parentType: TvmType?,
)

/** type that exists in TVM */
sealed class TvmRealType(
    parentType: TvmType,
) : TvmType(parentType)

/** reference type that exists in TVM */
sealed class TvmRealReferenceType(
    parentType: TvmType,
) : TvmRealType(parentType)

data object TvmAnyType : TvmType(null)

data object TvmNullType : TvmRealType(TvmAnyType)

data object TvmIntegerType : TvmRealType(TvmAnyType)

data object TvmTupleType : TvmRealType(TvmAnyType)

// TODO there are functional types in FunC (such as int -> cell) that are represented as continuations in bytecode,
//  but there are no examples of such usage, so for now assume that continuations cannot be passed as method parameters
data object TvmContinuationType : TvmRealType(TvmAnyType)

/** Reference types */

sealed interface TvmFinalReferenceType

data object TvmSliceType : TvmRealReferenceType(TvmAnyType), TvmFinalReferenceType

data object TvmBuilderType : TvmRealReferenceType(TvmAnyType), TvmFinalReferenceType

data object TvmCellType : TvmRealReferenceType(TvmAnyType)

data object TvmDataCellType : TvmType(TvmCellType), TvmFinalReferenceType

data object TvmDictCellType : TvmType(TvmCellType), TvmFinalReferenceType
