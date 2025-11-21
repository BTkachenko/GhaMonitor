package com.example.ghamonitor

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

/**
 * Shared JSON support with a configured ObjectMapper for Kotlin.
 */
object JsonSupport {

    /**
     * Shared Jackson ObjectMapper configured for Kotlin.
     */
    val mapper: ObjectMapper = ObjectMapper()
        .registerModule(
            KotlinModule.Builder().build()
        )
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
