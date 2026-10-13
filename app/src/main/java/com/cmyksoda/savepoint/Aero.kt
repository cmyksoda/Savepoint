package com.cmyksoda.savepoint

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

val LocalAeroDark = staticCompositionLocalOf { false }

fun aeroColors(dark: Boolean): ColorScheme =
    if (dark)
        darkColorScheme(
            primary = Color(0xFFFFC9E3),
            onPrimary = Color(0xFF40172F),
            background = Color(0xFF101425),
            onBackground = Color(0xFFF4EDFA),
            surface = Color(0xFF24293F),
            onSurface = Color(0xFFF4EDFA),
            surfaceVariant = Color(0xFF353C54),
            onSurfaceVariant = Color(0xFFD5D6E8),
            secondary = Color(0xFFAADBE9),
            outline = Color(0xFF75809B),
            surfaceContainer = Color(0xFF272D43),
            surfaceContainerHigh = Color(0xFF30364D),
        )
    else
        lightColorScheme(
            primary = Color(0xFF8A2D59),
            onPrimary = Color.White,
            background = Color(0xFFF5E4EE),
            onBackground = Color(0xFF392B40),
            surface = Color(0xFFFFF7FC),
            onSurface = Color(0xFF392B40),
            surfaceVariant = Color(0xFFEADCE8),
            onSurfaceVariant = Color(0xFF6C5970),
            secondary = Color(0xFF426977),
            outline = Color(0xFFAA8FA5),
            surfaceContainer = Color(0xFFFCEBF5),
            surfaceContainerHigh = Color(0xFFFFF7FC),
        )

val AeroTypography =
    Typography(
        bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge =
            TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
        labelMedium =
            TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
        titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 26.sp),
    )

fun Modifier.aeroBackdrop(dark: Boolean): Modifier = drawWithCache {
    if (size.width <= 0f || size.height <= 0f) return@drawWithCache onDrawBehind {}
    val renderWidth = minOf(size.width.toInt(), 720).coerceAtLeast(1)
    val renderHeight = (size.height * renderWidth / size.width).toInt().coerceAtLeast(1)
    val bitmap = ImageBitmap(renderWidth, renderHeight)
    val w = renderWidth.toFloat()
    val h = renderHeight.toFloat()
    val base =
        Brush.verticalGradient(
            if (dark) listOf(Color(0xFF172D43), Color(0xFF221C3A), Color(0xFF121A33))
            else listOf(Color(0xFFEDF7FB), Color(0xFFF9D6E9), Color(0xFFE4DDF5))
        )
    val roseLight =
        Brush.radialGradient(
            listOf(
                (if (dark) Color(0xFFB75094) else Color(0xFFF39ACB)).copy(alpha = .58f),
                Color.Transparent,
            ),
            center = Offset(w * 1.03f, h * .39f),
            radius = w * 1.15f,
        )
    val blueLight =
        Brush.radialGradient(
            listOf(
                (if (dark) Color(0xFF66C9DA) else Color.White).copy(
                    alpha = if (dark) .26f else .72f
                ),
                Color.Transparent,
            ),
            center = Offset(-w * .18f, h * .09f),
            radius = w * 1.15f,
        )
    val lilacLight =
        Brush.radialGradient(
            listOf(
                (if (dark) Color(0xFF8776CE) else Color(0xFFC7BAEF)).copy(alpha = .28f),
                Color.Transparent,
            ),
            center = Offset(w * .12f, h * .78f),
            radius = w * .95f,
        )
    val origin = Offset(-w * .55f, -h * .14f)
    val rays =
        listOf(
                Offset(w * .55f, h * 1.16f),
                Offset(w * 1.45f, h * 1.16f),
                Offset(w * 2.7f, h * 1.04f),
                Offset(w * 2.4f, h * .56f),
            )
            .map { end ->
                val path =
                    Path().apply {
                        moveTo(origin.x, origin.y)
                        lineTo(end.x, end.y)
                    }
                val light =
                    Brush.linearGradient(
                        0f to
                            (if (dark) Color(0xFFD0EBF7) else Color.White).copy(
                                alpha = if (dark) .10f else .34f
                            ),
                        .4f to Color.White.copy(alpha = if (dark) .05f else .20f),
                        1f to Color.Transparent,
                        start = origin,
                        end = end,
                    )
                path to light
            }
    val waveIndices = if (dark) listOf(1, 4, 7, 10) else (0..10).toList()
    val waves =
        waveIndices.map { index ->
            val phase = index * .28f
            val level = index * .115f - .08f
            val amplitude = .046f + .008f * kotlin.math.sin(phase)
            val path =
                Path().apply {
                    for (step in 0..48) {
                        val t = -.2f + step / 48f * 1.4f
                        val y = level - .09f * t + amplitude * kotlin.math.sin(t * 4.52f + phase)
                        if (step == 0) moveTo(t * w, y * h) else lineTo(t * w, y * h)
                    }
                }
            val shade = Path().apply { addPath(path, Offset(0f, h * .018f)) }
            val strength = .65f + .35f * kotlin.math.sin(index * .45f)
            val light =
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    .2f to Color(0xFFE6D6F6).copy(alpha = strength * if (dark) .04f else .1f),
                    .55f to Color.White.copy(alpha = strength * if (dark) .095f else .25f),
                    .85f to Color(0xFFDAEDF8).copy(alpha = strength * if (dark) .05f else .12f),
                    1f to Color.Transparent,
                    startX = -w * .2f,
                    endX = w * 1.2f,
                )
            Triple(path, shade, light)
        }
    val glint =
        Brush.horizontalGradient(
            0f to Color.Transparent,
            .25f to Color(0xFFF9D5EB).copy(alpha = if (dark) .04f else .16f),
            .6f to Color.White.copy(alpha = if (dark) .17f else .50f),
            .88f to Color(0xFFDFEDF9).copy(alpha = if (dark) .06f else .20f),
            1f to Color.Transparent,
            startX = -w * .2f,
            endX = w * 1.2f,
        )
    val valley =
        Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                (if (dark) Color(0xFF0B142E) else Color(0xFFD48CB9)).copy(
                    alpha = if (dark) .06f else .11f
                ),
                Color.Transparent,
            ),
            startX = -w * .2f,
            endX = w * 1.2f,
        )
    CanvasDrawScope().draw(
        this,
        layoutDirection,
        androidx.compose.ui.graphics.Canvas(bitmap),
        Size(w, h),
    ) {
        fun glow(path: Path, brush: Brush, width: Float, blur: Float) {
            drawIntoCanvas { canvas ->
                val paint =
                    Paint().apply {
                        style = PaintingStyle.Stroke
                        strokeWidth = width
                        strokeCap = StrokeCap.Round
                        strokeJoin = StrokeJoin.Round
                        isAntiAlias = true
                    }
                brush.applyTo(size, paint, 1f)
                paint.asFrameworkPaint().maskFilter =
                    android.graphics.BlurMaskFilter(
                        blur,
                        android.graphics.BlurMaskFilter.Blur.NORMAL,
                    )
                canvas.drawPath(path, paint)
            }
        }
        drawRect(base)
        drawRect(roseLight)
        drawRect(blueLight)
        drawRect(lilacLight)
        for ((path, light) in rays) glow(path, light, w * .06f, w * if (dark) .07f else .035f)
        for ((index, wave) in waves.withIndex()) {
            val (path, shade, light) = wave
            glow(shade, valley, h * if (dark) .05f else .028f, h * if (dark) .045f else .022f)
            glow(path, light, h * if (dark) .06f else .026f, h * if (dark) .042f else .020f)
            if (!dark && index in 2..8 && index % 2 == 0) glow(path, glint, h * .0015f, h * .0018f)
        }
    }
    onDrawBehind {
        drawImage(
            bitmap,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.High,
        )
    }
}

fun Modifier.glassShelf(dark: Boolean): Modifier = drawWithCache {
    val x = 0f
    val height = 2.5.dp.toPx()
    val y = size.height - 10.dp.toPx()
    val width = size.width - x * 2
    val shadowHeight = 34.dp.toPx()
    val shadowWidth = minOf(size.width.toInt(), 720).coerceAtLeast(1)
    val scale = shadowWidth / size.width.coerceAtLeast(1f)
    val shadow = ImageBitmap(shadowWidth, (shadowHeight * scale).toInt().coerceAtLeast(1))
    CanvasDrawScope().draw(
        this,
        layoutDirection,
        androidx.compose.ui.graphics.Canvas(shadow),
        Size(shadowWidth.toFloat(), shadow.height.toFloat()),
    ) {
        drawIntoCanvas { canvas ->
            val paint =
                Paint().apply {
                    color = Color(0xFF19132C).copy(alpha = if (dark) .12f else .08f)
                    isAntiAlias = true
                    asFrameworkPaint().maskFilter =
                        android.graphics.BlurMaskFilter(
                            10.dp.toPx() * scale,
                            android.graphics.BlurMaskFilter.Blur.NORMAL,
                        )
                }
            val glow =
                Paint().apply {
                    color =
                        if (dark) Color(0xFFFFC9E3).copy(alpha = .18f)
                        else Color.White.copy(alpha = .4f)
                    isAntiAlias = true
                    asFrameworkPaint().maskFilter =
                        android.graphics.BlurMaskFilter(
                            5.dp.toPx() * scale,
                            android.graphics.BlurMaskFilter.Blur.NORMAL,
                        )
                }
            canvas.drawRoundRect(
                x * scale,
                6.dp.toPx() * scale,
                (size.width - x) * scale,
                11.dp.toPx() * scale,
                5.dp.toPx() * scale,
                5.dp.toPx() * scale,
                glow,
            )
            canvas.drawRoundRect(
                x * scale,
                12.dp.toPx() * scale,
                (size.width - x) * scale,
                20.dp.toPx() * scale,
                5.dp.toPx() * scale,
                5.dp.toPx() * scale,
                paint,
            )
        }
    }
    onDrawBehind {
        drawImage(
            shadow,
            dstOffset = IntOffset(0, (y - 8.dp.toPx()).toInt()),
            dstSize = IntSize(size.width.toInt(), shadowHeight.toInt()),
            filterQuality = FilterQuality.High,
        )
        drawRoundRect(
            Brush.verticalGradient(
                if (dark) listOf(Color(0xAA8293B3), Color(0x55545A84), Color(0xAA38384F))
                else listOf(Color(0xDDFFFFFF), Color(0x99D9C4DA), Color(0x66B590B1)),
                startY = y,
                endY = y + height,
            ),
            Offset(x, y),
            Size(width, height),
            CornerRadius.Zero,
        )
        drawLine(
            Color.White.copy(alpha = if (dark) .55f else .95f),
            Offset(x, y),
            Offset(size.width - x, y),
            strokeWidth = .65.dp.toPx(),
        )
    }
}

fun Modifier.glassMaterial(
    dark: Boolean,
    radius: Int = 18,
    strong: Boolean = false,
    elevated: Boolean = true,
): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    val colors =
        if (dark) {
            if (strong) listOf(Color(0xFF555B74), Color(0xFF343B55), Color(0xFF232B42))
            else listOf(Color(0xE64B536B), Color(0xE02B314A), Color(0xED21273C))
        } else {
            if (strong) listOf(Color(0xFFFFFFFF), Color(0xFFFFEFF9), Color(0xFFF1E1ED))
            else listOf(Color(0xF9FFFFFF), Color(0xDFFBEAF4), Color(0xECEDDFEC))
        }
    return then(
            if (elevated)
                Modifier.shadow(
                    if (strong) 12.dp else 7.dp,
                    shape,
                    ambientColor = Color(0xFF261932),
                    spotColor = Color(0xFF181C34),
                )
            else Modifier
        )
        .clip(shape)
        .background(Brush.verticalGradient(colors))
        .background(
            Brush.linearGradient(
                listOf(
                    Color(0xFFB5E5F0).copy(alpha = if (dark) .055f else .05f),
                    Color.Transparent,
                    Color(0xFFF6B3DE).copy(alpha = if (dark) .075f else .1f),
                )
            )
        )
        .drawWithContent {
            val shine =
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height * .18f)
                    quadraticTo(size.width * .3f, size.height * .34f, 0f, size.height * .22f)
                    close()
                }
            drawPath(
                shine,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (dark) .08f else .33f), Color.Transparent)
                ),
            )
            drawContent()
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) .55f else .95f),
                        Color.White.copy(alpha = .08f),
                        if (dark) Color(0xFF7C89AD).copy(alpha = .45f)
                        else Color(0xFFB790AE).copy(alpha = .55f),
                    )
                ),
                topLeft = Offset(.5.dp.toPx(), .5.dp.toPx()),
                size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                cornerRadius = CornerRadius(radius.dp.toPx()),
                style = Stroke(1.dp.toPx()),
            )
        }
}

@Composable
fun Glass(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.glassMaterial(LocalAeroDark.current, strong = strong), content = content)
}

@Composable
private fun AeroControl(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    primary: Boolean,
    haptic: Boolean,
    minHeight: Dp = 44.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val dark = LocalAeroDark.current
    val feedback = rememberActionFeedback()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)
    val gradient =
        if (dark) listOf(Color(0xFFFFEAF5), Color(0xFFFAC3DF), Color(0xFFE697C5), Color(0xFFF8B9DF))
        else listOf(Color(0xFF913767), Color(0xFF882955), Color(0xFF7B1E4E), Color(0xFF9B3C70))
    Box(
        modifier
            .width(IntrinsicSize.Max)
            .heightIn(min = maxOf(48.dp, minHeight))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    if (haptic) feedback(true)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier.fillMaxWidth().alpha(if (!enabled) .5f else if (pressed) .94f else 1f),
            shape = shape,
            color = Color.Transparent,
            contentColor =
                if (primary) {
                    if (dark) Color(0xFF421D39) else Color.White
                } else MaterialTheme.colorScheme.onSurface,
            shadowElevation = if (pressed) 0.dp else 2.dp,
            border = if (primary) BorderStroke(1.dp, Color.White.copy(alpha = .6f)) else null,
        ) {
            val material =
                if (primary)
                    Modifier.background(Brush.verticalGradient(gradient)).drawWithContent {
                        drawRoundRect(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = .2f), Color.Transparent)
                            ),
                            topLeft = Offset(2.dp.toPx(), 1.dp.toPx()),
                            size = Size(size.width - 4.dp.toPx(), size.height * .43f),
                            cornerRadius = CornerRadius(10.dp.toPx()),
                        )
                        drawContent()
                    }
                else Modifier.glassMaterial(dark, 12, elevated = false)
            Row(
                Modifier.defaultMinSize(minWidth = 96.dp, minHeight = minHeight)
                    .then(material)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
            }
        }
    }
}

@Composable
fun PinkButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: Boolean = false,
    minHeight: Dp = 44.dp,
    content: @Composable RowScope.() -> Unit,
) {
    AeroControl(
        onClick,
        modifier,
        enabled,
        primary = true,
        haptic = haptic,
        minHeight = minHeight,
        content = content,
    )
}

@Composable
fun AeroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: Boolean = false,
    minHeight: Dp = 44.dp,
    content: @Composable RowScope.() -> Unit,
) {
    AeroControl(
        onClick,
        modifier,
        enabled,
        primary = false,
        haptic = haptic,
        minHeight = minHeight,
        content = content,
    )
}

@Composable
fun AeroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
) {
    val dark = LocalAeroDark.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier,
        )
        OutlinedTextField(
            value,
            onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            shape = RoundedCornerShape(14.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = if (dark) Color(0x99333B53) else Color(0xBFFFFFFF),
                    focusedContainerColor = if (dark) Color(0xE02A334D) else Color(0xF5FFFFFF),
                    unfocusedBorderColor = if (dark) Color(0xFF7B88A6) else Color(0xFFC6AEBF),
                ),
        )
    }
}

@Composable
fun Choice(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier,
        )
        Box(Modifier.fillMaxWidth().onSizeChanged { anchorWidth = it.width }) {
            AeroButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(value, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                AeroIcon(AeroSymbol.DOWN, Modifier.padding(start = 8.dp).size(16.dp))
            }
            DropdownMenu(
                expanded,
                { expanded = false },
                modifier =
                    Modifier.width(with(density) { anchorWidth.toDp() }).heightIn(max = 360.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                options.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item) },
                        onClick = {
                            expanded = false
                            onSelect(item)
                        },
                    )
                }
            }
        }
    }
}

enum class AeroSymbol {
    GRID,
    LIST,
    BACK,
    NEXT,
    DOWN,
    EDIT,
    SHELVES,
    BACKLOG,
    PAUSE,
    LOG,
    ADD,
    SETTINGS,
    SEARCH,
    DELETE,
    HEART,
    THUMB_UP,
    THUMB_DOWN,
    CLOCK,
    REPLAY,
    PLAY,
}

@Composable
fun AeroIcon(
    symbol: AeroSymbol,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier.size(22.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun path(vararg points: Float) {
                drawPath(
                    Path().apply {
                        moveTo(points[0], points[1])
                        for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
                    },
                    tint,
                    style = stroke,
                )
            }
            when (symbol) {
                AeroSymbol.BACK -> {
                    path(11f, 5f, 4f, 12f, 11f, 19f)
                    path(4f, 12f, 21f, 12f)
                }
                AeroSymbol.NEXT -> path(9f, 5f, 16f, 12f, 9f, 19f)
                AeroSymbol.DOWN -> path(5f, 9f, 12f, 16f, 19f, 9f)
                AeroSymbol.ADD -> {
                    path(12f, 5f, 12f, 19f)
                    path(5f, 12f, 19f, 12f)
                }
                AeroSymbol.SEARCH -> {
                    drawCircle(tint, 6.5f, Offset(10f, 10f), style = stroke)
                    path(15f, 15f, 21f, 21f)
                }
                AeroSymbol.DELETE -> {
                    path(4f, 6f, 20f, 6f)
                    path(9f, 6f, 9f, 3f, 15f, 3f, 15f, 6f)
                    path(6f, 6f, 7f, 21f, 17f, 21f, 18f, 6f)
                    path(10f, 10f, 10f, 17f)
                    path(14f, 10f, 14f, 17f)
                }
                AeroSymbol.PAUSE -> {
                    drawRoundRect(tint, Offset(6f, 5f), Size(4f, 14f), CornerRadius(1.4f))
                    drawRoundRect(tint, Offset(14f, 5f), Size(4f, 14f), CornerRadius(1.4f))
                }
                AeroSymbol.PLAY -> path(7f, 4f, 19f, 12f, 7f, 20f, 7f, 4f)
                AeroSymbol.EDIT -> {
                    path(5f, 15f, 16f, 4f, 20f, 8f, 9f, 19f, 4f, 20f, 5f, 15f)
                    path(14f, 6f, 18f, 10f)
                }
                AeroSymbol.SHELVES -> {
                    path(3f, 3f, 3f, 21f)
                    path(21f, 3f, 21f, 21f)
                    drawRoundRect(tint, Offset(2f, 10f), Size(20f, 2.3f), CornerRadius(.7f))
                    drawRoundRect(tint, Offset(2f, 20f), Size(20f, 2.3f), CornerRadius(.7f))
                    drawRoundRect(
                        tint,
                        Offset(5.5f, 4f),
                        Size(3.5f, 6f),
                        CornerRadius(.6f),
                        style = Stroke(1.5f),
                    )
                    drawRoundRect(
                        tint,
                        Offset(12f, 3f),
                        Size(3.5f, 7f),
                        CornerRadius(.6f),
                        style = Stroke(1.5f),
                    )
                    drawRoundRect(
                        tint,
                        Offset(5.5f, 14f),
                        Size(4f, 6f),
                        CornerRadius(.6f),
                        style = Stroke(1.5f),
                    )
                    path(14f, 14f, 17f, 19f)
                }
                AeroSymbol.BACKLOG -> {
                    drawRoundRect(
                        tint,
                        Offset(4f, 8f),
                        Size(16f, 13f),
                        CornerRadius(2f),
                        style = stroke,
                    )
                    path(3f, 4f, 21f, 4f, 21f, 8f, 3f, 8f, 3f, 4f)
                    path(9f, 12f, 15f, 12f)
                }
                AeroSymbol.LOG -> {
                    path(
                        12f,
                        5f,
                        7f,
                        3f,
                        3f,
                        4f,
                        3f,
                        19f,
                        8f,
                        18f,
                        12f,
                        20f,
                        16f,
                        18f,
                        21f,
                        19f,
                        21f,
                        4f,
                        17f,
                        3f,
                        12f,
                        5f,
                        12f,
                        20f,
                    )
                }
                AeroSymbol.HEART ->
                    drawPath(
                        Path().apply {
                            moveTo(12f, 20f)
                            cubicTo(1f, 13f, 1f, 6f, 6f, 4f)
                            cubicTo(9f, 3f, 11f, 5f, 12f, 7f)
                            cubicTo(15f, 2f, 22f, 3f, 22f, 9f)
                            cubicTo(22f, 13f, 16f, 17f, 12f, 20f)
                            close()
                        },
                        tint,
                        style = stroke,
                    )
                AeroSymbol.THUMB_UP,
                AeroSymbol.THUMB_DOWN -> {
                    rotate(if (symbol == AeroSymbol.THUMB_DOWN) 180f else 0f, Offset(12f, 12f)) {
                        drawRoundRect(
                            tint,
                            Offset(3f, 10f),
                            Size(4f, 11f),
                            CornerRadius(1f),
                            style = stroke,
                        )
                        path(
                            7f,
                            11f,
                            10f,
                            5f,
                            11f,
                            3f,
                            13f,
                            3f,
                            14f,
                            5f,
                            13f,
                            9f,
                            19f,
                            9f,
                            21f,
                            11f,
                            19f,
                            19f,
                            17f,
                            21f,
                            7f,
                            21f,
                        )
                    }
                }
                AeroSymbol.CLOCK -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
                    path(12f, 6f, 12f, 12f, 16f, 14f)
                }
                AeroSymbol.REPLAY -> {
                    drawArc(tint, 30f, 290f, false, Offset(3f, 3f), Size(18f, 18f), style = stroke)
                    path(17f, 3f, 20f, 7f, 15f, 8f)
                }
                AeroSymbol.GRID -> {
                    for (x in listOf(3f, 13f)) for (y in listOf(3f, 13f)) drawRoundRect(
                        tint,
                        Offset(x, y),
                        Size(8f, 8f),
                        CornerRadius(1.5f),
                        style = stroke,
                    )
                }
                AeroSymbol.LIST -> {
                    for (y in listOf(5f, 12f, 19f)) {
                        drawCircle(tint, 1.2f, Offset(4f, y))
                        path(9f, y, 21f, y)
                    }
                }
                AeroSymbol.SETTINGS -> {
                    drawCircle(tint, 3f, Offset(12f, 12f), style = stroke)
                    drawCircle(tint, 7f, Offset(12f, 12f), style = stroke)
                    repeat(8) { rotate(it * 45f, Offset(12f, 12f)) { path(12f, 2f, 12f, 5f) } }
                }
            }
        }
    }
}

@Composable
fun AeroIconButton(
    symbol: AeroSymbol,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    tint: Color? = null,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(if (compact) 32.dp else 40.dp)
                .glassMaterial(LocalAeroDark.current, if (compact) 10 else 20),
            contentAlignment = Alignment.Center,
        ) {
            AeroIcon(
                symbol,
                Modifier.size(if (compact) 17.dp else 22.dp),
                tint ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun AeroDock(
    route: String,
    onNavigate: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalAeroDark.current
    val feedback = rememberActionFeedback()
    val addInteraction = remember { MutableInteractionSource() }
    val addPressed by addInteraction.collectIsPressedAsState()
    val addScale by animateFloatAsState(if (addPressed) .94f else 1f, label = "add press")
    Glass(modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()) {
        Row(Modifier.padding(6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                    Triple("home", "shelves", AeroSymbol.SHELVES),
                    Triple("backlog", "backlog", AeroSymbol.BACKLOG),
                    Triple("paused", "paused", AeroSymbol.PAUSE),
                    Triple("log", "play log", AeroSymbol.LOG),
                )
                .forEach { (destination, label, icon) ->
                    val active = route == destination
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .then(
                                if (active)
                                    Modifier.background(
                                        Brush.verticalGradient(
                                            if (LocalAeroDark.current)
                                                listOf(Color(0x88D6A6CD), Color(0x554D4A74))
                                            else listOf(Color.White, Color(0x77ECA8CF))
                                        )
                                    )
                                else Modifier
                            )
                            .clickable(role = Role.Tab) {
                                if (!active) {
                                    feedback(false)
                                    onNavigate(destination)
                                }
                            }
                            .semantics { selected = active }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AeroIcon(
                            icon,
                            Modifier.size(22.dp),
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            label,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            color =
                                if (active) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(48.dp)
                    .clickable(
                        interactionSource = addInteraction,
                        indication = null,
                        role = Role.Button,
                    ) {
                        feedback(true)
                        onAdd()
                    }
                    .semantics { contentDescription = "+ add" }
                    .scale(addScale)
                    .clip(CircleShape)
                    .glassMaterial(dark, 24)
                    .background(
                        Brush.verticalGradient(
                            if (dark) {
                                if (addPressed)
                                    listOf(Color(0xFFFFD6F1), Color(0xFFEA84BE), Color(0xFFC756A0))
                                else listOf(Color(0xFFFFEAF5), Color(0xFFF9BADF), Color(0xFFE894C6))
                            } else {
                                if (addPressed) listOf(Color(0xDDFFE8F5), Color(0xCCDC88B8))
                                else listOf(Color(0x88FFE8F5), Color(0x88D675AB))
                            }
                        )
                    )
                    .drawWithContent {
                        drawContent()
                        drawOval(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (addPressed) .16f else .35f),
                                    Color.Transparent,
                                )
                            ),
                            topLeft = Offset(size.width * .12f, size.height * .05f),
                            size = Size(size.width * .76f, size.height * .38f),
                        )
                    }
                    .border(1.dp, Color.White.copy(alpha = if (dark) .6f else .45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AeroIcon(AeroSymbol.ADD, tint = if (dark) Color(0xFF4C2040) else Color(0xFF743352))
            }
        }
    }
}

@Composable
fun AeroActionRow(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxHeight(), propagateMinConstraints = true) { first() }
        Box(Modifier.weight(1f).fillMaxHeight(), propagateMinConstraints = true) { second() }
    }
}

@Composable
fun QuietAction(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    AeroButton(onClick = onClick, content = content)
}

@Composable
fun AeroDialog(
    onDismissRequest: () -> Unit,
    title: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val maxHeight =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } - 96.dp
    var panelBounds by remember { mutableStateOf<Rect?>(null) }
    val dark = LocalAeroDark.current
    Dialog(
        onDismissRequest,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
                if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            }
        }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(onDismissRequest) {
                    detectTapGestures { point ->
                        if (panelBounds?.contains(point) == false) onDismissRequest()
                    }
                }
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Glass(
                Modifier.widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .onGloballyPositioned { panelBounds = it.boundsInRoot() },
                strong = true,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    title?.let { ProvideTextStyle(MaterialTheme.typography.titleLarge, it) }
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge, text)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        if (dismissButton != null) Spacer(Modifier.width(8.dp))
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
fun GameIdentity(
    game: Game,
    artwork: Artwork,
    showShelf: Boolean = false,
    prominent: Boolean = false,
) {
    if (prominent || game.shelf == Shelf.HUNTS) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val hunt = game.shelf == Shelf.HUNTS
            Art(
                if (hunt) game.sprite else game.cover,
                artwork,
                game.title,
                if (hunt) Modifier.size(if (prominent) 112.dp else 84.dp)
                else
                    Modifier.shadow(10.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = .6f), RoundedCornerShape(16.dp)),
                sprite = hunt,
                transparent = hunt,
                showPlaceholderTitle = false,
                system = game.system,
                coverBounds = if (hunt) null else CoverSize(180f, 220f),
            )
            Text(
                if (hunt) displayPokemonName(game.pokemon) else game.title,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                lineHeight = 25.sp,
            )
            Text(
                if (hunt) huntVersions[game.pokemonVersion] ?: game.title else game.system,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showShelf && !hunt)
                Text(
                    game.shelf.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        return
    }
    Glass(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Art(
                if (game.shelf == Shelf.HUNTS) game.sprite else game.cover,
                artwork,
                game.title,
                Modifier.shadow(6.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = .5f), RoundedCornerShape(12.dp)),
                game.shelf == Shelf.HUNTS,
                showPlaceholderTitle = false,
                system = game.system,
                coverBounds = CoverSize(88f, 120f),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (game.shelf == Shelf.HUNTS) game.pokemon.ifBlank { "shiny hunt" }
                    else game.title,
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    game.system,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showShelf)
                    Text(
                        game.shelf.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
    }
}
