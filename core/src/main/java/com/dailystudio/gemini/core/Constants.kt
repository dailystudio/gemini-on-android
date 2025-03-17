package com.dailystudio.gemini.core

import com.dailystudio.devbricksx.development.LT
import com.dailystudio.devbricksx.development.TagDescriptor

class LeadingTagModel(model: String? = null): TagDescriptor.LeadingTag(buildString{
    append("[MODEL")
    if (!model.isNullOrEmpty()) {
        append(" ")
        append(model)
    }
    append("]")
})

typealias LT_MODEL = LeadingTagModel

object Constants {

    val LT_MODEL_GEMINI = LT_MODEL("Gemini")
    val LT_MODEL_VERTEX = LT_MODEL("Vertex")
    val LT_MODEL_NANO = LT_MODEL("Gemini Nano")
    val LT_MODEL_GEMMA = LT_MODEL("Gemma")

    val LT_RESP = LT("[RESPONSE]")
}