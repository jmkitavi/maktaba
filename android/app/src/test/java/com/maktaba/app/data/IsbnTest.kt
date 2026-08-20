package com.maktaba.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnTest {
    @Test
    fun validatesIsbn10And13() {
        assertTrue(isValidIsbn("1250255171"))
        assertTrue(isValidIsbn("978-1-250-25517-4"))
        assertFalse(isValidIsbn("9781250255175"))
        assertFalse(isValidIsbn("not-an-isbn"))
    }
}
