package com.maktaba.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityTest {
    @Test
    fun restoredStateWinsOverTheUnchangedLaunchIntent() {
        assertNull(
            selectPendingRoute(
                hasSavedState = true,
                restoredRoute = null,
                launchRoute = "confirm_borrow/ABCD-2345"
            )
        )
        assertEquals(
            "active_loan/copy_123",
            selectPendingRoute(
                hasSavedState = true,
                restoredRoute = "active_loan/copy_123",
                launchRoute = "confirm_borrow/ABCD-2345"
            )
        )
    }

    @Test
    fun coldLaunchUsesTheIncomingRoute() {
        assertEquals(
            "confirm_borrow/ABCD-2345",
            selectPendingRoute(
                hasSavedState = false,
                restoredRoute = null,
                launchRoute = "confirm_borrow/ABCD-2345"
            )
        )
    }

    @Test
    fun debugRoutesAreStrictlyValidated() {
        assertEquals("loans", validatedDebugRoute("loans"))
        assertEquals(
            "confirm_borrow/ABCD-2345",
            validatedDebugRoute("confirm_borrow/ABCD-2345")
        )
        assertNull(validatedDebugRoute("unknown/route"))
        assertNull(validatedDebugRoute("book_detail/../../bad"))
        assertNull(validatedDebugRoute("confirm_borrow/not-valid"))
    }
}
