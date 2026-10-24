package com.maktaba.app.data

import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PublicCollectionFeedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun revokedAuthorizationCannotBeReopenedByAnEarlierInFlightRead() {
        val response = CompletableDeferred<String>()
        lateinit var feed: PublicCollectionFeed<String>
        compose.runOnIdle {
            feed = PublicCollectionFeed {}
            feed.load = { _, _ -> response.await() }
            feed.refresh()
            feed.invalidate()
            response.complete("stale title associations")
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(feed.state is LoadState.Loading)
            feed.load = { _, _ -> "new authoritative publication" }
            feed.refresh()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("new authoritative publication", (feed.state as LoadState.Ready).value)
            feed.close()
        }
    }

    @Test
    fun sessionCloseClearsPublishedContentAndRejectsLateCompletion() {
        lateinit var feed: PublicCollectionFeed<String>
        compose.runOnIdle {
            feed = PublicCollectionFeed {}
            feed.load = { _, _ -> "previous account publication" }
            feed.refresh()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(feed.state is LoadState.Ready)
            feed.close()
            feed.refresh()
            assertTrue(feed.state is LoadState.Loading)
        }
    }

    @Test
    fun olderRequestCannotAdvanceTheCurrentPaginationCursor() {
        val older = CompletableDeferred<String>()
        val current = CompletableDeferred<String>()
        var cursor = ""
        var calls = 0
        lateinit var feed: PublicCollectionFeed<String>
        compose.runOnIdle {
            feed = PublicCollectionFeed {}
            feed.load = { _, request ->
                val result = if (calls++ == 0) older.await() else current.await()
                if (feed.isCurrent(request)) cursor = result
                result
            }
            feed.refresh()
            feed.refresh()
            current.complete("first current page")
        }
        compose.waitForIdle()
        compose.runOnIdle { older.complete("obsolete later page") }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("first current page", cursor)
            assertEquals("first current page", (feed.state as LoadState.Ready).value)
            feed.close()
        }
    }

    @Test
    fun publicationBarrierClearsOldTitlesAndShowsUnavailableInsteadOfAnEmptyShelf() {
        lateinit var feed: PublicCollectionFeed<String>
        compose.runOnIdle {
            feed = PublicCollectionFeed {}
            feed.load = { _, _ -> "previously shared titles" }
            feed.refresh()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            feed.load = { _, _ -> throw PublicationUnavailable() }
            feed.refresh()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(feed.state is LoadState.NotFound)
            assertFalse(feed.hasMore)
            feed.close()
        }
    }
}
