package ca.kieve.yomiyou.dictionary

import ca.kieve.yomiyou.book.ZipIndex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * Yomitan dictionaries: a zip of index.json, tag banks and term banks (v3). Read as yomiage's `dictionary.ts` reads
 * them; both pass the cases in `src/test/resources/dictionary/entries.json`, which comes from yomiage.
 */
object Yomitan {
    data class Info(val title: String, val revision: String, val attribution: String)

    fun isDictionary(zip: ZipIndex): Boolean = zip.has("index.json") && termBanks(zip).isNotEmpty()

    /** The term banks, in order: term_bank_1.json, term_bank_2.json... */
    fun termBanks(zip: ZipIndex): List<String> =
        zip.names.mapNotNull { name -> TERM_BANK.matchEntire(name)?.let { name to it.groupValues[1].toInt() } }
            .sortedBy { it.second }
            .map { it.first }

    /** @throws IOException with a message fit to show, when it isn't a dictionary this app can read */
    fun info(zip: ZipIndex): Info {
        if (!isDictionary(zip)) throw IOException("Not a Yomitan dictionary.")
        val index = Json.parseToJsonElement(zip.text("index.json")).jsonObject
        val format = index["format"]?.jsonPrimitive?.intOrNull ?: index["version"]?.jsonPrimitive?.intOrNull
        if (format != 3) throw IOException("This dictionary is in an older Yomitan format.")
        fun string(name: String) = (index[name] as? JsonPrimitive)?.contentOrNull
        return Info(string("title") ?: "Dictionary", string("revision").orEmpty(), string("attribution").orEmpty())
    }

    /** A term bank's entries. */
    fun bank(zip: ZipIndex, name: String): List<DictionaryEntry> =
        Json.parseToJsonElement(zip.text(name)).jsonArray.mapNotNull { entryOf(it.jsonArray) }

    /** An entry from a term bank row; null when it has nothing to show (a table of forms, a pointer to another word). */
    fun entryOf(row: JsonArray): DictionaryEntry? {
        val term = row[0].jsonPrimitive.content
        val reading = row[1].jsonPrimitive.content
        val tags = (row[2] as? JsonPrimitive)?.contentOrNull.orEmpty().split(" ")
            .filter { it.isNotEmpty() && !it.all(Char::isDigit) }
        if ("forms" in tags) return null
        val meanings = meaningsOf(row[5].jsonArray)
        if (meanings.isEmpty()) return null
        val kinds = LinkedHashSet(row[3].jsonPrimitive.content.split(" ").filter { it.isNotEmpty() })
        tags.mapNotNullTo(kinds, ::kindOfTag)
        return DictionaryEntry(
            term = term,
            reading = reading.ifEmpty { term },
            rules = kinds.toList(),
            score = row[4].jsonPrimitive.doubleOrNull?.toInt() ?: 0,
            tags = tags,
            meanings = meanings,
            sequence = row[6].jsonPrimitive.intOrNull ?: 0,
        )
    }

    /** Tags that make an entry a name (JMnedict's): a name has readings where a word has meanings, and no reading. */
    private val NAME_TAGS = setOf(
        "char", "company", "creat", "dei", "doc", "ev", "fem", "fict", "given", "group", "leg", "male", "masc", "myth", "obj",
        "organization", "oth", "person", "place", "product", "relig", "serv", "ship", "station", "surname", "unclass", "work",
    )

    /** Whether an entry is a name rather than a word: 小柳 (surname), its readings こやなぎ, おやなぎ as its "meanings". */
    fun isName(entry: DictionaryEntry): Boolean = entry.tags.isNotEmpty() && entry.tags.all { it in NAME_TAGS }

    /** Kinds of word found in part-of-speech tags too: JMdict marks a する noun only there (勉強: "n vs vt"). */
    private fun kindOfTag(tag: String): String? = when {
        tag.startsWith("v5") -> "v5"
        tag == "v1" || tag == "v1-s" -> "v1"
        tag == "vs" || tag == "vs-i" || tag == "vs-s" -> "vs"
        tag == "vk" -> "vk"
        tag == "adj-i" || tag == "adj-ix" -> "adj-i"
        else -> null
    }

    /** The meanings in a definition: plain strings, or the glossary lists of Yomitan's structured content. */
    private fun meaningsOf(definitions: JsonArray): List<String> {
        val meanings = mutableListOf<String>()
        for (definition in definitions) {
            when {
                definition is JsonPrimitive && definition.isString -> meanings += definition.content
                definition is JsonObject && definition.string("type") == "text" ->
                    definition.string("text")?.let { meanings += it }
                definition is JsonObject && definition.string("type") == "structured-content" ->
                    collectGlossary(definition["content"], false, meanings)
            }
        }
        return meanings
    }

    /** Each list item of the parts marked as glossary, as text; notes, references and form tables are left out. */
    private fun collectGlossary(node: JsonElement?, inGlossary: Boolean, into: MutableList<String>) {
        when (node) {
            is JsonArray -> node.forEach { collectGlossary(it, inGlossary, into) }
            is JsonObject -> {
                val glossary = inGlossary || (node["data"] as? JsonObject)?.string("content") == "glossary"
                if (glossary && node.string("tag") == "li") {
                    textOf(node["content"]).trim().takeIf { it.isNotEmpty() }?.let { into += it }
                } else {
                    collectGlossary(node["content"], glossary, into)
                }
            }
            else -> {}
        }
    }

    private fun textOf(node: JsonElement?): String = when (node) {
        null, JsonNull -> ""
        is JsonPrimitive -> if (node.isString) node.content else ""
        is JsonArray -> node.joinToString("") { textOf(it) }
        is JsonObject -> textOf(node["content"])
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private val TERM_BANK = Regex("""term_bank_(\d+)\.json""")
}
