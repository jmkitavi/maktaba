package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.R
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.theme.*

private val availableCovers = listOf(
    R.drawable.cover_the_night_circus,
    R.drawable.cover_atomic_habits,
    R.drawable.cover_hobbit,
    R.drawable.cover_crawdads,
    R.drawable.cover_mockingbird,
    R.drawable.cover_alchemist,
    R.drawable.cover_midnight_library
)

/**
 * Reached via My Library's "+" FAB → "Add a Book". Simple mock form: title, author,
 * and a cover picker from the existing illustration/cover set. Saves into the shared
 * LibraryRepository as an OWNED book, then returns to My Library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookScreen(navController: NavHostController) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var selectedCover by remember { mutableStateOf(availableCovers.first()) }

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(title = "Add a Book", onBack = { navController.popBackStack() })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp)
            ) {
                Text("Cover", color = InkBrown, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(availableCovers) { cover ->
                        Image(
                            painter = painterResource(cover),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 56.dp, height = 80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (cover == selectedCover) 2.dp else 0.dp,
                                    color = if (cover == selectedCover) WoodBrown else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedCover = cover }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Title", color = InkBrown, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("e.g. Project Hail Mary") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor = SurfaceCard,
                        focusedBorderColor = WoodBrown,
                        unfocusedBorderColor = DividerTan
                    )
                )
                Spacer(Modifier.height(16.dp))
                Text("Author", color = InkBrown, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    placeholder = { Text("e.g. Andy Weir") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor = SurfaceCard,
                        focusedBorderColor = WoodBrown,
                        unfocusedBorderColor = DividerTan
                    )
                )
            }
            Column(modifier = Modifier.padding(20.dp)) {
                PrimaryButton(
                    text = "Save to My Library",
                    onClick = {
                        if (title.isNotBlank() && author.isNotBlank()) {
                            LibraryRepository.addBook(title.trim(), author.trim(), selectedCover)
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "AddBookScreenPreview")
@Composable
private fun AddBookScreenPreview() {
    BookHavenTheme {
        AddBookScreen(navController = rememberNavController())
    }
}
