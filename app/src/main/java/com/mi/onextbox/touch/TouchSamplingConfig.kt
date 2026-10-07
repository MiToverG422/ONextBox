package com.mi.onextbox.touch

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

internal data class TouchConfigProfile(
    val panelIndex: Int?,
    val node: Int = 182,
    val presets: List<TouchRatePreset>,
    val defaultChipValue: Int?,
    val sourceLabel: String? = null,
)

/** Reads indexed touch modes without inventing frequencies or chip mappings. */
internal object TouchSamplingConfig {
    private const val MAX_XML_BYTES = 1024 * 1024
    private const val MAX_NODES = 16384
    private const val MAX_DEPTH = 64
    private const val MAX_MODES = 256
    private val declarations = Regex("<!\\s*(DOCTYPE|ENTITY)\\b", RegexOption.IGNORE_CASE)
    private val rateComment = Regex("\\brate\\s*:\\s*([^\\r\\n]+)", RegexOption.IGNORE_CASE)
    private val frequencyLabel = Regex("(?i)(?<!\\d)(\\d+)\\s*hz(?:\\s*ist)?\\b")
    private val plainRateLabel = Regex("(?i)^(\\d+)\\s*(?:hz)?\\s*(ist)?$")
    private val istLabel = Regex("(?i)\\bist\\b|(?<=\\d)ist\\b")
    private val frequencyParams = setOf("frequency", "frequencies", "frequency_hz", "frequencies_hz", "rates_hz", "hz")

    fun parse(xml: String): List<TouchConfigProfile> {
        if (xml.length > MAX_XML_BYTES || xml.toByteArray(Charsets.UTF_8).size > MAX_XML_BYTES ||
            declarations.containsMatchIn(xml)
        ) return emptyList()
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
                setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
                setFeatureIfSupported("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
            val builder = factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> throw SAXException("External entities are disabled") }
                setErrorHandler(DefaultHandler())
            }
            val document = builder.parse(InputSource(StringReader(xml)))
            val nodes = boundedNodes(document.documentElement) ?: return emptyList()
            val modules = nodes.filterIsInstance<Element>()
                .filter { it.tagName in setOf("modules", "module") && it.getAttribute("id").trim() == "report_rate" }
                .filterNot { it.getAttribute("enable").trim().equals("false", ignoreCase = true) }
            val profiles = modules.map(::parseModule)
            if (profiles.any { it == null }) emptyList() else profiles.filterNotNull().distinct()
        }.getOrDefault(emptyList())
    }

    fun select(
        profiles: List<TouchConfigProfile>,
        panelIndex: Int,
        currentMode: Pair<Int, Int>?,
    ): TouchConfigProfile? {
        if (panelIndex < 0) return null
        val exact = profiles.filter { it.panelIndex == panelIndex }
        val candidates = exact.ifEmpty { profiles.filter { it.panelIndex == null } }
        if (candidates.isEmpty()) return null
        val first = candidates.first()
        if (candidates.any { !compatible(first, it) }) return null
        if (currentMode != null && !first.matches(currentMode)) return null
        val merged = first.presets.map { preset ->
            val alternatives = candidates.map { it.presets.first { mode -> mode.index == preset.index } }
            val frequencies = alternatives.mapNotNull { it.hz }.distinct()
            if (frequencies.size > 1) return null
            preset.copy(
                hz = frequencies.singleOrNull(),
                isIstMode = alternatives.any { it.isIstMode },
            )
        }
        return first.copy(presets = merged)
    }

    private data class ModeLabel(val hz: Int? = null, val ist: Boolean = false)
    private data class ModeTable(val codes: List<Int>, val labels: List<ModeLabel>)

    private fun parseModule(module: Element): TouchConfigProfile? {
        if (module.getAttribute("enable").trim().equals("false", ignoreCase = true)) return null
        val panel = module.ancestors().filterIsInstance<Element>().firstOrNull { it.tagName == "panel" }
        val panelIndex = if (panel == null) null else {
            val indexes = listOf("id", "index").filter(panel::hasAttribute)
                .map { panel.getAttribute(it).trim().toIntOrNull() ?: return null }
            if (indexes.isEmpty() || indexes.distinct().size != 1 || indexes.first() !in 0..255) return null
            indexes.first()
        }
        val node = if (module.hasAttribute("node")) {
            module.getAttribute("node").trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        } else 182
        val params = module.children().filterIsInstance<Element>().filter { it.tagName == "param" }
        val supports = params.filter { it.getAttribute("name").trim() == "supports" }
        if (supports.isEmpty()) return null
        val tables = supports.map { parseSupports(it) ?: return null }
        val table = tables.first()
        if (tables.any { it != table }) return null
        val labelSources = mutableListOf(table.labels)
        for (param in params.filter { it.getAttribute("name").trim().lowercase() in frequencyParams }) {
            val values = param.children().filterIsInstance<Element>().filter { it.tagName == "value" }
            if (values.size != 1) return null
            val frequencies = integerList(values.single().textContent) ?: return null
            if (frequencies.size != table.codes.size) return null
            if (frequencies.withIndex().any { (index, hz) -> hz !in 30..4000 && !(index == 0 && hz == 0) }) return null
            labelSources += frequencies.map { ModeLabel(it.takeIf { hz -> hz != 0 }) }
        }
        val labels = table.codes.indices.map { index ->
            val candidates = labelSources.map { it[index] }
            val frequencies = candidates.mapNotNull { it.hz }.distinct()
            if (frequencies.size > 1) return null
            ModeLabel(frequencies.singleOrNull(), candidates.any { it.ist })
        }
        return TouchConfigProfile(
            panelIndex = panelIndex,
            node = node,
            presets = table.codes.indices.drop(1).map { index ->
                TouchRatePreset(index, labels[index].hz, table.codes[index], labels[index].ist)
            },
            defaultChipValue = table.codes.first(),
            sourceLabel = panelIndex?.let { "panel $it" },
        )
    }

    private fun parseSupports(param: Element): ModeTable? {
        val values = param.children().filterIsInstance<Element>().filter { it.tagName == "value" }
        if (values.isEmpty()) return null
        val indexed = values.any { it.hasAttribute("index") }
        val codes: List<Int>
        val labels: List<ModeLabel>
        if (indexed) {
            if (values.any { !it.hasAttribute("index") } || values.size !in 2..MAX_MODES) return null
            val rows = values.map { value ->
                val index = value.getAttribute("index").trim().toIntOrNull()?.takeIf { it in 0 until MAX_MODES } ?: return null
                val code = integerList(value.textContent)?.singleOrNull() ?: return null
                val label = valueLabel(value, index == 0) ?: return null
                Triple(index, code, label)
            }.sortedBy { it.first }
            if (rows.map { it.first } != rows.indices.toList()) return null
            codes = rows.map { it.second }
            labels = rows.map { it.third }
        } else {
            if (values.size != 1) return null
            codes = integerList(values.single().textContent)?.takeIf { it.size in 2..MAX_MODES } ?: return null
            labels = List(codes.size) { ModeLabel() }
        }
        val comments = boundedNodes(param)?.filter { it.nodeType == Node.COMMENT_NODE }.orEmpty()
            .flatMap { node -> rateComment.findAll(node.nodeValue.orEmpty()).map { it.groupValues[1] }.toList() }
        val commentLabels = comments.mapNotNull { text ->
            text.split(',').takeIf { it.size == codes.size }?.map(::commentLabel)
        }
        val combined = codes.indices.map { index ->
            val candidates = listOf(labels[index]) + commentLabels.map { it[index] }
            val frequencies = candidates.mapNotNull { it.hz }.distinct()
            if (frequencies.size > 1) return null
            ModeLabel(frequencies.singleOrNull(), candidates.any { it.ist })
        }
        return ModeTable(codes, combined)
    }

    private fun valueLabel(value: Element, isDefault: Boolean): ModeLabel? {
        val labels = listOf("label", "name").filter(value::hasAttribute).map { value.getAttribute(it) }
        val explicit = listOf("hz", "frequency_hz").filter(value::hasAttribute).map { attribute ->
            value.getAttribute(attribute).trim().toIntOrNull()
                ?.takeIf { it in 30..4000 || (isDefault && it == 0) } ?: return null
        }
        val named = labels.flatMap { text -> frequencyLabel.findAll(text).map { it.groupValues[1].toIntOrNull() }.toList() }
        if (named.any { it == null || it !in 30..4000 }) return null
        val frequencies = (explicit.filter { it != 0 } + named.filterNotNull()).distinct()
        if (frequencies.size > 1) return null
        return ModeLabel(frequencies.singleOrNull(), labels.any(istLabel::containsMatchIn))
    }

    private fun commentLabel(text: String): ModeLabel {
        val match = plainRateLabel.matchEntire(text.trim())
        val hz = match?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 30..4000 }
        return ModeLabel(hz, istLabel.containsMatchIn(text))
    }

    private fun integerList(text: String): List<Int>? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val parts = if (',' in clean) clean.split(',').map(String::trim) else clean.split(Regex("\\s+"))
        if (parts.isEmpty() || parts.size > MAX_MODES) return null
        return parts.map { token ->
            if (!token.matches(Regex("[0-9]+"))) return null
            token.toIntOrNull() ?: return null
        }
    }

    private fun compatible(first: TouchConfigProfile, second: TouchConfigProfile): Boolean =
        first.node == second.node && first.defaultChipValue == second.defaultChipValue &&
            first.presets.size == second.presets.size && first.presets.zip(second.presets).all { (a, b) ->
                a.index == b.index && a.chipValue == b.chipValue &&
                    (a.hz == null || b.hz == null || a.hz == b.hz)
            }

    private fun TouchConfigProfile.matches(mode: Pair<Int, Int>): Boolean =
        if (mode.first == 0) mode.second == defaultChipValue
        else presets.any { it.index == mode.first && it.chipValue == mode.second }

    private fun Element.ancestors(): Sequence<Node> = generateSequence(parentNode) { it.parentNode }

    private fun Node.children(): List<Node> = buildList {
        for (index in 0 until childNodes.length) add(childNodes.item(index))
    }

    private fun boundedNodes(root: Node): List<Node>? {
        val pending = ArrayDeque<Pair<Node, Int>>()
        val result = mutableListOf<Node>()
        pending.addLast(root to 0)
        while (pending.isNotEmpty()) {
            val (node, depth) = pending.removeLast()
            if (depth > MAX_DEPTH || result.size >= MAX_NODES) return null
            result += node
            for (index in 0 until node.childNodes.length) pending.addLast(node.childNodes.item(index) to depth + 1)
        }
        return result
    }

    private fun DocumentBuilderFactory.setFeatureIfSupported(name: String, enabled: Boolean) {
        // Android and JVM XML providers expose different optional security features.
        runCatching { setFeature(name, enabled) }
    }
}
