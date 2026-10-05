package com.mi.onextbox.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val ToolsFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "ToolsFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(8f, 3f)
                horizontalLineTo(16f)
                curveTo(17.1f, 3f, 18f, 3.9f, 18f, 5f)
                verticalLineTo(7f)
                horizontalLineTo(19f)
                curveTo(20.66f, 7f, 22f, 8.34f, 22f, 10f)
                verticalLineTo(18f)
                curveTo(22f, 19.66f, 20.66f, 21f, 19f, 21f)
                horizontalLineTo(5f)
                curveTo(3.34f, 21f, 2f, 19.66f, 2f, 18f)
                verticalLineTo(10f)
                curveTo(2f, 8.34f, 3.34f, 7f, 5f, 7f)
                horizontalLineTo(6f)
                verticalLineTo(5f)
                curveTo(6f, 3.9f, 6.9f, 3f, 8f, 3f)
                close()
                moveTo(8f, 5f)
                verticalLineTo(7f)
                horizontalLineTo(16f)
                verticalLineTo(5f)
                close()
                moveTo(4f, 12f)
                horizontalLineTo(11f)
                verticalLineTo(11f)
                horizontalLineTo(13f)
                verticalLineTo(12f)
                horizontalLineTo(20f)
                verticalLineTo(14f)
                horizontalLineTo(13f)
                verticalLineTo(15f)
                horizontalLineTo(11f)
                verticalLineTo(14f)
                horizontalLineTo(4f)
                close()
            }
        }.build()
    }

    val ToolsOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "ToolsOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 7.5f)
                verticalLineTo(5.5f)
                curveTo(7f, 4.67f, 7.67f, 4f, 8.5f, 4f)
                horizontalLineTo(15.5f)
                curveTo(16.33f, 4f, 17f, 4.67f, 17f, 5.5f)
                verticalLineTo(7.5f)
                moveTo(5.5f, 7.5f)
                horizontalLineTo(18.5f)
                curveTo(19.88f, 7.5f, 21f, 8.62f, 21f, 10f)
                verticalLineTo(17.5f)
                curveTo(21f, 18.88f, 19.88f, 20f, 18.5f, 20f)
                horizontalLineTo(5.5f)
                curveTo(4.12f, 20f, 3f, 18.88f, 3f, 17.5f)
                verticalLineTo(10f)
                curveTo(3f, 8.62f, 4.12f, 7.5f, 5.5f, 7.5f)
                close()
                moveTo(3f, 13f)
                horizontalLineTo(21f)
                moveTo(12f, 11.5f)
                verticalLineTo(14.5f)
            }
        }.build()
    }

    val HomeFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "HomeFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(4.05f, 9.85f)
                curveTo(4.05f, 9.18f, 4.35f, 8.54f, 4.87f, 8.11f)
                lineTo(10.47f, 3.43f)
                curveTo(11.35f, 2.69f, 12.65f, 2.69f, 13.53f, 3.43f)
                lineTo(19.13f, 8.11f)
                curveTo(19.65f, 8.54f, 19.95f, 9.18f, 19.95f, 9.85f)
                verticalLineTo(18.18f)
                curveTo(19.95f, 19.38f, 18.98f, 20.35f, 17.78f, 20.35f)
                horizontalLineTo(14.28f)
                verticalLineTo(13.12f)
                curveTo(14.28f, 12.57f, 13.83f, 12.12f, 13.28f, 12.12f)
                horizontalLineTo(10.72f)
                curveTo(10.17f, 12.12f, 9.72f, 12.57f, 9.72f, 13.12f)
                verticalLineTo(20.35f)
                horizontalLineTo(6.22f)
                curveTo(5.02f, 20.35f, 4.05f, 19.38f, 4.05f, 18.18f)
                close()
            }
        }.build()
    }

    val HomeOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "HomeOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(9.55f, 19.55f)
                horizontalLineTo(6.22f)
                curveTo(5.02f, 19.55f, 4.05f, 18.58f, 4.05f, 17.38f)
                verticalLineTo(9.85f)
                curveTo(4.05f, 9.18f, 4.35f, 8.54f, 4.87f, 8.11f)
                lineTo(10.47f, 3.43f)
                curveTo(11.35f, 2.69f, 12.65f, 2.69f, 13.53f, 3.43f)
                lineTo(19.13f, 8.11f)
                curveTo(19.65f, 8.54f, 19.95f, 9.18f, 19.95f, 9.85f)
                verticalLineTo(17.38f)
                curveTo(19.95f, 18.58f, 18.98f, 19.55f, 17.78f, 19.55f)
                horizontalLineTo(14.45f)
                verticalLineTo(13.2f)
                curveTo(14.45f, 12.65f, 14f, 12.2f, 13.45f, 12.2f)
                horizontalLineTo(10.55f)
                curveTo(10f, 12.2f, 9.55f, 12.65f, 9.55f, 13.2f)
                close()
            }
        }.build()
    }

    val BlurDots: ImageVector by lazy {
        ImageVector.Builder(
            name = "BlurDots",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                val dots = listOf(
                    12f to 4f,
                    8f to 6f, 12f to 6f, 16f to 6f,
                    6f to 10f, 10f to 10f, 14f to 10f, 18f to 10f,
                    4f to 14f, 8f to 14f, 12f to 14f, 16f to 14f, 20f to 14f,
                    6f to 18f, 10f to 18f, 14f to 18f, 18f to 18f,
                    8f to 22f, 12f to 22f, 16f to 22f,
                )
                dots.forEach { (x, y) ->
                    moveTo(x + 1.1f, y)
                    arcToRelative(1.1f, 1.1f, 0f, true, true, -2.2f, 0f)
                    arcToRelative(1.1f, 1.1f, 0f, true, true, 2.2f, 0f)
                    close()
                }
            }
        }.build()
    }

    val FloatingBar: ImageVector by lazy {
        ImageVector.Builder(
            name = "FloatingBar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(4f, 5f)
                horizontalLineTo(20f)
                curveTo(21.1f, 5f, 22f, 5.9f, 22f, 7f)
                verticalLineTo(17f)
                curveTo(22f, 18.1f, 21.1f, 19f, 20f, 19f)
                horizontalLineTo(4f)
                curveTo(2.9f, 19f, 2f, 18.1f, 2f, 17f)
                verticalLineTo(7f)
                curveTo(2f, 5.9f, 2.9f, 5f, 4f, 5f)
                close()

                moveTo(5f, 15f)
                horizontalLineTo(19f)
                curveTo(19.55f, 15f, 20f, 15.45f, 20f, 16f)
                reflectiveCurveTo(19.55f, 17f, 19f, 17f)
                horizontalLineTo(5f)
                curveTo(4.45f, 17f, 4f, 16.55f, 4f, 16f)
                reflectiveCurveTo(4.45f, 15f, 5f, 15f)
                close()
            }
        }.build()
    }

    val Droplet: ImageVector by lazy {
        ImageVector.Builder(
            name = "Droplet",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(12f, 2f)
                curveTo(8.4f, 6.2f, 5f, 10.1f, 5f, 14f)
                curveTo(5f, 18.42f, 8.13f, 22f, 12f, 22f)
                reflectiveCurveTo(19f, 18.42f, 19f, 14f)
                curveTo(19f, 10.1f, 15.6f, 6.2f, 12f, 2f)
                close()

                moveTo(10.2f, 17.3f)
                curveTo(8.95f, 16.8f, 8f, 15.45f, 8f, 14f)
                curveTo(8f, 13.45f, 8.45f, 13f, 9f, 13f)
                reflectiveCurveTo(10f, 13.45f, 10f, 14f)
                curveTo(10f, 14.65f, 10.42f, 15.25f, 11f, 15.5f)
                curveTo(11.5f, 15.72f, 11.74f, 16.3f, 11.52f, 16.82f)
                curveTo(11.3f, 17.32f, 10.72f, 17.54f, 10.2f, 17.3f)
                close()
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(9f, 16.17f)
                lineTo(4.83f, 12f)
                lineTo(3.41f, 13.41f)
                lineTo(9f, 19f)
                lineTo(21f, 7f)
                lineTo(19.59f, 5.59f)
                close()
            }
        }.build()
    }

    val Widgets: ImageVector by lazy {
        ImageVector.Builder(
            name = "Widgets",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(5.2f, 3.15f)
                horizontalLineTo(8.8f)
                curveTo(10.12f, 3.15f, 10.85f, 3.88f, 10.85f, 5.2f)
                verticalLineTo(8.8f)
                curveTo(10.85f, 10.12f, 10.12f, 10.85f, 8.8f, 10.85f)
                horizontalLineTo(5.2f)
                curveTo(3.88f, 10.85f, 3.15f, 10.12f, 3.15f, 8.8f)
                verticalLineTo(5.2f)
                curveTo(3.15f, 3.88f, 3.88f, 3.15f, 5.2f, 3.15f)
                close()

                moveTo(15.2f, 3.15f)
                horizontalLineTo(18.8f)
                curveTo(20.12f, 3.15f, 20.85f, 3.88f, 20.85f, 5.2f)
                verticalLineTo(8.8f)
                curveTo(20.85f, 10.12f, 20.12f, 10.85f, 18.8f, 10.85f)
                horizontalLineTo(15.2f)
                curveTo(13.88f, 10.85f, 13.15f, 10.12f, 13.15f, 8.8f)
                verticalLineTo(5.2f)
                curveTo(13.15f, 3.88f, 13.88f, 3.15f, 15.2f, 3.15f)
                close()

                moveTo(5.2f, 13.15f)
                horizontalLineTo(8.8f)
                curveTo(10.12f, 13.15f, 10.85f, 13.88f, 10.85f, 15.2f)
                verticalLineTo(18.8f)
                curveTo(10.85f, 20.12f, 10.12f, 20.85f, 8.8f, 20.85f)
                horizontalLineTo(5.2f)
                curveTo(3.88f, 20.85f, 3.15f, 20.12f, 3.15f, 18.8f)
                verticalLineTo(15.2f)
                curveTo(3.15f, 13.88f, 3.88f, 13.15f, 5.2f, 13.15f)
                close()

                moveTo(15.2f, 13.15f)
                horizontalLineTo(18.8f)
                curveTo(20.12f, 13.15f, 20.85f, 13.88f, 20.85f, 15.2f)
                verticalLineTo(18.8f)
                curveTo(20.85f, 20.12f, 20.12f, 20.85f, 18.8f, 20.85f)
                horizontalLineTo(15.2f)
                curveTo(13.88f, 20.85f, 13.15f, 20.12f, 13.15f, 18.8f)
                verticalLineTo(15.2f)
                curveTo(13.15f, 13.88f, 13.88f, 13.15f, 15.2f, 13.15f)
                close()
            }
        }.build()
    }

    val WidgetsOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "WidgetsOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(5.2f, 3.95f)
                horizontalLineTo(8.8f)
                curveTo(9.6f, 3.95f, 10.05f, 4.4f, 10.05f, 5.2f)
                verticalLineTo(8.8f)
                curveTo(10.05f, 9.6f, 9.6f, 10.05f, 8.8f, 10.05f)
                horizontalLineTo(5.2f)
                curveTo(4.4f, 10.05f, 3.95f, 9.6f, 3.95f, 8.8f)
                verticalLineTo(5.2f)
                curveTo(3.95f, 4.4f, 4.4f, 3.95f, 5.2f, 3.95f)
                close()

                moveTo(15.2f, 3.95f)
                horizontalLineTo(18.8f)
                curveTo(19.6f, 3.95f, 20.05f, 4.4f, 20.05f, 5.2f)
                verticalLineTo(8.8f)
                curveTo(20.05f, 9.6f, 19.6f, 10.05f, 18.8f, 10.05f)
                horizontalLineTo(15.2f)
                curveTo(14.4f, 10.05f, 13.95f, 9.6f, 13.95f, 8.8f)
                verticalLineTo(5.2f)
                curveTo(13.95f, 4.4f, 14.4f, 3.95f, 15.2f, 3.95f)
                close()

                moveTo(5.2f, 13.95f)
                horizontalLineTo(8.8f)
                curveTo(9.6f, 13.95f, 10.05f, 14.4f, 10.05f, 15.2f)
                verticalLineTo(18.8f)
                curveTo(10.05f, 19.6f, 9.6f, 20.05f, 8.8f, 20.05f)
                horizontalLineTo(5.2f)
                curveTo(4.4f, 20.05f, 3.95f, 19.6f, 3.95f, 18.8f)
                verticalLineTo(15.2f)
                curveTo(3.95f, 14.4f, 4.4f, 13.95f, 5.2f, 13.95f)
                close()

                moveTo(15.2f, 13.95f)
                horizontalLineTo(18.8f)
                curveTo(19.6f, 13.95f, 20.05f, 14.4f, 20.05f, 15.2f)
                verticalLineTo(18.8f)
                curveTo(20.05f, 19.6f, 19.6f, 20.05f, 18.8f, 20.05f)
                horizontalLineTo(15.2f)
                curveTo(14.4f, 20.05f, 13.95f, 19.6f, 13.95f, 18.8f)
                verticalLineTo(15.2f)
                curveTo(13.95f, 14.4f, 14.4f, 13.95f, 15.2f, 13.95f)
                close()
            }
        }.build()
    }

    val Event: ImageVector by lazy {
        ImageVector.Builder(
            name = "Event",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(7f, 2f)
                lineTo(9f, 2f)
                lineTo(9f, 6f)
                lineTo(7f, 6f)
                close()

                moveTo(15f, 2f)
                lineTo(17f, 2f)
                lineTo(17f, 6f)
                lineTo(15f, 6f)
                close()

                moveTo(3f, 4f)
                lineTo(21f, 4f)
                lineTo(21f, 21f)
                lineTo(3f, 21f)
                close()

                moveTo(3f, 8f)
                lineTo(21f, 8f)
                lineTo(21f, 10f)
                lineTo(3f, 10f)
                close()

                moveTo(7f, 13f)
                lineTo(10f, 13f)
                lineTo(10f, 16f)
                lineTo(7f, 16f)
                close()

                moveTo(12f, 13f)
                lineTo(15f, 13f)
                lineTo(15f, 16f)
                lineTo(12f, 16f)
                close()
            }
        }.build()
    }

    val Extension: ImageVector by lazy {
        ImageVector.Builder(
            name = "Extension",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(20.5f, 11.0f)
                horizontalLineTo(19.0f)
                verticalLineTo(7.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(3.5f)
                curveTo(13.0f, 2.12f, 11.88f, 1.0f, 10.5f, 1.0f)
                reflectiveCurveTo(8.0f, 2.12f, 8.0f, 3.5f)
                verticalLineTo(5.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                verticalLineToRelative(3.8f)
                horizontalLineTo(3.5f)
                curveToRelative(1.49f, 0.0f, 2.7f, 1.21f, 2.7f, 2.7f)
                reflectiveCurveToRelative(-1.21f, 2.7f, -2.7f, 2.7f)
                horizontalLineTo(2.0f)
                verticalLineTo(20.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(3.8f)
                verticalLineToRelative(-1.5f)
                curveToRelative(0.0f, -1.49f, 1.21f, -2.7f, 2.7f, -2.7f)
                reflectiveCurveToRelative(2.7f, 1.21f, 2.7f, 2.7f)
                verticalLineTo(22.0f)
                horizontalLineTo(17.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(1.5f)
                curveToRelative(1.38f, 0.0f, 2.5f, -1.12f, 2.5f, -2.5f)
                reflectiveCurveTo(21.88f, 11.0f, 20.5f, 11.0f)
                close()
            }
        }.build()
    }

    val Tune: ImageVector by lazy {
        ImageVector.Builder(
            name = "Tune",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(5f, 4f)
                lineTo(7f, 4f)
                lineTo(7f, 20f)
                lineTo(5f, 20f)
                close()

                moveTo(11f, 4f)
                lineTo(13f, 4f)
                lineTo(13f, 20f)
                lineTo(11f, 20f)
                close()

                moveTo(17f, 4f)
                lineTo(19f, 4f)
                lineTo(19f, 20f)
                lineTo(17f, 20f)
                close()

                moveTo(3f, 7f)
                lineTo(9f, 7f)
                lineTo(9f, 9f)
                lineTo(3f, 9f)
                close()

                moveTo(9f, 13f)
                lineTo(15f, 13f)
                lineTo(15f, 15f)
                lineTo(9f, 15f)
                close()

                moveTo(15f, 9f)
                lineTo(21f, 9f)
                lineTo(21f, 11f)
                lineTo(15f, 11f)
                close()
            }
        }.build()
    }

    val Filter: ImageVector by lazy {
        ImageVector.Builder(
            name = "Filter",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(10f, 18f)
                lineTo(14f, 18f)
                lineTo(14f, 16f)
                lineTo(10f, 16f)
                close()

                moveTo(3f, 12f)
                lineTo(21f, 12f)
                lineTo(21f, 10f)
                lineTo(3f, 10f)
                close()

                moveTo(6f, 6f)
                lineTo(18f, 6f)
                lineTo(18f, 4f)
                lineTo(6f, 4f)
                close()
            }
        }.build()
    }

    val Save: ImageVector by lazy {
        ImageVector.Builder(
            name = "Save",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(5f, 3f)
                horizontalLineTo(17f)
                lineTo(21f, 7f)
                verticalLineTo(19f)
                curveTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
                horizontalLineTo(5f)
                curveTo(3.9f, 21f, 3f, 20.1f, 3f, 19f)
                verticalLineTo(5f)
                curveTo(3f, 3.9f, 3.9f, 3f, 5f, 3f)
                close()

                moveTo(12f, 19f)
                curveTo(13.66f, 19f, 15f, 17.66f, 15f, 16f)
                curveTo(15f, 14.34f, 13.66f, 13f, 12f, 13f)
                curveTo(10.34f, 13f, 9f, 14.34f, 9f, 16f)
                curveTo(9f, 17.66f, 10.34f, 19f, 12f, 19f)
                close()

                moveTo(15f, 9f)
                verticalLineTo(5f)
                horizontalLineTo(5f)
                verticalLineTo(9f)
                horizontalLineTo(15f)
                close()
            }
        }.build()
    }

    val LightMode: ImageVector by lazy {
        ImageVector.Builder(
            name = "LightMode",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12.0f, 7.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
                reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
                reflectiveCurveToRelative(5.0f, -2.24f, 5.0f, -5.0f)
                reflectiveCurveTo(14.76f, 7.0f, 12.0f, 7.0f)
                lineTo(12.0f, 7.0f)
                close()

                moveTo(2.0f, 13.0f)
                lineToRelative(2.0f, 0.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineToRelative(-2.0f, 0.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveTo(1.45f, 13.0f, 2.0f, 13.0f)
                close()

                moveTo(20.0f, 13.0f)
                lineToRelative(2.0f, 0.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineToRelative(-2.0f, 0.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveTo(19.45f, 13.0f, 20.0f, 13.0f)
                close()

                moveTo(11.0f, 2.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveTo(11.0f, 1.45f, 11.0f, 2.0f)
                close()

                moveTo(11.0f, 20.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                curveTo(11.45f, 19.0f, 11.0f, 19.45f, 11.0f, 20.0f)
                close()

                moveTo(5.99f, 4.58f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
                lineToRelative(1.06f, 1.06f)
                curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0.0f)
                reflectiveCurveToRelative(0.39f, -1.03f, 0.0f, -1.41f)
                lineTo(5.99f, 4.58f)
                close()

                moveTo(18.36f, 16.95f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
                lineToRelative(1.06f, 1.06f)
                curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0.0f, -1.41f)
                lineTo(18.36f, 16.95f)
                close()

                moveTo(19.42f, 5.99f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0.0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
                lineToRelative(-1.06f, 1.06f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
                reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0.0f)
                lineTo(19.42f, 5.99f)
                close()

                moveTo(7.05f, 18.36f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0.0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
                lineToRelative(-1.06f, 1.06f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
                reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0.0f)
                lineTo(7.05f, 18.36f)
                close()
            }
        }.build()
    }

    val Palette: ImageVector by lazy {
        ImageVector.Builder(
            name = "Palette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 2f)
                curveTo(6.49f, 2f, 2f, 6.49f, 2f, 12f)
                reflectiveCurveToRelative(4.49f, 10f, 10f, 10f)
                curveToRelative(1.38f, 0f, 2.5f, -1.12f, 2.5f, -2.5f)
                curveToRelative(0f, -0.61f, -0.23f, -1.2f, -0.64f, -1.67f)
                curveToRelative(-0.08f, -0.1f, -0.13f, -0.21f, -0.13f, -0.33f)
                curveToRelative(0f, -0.28f, 0.22f, -0.5f, 0.5f, -0.5f)
                horizontalLineTo(16f)
                curveToRelative(3.31f, 0f, 6f, -2.69f, 6f, -6f)
                curveTo(22f, 6.04f, 17.51f, 2f, 12f, 2f)
                close()

                moveTo(17.5f, 13f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveToRelative(0.67f, -1.5f, 1.5f, -1.5f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                curveTo(19f, 12.33f, 18.33f, 13f, 17.5f, 13f)
                close()

                moveTo(14.5f, 9f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveToRelative(0.67f, -1.5f, 1.5f, -1.5f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveToRelative(-0.67f, 1.5f, -1.5f, 1.5f)
                close()

                moveTo(6.5f, 13f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveToRelative(0.67f, -1.5f, 1.5f, -1.5f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveToRelative(-0.67f, 1.5f, -1.5f, 1.5f)
                close()

                moveTo(9.5f, 9f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveToRelative(0.67f, -1.5f, 1.5f, -1.5f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveToRelative(-0.67f, 1.5f, -1.5f, 1.5f)
                close()
            }
        }.build()
    }

    val Phone: ImageVector by lazy {
        ImageVector.Builder(
            name = "Phone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(7f, 2f)
                horizontalLineTo(17f)
                curveTo(18.1f, 2f, 19f, 2.9f, 19f, 4f)
                verticalLineTo(20f)
                curveTo(19f, 21.1f, 18.1f, 22f, 17f, 22f)
                horizontalLineTo(7f)
                curveTo(5.9f, 22f, 5f, 21.1f, 5f, 20f)
                verticalLineTo(4f)
                curveTo(5f, 2.9f, 5.9f, 2f, 7f, 2f)
                close()

                moveTo(8f, 5f)
                horizontalLineTo(16f)
                verticalLineTo(17f)
                horizontalLineTo(8f)
                close()

                moveTo(12f, 20f)
                curveTo(12.55f, 20f, 13f, 19.55f, 13f, 19f)
                curveTo(13f, 18.45f, 12.55f, 18f, 12f, 18f)
                curveTo(11.45f, 18f, 11f, 18.45f, 11f, 19f)
                curveTo(11f, 19.55f, 11.45f, 20f, 12f, 20f)
                close()
            }
        }.build()
    }

    val EventOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "EventOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(7f, 3f)
                verticalLineTo(6f)
                moveTo(17f, 3f)
                verticalLineTo(6f)
                moveTo(5f, 5f)
                horizontalLineTo(19f)
                verticalLineTo(21f)
                horizontalLineTo(5f)
                close()
                moveTo(5f, 9f)
                horizontalLineTo(19f)
                moveTo(8f, 13f)
                horizontalLineTo(10f)
                moveTo(14f, 13f)
                horizontalLineTo(16f)
                moveTo(8f, 17f)
                horizontalLineTo(10f)
                moveTo(14f, 17f)
                horizontalLineTo(16f)
            }
        }.build()
    }

    val ContributorsC17: ImageVector by lazy {
        ImageVector.Builder(
            name = "ContributorsC17",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.65f, 3.75f)
                arcToRelative(3.15f, 3.15f, 0f, true, true, 0f, 6.3f)
                arcToRelative(3.15f, 3.15f, 0f, true, true, 0f, -6.3f)

                moveTo(3.55f, 19.75f)
                curveTo(3.78f, 15.94f, 5.83f, 13.75f, 8.65f, 13.75f)
                curveTo(11.47f, 13.75f, 13.52f, 15.94f, 13.75f, 19.75f)
                close()

                moveTo(15.15f, 5.05f)
                curveTo(17.12f, 4.64f, 19.05f, 6.15f, 19.05f, 8.2f)
                curveTo(19.05f, 10.25f, 17.12f, 11.76f, 15.15f, 11.35f)

                moveTo(15.35f, 14.05f)
                curveTo(18.4f, 14.05f, 20.18f, 16.08f, 20.45f, 19.45f)
            }
        }.build()
    }

    val AgreementsC17: ImageVector by lazy {
        ImageVector.Builder(
            name = "AgreementsC17",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.6f, 5.35f)
                curveTo(3.6f, 4.47f, 4.32f, 3.75f, 5.2f, 3.75f)
                horizontalLineTo(8.65f)
                curveTo(10.05f, 3.75f, 11.28f, 4.43f, 12f, 5.48f)
                curveTo(12.72f, 4.43f, 13.95f, 3.75f, 15.35f, 3.75f)
                horizontalLineTo(18.8f)
                curveTo(19.68f, 3.75f, 20.4f, 4.47f, 20.4f, 5.35f)
                verticalLineTo(18.65f)
                curveTo(20.4f, 19.53f, 19.68f, 20.25f, 18.8f, 20.25f)
                horizontalLineTo(15.3f)
                curveTo(13.92f, 20.25f, 12.7f, 20.82f, 12f, 21.45f)
                curveTo(11.3f, 20.82f, 10.08f, 20.25f, 8.7f, 20.25f)
                horizontalLineTo(5.2f)
                curveTo(4.32f, 20.25f, 3.6f, 19.53f, 3.6f, 18.65f)
                close()

                moveTo(12f, 5.48f)
                verticalLineTo(21.45f)
            }
        }.build()
    }

    val GitRepositoryC17: ImageVector by lazy {
        ImageVector.Builder(
            name = "GitRepositoryC17",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.05f, 3.35f)
                horizontalLineTo(18.15f)
                curveTo(19.25f, 3.35f, 20.15f, 4.25f, 20.15f, 5.35f)
                verticalLineTo(19.9f)
                horizontalLineTo(6.05f)
                curveTo(4.83f, 19.9f, 3.85f, 18.92f, 3.85f, 17.7f)
                verticalLineTo(5.55f)
                curveTo(3.85f, 4.33f, 4.83f, 3.35f, 6.05f, 3.35f)
                close()

                moveTo(7.65f, 3.35f)
                verticalLineTo(19.9f)

                moveTo(11.7f, 8.05f)
                arcToRelative(1.25f, 1.25f, 0f, true, true, 0f, 2.5f)
                arcToRelative(1.25f, 1.25f, 0f, true, true, 0f, -2.5f)

                moveTo(16.25f, 8.05f)
                arcToRelative(1.25f, 1.25f, 0f, true, true, 0f, 2.5f)
                arcToRelative(1.25f, 1.25f, 0f, true, true, 0f, -2.5f)

                moveTo(11.7f, 14.25f)
                arcToRelative(1.25f, 1.25f, 0f, true, true, 0f, 2.5f)
                arcToRelative(1.25f, 1.25f, 0f, true, true, 0f, -2.5f)

                moveTo(12.95f, 9.3f)
                verticalLineTo(14.25f)
                moveTo(12.95f, 11.15f)
                horizontalLineTo(15.25f)
                curveTo(16.35f, 11.15f, 17.5f, 10.4f, 17.5f, 9.3f)
            }
        }.build()
    }

    val TelegramChannelC17: ImageVector by lazy {
        ImageVector.Builder(
            name = "TelegramChannelC17",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.85f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.72f, 10.62f)
                lineTo(19.1f, 4.18f)
                curveTo(20.15f, 3.74f, 20.63f, 4.22f, 20.4f, 5.34f)
                lineTo(17.48f, 19.2f)
                curveTo(17.25f, 20.3f, 16.5f, 20.55f, 15.65f, 19.83f)
                lineTo(11.5f, 16.32f)
                lineTo(8.48f, 18.72f)
                lineTo(8.55f, 14.02f)
                lineTo(3.92f, 12.43f)
                curveTo(2.88f, 12.08f, 2.7f, 11.05f, 3.72f, 10.62f)
                close()

                moveTo(8.55f, 14.02f)
                lineTo(18.85f, 5.25f)
                moveTo(11.5f, 16.32f)
                lineTo(8.55f, 14.02f)
            }
        }.build()
    }

    val ContributorsSolidC17: ImageVector by lazy {
        ImageVector.Builder(
            name = "ContributorsSolidC17",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16.35f, 4.35f)
                arcToRelative(2.65f, 2.65f, 0f, true, true, 0f, 5.3f)
                arcToRelative(2.65f, 2.65f, 0f, true, true, 0f, -5.3f)
                close()

                moveTo(11.85f, 19.7f)
                curveTo(12.15f, 15.98f, 13.82f, 13.85f, 16.35f, 13.85f)
                curveTo(18.88f, 13.85f, 20.55f, 15.98f, 20.85f, 19.7f)
                curveTo(20.9f, 20.35f, 20.43f, 20.75f, 19.8f, 20.75f)
                horizontalLineTo(12.9f)
                curveTo(12.27f, 20.75f, 11.8f, 20.35f, 11.85f, 19.7f)
                close()

                moveTo(8.55f, 3.25f)
                arcToRelative(3.25f, 3.25f, 0f, true, true, 0f, 6.5f)
                arcToRelative(3.25f, 3.25f, 0f, true, true, 0f, -6.5f)
                close()

                moveTo(2.85f, 19.85f)
                curveTo(3.17f, 15.68f, 5.3f, 13.3f, 8.55f, 13.3f)
                curveTo(11.8f, 13.3f, 13.93f, 15.68f, 14.25f, 19.85f)
                curveTo(14.3f, 20.5f, 13.83f, 20.9f, 13.2f, 20.9f)
                horizontalLineTo(3.9f)
                curveTo(3.27f, 20.9f, 2.8f, 20.5f, 2.85f, 19.85f)
                close()
            }
        }.build()
    }

    val AgreementsSolidC17: ImageVector by lazy {
        ImageVector.Builder(
            name = "AgreementsSolidC17",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4.5f, 3.35f)
                horizontalLineTo(8.45f)
                curveTo(10.05f, 3.35f, 11.35f, 4.65f, 11.35f, 6.25f)
                verticalLineTo(20.65f)
                curveTo(10.45f, 19.93f, 9.35f, 19.55f, 8.2f, 19.55f)
                horizontalLineTo(4.5f)
                curveTo(3.55f, 19.55f, 2.8f, 18.8f, 2.8f, 17.85f)
                verticalLineTo(5.05f)
                curveTo(2.8f, 4.1f, 3.55f, 3.35f, 4.5f, 3.35f)
                close()

                moveTo(15.55f, 3.35f)
                horizontalLineTo(19.5f)
                curveTo(20.45f, 3.35f, 21.2f, 4.1f, 21.2f, 5.05f)
                verticalLineTo(17.85f)
                curveTo(21.2f, 18.8f, 20.45f, 19.55f, 19.5f, 19.55f)
                horizontalLineTo(15.8f)
                curveTo(14.65f, 19.55f, 13.55f, 19.93f, 12.65f, 20.65f)
                verticalLineTo(6.25f)
                curveTo(12.65f, 4.65f, 13.95f, 3.35f, 15.55f, 3.35f)
                close()
            }
        }.build()
    }

    val TelegramChannelSolidC17: ImageVector by lazy {
        ImageVector.Builder(
            name = "TelegramChannelSolidC17",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3.55f, 10.35f)
                lineTo(19.2f, 3.82f)
                curveTo(20.4f, 3.32f, 20.98f, 3.9f, 20.72f, 5.17f)
                lineTo(17.72f, 19.42f)
                curveTo(17.45f, 20.7f, 16.55f, 20.98f, 15.55f, 20.13f)
                lineTo(11.45f, 16.65f)
                lineTo(8.25f, 19.2f)
                verticalLineTo(14.22f)
                lineTo(3.78f, 12.68f)
                curveTo(2.55f, 12.27f, 2.36f, 10.85f, 3.55f, 10.35f)
                close()
            }
        }.build()
    }

    val Heart: ImageVector by lazy {
        ImageVector.Builder(
            name = "Heart",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 21.35f)
                lineTo(10.55f, 20.03f)
                curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
                curveTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
                curveTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.08f)
                curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
                curveTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
                curveTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 20.04f)
                close()
            }
        }.build()
    }

    val BookOpen: ImageVector by lazy {
        ImageVector.Builder(
            name = "BookOpen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(4f, 4.5f)
                curveTo(4f, 3.67f, 4.67f, 3f, 5.5f, 3f)
                horizontalLineTo(10f)
                curveTo(11.2f, 3f, 12.24f, 3.52f, 13f, 4.24f)
                curveTo(13.76f, 3.52f, 14.8f, 3f, 16f, 3f)
                horizontalLineTo(20.5f)
                curveTo(21.33f, 3f, 22f, 3.67f, 22f, 4.5f)
                verticalLineTo(18.5f)
                curveTo(22f, 19.33f, 21.33f, 20f, 20.5f, 20f)
                horizontalLineTo(16.2f)
                curveTo(15.13f, 20f, 14.12f, 20.42f, 13.36f, 21.18f)
                curveTo(13.16f, 21.38f, 12.84f, 21.38f, 12.64f, 21.18f)
                curveTo(11.88f, 20.42f, 10.87f, 20f, 9.8f, 20f)
                horizontalLineTo(5.5f)
                curveTo(4.67f, 20f, 4f, 19.33f, 4f, 18.5f)
                close()

                moveTo(6f, 5f)
                verticalLineTo(18f)
                horizontalLineTo(9.8f)
                curveTo(10.56f, 18f, 11.3f, 18.17f, 12f, 18.48f)
                verticalLineTo(6.2f)
                curveTo(11.55f, 5.47f, 10.78f, 5f, 10f, 5f)
                close()

                moveTo(14f, 6.2f)
                verticalLineTo(18.48f)
                curveTo(14.7f, 18.17f, 15.44f, 18f, 16.2f, 18f)
                horizontalLineTo(20f)
                verticalLineTo(5f)
                horizontalLineTo(16f)
                curveTo(15.22f, 5f, 14.45f, 5.47f, 14f, 6.2f)
                close()
            }
        }.build()
    }

    val InfoFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "InfoFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            ) {
                moveTo(12f, 2.65f)
                arcToRelative(9.35f, 9.35f, 0f, true, true, 0f, 18.7f)
                arcToRelative(9.35f, 9.35f, 0f, true, true, 0f, -18.7f)
                close()

                moveTo(10.95f, 10.15f)
                curveTo(10.95f, 9.57f, 11.42f, 9.1f, 12f, 9.1f)
                curveTo(12.58f, 9.1f, 13.05f, 9.57f, 13.05f, 10.15f)
                verticalLineTo(16.45f)
                curveTo(13.05f, 17.03f, 12.58f, 17.5f, 12f, 17.5f)
                curveTo(11.42f, 17.5f, 10.95f, 17.03f, 10.95f, 16.45f)
                close()

                moveTo(12f, 5.85f)
                arcToRelative(1.15f, 1.15f, 0f, true, true, 0f, 2.3f)
                arcToRelative(1.15f, 1.15f, 0f, true, true, 0f, -2.3f)
                close()
            }
        }.build()
    }

    val InfoOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "InfoOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(12f, 3.15f)
                arcToRelative(8.85f, 8.85f, 0f, true, true, 0f, 17.7f)
                arcToRelative(8.85f, 8.85f, 0f, true, true, 0f, -17.7f)
                close()
            }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.05f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(12f, 10.1f)
                verticalLineTo(16.25f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 6.15f)
                arcToRelative(1.15f, 1.15f, 0f, true, true, 0f, 2.3f)
                arcToRelative(1.15f, 1.15f, 0f, true, true, 0f, -2.3f)
                close()
            }
        }.build()
    }

    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "Search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10.5f, 5f)
                arcToRelative(5.5f, 5.5f, 0f, true, true, 0f, 11f)
                arcToRelative(5.5f, 5.5f, 0f, true, true, 0f, -11f)
                moveTo(15f, 15f)
                lineTo(20f, 20f)
            }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "Refresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19.45f, 4.4f)
                verticalLineTo(9.45f)
                horizontalLineTo(14.4f)
                moveTo(19.05f, 8.95f)
                curveTo(17.83f, 5.63f, 14.35f, 3.63f, 10.84f, 4.36f)
                curveTo(8.63f, 4.82f, 6.73f, 6.25f, 5.7f, 8.25f)
                moveTo(4.55f, 19.6f)
                verticalLineTo(14.55f)
                horizontalLineTo(9.6f)
                moveTo(4.95f, 15.05f)
                curveTo(6.17f, 18.37f, 9.65f, 20.37f, 13.16f, 19.64f)
                curveTo(15.37f, 19.18f, 17.27f, 17.75f, 18.3f, 15.75f)
            }
        }.build()
    }

    val RefreshFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "RefreshFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19.45f, 4.4f)
                verticalLineTo(9.45f)
                horizontalLineTo(14.4f)
                moveTo(19.05f, 8.95f)
                curveTo(17.83f, 5.63f, 14.35f, 3.63f, 10.84f, 4.36f)
                curveTo(8.63f, 4.82f, 6.73f, 6.25f, 5.7f, 8.25f)
                moveTo(4.55f, 19.6f)
                verticalLineTo(14.55f)
                horizontalLineTo(9.6f)
                moveTo(4.95f, 15.05f)
                curveTo(6.17f, 18.37f, 9.65f, 20.37f, 13.16f, 19.64f)
                curveTo(15.37f, 19.18f, 17.27f, 17.75f, 18.3f, 15.75f)
            }
        }.build()
    }

    val Share: ImageVector by lazy {
        ImageVector.Builder(
            name = "Share",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.5f, 11f)
                lineTo(15.5f, 7f)
                moveTo(8.5f, 13f)
                lineTo(15.5f, 17f)
                moveTo(6f, 9f)
                arcToRelative(3f, 3f, 0f, true, true, 0f, 6f)
                arcToRelative(3f, 3f, 0f, true, true, 0f, -6f)
                moveTo(18f, 4f)
                arcToRelative(3f, 3f, 0f, true, true, 0f, 6f)
                arcToRelative(3f, 3f, 0f, true, true, 0f, -6f)
                moveTo(18f, 14f)
                arcToRelative(3f, 3f, 0f, true, true, 0f, 6f)
                arcToRelative(3f, 3f, 0f, true, true, 0f, -6f)
            }
        }.build()
    }

    val Trash: ImageVector by lazy {
        ImageVector.Builder(
            name = "Trash",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 7f)
                horizontalLineTo(20f)
                moveTo(10f, 11f)
                verticalLineTo(17f)
                moveTo(14f, 11f)
                verticalLineTo(17f)
                moveTo(6f, 7f)
                lineTo(7f, 20f)
                horizontalLineTo(17f)
                lineTo(18f, 7f)
                moveTo(9f, 7f)
                verticalLineTo(4f)
                horizontalLineTo(15f)
                verticalLineTo(7f)
            }
        }.build()
    }
}
