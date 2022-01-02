package ca.kieve.yomiyou

import ca.kieve.yomiyou.copydown.CopyDown
import ca.kieve.yomiyou.copydown.Options
import ca.kieve.yomiyou.copydown.style.CodeBlockStyle
import ca.kieve.yomiyou.copydown.style.HeadingStyle
import ca.kieve.yomiyou.copydown.style.LinkReferenceStyle
import ca.kieve.yomiyou.copydown.style.LinkStyle
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.lang.IllegalStateException
import java.util.stream.Stream

class CopyDownTest {
    companion object {
        private fun getFileContents(path: String): String {
            return CopyDownTest::class.java.getResource(path)?.readText() ?: ""
        }

        @JvmStatic
        fun testCases(): Stream<Arguments> {
            val jsonFile = getFileContents("/copydown/tests.json")

            val mapper = jacksonObjectMapper()
            val testCases: List<CopyDownTestCase> = mapper.readValue(jsonFile)

            val DEBUG = listOf(testCases.get(51))
            return testCases.stream().map { test ->
                Arguments.of(test.name, test)
            }
        }

        private fun hasProperty(node: JsonNode, key: String, value: String): Boolean {
            if (!node.has(key)) {
                return false
            }
            return node.get(key).textValue() == value
        }

        fun buildOptions(testCase: CopyDownTestCase): Options {
            var result = Options()
            val optionsJson = testCase.options ?: return result

            if (hasProperty(optionsJson, "headingStyle", "atx")) {
                result = result.copy(
                    headingStyle = HeadingStyle.ATX
                )
            }
            if (optionsJson.has("hr")) {
                result = result.copy(
                    hr = optionsJson.get("hr").textValue()
                )
            }
            if (optionsJson.has("br")) {
                result = result.copy(
                    br = optionsJson.get("br").textValue()
                )
            }
            if (hasProperty(optionsJson, "linkStyle", "referenced")) {
                result = result.copy(
                    linkStyle = LinkStyle.REFERENCED
                )
                if (optionsJson.has("linkReferenceStyle")) {
                    val linkReferenceStyle = optionsJson.get("linkReferenceStyle").textValue()
                    result = when (linkReferenceStyle) {
                        "collapsed" -> {
                            result.copy(
                                linkReferenceStyle = LinkReferenceStyle.COLLAPSED
                            )
                        }
                        "shortcut" -> {
                            result.copy(
                                linkReferenceStyle = LinkReferenceStyle.SHORTCUT
                            )
                        }
                        else -> {
                            throw IllegalStateException(
                                "This linkReferenceStyle is wrong: $linkReferenceStyle")
                        }
                    }
                }
            }
            if (hasProperty(optionsJson, "codeBlockStyle", "fenced")) {
                result = result.copy(
                    codeBlockStyle = CodeBlockStyle.FENCED
                )
                if (optionsJson.has("fence")) {
                    result = result.copy(
                        fence = optionsJson.get("fence").textValue()
                    )
                }
            }
            if (optionsJson.has("bulletListMarker")) {
                result = result.copy(
                    bulletListMarker = optionsJson.get("bulletListMarker").textValue()
                )
            }
            return result
        }
    }

    @ParameterizedTest
    @MethodSource("testCases")
    fun mainTest(name: String, testCase: CopyDownTestCase) {
        val copyDown = CopyDown(buildOptions(testCase))
        val markdown = copyDown.convert(testCase.input)
        assertEquals(testCase.output, markdown)
    }

    @Test
    fun convertRealWebsiteTest() {
        val html = getFileContents("/copydown/gastronomia_y_cia_1.html")
        val expected = getFileContents("/copydown/gastronomia_result.md")

        val converted = CopyDown().convert(html)
        assertEquals(expected, converted + "\n")
    }
}
