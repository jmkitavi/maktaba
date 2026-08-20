package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.R
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.theme.*

@Composable
fun OnboardingScreen(navController: NavHostController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(28.dp))

            // Branding
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_book_arch),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Book Haven",
                    fontFamily = SerifDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = WoodBrown
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Y O U R   P E R S O N A L   L I B R A R Y",
                    fontFamily = SansBody,
                    fontSize = 11.sp,
                    color = InkBrownSoft
                )
            }

            Spacer(Modifier.height(8.dp))

            // Hero illustration area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.illus_onboarding),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Your Library,\nAnywhere",
                fontFamily = SerifDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                color = InkBrown,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(64.dp)
                    .height(1.dp)
                    .background(DividerTan)
            )
            Spacer(Modifier.height(10.dp))

            Text(
                "Organize your books, track your reading,\nand discover your next favorite read.",
                fontFamily = SansBody,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = InkBrownSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            PrimaryButton(
                text = "Get Started",
                trailingIcon = {
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                },
                onClick = {
                    navController.navigate(Routes.Auth.route)
                }
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Skip", color = InkBrownSoft, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).background(WoodBrown, shape = androidx.compose.foundation.shape.CircleShape))
                    Box(Modifier.size(8.dp).background(DividerTan, shape = androidx.compose.foundation.shape.CircleShape))
                    Box(Modifier.size(8.dp).background(DividerTan, shape = androidx.compose.foundation.shape.CircleShape))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Next", color = InkBrownSoft, fontSize = 15.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = null,
                        tint = InkBrownSoft,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Preview(showBackground = true, name = "OnboardingScreenPreview")
@Composable
private fun OnboardingScreenPreview() {
    BookHavenTheme {
        OnboardingScreen(navController = rememberNavController())
    }
}
