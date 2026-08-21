package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.BookMetadata
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.compactIsbn
import com.maktaba.app.data.isValidIsbn
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.SecondaryButton
import com.maktaba.app.ui.theme.MaktabaAppTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AddBookScreen(navController: NavHostController) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing

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
            error = "That is not a valid ISBN-10 or ISBN-13. Check the digits and try again."
            return
        }
        loading = true
        error = null
        scope.launch {
            runCatching { LibraryRepository.lookupBookByIsbn(normalized) }
                .onSuccess(::populate)
                .onFailure {
                    error = it.localizedMessage ?: "We could not find that ISBN."
                    isbn13 = if (normalized.length == 13) normalized else ""
                    isbn10 = if (normalized.length == 10) normalized else ""
                }
            loading = false
        }
    }

    MaktabaScaffold(
        topBar = {
            ScreenTopBar(
                title = if (showForm) "Check the details" else "Add a book",
                onBack = {
                    if (showForm) {
                        showForm = false
                        error = null
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = spacing.gutter)
        ) {
            if (!showForm) {
                Spacer(Modifier.height(spacing.lg))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.height(spacing.md))
                Text(
                    "Scan the barcode on the back cover, or type the ISBN printed beside it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.inkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(spacing.lg))
                PrimaryButton(
                    text = "Scan barcode",
                    enabled = !loading,
                    leadingIcon = {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = colors.onPrimary
                        )
                    },
                    onClick = {
                        scope.launch {
                            runCatching { scanner.startScan().await().rawValue.orEmpty() }
                                .onSuccess {
                                    isbn = it
                                    lookup()
                                }
                                .onFailure {
                                    error = "Scanning was cancelled, or the camera is unavailable."
                                }
                        }
                    }
                )
                Spacer(Modifier.height(spacing.md))
                BookField(
                    value = isbn,
                    onValueChange = { isbn = it; error = null },
                    label = "ISBN",
                    placeholder = "9781250255174",
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Go,
                    onImeAction = ::lookup
                )
                PrimaryButton(
                    text = "Look up this ISBN",
                    enabled = !loading && isbn.isNotBlank(),
                    loading = loading,
                    onClick = ::lookup
                )
                Spacer(Modifier.height(spacing.sm))
                SecondaryButton(
                    text = "Enter details by hand",
                    enabled = !loading,
                    leadingIcon = {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = colors.onSecondary)
                    },
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
                    Spacer(Modifier.height(spacing.sm))
                    ErrorNote(it, "You can still add the book by entering its details by hand.")
                }
                Spacer(Modifier.height(spacing.lg))
            } else {
                Spacer(Modifier.height(spacing.sm))
                Text(
                    "From ${
                        when (source) {
                            "isbnsearch" -> "ISBNsearch"
                            "firebase" -> "the Maktaba catalogue"
                            else -> "your own entry"
                        }
                    }",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkMuted
                )
                if (displayedFormat == BookFormat.DIGITAL) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        buildString {
                            append("This is a digital edition, so it cannot be lent through Maktaba.")
                            if (physicalEditionIsbn13.isNotBlank()) {
                                append(" The physical edition is ISBN ")
                                append(physicalEditionIsbn13)
                                append(".")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.warning
                    )
                }
                Spacer(Modifier.height(spacing.sm))
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "Cover found for $title",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 100.dp, height = 145.dp)
                            .clip(MaktabaShapes.small)
                    )
                    Spacer(Modifier.height(spacing.sm))
                }
                BookField(title, { title = it; error = null }, "Title", "Book title", required = true)
                BookField(author, { author = it; error = null }, "Author", "Author name", required = true)
                BookField(isbn13, { isbn13 = it }, "ISBN-13", "Optional", KeyboardType.Number)
                BookField(isbn10, { isbn10 = it }, "ISBN-10", "Optional", KeyboardType.Ascii)
                BookField(publisher, { publisher = it }, "Publisher", "Optional")
                BookField(publishedDate, { publishedDate = it }, "Publication date", "YYYY-MM-DD")
                BookField(binding, { binding = it }, "Binding", "Paperback, hardcover...")
                BookField(genre, { genre = it }, "Genre", "Optional")
                BookField(
                    pages,
                    { pages = it.filter(Char::isDigit) },
                    "Page count",
                    "Optional",
                    KeyboardType.Number
                )
                BookField(description, { description = it }, "Description", "Optional", singleLine = false)
                error?.let {
                    ErrorNote(it)
                    Spacer(Modifier.height(spacing.xs))
                }
                PrimaryButton(
                    text = "Add to my library",
                    enabled = !loading && title.isNotBlank() && author.isNotBlank(),
                    loading = loading,
                    onClick = {
                        val suppliedIsbn = isbn13.ifBlank { isbn10 }
                        if (suppliedIsbn.isNotBlank() && !isValidIsbn(suppliedIsbn)) {
                            error = "That ISBN is not valid. Check the digits, or clear the field."
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
                                .onFailure {
                                    error = it.localizedMessage ?: "We could not add this book."
                                }
                            loading = false
                        }
                    }
                )
                Spacer(Modifier.height(spacing.lg))
            }
        }
    }
}

@Composable
private fun ErrorNote(message: String, hint: String? = null) {
    val colors = MaktabaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaktabaShapes.small)
            .background(colors.dangerContainer)
            .padding(12.dp)
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.danger)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
            }
        }
    }
}

@Composable
private fun BookField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    required: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {}
) {
    val colors = MaktabaTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (required) "$label (required)" else label) },
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onGo = { onImeAction() },
            onDone = { onImeAction() }
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = MaktabaShapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.divider,
            focusedTextColor = colors.ink,
            unfocusedTextColor = colors.ink,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.inkMuted,
            focusedPlaceholderColor = colors.inkMuted,
            unfocusedPlaceholderColor = colors.inkMuted,
            cursorColor = colors.primary
        )
    )
}

@Preview(showBackground = true, name = "Add a book")
@Composable
private fun AddBookScreenPreview() {
    MaktabaAppTheme { AddBookScreen(navController = rememberNavController()) }
}
