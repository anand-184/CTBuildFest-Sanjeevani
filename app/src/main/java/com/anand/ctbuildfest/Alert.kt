data class Alert(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val severity: String = "",
    val type: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return """{"title":"$title","description":"$description","location":"$location"}"""
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "title" to title,
            "description" to description,
            "location" to location,
            "severity" to severity,
            "type" to type,
            "timestamp" to timestamp
        )
    }
}