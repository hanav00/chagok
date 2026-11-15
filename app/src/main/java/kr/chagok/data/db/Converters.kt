package kr.chagok.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Room 이 모르는 시간 타입 변환. 열거형은 Room 이 이름(글자)으로 저장한다 */
class Converters {
    @TypeConverter
    fun dateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun timeToSecondOfDay(value: LocalTime?): Int? = value?.toSecondOfDay()

    @TypeConverter
    fun secondOfDayToTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter
    fun dateTimeToText(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun textToDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)
}
