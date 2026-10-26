package com.maktaba.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.FirebaseException
import com.maktaba.app.R
import com.maktaba.app.data.*
import com.maktaba.app.ui.components.*
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.launch
import java.io.IOException

internal enum class BookEditPage { FORM, DISCARD, SAVED }

private val draftSaver = listSaver<OwnedBookDraft, String>(
    save = { listOf(it.title, it.author, it.binding, it.isbn13, it.isbn10, it.publisher,
        it.published, it.genre, it.pages, it.description) },
    restore = { OwnedBookDraft(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7], it[8], it[9]) }
)

@Composable
fun EditOwnedBookScreen(navController: NavHostController, bookId: String) {
    val book = LibraryRepository.books.find { it.id == bookId && it.status != BookStatus.BORROWED &&
        it.ownerId == FirebaseSession.currentUser?.uid }
    if (book == null) {
        SoulAccountPage("Edit book details", onBack = { navController.popBackStack() }) {
            if (!LibraryRepository.hasLoadedLibrary && LibraryRepository.libraryError == null) {
                Text("Loading your book...", color = MaktabaTheme.colors.inkMuted)
            } else {
                SoulNotice(LibraryRepository.libraryError ?: "Only books you own can be edited.", error = true)
                if (LibraryRepository.libraryError != null) PrimaryButton("Try again", onClick = LibraryRepository::retryLoads)
                OutlinedPillButton("Back to Library", onClick = { navController.popBackStack() })
            }
        }
        return
    }
    val original = remember(book.id) { OwnedBookDraft.from(book) }
    var draft by rememberSaveable(book.id, stateSaver = draftSaver) { mutableStateOf(original) }
    var page by rememberSaveable(book.id) { mutableStateOf(BookEditPage.FORM) }
    var expanded by rememberSaveable(book.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingTab by remember { mutableStateOf<BottomNavTab?>(null) }
    val scope = rememberCoroutineScope()
    fun back() {
        if (saving) return
        when {
            page == BookEditPage.DISCARD -> { pendingTab = null; page = BookEditPage.FORM }
            page == BookEditPage.SAVED -> navController.popBackStack()
            draft != original -> page = BookEditPage.DISCARD
            else -> navController.popBackStack()
        }
    }
    BackHandler { back() }
    EditOwnedBookContent(
        book, draft, page, expanded, saving, error,
        onBack = ::back, onDraft = { draft = it; error = null },
        onExpand = { expanded = !expanded },
        onKeepEditing = { pendingTab = null; page = BookEditPage.FORM },
        onDiscard = {
            pendingTab?.let { navController.navigateToTab(it) } ?: navController.popBackStack()
        },
        onTabSelected = { tab ->
            if (!saving) {
                if (page != BookEditPage.SAVED && draft != original) {
                    pendingTab = tab
                    page = BookEditPage.DISCARD
                } else navController.navigateToTab(tab)
            }
        },
        onSave = {
            if (!saving) {
                error = draft.validationError()
                if (error == null) {
                    val request = draft
                    saving = true
                    scope.launch {
                        try {
                            LibraryRepository.updateOwnedBook(book.id, request)
                            page = BookEditPage.SAVED
                        } catch (failure: FirebaseException) {
                            error = failure.localizedMessage ?: "Couldn't save. Your draft is still here. Try again."
                        } catch (failure: IOException) {
                            error = "Check your connection and try again. Your draft is still here."
                        } catch (failure: IllegalArgumentException) {
                            error = failure.message ?: "Check the details and try again."
                        } catch (failure: IllegalStateException) {
                            error = failure.message ?: "Sign in and try again."
                        } finally {
                            saving = false
                        }
                    }
                }
            }
        }
    )
}

@Composable
internal fun EditOwnedBookContent(
    book: Book, draft: OwnedBookDraft, page: BookEditPage, expanded: Boolean,
    saving: Boolean, error: String?,
    onBack: () -> Unit, onDraft: (OwnedBookDraft) -> Unit, onExpand: () -> Unit,
    onKeepEditing: () -> Unit, onDiscard: () -> Unit, onSave: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit = {}
) {
    val colors = MaktabaTheme.colors
    SoulAccountPage(when (page) {
        BookEditPage.FORM -> "Edit book details"
        BookEditPage.DISCARD -> "Unsaved changes"
        BookEditPage.SAVED -> "Save feedback"
    }, onBack = onBack, headerArt = if (page == BookEditPage.SAVED) R.drawable.illus_cup_books else null,
        selectedTab = BottomNavTab.LIBRARY, onTabSelected = onTabSelected) {
        when (page) {
            BookEditPage.DISCARD -> {
                Text("Keep those changes?", style = MaterialTheme.typography.headlineMedium, color = colors.ink)
                Text("You have a draft for ${book.title}. Leaving now discards this draft, not the book.",
                    color = colors.inkMuted, style = MaterialTheme.typography.bodySmall)
                PrimaryButton("Keep editing", onClick = onKeepEditing)
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = MaktabaShapes.button,
                    border = BorderStroke(1.dp, colors.terraAction),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.terracotta, contentColor = colors.terraAction
                    )
                ) {
                    Text("Discard draft", style = MaterialTheme.typography.labelLarge)
                }
            }
            BookEditPage.SAVED -> {
                SoulFeedbackHeader(R.drawable.illus_cup_books, "A few details, made yours.",
                    "${draft.title.trim()} is updated in your library.")
                SoulNotice("These details are private to your copy. Shared catalogue details, loans, ownership and availability are unchanged.")
                PrimaryButton("View the edited book", onClick = onDiscard)
            }
            BookEditPage.FORM -> {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BookCoverImage(book, modifier = Modifier.size(40.dp, 60.dp))
                    Column(Modifier.weight(1f)) {
                        Text(book.title, color = colors.ink, style = MaterialTheme.typography.bodyMedium)
                        Text(book.author, color = colors.inkMuted, style = MaterialTheme.typography.bodySmall)
                        Text("Owned book", color = colors.inkMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text("Make these details yours. Shared catalogue details and loans stay unchanged.",
                    color = colors.inkMuted, style = MaterialTheme.typography.bodySmall)
                SoulTextField("Title", draft.title, { onDraft(draft.copy(title = it)) }, enabled = !saving)
                SoulTextField("Author", draft.author, { onDraft(draft.copy(author = it)) }, enabled = !saving)
                SoulTextField("Binding / edition", draft.binding, { onDraft(draft.copy(binding = it)) }, enabled = !saving)
                TextActionButton(if (expanded) "Hide ISBN & more details" else "ISBN & more details", enabled = !saving, onClick = onExpand)
                if (expanded) {
                    SoulTextField("ISBN-13", draft.isbn13, { onDraft(draft.copy(isbn13 = it)) }, enabled = !saving)
                    SoulTextField("ISBN-10", draft.isbn10, { onDraft(draft.copy(isbn10 = it)) }, enabled = !saving)
                    SoulTextField("Publisher", draft.publisher, { onDraft(draft.copy(publisher = it)) }, enabled = !saving)
                    SoulTextField("Publication date", draft.published, { onDraft(draft.copy(published = it)) }, enabled = !saving)
                    SoulTextField("Genre", draft.genre, { onDraft(draft.copy(genre = it)) }, enabled = !saving)
                    SoulTextField("Page count", draft.pages, { onDraft(draft.copy(pages = it)) },
                        keyboardType = KeyboardType.Number, enabled = !saving)
                    SoulTextField("Description", draft.description, { onDraft(draft.copy(description = it)) }, enabled = !saving, multiline = true)
                }
                error?.let { SoulNotice(it, error = true) }
                PrimaryButton("Save changes", enabled = draft.title.isNotBlank() && draft.author.isNotBlank(),
                    loading = saving, onClick = onSave)
                OutlinedPillButton("Cancel", enabled = !saving, onClick = onBack)
            }
        }
    }
}
