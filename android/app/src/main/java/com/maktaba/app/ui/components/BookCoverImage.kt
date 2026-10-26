package com.maktaba.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.maktaba.app.data.Book
import com.maktaba.app.data.BookJacketTone
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.ui.theme.SerifDisplay
import com.maktaba.app.ui.theme.MaktabaShapes
import java.util.Locale

@Composable
fun BookCoverImage(
    book: Book,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    decorative: Boolean = false,
    plainFallback: Boolean = false,
    backgroundColor: Color = MaktabaTheme.colors.surfaceAlt
) {
    val accessible = modifier.clip(MaktabaShapes.cover).clearAndSetSemantics {
        testTag = "cover:${book.catalogId}"
        if (!decorative) contentDescription = "Cover of ${book.title}"
    }
    if (book.coverUrl.isBlank()) {
        GeneratedBookJacket(book, accessible, plainFallback)
    } else {
        SubcomposeAsyncImage(
            model = book.coverUrl,
            contentDescription = null,
            contentScale = contentScale,
            modifier = accessible.background(backgroundColor),
            loading = { GeneratedBookJacket(book, Modifier.fillMaxSize(), plainFallback) },
            error = { GeneratedBookJacket(book, Modifier.fillMaxSize(), plainFallback) }
        )
    }
}

@Composable
private fun GeneratedBookJacket(book: Book, modifier: Modifier, plainFallback: Boolean) {
    val colors = MaktabaTheme.colors
    val olive = book.jacketTone == BookJacketTone.OLIVE
    val paper = if (olive) colors.jacketOlivePaper else colors.jacketPaper
    val ink = if (olive) colors.jacketOliveInk else colors.jacketInk
    val spine = if (olive) colors.jacketOliveSpine else colors.jacketSpine
    BoxWithConstraints(modifier.background(paper)) {
        val compact = maxWidth < 88.dp
        val shelfSized = maxWidth <= 130.dp
        val titleSize = if (compact) 8.sp else (maxWidth.value * 0.12f).coerceIn(12f, 24f).sp
        val imprintSize = (maxWidth.value * 0.05f).coerceIn(6f, 8f).sp
        val authorSize = if (compact) 4.sp else (maxWidth.value * 0.05f).coerceIn(6f, 9f).sp
        val markWidth = maxWidth * 0.18f
        Canvas(Modifier.fillMaxSize()) {
            val step = 4.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(colors.onPrimary.copy(alpha = 0.12f), Offset(x, 0f), Offset(x, size.height))
                x += step
            }
            drawRect(spine, size = Size((if (shelfSized) 5.dp else 7.dp).toPx(), size.height))
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(
                start = if (compact) 8.dp else if (shelfSized) 13.dp else 20.dp,
                end = if (compact) 4.dp else if (shelfSized) 8.dp else 13.dp,
                top = if (compact) 5.dp else if (shelfSized) 12.dp else 17.dp,
                bottom = if (compact) 5.dp else if (shelfSized) 12.dp else 17.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (!compact && !plainFallback) {
                Text(
                "THE BOOK HAVEN\nCOLLECTION",
                color = ink,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = imprintSize, lineHeight = imprintSize * 1.7f, letterSpacing = 1.5.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 2
                )
                Canvas(Modifier.width(markWidth).height(14.dp)) {
                    val sx = size.width / 30f
                    val sy = size.height / 20f
                    val markInk = ink.copy(alpha = 0.7f)
                    val stroke = 0.65.dp.toPx()
                    for ((left, top, diameter) in listOf(Triple(3f, 0f, 24f), Triple(9f, 6f, 12f))) {
                        drawArc(
                            markInk, 180f, 180f, false, Offset(left * sx, top * sy),
                            Size(diameter * sx, diameter * sy), style = Stroke(stroke)
                        )
                        drawLine(markInk, Offset(left * sx, 12f * sy), Offset(left * sx, 18f * sy), stroke)
                        drawLine(markInk, Offset((30f - left) * sx, 12f * sy), Offset((30f - left) * sx, 18f * sy), stroke)
                    }
                    drawLine(markInk, Offset(sx, 18f * sy), Offset(29f * sx, 18f * sy), stroke)
                }
            }
            Text(
                book.title,
                color = ink,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = SerifDisplay, fontSize = titleSize,
                    lineHeight = titleSize * 1.13f, fontWeight = FontWeight.Normal
                ),
                textAlign = TextAlign.Center,
                maxLines = if (compact) 3 else 5,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                book.author.uppercase(Locale.ROOT),
                color = ink,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = authorSize, lineHeight = authorSize * 1.5f, letterSpacing = if (compact) 0.sp else 1.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
