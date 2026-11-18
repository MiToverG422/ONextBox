package com.mi.onextbox.ui.screens

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class TranslationCompletenessTest {
    private val resources = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private fun strings(directory: File): Map<String, String> = buildMap {
        directory.listFiles { file -> file.extension == "xml" }.orEmpty().forEach { file ->
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                if (element.getAttribute("translatable") == "false") continue
                val key = element.getAttribute("name")
                assertNull("Duplicate ${directory.name}/$key", put(key, element.textContent))
            }
        }
    }

    @Test fun everySupportedLanguageHasEveryAppStringWithoutFallbackExceptions() {
        val base = strings(File(resources, "values"))
        val allowed = javaClass.getResourceAsStream("/translation-fallbacks.txt")!!.bufferedReader().useLines { lines ->
            lines.map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.toSet()
        }
        val actual = buildSet {
            resources.listFiles { file -> file.isDirectory && file.name != "values" && File(file, "strings.xml").isFile }.orEmpty().forEach { locale ->
                val translated = strings(locale)
                (base.keys - translated.keys).forEach { add("${locale.name}/$it") }
            }
        }
        assertTrue("Translation fallback exceptions must remain empty.", allowed.isEmpty())
        assertTrue("Missing translations: $actual", actual.isEmpty())
    }

    @Test fun allTranslationsPreserveFormatArgumentsAndIntentionalEmptyStrings() {
        val base = strings(File(resources, "values"))
        val arguments = Regex("%[0-9]+\\$[-#+ 0,(]*[0-9]*(?:\\.[0-9]+)?[a-zA-Z]")
        resources.listFiles { file -> file.isDirectory && File(file, "strings.xml").isFile }.orEmpty().forEach { locale ->
            val translated = strings(locale)
            base.forEach { (key, value) ->
                assertEquals("${locale.name}/$key empty text", value.isBlank(), translated.getValue(key).isBlank())
                assertEquals("${locale.name}/$key", arguments.findAll(value).map { it.value }.sorted().toList(),
                    arguments.findAll(translated.getValue(key)).map { it.value }.sorted().toList())
            }
            assertFalse("Outdated cache description in ${locale.name}", translated["diagnostics_cache_summary"].orEmpty().contains("no separate DexKit cache files"))
        }
    }

    @Test fun everyLocaleHasEveryPluralWithMatchingArgumentsAndAnOtherForm() {
        fun plurals(directory: File): Map<String, List<String>> = buildMap {
            directory.listFiles { file -> file.extension == "xml" }.orEmpty().forEach { file ->
                val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("plurals")
                for (index in 0 until nodes.length) {
                    val plural = nodes.item(index) as Element
                    if (plural.getAttribute("translatable") == "false") continue
                    val items = plural.getElementsByTagName("item")
                    val values = (0 until items.length).map { items.item(it) as Element }
                    assertTrue("${directory.name}/${plural.getAttribute("name")} requires other", values.any { it.getAttribute("quantity") == "other" })
                    put(plural.getAttribute("name"), values.map { it.textContent })
                }
            }
        }
        val base = plurals(File(resources, "values"))
        val arguments = Regex("%[0-9]+\\$[a-zA-Z]")
        resources.listFiles { file -> file.isDirectory && File(file, "strings.xml").isFile }.orEmpty().forEach { locale ->
            val translated = plurals(locale)
            assertEquals(locale.name, base.keys, translated.keys)
            base.forEach { (key, values) ->
                val expected = arguments.findAll(values.first()).map { it.value }.sorted().toList()
                translated.getValue(key).forEach { value ->
                    assertTrue("${locale.name}/$key", value.isNotBlank())
                    assertEquals("${locale.name}/$key", expected, arguments.findAll(value).map { it.value }.sorted().toList())
                }
            }
        }
    }

    @Test fun selectionArraysHaveTheSameChoicesInEveryLanguage() {
        fun arrays(directory: File): Map<String, List<String>> = buildMap {
            directory.listFiles { file -> file.extension == "xml" }.orEmpty().forEach { file ->
                val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string-array")
                for (index in 0 until nodes.length) {
                    val array = nodes.item(index) as Element
                    if (array.getAttribute("translatable") == "false") continue
                    val items = array.getElementsByTagName("item")
                    put(array.getAttribute("name"), (0 until items.length).map { items.item(it).textContent })
                }
            }
        }
        val base = arrays(File(resources, "values"))
        resources.listFiles { file -> file.isDirectory && File(file, "strings.xml").isFile }.orEmpty().forEach { locale ->
            val translated = arrays(locale)
            assertEquals(locale.name, base.keys, translated.keys)
            base.forEach { (key, values) ->
                assertEquals("${locale.name}/$key", values.size, translated.getValue(key).size)
                assertTrue("${locale.name}/$key", translated.getValue(key).all(String::isNotBlank))
            }
        }
    }
}
