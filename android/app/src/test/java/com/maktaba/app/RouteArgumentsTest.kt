package com.maktaba.app

import com.maktaba.app.nav.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteArgumentsTest {
    @Test
    fun encodesReservedCharactersInsteadOfCreatingRouteSegmentsOrQueries() {
        assertEquals("catalogue_book/a%2Fb%3Fc%3Dd%26e%20f", Routes.CatalogueBookDetail.createRoute("a/b?c=d&e f"))
        assertEquals("shared_collection/reader%23one/list%2Ftwo", Routes.SharedCollection.createRoute("reader#one", "list/two"))
    }

    @Test
    fun rawInviteCodeIsNotADebugDestination() {
        assertNull(validatedDebugRoute("ABCD-1234"))
        assertEquals("confirm_borrow/ABCD-1234", validatedDebugRoute("confirm_borrow/ABCD-1234"))
    }
}
