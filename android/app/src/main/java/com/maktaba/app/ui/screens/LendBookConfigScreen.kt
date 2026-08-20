package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.R
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.BookFormat
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import com.maktaba.app.util.LoanTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendBookConfigScreen(navController: NavHostController, bookId: String) {
    var borrowerName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val book = LibraryRepository.bookById(bookId)
    if (book == null) {
        UnavailableState(
            title = "Book unavailable",
            message = "This book is no longer available to lend.",
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
        )
        return
    }
    if (book.format == BookFormat.DIGITAL) {
        UnavailableState(
            title = "Digital edition",
            message = "Digital editions can’t be lent through Maktaba." +
                book.physicalEditionIsbn13.takeIf { it.isNotBlank() }
                    .let { if (it == null) "" else " Try physical ISBN $it." },
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
        )
        return
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000
    )
    val selectedDateMillis = datePickerState.selectedDateMillis
        ?: System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000
    val dueAtMillis = LoanTimeFormatter.localDateToEndOfDayMillis(selectedDateMillis)
    val dueDate = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dueAtMillis))

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground).statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = InkBrown)
                }
                Text(
                    "Lend Book",
                    modifier = Modifier.weight(1f),
                    color = InkBrown,
                    fontFamily = SerifDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(10.dp))

                // Book summary card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard)
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    BookCoverImage(
                        book = book,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(96.dp)
                            .aspectRatio(0.68f)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            book.title,
                            fontFamily = SerifDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = InkBrown
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(book.author, fontSize = 15.sp, color = MutedText)
                        if (book.genre.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceCardAlt)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(book.genre, fontSize = 13.sp, color = InkBrownSoft)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = WoodBrown, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Return by", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = InkBrown)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceCard)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(dueDate, fontSize = 16.sp, color = InkBrown, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MutedText)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Select the date by which the book should be returned.",
                    fontSize = 12.sp,
                    color = MutedText
                )

                Spacer(Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = WoodBrown, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Borrower's name", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = InkBrown)
                    Spacer(Modifier.width(6.dp))
                    Text("(optional)", fontSize = 14.sp, color = MutedText)
                }
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = borrowerName,
                    onValueChange = { borrowerName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
                    placeholder = { Text("e.g., Alex Johnson", color = MutedText) },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = MutedText) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard,
                        focusedIndicatorColor = DividerTan,
                        unfocusedIndicatorColor = DividerTan
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add a name to personalize the lend (optional).",
                    fontSize = 12.sp,
                    color = MutedText
                )

                Spacer(Modifier.height(20.dp))
                if (error != null) {
                    Text(error!!, color = Color(0xFFB3261E), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                }
                PrimaryButton(
                    text = "Generate Code",
                    loading = loading,
                    enabled = !loading,
                    leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        if (loading) return@PrimaryButton
                        if (dueAtMillis <= System.currentTimeMillis()) {
                            error = "Choose a future return date."
                            return@PrimaryButton
                        }
                        loading = true
                        error = null
                        scope.launch {
                            runCatching {
                                LibraryRepository.startLending(
                                    bookId = book.id,
                                    borrowerName = borrowerName.ifBlank { "a friend" },
                                    dueAtMillis = dueAtMillis
                                )
                            }.onSuccess { invite ->
                                navController.navigate(Routes.ShareLendingCode.createRoute(invite.id))
                            }.onFailure {
                                error = it.localizedMessage ?: "Could not create a lending invitation."
                            }
                            loading = false
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

        }
    }
}

@Preview(showBackground = true, name = "LendBookConfigScreenPreview")
@Composable
private fun LendBookConfigScreenPreview() {
    BookHavenTheme {
        LendBookConfigScreen(navController = rememberNavController(), bookId = "preview")
    }
}
