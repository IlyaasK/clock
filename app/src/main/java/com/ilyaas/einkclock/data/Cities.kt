package com.ilyaas.einkclock.data

/**
 * The world-clock catalogue shown on the Clock tab. Ids are IANA zone ids; the
 * label is what the row prints (the stock app shows Berlin / Mumbai / Auckland,
 * which is also our default selection).
 */
object Cities {

    data class City(val zoneId: String, val label: String)

    val ALL: List<City> = listOf(
        City("Pacific/Auckland", "Auckland"),
        City("Australia/Sydney", "Sydney"),
        City("Asia/Tokyo", "Tokyo"),
        City("Asia/Seoul", "Seoul"),
        City("Asia/Shanghai", "Shanghai"),
        City("Asia/Hong_Kong", "Hong Kong"),
        City("Asia/Singapore", "Singapore"),
        City("Asia/Bangkok", "Bangkok"),
        City("Asia/Jakarta", "Jakarta"),
        City("Asia/Dhaka", "Dhaka"),
        City("Asia/Kolkata", "Mumbai"),
        City("Asia/Karachi", "Karachi"),
        City("Asia/Dubai", "Dubai"),
        City("Asia/Jerusalem", "Jerusalem"),
        City("Europe/Istanbul", "Istanbul"),
        City("Europe/Moscow", "Moscow"),
        City("Africa/Lagos", "Lagos"),
        City("Africa/Johannesburg", "Johannesburg"),
        City("Europe/Berlin", "Berlin"),
        City("Europe/Paris", "Paris"),
        City("Europe/Madrid", "Madrid"),
        City("Europe/Rome", "Rome"),
        City("Europe/London", "London"),
        City("UTC", "UTC"),
        City("America/Sao_Paulo", "Sao Paulo"),
        City("America/Argentina/Buenos_Aires", "Buenos Aires"),
        City("America/New_York", "New York"),
        City("America/Toronto", "Toronto"),
        City("America/Chicago", "Chicago"),
        City("America/Mexico_City", "Mexico City"),
        City("America/Denver", "Denver"),
        City("America/Los_Angeles", "Los Angeles"),
        City("America/Anchorage", "Anchorage"),
        City("Pacific/Honolulu", "Honolulu"),
    )

    private val byId = ALL.associateBy { it.zoneId }

    fun label(zoneId: String): String =
        byId[zoneId]?.label ?: zoneId.substringAfterLast('/').replace('_', ' ')

    /** Default selection: the three cities the stock app shows. */
    const val DEFAULT = "Europe/Berlin,Asia/Kolkata,Pacific/Auckland"
}
