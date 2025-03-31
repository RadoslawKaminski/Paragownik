package com.kaminski.paragownik.data

import androidx.room.TypeConverter
import java.util.Date

/**
 * Klasa zawierająca konwertery typów dla bazy danych Room.
 * Room potrafi przechowywać tylko proste typy danych (jak Long, String, Int).
 * Konwertery pozwalają na zapis i odczyt bardziej złożonych typów, jak np. [Date] i [PhotoType].
 */
class Converters {

    /**
     * Konwertuje wartość typu Long (timestamp zapisaną w bazie) na obiekt [Date].
     * Wywoływany podczas odczytu danych z bazy.
     * @param value Wartość Long (timestamp) z bazy danych lub null.
     * @return Obiekt [Date] lub null, jeśli wartość w bazie była null.
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    /**
     * Konwertuje obiekt [Date] na wartość typu Long (timestamp), która może być zapisana w bazie.
     * Wywoływany podczas zapisu danych do bazy.
     * @param date Obiekt [Date] lub null.
     * @return Wartość Long (timestamp) reprezentująca datę lub null, jeśli obiekt Date był null.
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    /**
     * Konwertuje obiekt [PhotoType] na String do zapisu w bazie.
     * @param type Typ zdjęcia lub null.
     * @return Nazwa enuma jako String (np. "CLIENT") lub null.
     */
    @TypeConverter
    fun fromPhotoType(type: PhotoType?): String? {
        return type?.name
    }

    /**
     * Konwertuje String z bazy danych na obiekt [PhotoType].
     * @param value Wartość String z bazy (np. "CLIENT") lub null.
     * @return Obiekt [PhotoType] lub null.
     */
    @TypeConverter
    fun toPhotoType(value: String?): PhotoType? {
        return try {
            value?.let { PhotoType.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}



