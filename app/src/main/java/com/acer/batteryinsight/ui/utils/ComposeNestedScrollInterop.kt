/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.utils

import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

class ComposeNestedScrollInterop(
    view: View,
) : NestedScrollConnection {

    private val helper = NestedScrollingChildHelper(view).apply {
        isNestedScrollingEnabled = true
    }

    private val consumedScroll = IntArray(2)

    private fun NestedScrollSource.toViewType(): Int =
        if (this == NestedScrollSource.Fling) ViewCompat.TYPE_NON_TOUCH
        else ViewCompat.TYPE_TOUCH

    private fun ensureStarted(type: Int) {
        if (!helper.hasNestedScrollingParent(type)) {
            helper.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, type)
        }
    }

    private fun toViewDelta(value: Float): Int = (-value).roundToInt()
    private fun toComposeDelta(value: Int): Float = -value.toFloat()

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val type = source.toViewType()
        ensureStarted(type)
        consumedScroll[0] = 0
        consumedScroll[1] = 0
        helper.dispatchNestedPreScroll(
            toViewDelta(available.x),
            toViewDelta(available.y),
            consumedScroll,
            null,
            type,
        )
        return Offset(
            toComposeDelta(consumedScroll[0]),
            toComposeDelta(consumedScroll[1]),
        )
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val type = source.toViewType()
        ensureStarted(type)
        consumedScroll[0] = 0
        consumedScroll[1] = 0
        helper.dispatchNestedScroll(
            toViewDelta(consumed.x),
            toViewDelta(consumed.y),
            toViewDelta(available.x),
            toViewDelta(available.y),
            null,
            type,
            consumedScroll,
        )
        return Offset(
            toComposeDelta(consumedScroll[0]),
            toComposeDelta(consumedScroll[1]),
        )
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val consumed = helper.dispatchNestedPreFling(-available.x, -available.y)
        return if (consumed) available else Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        helper.dispatchNestedFling(-available.x, -available.y, true)
        return available
    }

    fun stop() {
        helper.stopNestedScroll(ViewCompat.TYPE_TOUCH)
        helper.stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
    }
}
