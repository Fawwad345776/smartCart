package com.aura.smartcart.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

private const val GRID = 64f
private const val CART_PATH = "M5,13h8l6.5,27h29L55,20H16"
private const val CHECK_PATH = "M26,29l5.5,5L42,24"

/**
 * The SmartCart mark: a cart with a check inside, drawn on a 64-unit grid.
 * [checkProgress] (0..1) draws the check stroke progressively, used by the splash animation.
 */
@Composable
fun CartLogo(
    modifier: Modifier = Modifier,
    cartColor: Color,
    checkColor: Color,
    checkProgress: Float = 1f
) {
    val cart = remember { PathParser().parsePathString(CART_PATH).toPath() }
    val check = remember { PathParser().parsePathString(CHECK_PATH).toPath() }
    val measure = remember { PathMeasure() }
    val partialCheck = remember { Path() }

    Canvas(modifier) {
        val s = size.minDimension / GRID
        val dx = (size.width - GRID * s) / 2f
        val dy = (size.height - GRID * s) / 2f
        val stroke = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        translate(dx, dy) {
            scale(s, pivot = Offset.Zero) {
                drawPath(cart, cartColor, style = stroke)
                drawCircle(cartColor, radius = 4f, center = Offset(24f, 50f))
                drawCircle(cartColor, radius = 4f, center = Offset(44f, 50f))

                val p = checkProgress.coerceIn(0f, 1f)
                if (p >= 1f) {
                    drawPath(check, checkColor, style = stroke)
                } else if (p > 0f) {
                    partialCheck.reset()
                    measure.setPath(check, false)
                    measure.getSegment(0f, measure.length * p, partialCheck, true)
                    drawPath(partialCheck, checkColor, style = stroke)
                }
            }
        }
    }
}
