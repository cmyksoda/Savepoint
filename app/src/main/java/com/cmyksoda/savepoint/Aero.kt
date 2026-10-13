package com.cmyksoda.savepoint

import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
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
        titleLarge =
            TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 26.sp),
    )

fun Modifier.aeroBackdrop(dark: Boolean): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
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
                    alpha = if (dark) .32f else .8f
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
    val ribbonEdge =
        Path().apply {
            moveTo(-w * .3f, h * .87f)
            cubicTo(w * .52f, h * .70f, -w * .07f, h * .42f, w * .76f, h * .36f)
            cubicTo(w * .96f, h * .34f, w * 1.14f, h * .30f, w * 1.32f, h * .26f)
        }
    val ribbon =
        Path().apply {
            addPath(ribbonEdge)
            lineTo(w * 1.32f, h * .35f)
            cubicTo(w * 1.13f, h * .39f, w * .98f, h * .44f, w * .79f, h * .44f)
            cubicTo(w * .20f, h * .45f, w * .77f, h * .75f, -w * .3f, h * .96f)
            close()
        }
    val ribbonReflection =
        Path().apply {
            addPath(ribbonEdge)
            lineTo(w * 1.32f, h * .275f)
            cubicTo(w * 1.14f, h * .315f, w * .96f, h * .355f, w * .76f, h * .375f)
            cubicTo(-w * .07f, h * .435f, w * .52f, h * .715f, -w * .3f, h * .885f)
            close()
        }
    val curlEdge =
        Path().apply {
            moveTo(w * 1.2f, h * .42f)
            cubicTo(w * .32f, h * .55f, w * .20f, h * .72f, w * 1.14f, h * .86f)
        }
    val curl =
        Path().apply {
            addPath(curlEdge)
            lineTo(w * 1.14f, h * .94f)
            cubicTo(w * .13f, h * .78f, w * .06f, h * .53f, w * 1.2f, h * .36f)
            close()
        }
    val curlReflection =
        Path().apply {
            addPath(curlEdge)
            lineTo(w * 1.14f, h * .874f)
            cubicTo(w * .20f, h * .734f, w * .32f, h * .564f, w * 1.2f, h * .434f)
            close()
        }
    val headerEdge =
        Path().apply {
            moveTo(-w * .12f, h * .10f)
            cubicTo(w * .25f, h * .03f, w * .47f, h * .20f, w * 1.12f, h * .07f)
        }
    val glass =
        Brush.linearGradient(
            0f to Color(0xFFCCEAF5).copy(alpha = if (dark) .03f else .12f),
            .42f to Color.White.copy(alpha = if (dark) .12f else .44f),
            .66f to Color(0xFFF8BBDD).copy(alpha = if (dark) .10f else .23f),
            1f to Color.White.copy(alpha = if (dark) .025f else .06f),
            start = Offset(w, h * .3f),
            end = Offset(0f, h),
        )
    val reflection =
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (dark) .27f else .70f),
                Color(0xFFDFEDF9).copy(alpha = if (dark) .06f else .15f),
                Color.Transparent,
            ),
            start = Offset(w, h * .36f),
            end = Offset(w * .1f, h * .9f),
        )
    val rim =
        Brush.linearGradient(
            listOf(
                Color(0xFFFFD4EA).copy(alpha = if (dark) .13f else .45f),
                Color.White.copy(alpha = if (dark) .55f else .95f),
                Color(0xFFC4EDFA).copy(alpha = if (dark) .18f else .5f),
            ),
            start = Offset(0f, h * .8f),
            end = Offset(w, h * .38f),
        )
    onDrawBehind {
        drawRect(base)
        drawRect(roseLight)
        drawRect(blueLight)
        drawRect(lilacLight)
        drawPath(curl, glass)
        drawPath(curlReflection, reflection)
        drawPath(
            curlEdge,
            Color.White.copy(alpha = if (dark) .035f else .12f),
            style = Stroke(7.dp.toPx()),
        )
        drawPath(curlEdge, rim, style = Stroke(1.dp.toPx()))
        drawPath(ribbon, glass)
        drawPath(ribbonReflection, reflection)
        drawPath(
            ribbonEdge,
            Color.White.copy(alpha = if (dark) .035f else .12f),
            style = Stroke(7.dp.toPx()),
        )
        drawPath(ribbonEdge, rim, style = Stroke(1.1.dp.toPx()))
        drawPath(
            headerEdge,
            Color.White.copy(alpha = if (dark) .13f else .55f),
            style = Stroke(1.dp.toPx()),
        )
    }
}

fun Modifier.glassShelf(dark: Boolean): Modifier = drawBehind {
    val x = 12.dp.toPx()
    val height = 10.dp.toPx()
    val y = size.height - height
    val width = size.width - x * 2
    drawRoundRect(
        Color(0xFF19132C).copy(alpha = if (dark) .25f else .12f),
        Offset(x, y + 3.dp.toPx()),
        Size(width, height),
        CornerRadius(5.dp.toPx()),
    )
    drawRoundRect(
        Brush.verticalGradient(
            if (dark) listOf(Color(0xAA8293B3), Color(0x55545A84), Color(0xAA38384F))
            else listOf(Color(0xDDFFFFFF), Color(0x99D9C4DA), Color(0xBBB590B1))
        ),
        Offset(x, y),
        Size(width, height),
        CornerRadius(5.dp.toPx()),
    )
    drawLine(
        Color.White.copy(alpha = if (dark) .55f else .95f),
        Offset(x + 5.dp.toPx(), y),
        Offset(size.width - x - 5.dp.toPx(), y),
        strokeWidth = 1.dp.toPx(),
    )
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
    minHeight: Dp = 44.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val dark = LocalAeroDark.current
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
                onClick = onClick,
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
    minHeight: Dp = 44.dp,
    content: @Composable RowScope.() -> Unit,
) {
    AeroControl(
        onClick,
        modifier,
        enabled,
        primary = true,
        minHeight = minHeight,
        content = content,
    )
}

@Composable
fun AeroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    AeroControl(onClick, modifier, enabled, primary = false, content = content)
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
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier,
        )
        Box {
            AeroButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(value, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                AeroIcon(AeroSymbol.DOWN, Modifier.padding(start = 8.dp).size(16.dp))
            }
            DropdownMenu(
                expanded,
                { expanded = false },
                modifier = Modifier.heightIn(max = 360.dp),
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
            AeroIcon(symbol, Modifier.size(if (compact) 17.dp else 22.dp))
        }
    }
}

@Composable
fun AeroDock(route: String, onNavigate: (String) -> Unit, onAdd: () -> Unit) {
    Glass(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()) {
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
                            .clickable(role = Role.Tab) { onNavigate(destination) }
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
            Box(
                Modifier.padding(start = 6.dp)
                    .size(48.dp)
                    .glassMaterial(LocalAeroDark.current, 24)
                    .background(
                        Brush.verticalGradient(listOf(Color(0x88FFE8F5), Color(0x88D675AB)))
                    )
                    .clickable(role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = "+ add" },
                contentAlignment = Alignment.Center,
            ) {
                AeroIcon(
                    AeroSymbol.ADD,
                    tint = if (LocalAeroDark.current) Color(0xFF38273F) else Color(0xFF743352),
                )
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
    TextButton(
        onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors =
            ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
        content = content,
    )
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
fun GameIdentity(game: Game, artwork: Artwork, showShelf: Boolean = false) {
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
