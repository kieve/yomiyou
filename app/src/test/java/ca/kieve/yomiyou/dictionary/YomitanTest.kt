package ca.kieve.yomiyou.dictionary

import ca.kieve.yomiyou.book.BytesAccess
import ca.kieve.yomiyou.book.TestFiles
import ca.kieve.yomiyou.book.ZipIndex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class YomitanTest {
    @Test
    fun `a title names its dictionary whatever the revision, and names come after words`() {
        assertEquals("JMdict (Legacy)", Dictionary.nameOf("JMdict (Legacy) [2026-11-11]"))
        assertEquals("JMnedict", Dictionary.nameOf("JMnedict"))
        val names = StoredInfo(1, "JMnedict", "", "", 1, names = true)
        val words = StoredInfo(2, "JMdict", "", "", 1, names = false)
        assertEquals(listOf(words, names), Dictionary.inOrder(listOf(names, words)))
    }

    @Test
    fun `term bank rows give the entries yomiage's shared cases expect`() {
        val text = javaClass.getResource("/dictionary/entries.json")!!.readText()
        val cases = Json.parseToJsonElement(text).jsonObject.getValue("cases").jsonArray
        assertTrue(cases.isNotEmpty())
        for (case in cases.map { it.jsonObject }) {
            val name = case.getValue("name").jsonPrimitive.content
            val expected = case.getValue("entry").takeUnless { it is JsonNull }
                ?.let { Json.decodeFromJsonElement(DictionaryEntry.serializer(), it) }
            val read = Yomitan.entryOf(case.getValue("row").jsonArray)
            assertEquals(expected, read, name)
            val isName = case["isName"]?.jsonPrimitive?.content == "true"
            if (read != null) assertEquals(isName, Yomitan.isName(read), name)
        }
    }

    private fun zip(index: String, vararg banks: Pair<String, String>) = ZipIndex.open(
        BytesAccess(TestFiles.zip("index.json" to index.toByteArray(), *banks.map { it.first to it.second.toByteArray() }.toTypedArray()))
    )

    @Test
    fun `a dictionary is told by what's inside, its banks read in order`() {
        val dictionary = zip(
            """{"title": "Test JMdict", "format": 3, "revision": "t.1", "attribution": "EDRDG"}""",
            "term_bank_10.json" to """[["本", "ほん", "n", "", 0, ["book"], 2, ""]]""",
            "term_bank_2.json" to """[["読む", "よむ", "v5m", "v5", 0, ["to read"], 1, ""]]""",
        )
        assertTrue(Yomitan.isDictionary(dictionary))
        assertEquals(listOf("term_bank_2.json", "term_bank_10.json"), Yomitan.termBanks(dictionary))
        assertEquals(Yomitan.Info("Test JMdict", "t.1", "EDRDG"), Yomitan.info(dictionary))
        assertEquals(listOf("to read"), Yomitan.bank(dictionary, "term_bank_2.json").single().meanings)

        val old = zip("""{"title": "Old", "format": 1}""", "term_bank_1.json" to "[]")
        assertEquals("This dictionary is in an older Yomitan format.", assertThrows(IOException::class.java) { Yomitan.info(old) }.message)
        val voice = ZipIndex.open(BytesAccess(TestFiles.zip("voice.json" to "{}".toByteArray())))
        assertFalse(Yomitan.isDictionary(voice))
    }
}
