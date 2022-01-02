package ca.kieve.yomiyou

import com.fasterxml.jackson.databind.JsonNode

data class CopyDownTestCase(
    val name: String,
    val options: JsonNode?,
    val input: String,
    val output: String
)
