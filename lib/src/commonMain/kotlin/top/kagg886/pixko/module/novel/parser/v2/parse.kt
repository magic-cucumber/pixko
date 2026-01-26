@file:OptIn(ExperimentalContracts::class)

package top.kagg886.pixko.module.novel.parser.v2

import top.kagg886.pixko.anno.ExperimentalNovelParserAPI
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/* group : note
 * s1    :　双方括号
 * s2    ： 单方括号
 * s3    ： 单方括号且无value
 */
private val TAG_REGEX =
    """(?<s1>\[{2}(?<tag1>rb|jumpuri)\s*:(?<value1>.*?)]{2}(?!]{1,2}))|(?<s2>\[(?<tag2>chapter|uploadedimage|pixivimage|jump)\s*:(?<value2>.*?)(?<!])](?!]))|(?<s3>\[(?<tag3>newpage)])""".toRegex()

// chapter中的rb注音
private val NOTATION_IN_CHARTER_REGEX =
    """\[{2}rb\s*:([^\[\]]*)>([^\[\]]*?)]{2}""".toRegex()

class ParseNovelException(message: String? = null) : Exception(message)

@Suppress("NOTHING_TO_INLINE")
private inline fun raise(message: String? = null): Nothing = throw ParseNovelException(message)

private inline fun checkParsing(value: Boolean, lazyMessage: () -> String?) {
    contract {
        returns() implies value
    }
    if (!value) {
        val message = lazyMessage()
        raise(message)
    }
}

fun tagToNode(
    name: String,
    rawValue: String?,
    position: IntRange,
): NovelNode =
    when (name) {
        // group: s1
        "rb" -> {
            val split = rawValue!!.split(">", limit = 2)
            val (notation, text) = split.also {
                checkParsing(it.size == 2) { "Cannot find separator(>)" }
            }
            TextNode(text.toNotatedText(notation).asSingle(), position)
        }

        "jumpuri" -> {
            val (text, url) = rawValue!!.split(">", limit = 2).also {
                checkParsing(it.size == 2) { "Cannot find separator(>)" }
            }
            val urlTrimmed = url.trim()
            @Suppress("HttpUrlsUsage")
            checkParsing(urlTrimmed.startsWith("http://") || urlTrimmed.startsWith("https://")) {
                "Invalid url:$urlTrimmed"
            }
            JumpUriNode(text, url, position)
        }
        // group: s2

        "jump" -> JumpPageNode(rawValue!!.toInt(), position)
        "uploadedimage" -> UploadImageNode(rawValue!!, position)
        "pixivimage" -> {
            val split = rawValue!!.split("-").map { it.toIntOrNull() ?: raise("Illegal image id: $it") }
            val id = split.first()
            val pageIndex = split.getOrElse(1) { 1 } - 1
            PixivImageNode(id, pageIndex, position)
        }

        "chapter" -> {
            val textNodes = mutableListOf<CombinedTextNode>()
            var lastIndex = 0
            NOTATION_IN_CHARTER_REGEX.findAll(rawValue!!).forEach { result ->
                val position = result.range
                if (position.first > lastIndex) {
                    val plain = rawValue.substring(lastIndex, position.first).toPlainText()
                    textNodes.add(plain)
                }
                val (notation, text) = result.destructured
                val notated = text.toNotatedText(notation)
                textNodes.add(notated)
                lastIndex = position.last + 1
            }
            if (lastIndex < rawValue.length) {
                val plain = rawValue.substring(lastIndex).toPlainText()
                textNodes.add(plain)
            }
            TitleNode(textNodes.asCombinedText(), position)
        }

        // group：s3
        "newpage" -> NewPageNode(position)
        else -> raise("Unknown tag name:$name")
    }

typealias Novel = List<NovelNode>

@ExperimentalNovelParserAPI
fun createNovelDataV2(str: String): Novel {
    val nodes = mutableListOf<NovelNode>()
    var lastIndex = 0

    TAG_REGEX.findAll(str).forEach { result ->
        val position = result.range

        if (position.first > lastIndex) {
            val plainText = str.substring(lastIndex, position.first).toPlainText().asSingle()
            nodes.add(TextNode(plainText, lastIndex..position.first))
        }

        val groups = result.groups
        val s1 = groups["s1"]
        val s2 = groups["s2"]
        val s3 = groups["s3"]
        check(listOfNotNull(s1, s2, s3).size == 1) {
            "s1 s2 s3中只可能有且只有一个不为null"
        }
        val node = try {
            when {
                s1 != null -> {
                    val tag = groups["tag1"]!!.value
                    val value = groups["value1"]!!.value
                    tagToNode(tag, value, position)
                }

                s2 != null -> {
                    val tag = groups["tag2"]!!.value
                    val value = groups["value2"]!!.value
                    tagToNode(tag, value, position)
                }

                s3 != null -> {
                    val tag = groups["tag3"]!!.value
                    tagToNode(tag, null, position)
                }

                else -> error("Impossible")
            }
        } catch (_: ParseNovelException) {
            TextNode(result.value.toPlainText().asSingle(), position)
        }
        nodes.add(node)

        lastIndex = position.last + 1
    }

    if (lastIndex < str.length) {
        val plainText = str.substring(lastIndex).toPlainText().asSingle()
        nodes.add(TextNode(plainText, lastIndex..(str.length)))
    }

    return nodes
}

/**
 * 通过给定的Novel猜测原始字符串
 */
@ExperimentalNovelParserAPI
fun Novel.inferOriginalString(): String {
    fun CombinedText.toOriginalString() = joinToString("") {
        when (it) {
            is NotatedText -> "[[rb:${it.notation}>${it.text}]]"
            is PlainText -> it.text
        }
    }

    return buildString {
        this@inferOriginalString.forEach { v ->
            append(
                when (v) {
                    is TextNode -> v.text.toOriginalString()
                    is JumpUriNode -> "[[jumpuri:${v.text}>${v.rawUri}]]"
                    is UploadImageNode -> "[uploadedimage:${v.url}]"
                    is PixivImageNode -> "[pixivimage:${v.id}${if (v.index != 0) "-${v.index + 1}" else ""}]"
                    is NewPageNode -> "[newpage]"
                    is TitleNode -> "[chapter:${v.text.toOriginalString()}]"
                    is JumpPageNode -> "[jump:${v.page}]"
                }
            )
        }
    }
}
