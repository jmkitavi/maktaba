package com.maktaba.app.data

fun compactIsbn(value: String): String = value.replace(Regex("[\\s-]"), "").uppercase()

fun isValidIsbn10(value: String): Boolean {
    val isbn = compactIsbn(value)
    if (!isbn.matches(Regex("\\d{9}[\\dX]"))) return false
    return isbn.mapIndexed { index, character ->
        val digit = if (character == 'X') 10 else character.digitToInt()
        (10 - index) * digit
    }.sum() % 11 == 0
}

fun isValidIsbn13(value: String): Boolean {
    val isbn = compactIsbn(value)
    if (!isbn.matches(Regex("\\d{13}"))) return false
    return isbn.mapIndexed { index, character ->
        character.digitToInt() * if (index % 2 == 0) 1 else 3
    }.sum() % 10 == 0
}

fun isValidIsbn(value: String): Boolean = isValidIsbn10(value) || isValidIsbn13(value)
