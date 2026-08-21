package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.R
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.theme.MaktabaAppTheme
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    val illustration: Int,
    val title: String,
    val body: String
)

/**
 * Three slides that describe what Maktaba actually does. The previous copy promised a
 * reading tracker and a discovery feed - neither of which exists - and never mentioned
 * lending, which is the entire product. The pager dots, Skip and Next were also inert.
 */
private val slides = listOf(
    OnboardingSlide(
        illustration = R.drawable.illus_onboarding,
        title = "Your shelf,\ncatalogued",
        body = "Scan a barcode and the book is on your shelf, cover and details included."
    ),
    OnboardingSlide(
        illustration = R.drawable.illus_enter_code,
        title = "Lend with\na code",
        body = "Pick a return date, share a code, and your friend claims the book in seconds."
    ),
    OnboardingSlide(
        illustration = R.drawable.illus_return_confirmation,
        title = "Get it back\non time",
        body = "Maktaba tracks every loan and nudges both of you before the date lapses."
    )
)

@Composable
fun OnboardingScreen(navController: NavHostController) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == slides.lastIndex

    fun finish() = navController.navigate(Routes.Auth.route)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(horizontal = spacing.lg)
    ) {
        Spacer(Modifier.height(spacing.lg))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_book_arch),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                "Maktaba",
                style = MaterialTheme.typography.displaySmall,
                color = colors.primary
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                "LEND YOUR BOOKS, GET THEM BACK",
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkSoft
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) { page ->
            val slide = slides[page]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(slide.illustration),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight()
                    )
                }
                Spacer(Modifier.height(spacing.xs))
                Text(
                    slide.title,
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() }
                )
                Spacer(Modifier.height(spacing.sm))
                Text(
                    slide.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.inkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(spacing.md))

        PrimaryButton(
            text = if (isLastPage) "Get started" else "Next",
            trailingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = colors.onPrimary
                )
            },
            onClick = {
                if (isLastPage) {
                    finish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            }
        )

        Spacer(Modifier.height(spacing.xs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = ::finish) {
                Text("Skip", color = colors.inkSoft, style = MaterialTheme.typography.labelLarge)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics {
                    contentDescription = "Step ${pagerState.currentPage + 1} of ${slides.size}"
                }
            ) {
                slides.indices.forEach { index ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) colors.primary
                                else colors.divider
                            )
                    )
                }
            }
            Spacer(Modifier.width(64.dp))
        }
        Spacer(Modifier.height(spacing.md))
    }
}

@Preview(showBackground = true, name = "Onboarding")
@Composable
private fun OnboardingScreenPreview() {
    MaktabaAppTheme {
        OnboardingScreen(navController = rememberNavController())
    }
}
