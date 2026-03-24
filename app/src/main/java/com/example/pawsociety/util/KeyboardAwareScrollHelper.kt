package com.example.pawsociety.util

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView

object KeyboardAwareScrollHelper {

    fun attach(vararg views: View, topPaddingDp: Int = 24) {
        views.forEach { view ->
            view.setOnFocusChangeListener { focusedView, hasFocus ->
                if (!hasFocus) return@setOnFocusChangeListener

                focusedView.postDelayed({
                    scrollIntoView(focusedView, topPaddingDp)
                }, 120)
            }
        }
    }

    private fun scrollIntoView(view: View, topPaddingDp: Int) {
        val topPaddingPx = (topPaddingDp * view.resources.displayMetrics.density).toInt()
        when (val scrollParent = findScrollableParent(view)) {
            is ScrollView -> {
                val rect = Rect()
                view.getDrawingRect(rect)
                scrollParent.offsetDescendantRectToMyCoords(view, rect)
                scrollParent.smoothScrollTo(0, (rect.top - topPaddingPx).coerceAtLeast(0))
            }
            is NestedScrollView -> {
                val rect = Rect()
                view.getDrawingRect(rect)
                scrollParent.offsetDescendantRectToMyCoords(view, rect)
                scrollParent.smoothScrollTo(0, (rect.top - topPaddingPx).coerceAtLeast(0))
            }
            else -> {
                val rect = Rect(0, 0, view.width, view.height)
                view.requestRectangleOnScreen(rect, true)
            }
        }
    }

    private fun findScrollableParent(view: View): View? {
        var currentParent = view.parent
        while (currentParent is ViewGroup) {
            if (currentParent is ScrollView || currentParent is NestedScrollView) {
                return currentParent
            }
            currentParent = currentParent.parent
        }
        return null
    }
}
