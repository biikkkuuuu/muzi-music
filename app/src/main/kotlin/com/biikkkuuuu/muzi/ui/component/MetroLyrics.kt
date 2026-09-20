package com.biikkkuuuu.muzi.ui.component

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biikkkuuuu.muzi.LocalPlayerConnection
import com.biikkkuuuu.muzi.constants.AppleMusicLyricsBlurKey
import com.biikkkuuuu.muzi.constants.LyricsRomanizeAsMainKey
import com.biikkkuuuu.muzi.lyrics.LyricsEntry
import com.biikkkuuuu.muzi.ui.screens.settings.LyricsPosition
import com.biikkkuuuu.muzi.utils.rememberPreference
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

data class HyphenGroupWord(
    val pos: Int,
    val groupSize: Int,
    val isLast: Boolean,
    val groupStartMs: Long,
    val groupEndMs: Long
)


private data class MetroWordTimestamp(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val hasTrailingSpace: Boolean = false
)

private data class ActiveWordSegment(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val progress: Float,
    val isRtl: Boolean
)




fun String.containsComplexScript(): Boolean {
    for (char in this) {
        val directionality = Character.getDirectionality(char)
        if (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
            return true
        }
        
        val block = Character.UnicodeBlock.of(char)
        if (block == Character.UnicodeBlock.DEVANAGARI ||
            block == Character.UnicodeBlock.BENGALI ||
            block == Character.UnicodeBlock.GURMUKHI ||
            block == Character.UnicodeBlock.GUJARATI ||
            block == Character.UnicodeBlock.ORIYA ||
            block == Character.UnicodeBlock.TAMIL ||
            block == Character.UnicodeBlock.TELUGU ||
            block == Character.UnicodeBlock.KANNADA ||
            block == Character.UnicodeBlock.MALAYALAM ||
            block == Character.UnicodeBlock.SINHALA ||
            block == Character.UnicodeBlock.THAI ||
            block == Character.UnicodeBlock.LAO ||
            block == Character.UnicodeBlock.TIBETAN ||
            block == Character.UnicodeBlock.MYANMAR ||
            block == Character.UnicodeBlock.KHMER) {
            return true
        }
    }
    return false
}

@Composable
fun MetroLyricsLine(
    entry: LyricsEntry,
    nextEntryTime: Long?,
    effectivePlaybackPosition: Long,
    lyricsOffset: Long = 0L,
    isSynced: Boolean,
    isActive: Boolean,
    distanceFromCurrent: Int,
    lyricsTextPosition: LyricsPosition,
    textColor: Color,
    showRomanized: Boolean,
    showTranslated: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    isAutoScrollActive: Boolean,
    expressiveAccent: Color,
    lyricsTextSize: Float = 36f,
    lyricsLineSpacing: Float = 1.3f,
    modifier: Modifier = Modifier
) {
    val (appleMusicLyricsBlur) = rememberPreference(AppleMusicLyricsBlurKey, true)
    val (romanizeAsMain) = rememberPreference(LyricsRomanizeAsMainKey, false)
    
    val romanizedTextState by entry.romanizedTextFlow.collectAsState()
    val isRomanizedAvailable = romanizedTextState != null
    
    val mainTextRaw = if (showRomanized && romanizeAsMain && isRomanizedAvailable) romanizedTextState else entry.text
    val subTextRaw = if (showRomanized && romanizeAsMain && isRomanizedAvailable) entry.text else if (showRomanized) romanizedTextState else null
    
    val mainText = if (entry.isBackground) mainTextRaw?.removePrefix("(")?.removeSuffix(")") ?: "" else mainTextRaw ?: ""
    val subText = if (entry.isBackground) subTextRaw?.removePrefix("(")?.removeSuffix(")") else subTextRaw
    
    val targetBlur = if (!appleMusicLyricsBlur || !isAutoScrollActive || isActive || !isSynced || isSelectionModeActive) {
        0f
    } else {
        when (distanceFromCurrent) {
            1 -> 0f
            2 -> 0f
            3 -> 2f
            4 -> 4f
            else -> 6f
        }
    }

    val animatedBlur by animateFloatAsState(
        targetValue = targetBlur,
        animationSpec = tween(durationMillis = 1000), label = "blur"
    )

    val focusedAlpha = if (entry.isBackground) 0.5f else 0.3f
    val activeAlpha = 1f
    
    val targetAlpha = when {
        !isSynced || (isSelectionModeActive && isSelected) -> 1f
        entry.isBackground || isActive -> activeAlpha
        isAutoScrollActive && distanceFromCurrent >= 0 -> {
            when (distanceFromCurrent) {
                0 -> focusedAlpha
                1, 2 -> 0.2f
                3 -> 0.15f
                4 -> 0.1f
                else -> 0.08f
            }
        }
        else -> 0.2f
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 300),
        label = "lineAlpha"
    )

    val itemModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .combinedClickable(
            enabled = true,
            onClick = onClick,
            onLongClick = onLongClick
        )
        .background(
            if (isSelected && isSelectionModeActive)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else Color.Transparent
        )
        .padding(horizontal = 24.dp, vertical = (8 * lyricsLineSpacing).dp)
        .blur(animatedBlur.dp)

    val agentAlignment = when {
        entry.isBackground -> Alignment.CenterHorizontally
        entry.agent == "v1" -> Alignment.Start
        entry.agent == "v2" -> Alignment.End
        entry.agent == "v1000" -> Alignment.CenterHorizontally
        else -> when (lyricsTextPosition) {
            LyricsPosition.LEFT -> Alignment.Start
            LyricsPosition.CENTER -> Alignment.CenterHorizontally
            LyricsPosition.RIGHT -> Alignment.End
        }
    }

    val agentTextAlign = when {
        entry.isBackground -> TextAlign.Center
        entry.agent == "v1" -> TextAlign.Left
        entry.agent == "v2" -> TextAlign.Right
        entry.agent == "v1000" -> TextAlign.Center
        else -> when (lyricsTextPosition) {
            LyricsPosition.LEFT -> TextAlign.Left
            LyricsPosition.CENTER -> TextAlign.Center
            LyricsPosition.RIGHT -> TextAlign.Right
        }
    }

    val lyricStyle = TextStyle(
        fontSize = lyricsTextSize.sp,
        fontWeight = FontWeight.Bold,
        fontStyle = if (entry.isBackground) FontStyle.Italic else FontStyle.Normal,
        lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
        letterSpacing = (-0.5).sp,
        textAlign = agentTextAlign,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

    Column(
        modifier = itemModifier,
        horizontalAlignment = agentAlignment
    ) {
        val duration = remember(entry.time, nextEntryTime) {
            if (nextEntryTime != null) (nextEntryTime - entry.time).coerceAtLeast(1000L) else 4000L
        }
        val activeDurationSec = remember(duration) {
            (duration * 0.95).coerceAtLeast(500.0) / 1000.0
        }

        val wordList = entry.words
        val effectiveWords: List<MetroWordTimestamp> = if (wordList != null && wordList.isNotEmpty() && (!showRomanized || !romanizeAsMain || !isRomanizedAvailable || mainText == entry.text)) {
            wordList.mapIndexed { idx, word ->
                MetroWordTimestamp(
                    text = word.text,
                    startTime = word.startTime,
                    endTime = word.endTime,
                    hasTrailingSpace = idx < wordList.size - 1
                )
            }
        } else {
            remember(mainText, entry.time, activeDurationSec) {
                val words = mainText.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.isEmpty()) {
                    emptyList()
                } else {
                    val totalChars = mainText.length.coerceAtLeast(1)
                    val startTimeSec = entry.time / 1000.0
                    var accumSec = 0.0
                    words.mapIndexed { idx, wordText ->
                        val wordCharCount = if (idx < words.size - 1) wordText.length + 1 else wordText.length
                        val wordDurSec = (activeDurationSec * wordCharCount.toDouble() / totalChars).coerceAtLeast(0.15)
                        val wStart = startTimeSec + accumSec
                        val wEnd = wStart + wordDurSec
                        accumSec += wordDurSec
                        MetroWordTimestamp(
                            text = wordText,
                            startTime = wStart,
                            endTime = wEnd,
                            hasTrailingSpace = idx < words.size - 1
                        )
                    }
                }
            }
        }

        val baseLineColor = expressiveAccent.copy(alpha = if (entry.isBackground) focusedAlpha else animatedAlpha)
        
        if (isSynced && effectiveWords.isNotEmpty() && (isActive || distanceFromCurrent <= 3) && mainText.isNotEmpty()) {
            WordLevelCanvasLyrics(
                mainText = mainText,
                words = effectiveWords,
                isActiveLine = isActive,
                effectivePlaybackPosition = effectivePlaybackPosition,
                lyricsOffset = lyricsOffset,
                lyricStyle = lyricStyle,
                lineColor = if (isActive && !entry.isBackground) expressiveAccent.copy(alpha = 1f) else baseLineColor,
                expressiveAccent = expressiveAccent,
                isBackground = entry.isBackground,
                focusedAlpha = focusedAlpha,
                alignment = agentTextAlign
            )
        } else {
            Text(
                text = mainText,
                style = lyricStyle.copy(color = if (isActive && !entry.isBackground) expressiveAccent else baseLineColor),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (subText != null) {
            Text(
                text = subText,
                fontSize = 18.sp,
                color = baseLineColor.copy(alpha = 0.6f),
                textAlign = agentTextAlign,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp).fillMaxWidth(),
                lineHeight = (18 * lyricsLineSpacing.coerceAtMost(1.3f)).sp
            )
        }

        if (showTranslated) {
            val translatedText by entry.translatedTextFlow.collectAsState()
            translatedText?.let { translated ->
                Text(
                    text = translated,
                    fontSize = 16.sp,
                    color = expressiveAccent.copy(alpha = 0.8f),
                    textAlign = agentTextAlign,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                    lineHeight = (16 * lyricsLineSpacing.coerceAtMost(1.3f)).sp
                )
            }
        }
    }
}

@Composable
private fun WordLevelCanvasLyrics(
    mainText: String,
    words: List<MetroWordTimestamp>,
    isActiveLine: Boolean,
    effectivePlaybackPosition: Long,
    lyricsOffset: Long = 0L,
    lyricStyle: TextStyle,
    lineColor: Color,
    expressiveAccent: Color,
    isBackground: Boolean,
    focusedAlpha: Float,
    alignment: TextAlign
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val playerConnection = LocalPlayerConnection.current

    var smoothPosition by remember { mutableLongStateOf(effectivePlaybackPosition + lyricsOffset) }
    
    LaunchedEffect(isActiveLine) {
        if (isActiveLine && playerConnection != null) {
            var lastPlayerPos = playerConnection.player.currentPosition
            var lastUpdateTime = System.currentTimeMillis()
            
            while (isActive) {
                withFrameMillis {
                    val now = System.currentTimeMillis()
                    val playerPos = playerConnection.player.currentPosition
                    val currentlyPlaying = playerConnection.player.isPlaying
                    
                    if (playerPos != lastPlayerPos) {
                        lastPlayerPos = playerPos
                        lastUpdateTime = now
                    }
                    
                    val elapsed = now - lastUpdateTime
                    smoothPosition = lastPlayerPos + lyricsOffset + (if (currentlyPlaying) elapsed else 0L)
                }
            }
        }
    }

    LaunchedEffect(effectivePlaybackPosition, isActiveLine) {
        if (!isActiveLine) {
            smoothPosition = effectivePlaybackPosition
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        val layoutResult = remember(mainText, maxWidthPx, lyricStyle) {
            textMeasurer.measure(
                text = mainText,
                style = lyricStyle,
                constraints = Constraints(minWidth = maxWidthPx, maxWidth = maxWidthPx),
                softWrap = true
            )
        }
        val glowStyle = remember(lyricStyle, expressiveAccent) {
            lyricStyle.copy(
                shadow = Shadow(
                    color = expressiveAccent.copy(alpha = 0.75f),
                    blurRadius = 24f,
                    offset = Offset.Zero
                )
            )
        }
        val glowLayoutResult = remember(mainText, maxWidthPx, glowStyle) {
            textMeasurer.measure(
                text = mainText,
                style = glowStyle,
                constraints = Constraints(minWidth = maxWidthPx, maxWidth = maxWidthPx),
                softWrap = true
            )
        }
        
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { layoutResult.size.height.toDp() })
            .graphicsLayer(clip = false)
        ) {
            if (mainText.isEmpty()) return@Canvas
            if (!isActiveLine) {
                drawText(layoutResult, color = lineColor)
            } else {
                drawText(layoutResult, color = expressiveAccent.copy(alpha = focusedAlpha))

                val completedPath = Path()
                var hasCompleted = false
                val activeSegments = mutableListOf<ActiveWordSegment>()
                
                val wordIdxMap = IntArray(mainText.length) { -1 }
                var currentPos = 0
                words.forEachIndexed { wordIdx, word ->
                    val rawWordText = word.text.let { 
                        if (isBackground) {
                            var t = it
                            if (wordIdx == 0) t = t.removePrefix("(")
                            if (wordIdx == words.size - 1) t = t.removeSuffix(")")
                            t
                        } else it
                    }
                    val indexInMain = mainText.indexOf(rawWordText, currentPos)
                    if (indexInMain != -1) {
                        for (i in 0 until rawWordText.length) {
                            wordIdxMap[indexInMain + i] = wordIdx
                        }
                        if (indexInMain + rawWordText.length < mainText.length && mainText[indexInMain + rawWordText.length] == ' ') {
                            wordIdxMap[indexInMain + rawWordText.length] = wordIdx
                        }
                        currentPos = indexInMain + rawWordText.length
                    }
                }

                for (wordIdx in words.indices) {
                    val word = words[wordIdx]
                    val wStartMs = (word.startTime * 1000).toLong()
                    val wEndMs = (word.endTime * 1000).toLong()
                    
                    if (smoothPosition < wStartMs) continue
                    
                    val progress = if (smoothPosition >= wEndMs) 1f 
                                   else (smoothPosition - wStartMs).toFloat() / (wEndMs - wStartMs).coerceAtLeast(1)

                    var firstChar = -1
                    var lastChar = -1
                    for (i in wordIdxMap.indices) {
                        if (wordIdxMap[i] == wordIdx) {
                            if (firstChar == -1) firstChar = i
                            lastChar = i
                        }
                    }
                    
                    if (firstChar != -1) {
                        var startLine = layoutResult.getLineForOffset(firstChar)
                        var lineStartLeft = Float.MAX_VALUE
                        var lineStartTop = Float.MAX_VALUE
                        var lineEndRight = Float.MIN_VALUE
                        var lineEndBottom = Float.MIN_VALUE
                        var currentLineIdx = startLine
                        
                        val isRtl = layoutResult.getBidiRunDirection(firstChar) == androidx.compose.ui.text.style.ResolvedTextDirection.Rtl
                        
                        for (i in firstChar..lastChar) {
                            if (wordIdxMap[i] != wordIdx) continue
                            val line = layoutResult.getLineForOffset(i)
                            val bounds = layoutResult.getBoundingBox(i)
                            
                            if (line != currentLineIdx) {
                                if (progress >= 1f) {
                                    completedPath.addRect(Rect(lineStartLeft, lineStartTop, lineEndRight, lineEndBottom))
                                    hasCompleted = true
                                } else {
                                    activeSegments.add(
                                        ActiveWordSegment(lineStartLeft, lineStartTop, lineEndRight, lineEndBottom, progress, isRtl)
                                    )
                                }
                                currentLineIdx = line
                                lineStartLeft = Float.MAX_VALUE
                                lineStartTop = Float.MAX_VALUE
                                lineEndRight = Float.MIN_VALUE
                                lineEndBottom = Float.MIN_VALUE
                            }
                            
                            if (bounds.left < lineStartLeft) lineStartLeft = bounds.left
                            if (bounds.top < lineStartTop) lineStartTop = bounds.top
                            if (bounds.right > lineEndRight) lineEndRight = bounds.right
                            if (bounds.bottom > lineEndBottom) lineEndBottom = bounds.bottom
                        }
                        
                        if (progress >= 1f) {
                            completedPath.addRect(Rect(lineStartLeft, lineStartTop, lineEndRight, lineEndBottom))
                            hasCompleted = true
                        } else {
                            activeSegments.add(
                                ActiveWordSegment(lineStartLeft, lineStartTop, lineEndRight, lineEndBottom, progress, isRtl)
                            )
                        }
                    }
                }

                if (hasCompleted) {
                    clipPath(completedPath) {
                        drawText(layoutResult, color = expressiveAccent)
                    }
                }

                activeSegments.forEach { seg ->
                    val segWidth = (seg.right - seg.left).coerceAtLeast(1f)
                    val fillWidth = segWidth * seg.progress
                    val headX = if (seg.isRtl) seg.right - fillWidth else seg.left + fillWidth
                    val centerY = (seg.top + seg.bottom) / 2f
                    val glowRadius = (seg.bottom - seg.top) * 1.6f

                    // 1. Apple Music Ambient Bloom Glow (soft radial bloom traveling with singer)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                expressiveAccent.copy(alpha = 0.35f),
                                expressiveAccent.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = Offset(headX, centerY),
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = Offset(headX, centerY)
                    )

                    // 2. Glowing shadow text for the singing portion
                    clipRect(
                        left = if (seg.isRtl) headX else seg.left - 8f,
                        top = seg.top - 8f,
                        right = if (seg.isRtl) seg.right + 8f else headX,
                        bottom = seg.bottom + 8f
                    ) {
                        drawText(glowLayoutResult, color = expressiveAccent)
                    }

                    // 3. Feathered gradient fill for crisp text (butter-smooth leading edge)
                    val featherPx = 18f
                    val brush = if (!seg.isRtl) {
                        val stop0 = ((headX - featherPx - seg.left) / segWidth).coerceIn(0f, 1f)
                        val stop1 = ((headX + 2f - seg.left) / segWidth).coerceIn(0f, 1f)
                        if (stop1 > stop0 && stop0 < 1f && stop1 > 0f) {
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to expressiveAccent,
                                    stop0 to expressiveAccent,
                                    stop1 to expressiveAccent.copy(alpha = focusedAlpha),
                                    1f to expressiveAccent.copy(alpha = focusedAlpha)
                                ),
                                startX = seg.left,
                                endX = seg.right
                            )
                        } else {
                            SolidColor(expressiveAccent)
                        }
                    } else {
                        val stop0 = ((headX - 2f - seg.left) / segWidth).coerceIn(0f, 1f)
                        val stop1 = ((headX + featherPx - seg.left) / segWidth).coerceIn(0f, 1f)
                        if (stop1 > stop0 && stop0 < 1f && stop1 > 0f) {
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to expressiveAccent.copy(alpha = focusedAlpha),
                                    stop0 to expressiveAccent.copy(alpha = focusedAlpha),
                                    stop1 to expressiveAccent,
                                    1f to expressiveAccent
                                ),
                                startX = seg.left,
                                endX = seg.right
                            )
                        } else {
                            SolidColor(expressiveAccent)
                        }
                    }

                    clipRect(left = seg.left, top = seg.top, right = seg.right, bottom = seg.bottom) {
                        drawText(layoutResult, brush = brush)
                    }
                }
            }
        }
    }
}
