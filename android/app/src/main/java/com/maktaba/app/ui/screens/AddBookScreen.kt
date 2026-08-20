package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.maktaba.app.R
import com.maktaba.app.data.BookMetadata
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.compactIsbn
import com.maktaba.app.data.isValidIsbn
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.SecondaryButton
import com.maktaba.app.ui.theme.CreamBackground
import com.maktaba.app.ui.theme.DividerTan
import com.maktaba.app.ui.theme.InkBrown
import com.maktaba.app.ui.theme.MutedText
import com.maktaba.app.ui.theme.SurfaceCard
import com.maktaba.app.ui.theme.WoodBrown
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookScreen(navController: NavHostController) {
    var isbn by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf("manual") }
    var catalogBookId by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(BookFormat.UNKNOWN) }
    var physicalEditionIsbn13 by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var isbn13 by remember { mutableStateOf("") }
    var isbn10 by remember { mutableStateOf("") }
    var publisher by remember { mutableStateOf("") }
    var publishedDate by remember { mutableStateOf("") }
    var binding by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scanner = remember {
        GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13)
                .enableAutoZoom()
                .build()
        )
    }
    val displayedFormat = BookFormat.from(null, binding)
        .takeUnless { it == BookFormat.UNKNOWN } ?: format

    fun populate(metadata: BookMetadata) {
        source = metadata.source
        catalogBookId = metadata.catalogBookId
        sourceUrl = metadata.sourceUrl
        format = metadata.format
        physicalEditionIsbn13 = metadata.physicalEditionIsbn13
        title = metadata.title
        author = metadata.authors.joinToString(", ")
        isbn13 = metadata.isbn13
        isbn10 = metadata.isbn10
        publisher = metadata.publisher
        publishedDate = metadata.publishedDate
        binding = metadata.binding
        coverUrl = metadata.coverUrl
        showForm = true
    }

    fun lookup() {
        val normalized = compactIsbn(isbn)
        if (!isValidIsbn(normalized)) {
            error = "Enter a valid ISBN-10 or ISBN-13."
            return
        }
        loading = true
        error = null
        scope.launch {
            runCatching { LibraryRepository.lookupBookByIsbn(normalized) }
                .onSuccess(::populate)
                .onFailure {
                    error = it.localizedMessage ?: "Could not look up this ISBN."
                    isbn13 = if (normalized.length == 13) normalized else ""
                    isbn10 = if (normalized.length == 10) normalized else ""
                }
            loading = false
        }
    }

    Box(Modifier.fillMaxSize().background(CreamBackground).statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(
                title = if (showForm) "Review Book" else "Scan or Enter ISBN",
                onBack = {
                    if (showForm) {
                        showForm = false
                        error = null
                    } else {
                        navController.popBackStack()
                    }
                }
            )
            if (!showForm) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(24.dp))
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = WoodBrown,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Scan the barcode printed near the ISBN, or enter the ISBN-10/ISBN-13 below.",
                        color = InkBrown,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    PrimaryButton(
                        text = "Scan ISBN Barcode",
                        enabled = !loading,
                        leadingIcon = { Icon(Icons.Filled.CameraAlt, null, tint = Color.White) },
                        onClick = {
                            scope.launch {
                                runCatching { scanner.startScan().await().rawValue.orEmpty() }
                                    .onSuccess {
                                        isbn = it
                                        lookup()
                                    }
                                    .onFailure { error = "Scanning was cancelled or unavailable." }
                            }
                        }
                    )
                    Spacer(Modifier.height(18.dp))
                    BookField(
                        value = isbn,
                        onValueChange = { isbn = it; error = null },
                        label = "ISBN",
                        placeholder = "9781250255174",
                        keyboardType = KeyboardType.Ascii
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Find Book",
                        enabled = !loading && isbn.isNotBlank(),
                        loading = loading,
                        onClick = ::lookup
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(
                        text = "Enter Details Manually",
                        enabled = !loading,
                        leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Color.White) },
                        onClick = {
                            val normalized = compactIsbn(isbn)
                            isbn13 = if (normalized.length == 13 && isValidIsbn(normalized)) normalized else ""
                            isbn10 = if (normalized.length == 10 && isValidIsbn(normalized)) normalized else ""
                            source = "manual"
                            sourceUrl = ""
                            showForm = true
                            error = null
                        }
                    )
                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Color(0xFFB3261E), fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You can still enter the book details manually.",
                            color = MutedText,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        "Source: ${if (source == "isbnsearch") "ISBNsearch" else if (source == "firebase") "Maktaba catalog" else "Manual"}",
                        color = MutedText,
                        fontSize = 13.sp
                    )
                    if (displayedFormat == BookFormat.DIGITAL) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            buildString {
                                append("Digital / eBook — this edition can’t be lent through Maktaba.")
                                if (physicalEditionIsbn13.isNotBlank()) {
                                    append(" Physical ISBN: $physicalEditionIsbn13.")
                                }
                            },
                            color = WoodBrown,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = "Retrieved cover for $title",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 100.dp, height = 145.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    BookField(title, { title = it; error = null }, "Title *", "Book title")
                    BookField(author, { author = it; error = null }, "Author *", "Author name")
                    BookField(isbn13, { isbn13 = it }, "ISBN-13", "Optional", KeyboardType.Number)
                    BookField(isbn10, { isbn10 = it }, "ISBN-10", "Optional", KeyboardType.Ascii)
                    BookField(publisher, { publisher = it }, "Publisher", "Optional")
                    BookField(publishedDate, { publishedDate = it }, "Publication date", "YYYY-MM-DD")
                    BookField(binding, { binding = it }, "Binding", "Paperback, Hardcover...")
                    BookField(genre, { genre = it }, "Genre", "Optional")
                    BookField(pages, { pages = it.filter(Char::isDigit) }, "Page count", "Optional", KeyboardType.Number)
                    BookField(description, { description = it }, "Description", "Optional", singleLine = false)
                    error?.let {
                        Text(it, color = Color(0xFFB3261E), fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    PrimaryButton(
                        text = "Add to My Library",
                        enabled = !loading && title.isNotBlank() && author.isNotBlank(),
                        loading = loading,
                        onClick = {
                            val suppliedIsbn = isbn13.ifBlank { isbn10 }
                            if (suppliedIsbn.isNotBlank() && !isValidIsbn(suppliedIsbn)) {
                                error = "The supplied ISBN is invalid."
                                return@PrimaryButton
                            }
                            loading = true
                            error = null
                            scope.launch {
                                val metadata = BookMetadata(
                                    catalogBookId = catalogBookId,
                                    title = title.trim(),
                                    authors = author.split(",").map(String::trim).filter(String::isNotBlank),
                                    isbn13 = compactIsbn(isbn13),
                                    isbn10 = compactIsbn(isbn10),
                                    publisher = publisher.trim(),
                                    publishedDate = publishedDate.trim(),
                                    binding = binding.trim(),
                                    pageCount = pages.toIntOrNull() ?: 0,
                                    genres = listOfNotNull(genre.trim().takeIf(String::isNotBlank)),
                                    description = description.trim(),
                                    coverUrl = coverUrl,
                                    source = source,
                                    sourceUrl = sourceUrl,
                                    format = displayedFormat,
                                    physicalEditionIsbn13 = physicalEditionIsbn13
                                )
                                runCatching { LibraryRepository.addBook(metadata) }
                                    .onSuccess { navController.popBackStack() }
                                    .onFailure { error = it.localizedMessage ?: "Could not add this book." }
                                loading = false
                            }
                        }
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            containerColor = SurfaceCard,
            focusedBorderColor = WoodBrown,
            unfocusedBorderColor = DividerTan
        )
    )
}
