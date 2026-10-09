package com.android.daw.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * SD Studio DAW Motion System
 *
 * Strict duration and spring physics tokens:
 * - Durations: fast=120ms, base=200ms, slow=320ms
 * - Springs:
 *     snappy (dampingRatio 0.8, stiffness 700) - immediate tactile response
 *     smooth (dampingRatio 0.9, stiffness 300) - drawer and panel slides
 *     playful (dampingRatio 0.65, stiffness 400) - fan-out, knob settle, sliding tabs
 */
@Immutable
data class DawMotion(
    val fastMs: Int = 120,
    val baseMs: Int = 200,
    val slowMs: Int = 320,

    val fastTween: TweenSpec<Float> = tween(120),
    val baseTween: TweenSpec<Float> = tween(200),
    val slowTween: TweenSpec<Float> = tween(320),

    val snappySpring: SpringSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = 700f
    ),
    val smoothSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = 300f
    ),
    val playfulSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.65f,
        stiffness = 400f
    )
) {
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 700f)
    fun <T> smooth(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)
    fun <T> playful(): SpringSpec<T> = spring(dampingRatio = 0.65f, stiffness = 400f)
    fun <T> fast(): TweenSpec<T> = tween(fastMs)
    fun <T> base(): TweenSpec<T> = tween(baseMs)
    fun <T> slow(): TweenSpec<T> = tween(slowMs)
}

object Motion {
    const val FastMs: Int = 120
    const val BaseMs: Int = 200
    const val SlowMs: Int = 320

    val SnappyFloat: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 700f)
    val SmoothFloat: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 300f)
    val PlayfulFloat: SpringSpec<Float> = spring(dampingRatio = 0.65f, stiffness = 400f)

    val defaultDawMotion = DawMotion()
}
