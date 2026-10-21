package com.jaz3d.app.model

import kotlin.math.max
import kotlin.math.min

object MorphEngine {
    fun normalize(value: Float): Float =
        min(1f, max(0f, value))

    fun width(value: Float, minimum: Float, maximum: Float): Float {
        return minimum +
            normalize(value) * (maximum - minimum)
    }

    fun heightScale(value: Float): Float =
        width(value, 0.85f, 1.15f)

    fun buildScale(value: Float): Float =
        width(value, 0.80f, 1.25f)

    fun shoulderScale(value: Float): Float =
        width(value, 0.75f, 1.30f)

    fun hipScale(value: Float): Float =
        width(value, 0.75f, 1.30f)

    fun headScale(value: Float): Float =
        width(value, 0.85f, 1.20f)
}
