package com.kaminski.paragownik.data

import androidx.room.Embedded

/**
 * Klasa pomocnicza do pobierania danych klienta wraz z URI jego pierwszego zdjęcia (miniatury).
 * Używana głównie do wyświetlania list klientów.
 *
 * @property client Osadzony obiekt Client.
 * @property thumbnailUri URI pierwszego zdjęcia typu CLIENT lub null, jeśli klient nie ma zdjęć tego typu.
 */
data class ClientWithThumbnail(
    @Embedded // Osadza wszystkie pola z Client
    val client: Client,
    // Kolumna z zapytania JOIN, przechowująca URI miniatury
    val thumbnailUri: String?
)

