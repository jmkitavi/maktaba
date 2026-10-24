package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.maktaba.app.R
import com.maktaba.app.data.*
import com.maktaba.app.ui.components.*
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class AddBookStep { ENTRY, SCANNER, ISBN, DETAILS, SUCCESS }

data class AddBookDraft(
    val title: String = "",
    val author: String = "",
    val binding: String = "",
    val isbn13: String = "",
    val isbn10: String = "",
    val publisher: String = "",
    val publishedDate: String = "",
    val pages: String = "",
    val genre: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val catalogBookId: String = "",
    val source: String = "manual",
    val sourceUrl: String = "",
    val originalFormat: BookFormat = BookFormat.UNKNOWN,
    val physicalEditionIsbn13: String = ""
) {
    fun metadata() = BookMetadata(
        catalogBookId = catalogBookId, title = title.trim(),
        authors = author.split(",").map(String::trim).filter(String::isNotBlank),
        binding = if (addBookEditionFormat(this) == BookFormat.DIGITAL &&
            !DigitalBindingPattern.containsMatchIn(binding.lowercase())) {
            binding.trim().ifBlank { "Digital edition" }.let {
                if (it == "Digital edition") it else "$it · Digital edition"
            }
        } else binding.trim(),
        isbn13 = compactIsbn(isbn13), isbn10 = compactIsbn(isbn10),
        publisher = publisher.trim(), publishedDate = publishedDate.trim(),
        pageCount = pages.trim().toIntOrNull() ?: 0,
        genres = genre.split(",").map(String::trim).filter(String::isNotBlank),
        description = description.trim(), coverUrl = coverUrl, source = source,
        sourceUrl = sourceUrl, format = addBookEditionFormat(this),
        physicalEditionIsbn13 = physicalEditionIsbn13
    )

    companion object {
        fun from(metadata: BookMetadata) = AddBookDraft(
            title = metadata.title, author = metadata.authors.joinToString(", "),
            binding = metadata.binding, isbn13 = metadata.isbn13, isbn10 = metadata.isbn10,
            publisher = metadata.publisher, publishedDate = metadata.publishedDate,
            pages = metadata.pageCount.takeIf { it > 0 }?.toString().orEmpty(),
            genre = metadata.genres.joinToString(", "), description = metadata.description,
            coverUrl = metadata.coverUrl, catalogBookId = metadata.catalogBookId,
            source = metadata.source, sourceUrl = metadata.sourceUrl,
            originalFormat = metadata.format, physicalEditionIsbn13 = metadata.physicalEditionIsbn13
        )
    }
}

private val AddBookDraftSaver = listSaver<AddBookDraft, String>(
    save = { listOf(it.title, it.author, it.binding, it.isbn13, it.isbn10, it.publisher,
        it.publishedDate, it.pages, it.genre, it.description, it.coverUrl, it.catalogBookId,
        it.source, it.sourceUrl, it.originalFormat.name, it.physicalEditionIsbn13) },
    restore = { AddBookDraft(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7],
        it[8], it[9], it[10], it[11], it[12], it[13], BookFormat.valueOf(it[14]), it[15]) }
)

private val DigitalBindingPattern =
    Regex("""\be[\s-]?book\b|\bepub\b|\bkindle\b|\bdigital\b|\belectronic\s+resource\b""")

private fun bindingEditionFormat(value: String): BookFormat {
    val binding = value.trim().lowercase()
    return when {
        binding in setOf("", "unknown", "unspecified", "n/a", "na", "none") -> BookFormat.UNKNOWN
        DigitalBindingPattern.containsMatchIn(binding) || binding.contains("electronic resources") -> BookFormat.DIGITAL
        else -> BookFormat.PHYSICAL
    }
}

internal fun addBookEditionFormat(draft: AddBookDraft): BookFormat {
    if (draft.originalFormat == BookFormat.DIGITAL) return BookFormat.DIGITAL
    return bindingEditionFormat(draft.binding).takeUnless { it == BookFormat.UNKNOWN } ?: draft.originalFormat
}

internal fun isExistingCatalogueDraft(draft: AddBookDraft): Boolean =
    draft.source == "firebase" && draft.catalogBookId.isNotBlank()

internal fun manualAddBookDraft(draft: AddBookDraft, isbn: String): AddBookDraft {
    if (isExistingCatalogueDraft(draft)) {
        return draft.copy(catalogBookId = "", isbn13 = "", isbn10 = "", source = "manual",
            sourceUrl = "", coverUrl = "", originalFormat = BookFormat.UNKNOWN, physicalEditionIsbn13 = "")
    }
    val normalized = compactIsbn(isbn)
    return draft.copy(
        isbn13 = draft.isbn13.ifBlank { normalized.takeIf { it.length == 13 && isValidIsbn(it) }.orEmpty() },
        isbn10 = draft.isbn10.ifBlank { normalized.takeIf { it.length == 10 && isValidIsbn(it) }.orEmpty() }
    )
}

internal fun validateAddBookDraft(draft: AddBookDraft): String? = when {
    draft.title.trim().isEmpty() -> "Enter a title."
    draft.title.trim().length > 240 -> "Keep the title to 240 characters."
    draft.author.split(",").none { it.isNotBlank() } -> "Enter at least one author."
    draft.isbn13.isNotBlank() && (compactIsbn(draft.isbn13).length != 13 || !isValidIsbn(draft.isbn13)) ->
        "Check the ISBN-13, or clear that optional field."
    draft.isbn10.isNotBlank() && (compactIsbn(draft.isbn10).length != 10 || !isValidIsbn(draft.isbn10)) ->
        "Check the ISBN-10, or clear that optional field."
    draft.pages.isNotBlank() && (draft.pages.toIntOrNull() == null || (draft.pages.toIntOrNull() ?: -1) < 0) ->
        "Enter a valid page count, or clear that optional field."
    draft.originalFormat == BookFormat.DIGITAL &&
        bindingEditionFormat(draft.binding) == BookFormat.PHYSICAL ->
        "This lookup is a digital edition. Look up the physical edition's ISBN instead of changing its binding."
    else -> null
}

data class AddBookState(
    val step: AddBookStep = AddBookStep.ENTRY,
    val isbn: String = "",
    val draft: AddBookDraft = AddBookDraft(),
    val working: Boolean = false,
    val error: String? = null,
    val moreDetails: Boolean = false
)

@Composable
fun AddBookScreen(navController: NavHostController) {
    var step by rememberSaveable { mutableStateOf(AddBookStep.ENTRY) }
    var isbn by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable(stateSaver = AddBookDraftSaver) { mutableStateOf(AddBookDraft()) }
    var moreDetails by rememberSaveable { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scanner = remember(context) {
        GmsBarcodeScanning.getClient(context, GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13).enableAutoZoom().build())
    }
    fun lookup(value: String = isbn) {
        if (working) return
        val normalized = compactIsbn(value)
        if (!isValidIsbn(normalized)) {
            error = "That is not a valid ISBN-10 or ISBN-13. Check the digits and try again."
            step = AddBookStep.ISBN
            return
        }
        working = true
        error = null
        step = AddBookStep.ISBN
        scope.launch(Dispatchers.Main.immediate) {
            handoffAttempt { LibraryRepository.lookupBookByIsbn(normalized) }
                .onSuccess { draft = AddBookDraft.from(it); step = AddBookStep.DETAILS }
                .onFailure { error = it.localizedMessage ?: "Could not look up that ISBN. Try again or enter the details by hand." }
            working = false
        }
    }
    fun enterManually() {
        if (working) return
        draft = manualAddBookDraft(draft, isbn)
        step = AddBookStep.DETAILS
        error = null
    }
    AddBookContent(
        AddBookState(step, isbn, draft, working, error, moreDetails),
        onBack = {
            if (!working) {
                error = null
                when (step) {
                    AddBookStep.ENTRY, AddBookStep.SUCCESS -> navController.popBackStack()
                    else -> step = AddBookStep.ENTRY
                }
            }
        },
        onTab = { if (!working) navController.navigateToTab(it) },
        onIsbn = { isbn = it; error = null },
        onDraft = { draft = it; error = null },
        onMoreDetails = { moreDetails = !moreDetails },
        onEnterIsbn = { if (!working) { step = AddBookStep.ISBN; error = null } },
        onManual = ::enterManually,
        onLookup = { lookup() },
        onScan = {
            if (!working) {
                step = AddBookStep.SCANNER
                working = true
                error = null
                scope.launch(Dispatchers.Main.immediate) {
                    var scanned: String? = null
                    try {
                        val raw = scanner.startScan().await().rawValue.orEmpty()
                        val normalized = compactIsbn(raw)
                        if (normalized.length == 13 && isValidIsbn(normalized)) {
                            isbn = normalized
                            scanned = normalized
                        } else {
                            error = "That barcode is not a valid ISBN-13. Type the ISBN or enter the details by hand."
                        }
                    } catch (cancelled: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        error = "Scan cancelled. Try again, type the ISBN, or enter details by hand."
                    } catch (failure: Exception) {
                        error = "The barcode scanner could not open. Type the ISBN or enter details by hand."
                    } finally {
                        working = false
                        step = AddBookStep.SCANNER
                    }
                    scanned?.let { lookup(it) }
                }
            }
        },
        onSave = {
            if (!working && step == AddBookStep.DETAILS) {
                val validation = validateAddBookDraft(draft)
                if (validation != null) error = validation
                else {
                    val metadata = draft.metadata()
                    working = true
                    error = null
                    scope.launch(Dispatchers.Main.immediate) {
                        handoffAttempt { LibraryRepository.addBook(metadata) }
                            .onSuccess { step = AddBookStep.SUCCESS }
                            .onFailure { error = it.localizedMessage ?: "Could not add the book. Your details are still here; try again." }
                        working = false
                    }
                }
            }
        },
        onLibrary = { navController.navigateToTab(BottomNavTab.LIBRARY) },
        onAnother = { draft = AddBookDraft(); isbn = ""; error = null; moreDetails = false; step = AddBookStep.ENTRY }
    )
}

@Composable
fun AddBookContent(
    state: AddBookState,
    onBack: () -> Unit,
    onTab: (BottomNavTab) -> Unit,
    onIsbn: (String) -> Unit,
    onDraft: (AddBookDraft) -> Unit,
    onMoreDetails: () -> Unit,
    onEnterIsbn: () -> Unit,
    onManual: () -> Unit,
    onLookup: () -> Unit,
    onScan: () -> Unit,
    onSave: () -> Unit,
    onLibrary: () -> Unit,
    onAnother: () -> Unit
) {
    val colors = MaktabaTheme.colors
    val title = when (state.step) {
        AddBookStep.ENTRY -> "Add a Book"
        AddBookStep.SCANNER -> "Scan barcode"
        AddBookStep.ISBN -> "ISBN lookup"
        AddBookStep.DETAILS -> "Check the details"
        AddBookStep.SUCCESS -> "Book added"
    }
    MaktabaScaffold(
        selectedTab = BottomNavTab.LIBRARY, onTabSelected = onTab,
        topBar = { ScreenTopBar(title, onBack = onBack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp).padding(top = 7.dp, bottom = 28.dp)) {
            when (state.step) {
                AddBookStep.ENTRY -> {
                    AddBookIllustration()
                    HandoffHeading("A new place on your shelf.")
                    Spacer(Modifier.height(14.dp))
                    Text("Scan the barcode on the back cover, enter its ISBN, or add the details yourself.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp), color = colors.inkMuted)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Scan barcode", enabled = !state.working, onClick = onScan)
                    Spacer(Modifier.height(12.dp))
                    OutlinedPillButton("Enter an ISBN", enabled = !state.working, onClick = onEnterIsbn)
                    Spacer(Modifier.height(12.dp))
                    OutlinedPillButton("Enter details by hand", enabled = !state.working, onClick = onManual)
                    Spacer(Modifier.height(18.dp))
                    Text("Add books you own. Borrowing a book still uses the lending-code journey, not this screen.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp), color = colors.inkMuted)
                }
                AddBookStep.SCANNER -> {
                    AddBookIllustration(scanner = true)
                    HandoffHeading("Scan the back-cover barcode.", fontSize = 24.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("The camera scans an EAN-13 barcode to find the ISBN. Review the details before adding the book.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp), color = colors.inkMuted)
                    state.error?.let {
                        Spacer(Modifier.height(12.dp))
                        HandoffNotice(it, error = true)
                    }
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("Open barcode scanner", enabled = !state.working, loading = state.working, onClick = onScan)
                    Spacer(Modifier.height(12.dp))
                    OutlinedPillButton("Type ISBN instead", enabled = !state.working, onClick = onEnterIsbn)
                    Spacer(Modifier.height(12.dp))
                    OutlinedPillButton("Enter details by hand", enabled = !state.working, onClick = onManual)
                }
                AddBookStep.ISBN -> {
                    Spacer(Modifier.height(14.dp))
                    HandoffHeading("Find this edition.")
                    Spacer(Modifier.height(14.dp))
                    Text("Type the ISBN-10 or ISBN-13 printed beside the barcode. Spaces and hyphens are accepted.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp), color = colors.inkMuted)
                    Spacer(Modifier.height(20.dp))
                    AddBookField(state.isbn, onIsbn, "ISBN", !state.working, KeyboardType.Ascii,
                        ImeAction.Go, onLookup, placeholder = "ISBN-10 or ISBN-13")
                    PrimaryButton("Look up this ISBN", enabled = !state.working && state.isbn.isNotBlank(),
                        loading = state.working, onClick = onLookup)
                    Spacer(Modifier.height(12.dp))
                    OutlinedPillButton("Enter details by hand", enabled = !state.working, onClick = onManual)
                }
                AddBookStep.DETAILS -> {
                    Spacer(Modifier.height(4.dp))
                    val draft = state.draft
                    val catalogueLinked = isExistingCatalogueDraft(draft)
                    Text(when (draft.source) {
                        "isbnsearch" -> "From ISBNsearch · check this edition"
                        "firebase" -> "From the Book Haven catalogue"
                        else -> "From your own entry · no lookup required"
                    }, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                    Spacer(Modifier.height(18.dp))
                    BookCoverImage(
                        Book("draft", draft.title.ifBlank { "Your book title" }, draft.author,
                            0, BookStatus.OWNED, coverUrl = draft.coverUrl),
                        contentScale = ContentScale.Fit,
                        plainFallback = true,
                        modifier = Modifier.align(Alignment.CenterHorizontally).width(94.dp).aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.height(18.dp))
                    HandoffNotice(when (addBookEditionFormat(draft)) {
                        BookFormat.DIGITAL -> "Digital edition · cannot be lent. Check the ISBN if you meant to add a physical copy."
                        BookFormat.UNKNOWN -> "Edition format is unknown. Add binding details if you know them; an unknown edition cannot be lent."
                        BookFormat.PHYSICAL -> "Physical edition. Check the binding and edition before adding."
                    }, positive = true)
                    Spacer(Modifier.height(16.dp))
                    if (catalogueLinked) {
                        HandoffNotice("This ISBN already has shared catalogue details. Your copy uses that edition. To edit different details, create a manual entry without its ISBN.")
                        TextActionButton("Enter different details without an ISBN", enabled = !state.working, onClick = onManual)
                    }
                    AddBookField(draft.title, { onDraft(draft.copy(title = it)) }, "Title (required)", !state.working,
                        readOnly = catalogueLinked)
                    AddBookField(draft.author, { onDraft(draft.copy(author = it)) }, "Author (required)", !state.working,
                        readOnly = catalogueLinked)
                    AddBookField(draft.binding, { onDraft(draft.copy(binding = it)) }, "Binding / edition (optional)", !state.working,
                        readOnly = catalogueLinked)
                    TextButton(onClick = onMoreDetails, enabled = !state.working,
                        contentPadding = PaddingValues(0.dp)) {
                        Text(if (state.moreDetails) "▾ ISBN & more details (optional)" else "▸ ISBN & more details (optional)",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.moreDetails) {
                        AddBookField(draft.isbn13, { onDraft(draft.copy(isbn13 = it)) }, "ISBN-13", !state.working, KeyboardType.Ascii, readOnly = catalogueLinked)
                        AddBookField(draft.isbn10, { onDraft(draft.copy(isbn10 = it)) }, "ISBN-10", !state.working, KeyboardType.Ascii, readOnly = catalogueLinked)
                        AddBookField(draft.publisher, { onDraft(draft.copy(publisher = it)) }, "Publisher", !state.working, readOnly = catalogueLinked)
                        AddBookField(draft.publishedDate, { onDraft(draft.copy(publishedDate = it)) }, "Publication date", !state.working, readOnly = catalogueLinked)
                        AddBookField(draft.pages, { onDraft(draft.copy(pages = it)) }, "Page count", !state.working, KeyboardType.Number, readOnly = catalogueLinked)
                        AddBookField(draft.genre, { onDraft(draft.copy(genre = it)) }, "Genres", !state.working, readOnly = catalogueLinked)
                        AddBookField(draft.description, { onDraft(draft.copy(description = it)) }, "Description", !state.working,
                            singleLine = false, readOnly = catalogueLinked)
                    }
                    Spacer(Modifier.height(4.dp))
                    PrimaryButton("Add to my library", enabled = !state.working && draft.title.isNotBlank() &&
                        draft.author.split(",").any { it.isNotBlank() }, loading = state.working, onClick = onSave)
                    Spacer(Modifier.height(16.dp))
                    Text("This adds an owned copy, not a loan or a Want to Read entry.",
                        style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                }
                AddBookStep.SUCCESS -> {
                    Spacer(Modifier.height(24.dp))
                    AddBookIllustration()
                    HandoffHeading("A new book at home.", centered = true)
                    Spacer(Modifier.height(14.dp))
                    Text("“${state.draft.title}” was added to your Library.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp), color = colors.inkMuted,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(40.dp))
                    HandoffNotice("Your owned copy has been saved. It is ready to find on your Library shelf.",
                        positive = true)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Return to Library", onClick = onLibrary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedPillButton("Try another book", onClick = onAnother)
                }
            }
            if (state.step != AddBookStep.SCANNER) {
                state.error?.let { Spacer(Modifier.height(16.dp)); HandoffNotice(it, error = true) }
            }
        }
    }
}

@Composable
private fun AddBookIllustration(scanner: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(if (scanner) R.drawable.illus_enter_code else R.drawable.illus_home_icon),
            null, contentScale = ContentScale.Fit,
            modifier = Modifier.width(150.dp).height(100.dp))
    }
}

@Composable
private fun AddBookField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onAction: () -> Unit = {},
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    placeholder: String = ""
) {
    val colors = MaktabaTheme.colors
    Text(label, style = MaterialTheme.typography.bodySmall, color = colors.ink)
    Spacer(Modifier.height(8.dp))
    HandoffTextField(value, onValue, enabled = enabled, readOnly = readOnly, singleLine = singleLine,
        placeholder = placeholder,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onGo = { if (enabled) onAction() }, onDone = { if (enabled) onAction() }))
    Spacer(Modifier.height(12.dp))
}
