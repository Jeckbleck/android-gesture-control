package com.gesturecontrol.gesture

sealed class GestureEvent {
    data class Static(val type: StaticGestureType, val hand: HandSide) : GestureEvent()
    data class Dynamic(val direction: SwipeDirection, val hand: HandSide) : GestureEvent()
}

enum class StaticGestureType { OPEN_PALM, VICTORY, THUMB_UP, THUMB_DOWN, CLOSED_FIST }
enum class SwipeDirection    { LEFT, RIGHT, UP, DOWN }
enum class HandSide          { LEFT, RIGHT }
