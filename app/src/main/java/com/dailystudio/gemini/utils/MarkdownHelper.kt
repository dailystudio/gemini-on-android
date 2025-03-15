package com.dailystudio.gemini.utils

import android.graphics.Color
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.RenderProps
import io.noties.markwon.html.HtmlTag
import io.noties.markwon.html.tag.SimpleTagHandler

// Custom HTML Tag Handler for <font>
class CustomFontTagHandler : SimpleTagHandler() {

    override fun supportedTags(): Collection<String> {
        return setOf("font");
    }

    override fun getSpans(
        configuration: MarkwonConfiguration,
        renderProps: RenderProps,
        tag: HtmlTag
    ): Any? {
        val color = tag.attributes()["color"]?.let {
            try {
                Color.parseColor(it)
            } catch (e: IllegalArgumentException) {
                Color.BLACK // Fallback to black if the color is invalid
            }
        } ?: Color.BLACK

        val size = tag.attributes()["size"]?.toFloatOrNull()?.takeIf { it > 0 } ?: 1.0f // Default size is 1.0

        // Combine spans for color and size
        return arrayOf(
            ForegroundColorSpan(color),
            RelativeSizeSpan(size)
        )
    }
}

// Custom Span to apply font color and size
class CustomFontSpan(private val color: Int, private val size: Float) :
    android.text.style.ForegroundColorSpan(color) {
    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        if (size > 0) {
            ds.textSize = size
        }
    }
}