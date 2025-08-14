data class QueryCondition(
    val conditions: List<String> = emptyList(),
    val parameters: Map<String, Any> = emptyMap()
) {
    fun isEmpty(): Boolean = conditions.isEmpty()
}

enum class SearchScope {
    WORLDWIDE,
    COUNTRY_ONLY
}

data class SearchParams(
    val searchScope: SearchScope,
    val searchAllRecords: Boolean = false
)