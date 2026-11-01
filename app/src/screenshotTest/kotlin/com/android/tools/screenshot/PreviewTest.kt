package com.android.tools.screenshot

/** Marker the Compose preview screenshot plugin discovers; declared locally to avoid the Kotlin 2.2 artifact. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PreviewTest
