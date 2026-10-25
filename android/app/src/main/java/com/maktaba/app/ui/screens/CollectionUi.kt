package com.maktaba.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.SubcomposeAsyncImage
import com.maktaba.app.R
import com.maktaba.app.data.*
import com.maktaba.app.ui.components.*
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.ui.theme.SansBody
import com.maktaba.app.ui.theme.SerifDisplay

internal val CollectionLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

internal fun NavHostController.collectionRoute(prefix: String, vararg arguments: String) {
    navigate((listOf(prefix) + arguments.map(Uri::encode)).joinToString("/"))
}

@Composable
internal fun CollectionPage(
    navController: NavHostController,
    title: String,
    root: Boolean = false,
    showBottomBar: Boolean = true,
    sheet: Boolean = false,
    scrollable: Boolean = true,
    illustratedRoot: Boolean = true,
    headerArt: Int? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    MaktabaScaffold(
        selectedTab = if (showBottomBar) BottomNavTab.COLLECTIONS else null,
        onTabSelected = { navController.navigateToTab(it) },
        topBar = {
            if (root && !illustratedRoot) CollectionCompactTopBar(title)
            else if (root) CollectionIllustratedTopBar(title)
            else ScreenTopBar(title = title,
                onBack = if (root) null else ({ navController.popBackStack(); Unit }),
                actions = {
                    headerArt?.let { CollectionHeaderArtwork(it) }
                    actions()
                })
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = if (sheet) MaktabaTheme.colors.background else Color.Transparent,
            shape = if (sheet) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) else RoundedCornerShape(0.dp),
            border = null
        ) {
            val compactRoot = root && !illustratedRoot
            val gutter = when {
                showBottomBar && (!root || compactRoot) -> 18.dp
                !sheet && LocalConfiguration.current.screenWidthDp <= 330 -> 16.dp
                else -> 20.dp
            }
            val top = if (sheet) 10.dp else if (compactRoot) 7.dp else if (root) 0.dp else if (showBottomBar) 7.dp else 6.dp
            if (scrollable) {
                LazyColumn(
                    Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(start = gutter, end = gutter, top = top, bottom = 28.dp)
                ) {
                    item { Column(verticalArrangement = Arrangement.spacedBy(if (compactRoot) 0.dp else 12.dp), content = content) }
                }
            } else Column(Modifier.fillMaxSize().imePadding()
                .padding(start = gutter, end = gutter, top = top, bottom = 10.dp), content = content)
        }
    }
}

@Composable
internal fun CollectionHeaderArtwork(resource: Int) {
    Image(painterResource(resource), null, Modifier.size(42.dp, 44.dp).alpha(.8f),
        contentScale = ContentScale.Fit)
}

@Composable
internal fun CollectionIllustratedTopBar(title: String) {
    Column(Modifier.fillMaxWidth().statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text("BOOK HAVEN", color = MaktabaTheme.colors.primary,
            style = TextStyle(fontFamily = SansBody, fontSize = 8.sp, lineHeight = 10.sp,
                letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.drawable.illus_home_icon), null, Modifier.size(36.dp, 18.dp),
                contentScale = ContentScale.Fit)
            Text(title, modifier = Modifier.semantics { heading() }, color = MaktabaTheme.colors.ink,
                style = TextStyle(fontFamily = SerifDisplay, fontSize = 26.sp, lineHeight = 29.12.sp,
                    letterSpacing = (-.8).sp, lineHeightStyle = CollectionLineHeight))
        }
    }
}

@Composable
internal fun CollectionCompactTopBar(title: String) {
    Row(Modifier.fillMaxWidth().statusBarsPadding()
        .padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 9.dp)) {
            Text("BOOK HAVEN", color = MaktabaTheme.colors.primary,
                style = TextStyle(fontFamily = SansBody, fontSize = 8.sp, lineHeight = 10.sp, letterSpacing = 1.8.sp))
            Spacer(Modifier.height(5.dp))
            Text(title, modifier = Modifier.semantics { heading() }, color = MaktabaTheme.colors.ink,
                style = TextStyle(fontFamily = SerifDisplay, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-.5).sp))
        }
        CollectionHeaderArtwork(R.drawable.illus_loan_shelf)
    }
}

@Composable
internal fun CollectionHeading(text: String) {
    Text(text, style = TextStyle(fontFamily = SerifDisplay, fontSize = 25.sp, lineHeight = 30.5.sp,
        lineHeightStyle = CollectionLineHeight),
        color = MaktabaTheme.colors.ink)
}

@Composable
internal fun CollectionNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = TextStyle(fontFamily = SansBody, fontSize = 12.sp, lineHeight = 20.4.sp,
        lineHeightStyle = CollectionLineHeight),
        color = MaktabaTheme.colors.inkMuted)
}

@Composable
internal fun CollectionSection(text: String) {
    Text(text, style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 15.sp,
        letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold), color = MaktabaTheme.colors.primary)
}

@Composable
internal fun CollectionBadge(text: String, shared: Boolean = false) {
    Surface(shape = RoundedCornerShape(5.dp),
        color = if (shared) MaktabaTheme.colors.sage else MaktabaTheme.colors.surfaceAlt) {
        Text(text, Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            color = if (shared) MaktabaTheme.colors.primary else MaktabaTheme.colors.inkMuted,
            style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 15.sp,
                lineHeightStyle = CollectionLineHeight))
    }
}

@Composable
internal fun CollectionLink(title: String, subtitle: String, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick)
            .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = TextStyle(fontFamily = SansBody, fontSize = 13.sp, lineHeight = 15.6.sp,
                    lineHeightStyle = CollectionLineHeight))
                Text(subtitle, style = TextStyle(fontFamily = SansBody, fontSize = 11.sp, lineHeight = 16.5.sp,
                    lineHeightStyle = CollectionLineHeight),
                    color = MaktabaTheme.colors.inkMuted)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(15.dp),
                tint = MaktabaTheme.colors.inkMuted)
        }
        HorizontalDivider(color = MaktabaTheme.colors.divider)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaktabaTheme.colors
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).alpha(if (enabled) 1f else .55f),
            shape = RoundedCornerShape(9.dp), contentPadding = PaddingValues(horizontal = 15.dp, vertical = 11.dp),
            colors = ButtonDefaults.buttonColors(disabledContainerColor = colors.primary,
                disabledContentColor = colors.onPrimary)) {
            Text(label, style = TextStyle(fontFamily = SansBody, fontSize = 12.sp, lineHeight = 18.sp,
                fontWeight = FontWeight.Normal, lineHeightStyle = CollectionLineHeight))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionOutline(label: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val colors = MaktabaTheme.colors
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        OutlinedButton(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, if (danger) colors.danger else colors.sageLine),
            contentPadding = PaddingValues(horizontal = 15.dp, vertical = 11.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (danger) colors.dangerContainer else Color.Transparent,
                contentColor = if (danger) colors.danger else colors.primary)) {
            Text(label, style = TextStyle(fontFamily = SansBody, fontSize = 12.sp, lineHeight = 18.sp,
                fontWeight = FontWeight.Normal, lineHeightStyle = CollectionLineHeight))
        }
    }
}

@Composable
internal fun CollectionTextField(
    value: String,
    onValue: (String) -> Unit,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
    limit: Int? = null,
    counter: Boolean = false,
    search: Boolean = false,
    paper: Boolean = false
) {
    val colors = MaktabaTheme.colors
    Column {
        if (label != null) {
            Text(label, style = TextStyle(fontFamily = SansBody, fontSize = 12.sp, lineHeight = 14.4.sp))
            Spacer(Modifier.height(8.dp))
        }
        BasicTextField(
            value, { if (limit == null || it.length <= limit) onValue(it) }, enabled = enabled,
            singleLine = true, cursorBrush = SolidColor(colors.primary),
            textStyle = TextStyle(fontFamily = SansBody, fontSize = 12.sp, lineHeight = 19.2.sp, color = colors.ink),
            modifier = Modifier.fillMaxWidth().heightIn(min = if (search) 44.dp else 46.dp)
                .border(1.dp, if (search) colors.sageLine else colors.divider, RoundedCornerShape(9.dp))
                .background(if (paper) colors.background else colors.surface, RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            decorationBox = { input ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (search) Icon(Icons.Outlined.Search, null, Modifier.size(15.dp), tint = colors.inkMuted)
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, color = colors.inkMuted,
                            style = TextStyle(fontFamily = SansBody, fontSize = 12.sp, lineHeight = 19.2.sp))
                        input()
                    }
                }
            }
        )
        if (counter && limit != null) {
            Spacer(Modifier.height(6.dp))
            Text("${value.trim().length}/$limit · required", Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                color = colors.inkMuted, style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 15.sp))
        }
    }
}

@Composable
internal fun CollectionPanel(
    modifier: Modifier = Modifier,
    color: Color = MaktabaTheme.colors.sage,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaktabaTheme.colors.divider), color = color) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun CollectionProblem(error: Throwable, retry: () -> Unit) {
    CollectionPanel(color = MaktabaTheme.colors.terracotta) {
        CollectionNote(collectionErrorMessage(error))
    }
    CollectionOutline("Retry", onClick = retry)
}

@Composable
internal fun collectionAccent(position: Int): Color = when (position % 3) {
    0 -> MaktabaTheme.colors.sage
    1 -> MaktabaTheme.colors.ochre
    else -> MaktabaTheme.colors.terracotta
}

@Composable
internal fun <T> CollectionLoad(state: LoadState<T>, retry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        LoadState.Loading -> {
            CircularProgressIndicator(Modifier.size(28.dp))
            CollectionNote("Loading…")
        }
        LoadState.NotFound -> {
            CollectionHeading("Not currently shared")
            CollectionNote("This reader may be publishing changes, or this content is now private.")
            CollectionOutline("Check again", onClick = retry)
        }
        is LoadState.Error -> CollectionProblem(state.cause, retry)
        is LoadState.Ready -> content(state.value)
    }
}

@Composable
internal fun <T> rememberCollectionFeed(key: String, factory: () -> PublicCollectionFeed<T>): PublicCollectionFeed<T> {
    val feed = remember(key) { factory() }
    DisposableEffect(feed) { onDispose { feed.close() } }
    return feed
}

internal fun SharedCollectionBook.coverBook(presentation: Book? = null) = Book(catalogBookId, title, author, R.drawable.ic_book_arch,
    BookStatus.OWNED, catalogId = catalogBookId, coverUrl = coverUrl, format = format,
    genre = presentation?.genre.orEmpty(), jacketTone = presentation?.jacketTone ?: BookJacketTone.LINEN)

internal fun SharedLibraryBook.coverBook(presentation: Book? = null) = Book(catalogBookId, title, author, R.drawable.ic_book_arch,
    BookStatus.OWNED, catalogId = catalogBookId, coverUrl = coverUrl, format = format,
    genre = presentation?.genre.orEmpty(), jacketTone = presentation?.jacketTone ?: BookJacketTone.LINEN)

@Composable
internal fun CollectionBookGrid(
    books: List<Book>,
    label: (Book) -> String = { "Catalogue reference" },
    onBook: (String) -> Unit,
    companion: Boolean = false,
    showAuthors: Boolean = true,
    showLabels: Boolean = true,
    labelPills: Boolean = false,
    labelColor: (Book) -> Color = { Color.Unspecified },
    digitalStatusIcon: Boolean = false
) {
    val colors = MaktabaTheme.colors
    BoxWithConstraints {
        val columns = if (maxWidth < 300.dp) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(if (companion) 16.dp else 22.dp)) {
            books.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(if (companion) 10.dp else 12.dp)) {
                    row.forEach { book ->
                        Column(Modifier.weight(1f).clickable { onBook(book.catalogId) }) {
                            BookCoverImage(book, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(5.dp)),
                                contentScale = ContentScale.Fit, decorative = true, plainFallback = companion)
                            Spacer(Modifier.height(if (companion) 8.dp else 9.dp))
                            Text(book.title, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontFamily = SerifDisplay, fontSize = if (companion) 12.sp else 13.sp,
                                    lineHeight = if (companion) 15.6.sp else 16.9.sp,
                                    lineHeightStyle = CollectionLineHeight))
                            if (showAuthors) {
                                Spacer(Modifier.height(5.dp))
                                Text(book.author, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 15.sp,
                                        lineHeightStyle = CollectionLineHeight),
                                    color = colors.inkMuted)
                            }
                            if (showLabels) {
                                Spacer(Modifier.height(if (!companion) 7.dp else if (!showAuthors) 5.dp else 0.dp))
                                if (labelPills) {
                                    Surface(shape = RoundedCornerShape(5.dp), color = colors.surfaceAlt) {
                                        Text(label(book), Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                            color = labelColor(book).takeUnless { it == Color.Unspecified } ?: colors.inkMuted,
                                            style = TextStyle(fontFamily = SansBody, fontSize = 10.sp, lineHeight = 15.sp,
                                                lineHeightStyle = CollectionLineHeight))
                                    }
                                } else Row(Modifier.heightIn(min = 27.dp)) {
                                    if (digitalStatusIcon && book.format == BookFormat.DIGITAL) {
                                        Icon(Icons.Outlined.PhoneAndroid, null, Modifier.padding(top = 1.dp).size(10.dp),
                                            tint = labelColor(book).takeUnless { it == Color.Unspecified } ?: colors.inkMuted)
                                        Spacer(Modifier.width(3.dp))
                                    }
                                    Text(label(book), style = TextStyle(fontFamily = SansBody, fontSize = 9.sp, lineHeight = 13.5.sp,
                                        lineHeightStyle = CollectionLineHeight),
                                        color = labelColor(book).takeUnless { it == Color.Unspecified } ?: colors.inkMuted)
                                }
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun CollectionMosaic(
    urls: List<String>,
    title: String,
    modifier: Modifier = Modifier.width(56.dp),
    previewBooks: List<SharedCollectionBook> = emptyList(),
    catalogue: List<Book> = emptyList()
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { column ->
                    val index = row * 2 + column
                    val preview = previewBooks.getOrNull(index)
                    val url = urls.getOrNull(index)
                    val cover = preview?.let { book ->
                        book.coverBook(catalogue.find { it.catalogId == book.catalogBookId })
                    } ?: url?.takeIf { it.isNotBlank() }?.let {
                        Book(it, title, "", R.drawable.ic_book_arch, BookStatus.OWNED, coverUrl = it)
                    }
                    val frame = Modifier.weight(1f).aspectRatio(2f / 3f).clip(RoundedCornerShape(2.dp))
                    if (cover != null) BookCoverImage(cover, frame, ContentScale.Fit, decorative = true)
                    else Box(frame.background(MaktabaTheme.colors.surfaceAlt))
                }
            }
        }
    }
}

@Composable
internal fun SharedCollectionCard(summary: SharedCollectionSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            summary.coverUrls.take(4).forEach { url ->
                BookCoverImage(Book(url, summary.name, "", R.drawable.ic_book_arch, BookStatus.OWNED, coverUrl = url),
                    Modifier.width(46.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(5.dp)),
                    ContentScale.Fit, decorative = true, plainFallback = true)
            }
        }
        CollectionLink(summary.name, "${summary.bookCount} visible titles · Shared", onClick)
    }
}

@Composable
internal fun PublicReaderIdentity(name: String, photoUrl: String, onClick: (() -> Unit)? = null, isOwner: Boolean = false) {
    Row(modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(shape = CircleShape, color = MaktabaTheme.colors.surfaceAlt, modifier = Modifier.size(29.dp)) {
            val initials: @Composable () -> Unit = {
                Box(contentAlignment = Alignment.Center) {
                    Text(name.trim().take(1).uppercase(), style = TextStyle(fontFamily = SerifDisplay, fontSize = 15.sp),
                        color = MaktabaTheme.colors.primary)
                }

            }
            if (photoUrl.isBlank()) initials() else SubcomposeAsyncImage(
                model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop,
                loading = { initials() }, error = { initials() })
        }
        Text(name, style = TextStyle(fontFamily = SansBody, fontSize = 11.sp, lineHeight = 16.5.sp),
            color = MaktabaTheme.colors.inkMuted)
        if (isOwner) Surface(shape = RoundedCornerShape(4.dp), color = MaktabaTheme.colors.successContainer) {
            Text("Yours", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = MaktabaTheme.colors.success,
                style = TextStyle(fontFamily = SansBody, fontSize = 8.sp, lineHeight = 12.sp))
        }
    }
}

@Composable
internal fun CollectionSharingSwitch(checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) {
    val colors = MaktabaTheme.colors
    Box(Modifier.size(width = 48.dp, height = 44.dp).alpha(if (enabled) 1f else .45f)
        .semantics { contentDescription = "Share My Library" }
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked),
        contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(26.dp)
            .background(if (checked) colors.success else colors.surfaceAlt, RoundedCornerShape(20.dp))
            .border(1.dp, if (checked) colors.success else colors.divider, RoundedCornerShape(20.dp)))
        Box(Modifier.align(Alignment.CenterStart).padding(start = if (checked) 26.dp else 4.dp)
            .size(18.dp).background(if (checked) colors.background else colors.inkMuted, RoundedCornerShape(9.dp)))
    }
}
