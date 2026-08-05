package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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

private data class ShareTarget(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val bg: Color)

private val shareTargets = listOf(
    ShareTarget("WhatsApp", Icons.Filled.Chat, Color(0xFF25D366)),
    ShareTarget("Messages", Icons.Filled.Sms, Color(0xFF4CAF50)),
    ShareTarget("Telegram", Icons.Filled.Send, Color(0xFF29B6F6)),
    ShareTarget("Gmail", Icons.Filled.Email, Color(0xFFEA4335)),
    ShareTarget("More", Icons.Filled.MoreHoriz, Color(0xFF8C7359))
)

@Composable
fun ShareLendingCodeScreen(navController: NavHostController, bookId: String = Routes.DEFAULT_BOOK_ID) {
    val book = LibraryRepository.bookById(bookId)
    val code = LibraryRepository.lendingCodeFor(bookId)
    Box(modifier = Modifier.fillMaxSize().background(CreamBackground)) {
        // Bookshelf/lamp background bleed (narrow illustration strip, mirrored on the right)
        Image(
            painter = painterResource(R.drawable.illus_book_detail),
            contentDescription = null,
            contentScale = ContentScale.FillHeight,
            alpha = 0.55f,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(100.dp)
        )
        Image(
            painter = painterResource(R.drawable.illus_book_detail),
            contentDescription = null,
            contentScale = ContentScale.FillHeight,
            alpha = 0.55f,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(100.dp)
                .graphicsLayer(scaleX = -1f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
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
                    "Share Lending Code",
                    modifier = Modifier.weight(1f),
                    color = InkBrown,
                    fontFamily = SerifDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(48.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(40.dp).height(1.dp).background(AccentGoldSoft))
                Icon(
                    Icons.Filled.AutoStories,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.padding(horizontal = 10.dp).size(26.dp)
                )
                Box(Modifier.width(40.dp).height(1.dp).background(AccentGoldSoft))
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Ask the borrower to scan or enter this code",
                fontFamily = SansBody,
                fontSize = 15.sp,
                color = InkBrownSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCard)
                    .padding(vertical = 24.dp, horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "YOUR LENDING CODE",
                    fontFamily = SansBody,
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp,
                    color = WoodBrown,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    code,
                    fontFamily = SerifDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    color = InkBrown
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(28.dp).height(1.dp).background(DividerTan))
                    Box(
                        Modifier
                            .padding(horizontal = 8.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(AccentGold)
                    )
                    Box(Modifier.width(28.dp).height(1.dp).background(DividerTan))
                }
                Spacer(Modifier.height(18.dp))
                Image(
                    painter = painterResource(R.drawable.qr_lending_code),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(10.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                GreenButton(
                    text = "Share Link",
                    leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    onClick = {}
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(DividerTan))
                Text(
                    "SHARE VIA",
                    color = MutedText,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                Box(Modifier.weight(1f).height(1.dp).background(DividerTan))
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                shareTargets.forEach { target ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (target.label == "More") SurfaceCardAlt else target.bg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                target.icon,
                                contentDescription = target.label,
                                tint = if (target.label == "More") MutedText else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(target.label, fontSize = 12.sp, color = InkBrownSoft)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceCardAlt)
                    .padding(14.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(OliveGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "This code is unique to you and secure. Do not share it with anyone you don't trust.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = InkBrownSoft
                )
            }

            Spacer(Modifier.height(18.dp))
            if (book != null) {
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            val popped = navController.popBackStack(Routes.HomeLibrary.route, inclusive = false)
                            if (!popped) {
                                navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text("Done", color = InkBrown, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            BookHavenBottomNav(
                selected = BottomNavTab.LIBRARY,
                onSelect = { navController.navigateToTab(it) }
            )
        }
    }
}

@Preview(showBackground = true, name = "ShareLendingCodeScreenPreview")
@Composable
private fun ShareLendingCodeScreenPreview() {
    BookHavenTheme {
        ShareLendingCodeScreen(navController = rememberNavController())
    }
}
