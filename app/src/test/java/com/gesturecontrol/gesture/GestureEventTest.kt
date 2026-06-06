package com.gesturecontrol.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureEventTest {

    @Test
    fun `Static event holds type and hand`() {
        val event = GestureEvent.Static(StaticGestureType.VICTORY, HandSide.RIGHT)
        assertEquals(StaticGestureType.VICTORY, event.type)
        assertEquals(HandSide.RIGHT, event.hand)
    }

    @Test
    fun `Dynamic event holds direction and hand`() {
        val event = GestureEvent.Dynamic(SwipeDirection.LEFT, HandSide.LEFT)
        assertEquals(SwipeDirection.LEFT, event.direction)
        assertEquals(HandSide.LEFT, event.hand)
    }
}
