package com.kaminski.paragownik.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Klasa relacyjna używana przez Room do pobierania obiektu [Receipt]
 * wraz z powiązanym obiektem [Client] w jednym zapytaniu.
 * Zapobiega problemowi N+1 zapytań.
 *
 * @property receipt Osadzony obiekt [Receipt] (wszystkie jego kolumny są traktowane jak kolumny tej klasy).
 * @property client Powiązany obiekt [Client]. Room automatycznie dopasuje klienta na podstawie
 *                  relacji zdefiniowanej przez `@Relation`. Klient może być null, jeśli wystąpi
 *                  niespójność danych (choć klucz obcy powinien temu zapobiegać).
 */
data class ReceiptWithClient(
    // Osadza wszystkie pola z encji Receipt bezpośrednio w tym obiekcie.
    @Embedded val receipt: Receipt,

    // Definiuje relację jeden-do-jednego (lub jeden-do-wielu, ale tu oczekujemy jednego klienta).
    @Relation(
        parentColumn = "clientId", // Kolumna w encji nadrzędnej (Receipt), która jest kluczem obcym.
        entityColumn = "id",       // Kolumna w encji podrzędnej (Client), która jest kluczem głównym.
        entity = Client::class     // Jawne określenie klasy encji podrzędnej (opcjonalne, jeśli typ pola jest jednoznaczny).
    )
    // Przechowuje powiązany obiekt Client. Jest to `Client?` (nullable),
    // na wypadek gdyby (teoretycznie) nie udało się znaleźć pasującego klienta.
    val client: Client?
)