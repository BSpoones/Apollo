package com.beespoon.namespaced
import kotlinx.serialization.*
interface PlainShape
@Serializable @SerialName("pdot") data class PDot(val radius: Int = 1) : PlainShape
@Serializable data class PlainShapeConfig(val shape: PlainShape = PDot())
