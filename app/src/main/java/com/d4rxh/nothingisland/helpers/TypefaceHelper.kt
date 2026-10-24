package com.d4rxh.nothingisland.helpers

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object TypefaceHelper {
    private var ndotTypeface: Typeface? = null

    fun getNdot(context: Context): Typeface {
        if (ndotTypeface == null) {
            try {
                ndotTypeface = Typeface.createFromAsset(context.assets, "fonts/ndot57.otf")
            } catch (e: Exception) {
                ndotTypeface = Typeface.DEFAULT_BOLD
            }
        }
        return ndotTypeface ?: Typeface.DEFAULT_BOLD
    }

    fun applyNdot(vararg views: TextView) {
        for (view in views) {
            view.typeface = getNdot(view.context)
        }
    }

    fun applyNdotRecursively(rootView: View) {
        if (rootView is TextView) {
            rootView.typeface = getNdot(rootView.context)
        }
        if (rootView is ViewGroup) {
            for (i in 0 until rootView.childCount) {
                applyNdotRecursively(rootView.getChildAt(i))
            }
        }
    }
}
