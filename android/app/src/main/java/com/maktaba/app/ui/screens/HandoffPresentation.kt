package com.maktaba.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.maktaba.app.data.*
import com.google.firebase.functions.FirebaseFunctionsException
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.R
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.CancellationException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun parseLendingCode(raw: String): String? {
    val text = raw.trim()
    LoanInviteCode.parse(text)?.let { return it.value }
    val uri = try {
        URI(text)
    } catch (_: URISyntaxException) {
        return null
    }
    if (!uri.scheme.equals("maktaba", ignoreCase = true) ||
        !uri.host.equals("loan", ignoreCase = true) ||
        uri.rawPath.orEmpty() !in setOf("", "/") || uri.rawFragment != null ||
        uri.rawUserInfo != null || uri.port != -1
    ) return null
    val entries = try {
        uri.rawQuery.orEmpty().split("&").map {
            val pair = it.split("=", limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to
                URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
    } catch (_: IllegalArgumentException) {
        return null
    }
    val codes = entries.filter { it.first == "code" }
    return codes.singleOrNull()?.second?.let { LoanInviteCode.parse(it)?.value }
}

internal fun defaultReturnPickerMillis(clock: Clock): Long =
    LocalDate.now(clock).plusDays(14).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun inviteBorrowerLabelKey(inviteId: String) = "issued-invite-label:$inviteId"

internal fun canLendCopy(book: Book, loan: ActiveLoan?): Boolean =
    book.status == BookStatus.OWNED && book.format == BookFormat.PHYSICAL &&
        loan?.status != LoanStatus.ACTIVE && loan?.status != LoanStatus.RETURN_REQUESTED

internal fun inviteIsPending(invite: PendingLoanInvite, now: Instant): Boolean =
    invite.status == "pending" && invite.expiresAt.isAfter(now) &&
        invite.dueAt?.isAfter(now) == true

internal fun sameIssuedInvite(first: PendingLoanInvite, next: PendingLoanInvite): Boolean =
    first.id == next.id && first.copyId == next.copyId && first.code == next.code &&
        first.dueAt == next.dueAt && first.expiresAt == next.expiresAt

internal fun isTerminalHandoffFailure(failure: Throwable): Boolean =
    failure is InviteUnavailableException || failure is FirebaseFunctionsException && failure.code in setOf(
        FirebaseFunctionsException.Code.NOT_FOUND,
        FirebaseFunctionsException.Code.FAILED_PRECONDITION,
        FirebaseFunctionsException.Code.ALREADY_EXISTS,
        FirebaseFunctionsException.Code.PERMISSION_DENIED,
        FirebaseFunctionsException.Code.UNAUTHENTICATED,
        FirebaseFunctionsException.Code.INVALID_ARGUMENT
    )

internal suspend fun <T> handoffAttempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}

@Composable
internal fun HandoffTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    isError: Boolean = false,
    placeholder: String = "",
    textStyle: TextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 20.sp),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    containerColor: Color = MaktabaTheme.colors.surface
) {
    val colors = MaktabaTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    BasicTextField(
        value = value, onValueChange = onValueChange, enabled = enabled, readOnly = readOnly,
        singleLine = singleLine, minLines = if (singleLine) 1 else 3,
        textStyle = textStyle.copy(color = if (enabled) colors.ink else colors.inkMuted),
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        cursorBrush = SolidColor(colors.primary), interactionSource = interactions,
        modifier = modifier.fillMaxWidth().heightIn(min = if (singleLine) 48.dp else 88.dp)
            .border(1.dp, when {
                isError -> colors.danger
                focused -> colors.primary
                else -> colors.divider
            }, MaktabaShapes.small)
            .background(containerColor, MaktabaShapes.small)
            .padding(horizontal = 13.dp, vertical = 13.dp)
            .semantics { if (isError) error("Check this field") },
        decorationBox = { input ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = colors.inkMuted)
                input()
            }
        }
    )
}

@Composable
internal fun HandoffPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    illustrated: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaktabaTheme.colors
    MaktabaScaffold(
        topBar = {
            ScreenTopBar(title = title, onBack = onBack, actions = {
                if (illustrated) {
                    Image(
                        painterResource(R.drawable.illus_trailing_shelf), null,
                        contentScale = ContentScale.Crop, alignment = Alignment.TopEnd,
                        modifier = Modifier.offset(x = 16.dp).width(56.dp).height(48.dp)
                            .graphicsLayer { alpha = if (colors.isDark) 0.55f else 0.7f }
                            .soulArtFade()
                    )
                }
            })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(top = 3.dp, bottom = 24.dp),
                content = content
            )
            Surface(color = colors.backgroundElevated) {
                Column {
                    HorizontalDivider(color = colors.divider)
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        content = actions
                    )
                }
            }
        }
    }
}

@Composable
internal fun HandoffHeading(text: String, centered: Boolean = false, fontSize: TextUnit = 25.sp) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = fontSize, lineHeight = 29.sp, letterSpacing = (-0.6).sp,
            fontWeight = FontWeight.Normal),
        color = MaktabaTheme.colors.ink,
        textAlign = if (centered) androidx.compose.ui.text.style.TextAlign.Center
            else androidx.compose.ui.text.style.TextAlign.Start,
        modifier = Modifier.fillMaxWidth().semantics { heading() }
    )
}

@Composable
internal fun HandoffBookSummary(book: Book, compact: Boolean = false) {
    val colors = MaktabaTheme.colors
    Surface(
        shape = MaktabaShapes.medium,
        color = colors.ochre,
        border = BorderStroke(1.dp, colors.divider)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(if (compact) 13.dp else 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverImage(
                book = book, contentScale = ContentScale.Fit, decorative = true,
                modifier = Modifier.width(if (compact) 42.dp else 64.dp).aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(if (compact) 12.dp else 14.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = if (compact) 17.sp else 20.sp,
                    lineHeight = if (compact) 21.25.sp else 24.sp,
                    letterSpacing = (-0.3).sp, fontWeight = FontWeight.Normal,
                    lineHeightStyle = LoanLineHeightStyle), color = colors.ink)
                Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
                Text(book.author, style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = if (compact) 10.sp else 11.sp, lineHeight = if (compact) 16.sp else 17.6.sp,
                    lineHeightStyle = LoanLineHeightStyle),
                    color = colors.inkMuted)
                Spacer(Modifier.height(if (compact) 5.dp else 10.dp))
                Text(
                    when (book.format) {
                        BookFormat.DIGITAL -> "Digital edition"
                        BookFormat.PHYSICAL -> "Physical edition"
                        BookFormat.UNKNOWN -> "Edition details unavailable"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 9.sp, lineHeight = 14.4.sp, lineHeightStyle = LoanLineHeightStyle),
                    color = colors.inkMuted
                )
            }
        }
    }
}

@Composable
internal fun HandoffNotice(
    text: String,
    error: Boolean = false,
    icon: ImageVector? = null,
    positive: Boolean = false
) {
    val colors = MaktabaTheme.colors
    Surface(
        shape = MaktabaShapes.small,
        color = when {
            error -> colors.terracotta
            else -> colors.sage
        },
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.heightIn(min = 66.dp).padding(13.dp)) {
            if (icon != null) {
                Icon(icon, null, tint = if (error) colors.danger else colors.primary,
                    modifier = Modifier.padding(top = 3.dp).size(15.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                color = when {
                    error -> colors.danger
                    positive -> colors.success
                    else -> colors.inkMuted
                })
        }
    }
}

@Composable
internal fun HandoffDateCard(dueAt: Instant?) {
    val colors = MaktabaTheme.colors
    Surface(
        shape = MaktabaShapes.medium, color = colors.sage,
        border = BorderStroke(1.dp, colors.divider), modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.heightIn(min = 70.dp).padding(start = 15.dp, end = 65.dp, top = 15.dp, bottom = 15.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(colors.surfaceAlt),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CalendarToday, null, modifier = Modifier.size(16.dp), tint = colors.primary)
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("Agreed Return Date", style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 9.sp, lineHeight = 14.sp), color = colors.inkMuted)
                    Spacer(Modifier.height(6.dp))
                    Text(handoffDateText(dueAt),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp, fontWeight = FontWeight.Medium), color = colors.ink)
                }
            }
            Image(painterResource(R.drawable.illus_cup_books), null, contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 8.dp)
                    .width(42.dp).height(46.dp).graphicsLayer { alpha = 0.75f })
        }
    }
}

internal fun Modifier.soulArtFade(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(0f to Color.White, 0.6f to Color.White, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn
            )
        }

@Composable
internal fun InviteShelfArt() {
    val opacity = if (MaktabaTheme.colors.isDark) 0.3f else 0.45f
    Image(
        painterResource(R.drawable.illus_book_detail), null,
        contentScale = ContentScale.Crop, alignment = Alignment.TopStart,
        modifier = Modifier.offset(x = (-22).dp).width(48.dp).height(240.dp)
            .graphicsLayer { alpha = opacity; compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val center = Offset(0f, size.height * 0.2f)
                scale(scaleX = 1f, scaleY = size.height * 0.8f / size.width, pivot = center) {
                    drawRect(
                        Brush.radialGradient(
                            0f to Color.White, 0.55f to Color.White, 1f to Color.Transparent,
                            center = center, radius = size.width
                        ),
                        topLeft = Offset(0f, -size.height),
                        size = Size(size.width, size.height * 3f),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
    )
}

@Composable
internal fun HandoffCompanionArt(resource: Int) {
    Image(painterResource(resource), null, contentScale = ContentScale.Fit,
        modifier = Modifier.width(42.dp).height(44.dp).graphicsLayer { alpha = 0.8f })
}

internal fun handoffDateText(dueAt: Instant?, numeric: Boolean = false): String =
    dueAt?.atZone(ZoneId.systemDefault())?.format(
        DateTimeFormatter.ofPattern(if (numeric) "dd/MM/yyyy" else "d MMM yyyy", Locale.getDefault())
    ) ?: "Date unavailable"
