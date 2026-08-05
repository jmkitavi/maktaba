package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookHavenBottomNav
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.GreenButton
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.*

@Composable
fun ScanEnterCodeScreen(navController: NavHostController) {
    var code by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
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
                    "Scan or Enter Code",
                    modifier = Modifier.weight(1f),
                    color = InkBrown,
                    fontFamily = SerifDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Icon(
                    Icons.Filled.MenuBook,
                    contentDescription = null,
                    tint = WoodBrown,
                    modifier = Modifier.padding(end = 12.dp).size(24.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(4.dp))
                Image(
                    painter = painterResource(R.drawable.illus_enter_code),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(80.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Scan the lender's QR code\nor enter the unique code provided.",
                    fontFamily = SansBody,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = InkBrownSoft,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                // Viewfinder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF4A3A2C), Color(0xFF2E2318)))
                        )
                ) {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Position the QR code\nwithin the frame",
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )
                    }
                    CornerBracket(Modifier.align(Alignment.TopStart).padding(16.dp))
                    CornerBracket(Modifier.align(Alignment.TopEnd).padding(16.dp), flipX = true)
                    CornerBracket(Modifier.align(Alignment.BottomStart).padding(16.dp), flipY = true)
                    CornerBracket(Modifier.align(Alignment.BottomEnd).padding(16.dp), flipX = true, flipY = true)
                }

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f).height(1.dp).background(DividerTan))
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SurfaceCardAlt),
                        contentAlignment = Alignment.Center
                    ) { Text("or", color = MutedText, fontSize = 13.sp) }
                    Box(Modifier.weight(1f).height(1.dp).background(DividerTan))
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Enter Code Manually",
                    fontFamily = SerifDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = InkBrown
                )
                Spacer(Modifier.height(10.dp))

                TextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
                    placeholder = { Text("Enter unique code", color = MutedText) },
                    leadingIcon = { Icon(Icons.Filled.Keyboard, contentDescription = null, tint = MutedText) },
                    trailingIcon = {
                        if (code.isNotEmpty()) {
                            IconButton(onClick = { code = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = MutedText)
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard,
                        focusedIndicatorColor = DividerTan,
                        unfocusedIndicatorColor = DividerTan
                    )
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Codes are usually 6–12 characters and may include letters and numbers.",
                    fontFamily = SansBody,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MutedText,
                    textAlign = TextAlign.Center
                )
                if (errorText != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(errorText!!, color = androidx.compose.ui.graphics.Color(0xFFB3261E), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(14.dp))
                GreenButton(
                    text = "Find Book",
                    leadingIcon = { Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        // Demo tip code (BOK-7291) always resolves; any other code falls back
                        // to the same demo book so the mock borrow flow is always reachable.
                        val bookId = LibraryRepository.bookIdForCode(code.ifBlank { "BOK-7291" })
                            ?: LibraryRepository.bookIdForCode("BOK-7291")
                            ?: Routes.DEFAULT_BOOK_ID
                        errorText = null
                        navController.navigate(Routes.ConfirmBorrow.createRoute(bookId))
                    }
                )
                Spacer(Modifier.height(6.dp))
            }

            BookHavenBottomNav(
                selected = BottomNavTab.LIBRARY,
                onSelect = { navController.navigateToTab(it) }
            )
        }
    }
}

@Composable
private fun CornerBracket(modifier: Modifier = Modifier, flipX: Boolean = false, flipY: Boolean = false) {
    val thickness = 3.dp
    val length = 24.dp
    Box(modifier = modifier.size(length)) {
        Box(
            Modifier
                .align(if (flipY) Alignment.BottomStart else Alignment.TopStart)
                .width(length)
                .height(thickness)
                .background(Color.White)
        )
        Box(
            Modifier
                .align(if (flipX) Alignment.TopEnd else Alignment.TopStart)
                .width(thickness)
                .height(length)
                .background(Color.White)
        )
    }
}

@Preview(showBackground = true, name = "ScanEnterCodeScreenPreview")
@Composable
private fun ScanEnterCodeScreenPreview() {
    BookHavenTheme {
        ScanEnterCodeScreen(navController = rememberNavController())
    }
}
