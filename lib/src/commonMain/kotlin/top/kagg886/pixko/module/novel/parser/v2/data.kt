package top.kagg886.pixko.module.novel.parser.v2


sealed interface NovelNode {
    val position: IntRange
}

data class TextNode(val text: CombinedText, override val position: IntRange) : NovelNode

class JumpUriNode(
    val text: String,
    internal val rawUri: String,
    override val position: IntRange
) : NovelNode {
    val uri: String = rawUri.trim()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as JumpUriNode

        if (text != other.text) return false
        if (rawUri != other.rawUri) return false
        if (position != other.position) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + rawUri.hashCode()
        result = 31 * result + position.hashCode()
        return result
    }

    override fun toString(): String {
        return "JumpUriNode(position=$position, text='$text', uri='$uri')"
    }
}

data class UploadImageNode(val url: String, override val position: IntRange) : NovelNode

data class PixivImageNode(val id: Int, val index: Int = 0, override val position: IntRange) : NovelNode

data class NewPageNode(override val position: IntRange) : NovelNode

data class TitleNode(val text: CombinedText, override val position: IntRange) : NovelNode

data class JumpPageNode(val page: Int, override val position: IntRange) : NovelNode

val NovelNode.isBlocking get() = this is JumpUriNode || this is TextNode || this is JumpPageNode


class CombinedText internal constructor(nodes: List<CombinedTextNode>) : List<CombinedTextNode> by nodes {
    override fun toString() = joinToString {
        when (it) {
            is NotatedText -> "${it.text}^{${it.notation}}"
            is PlainText -> it.text
        }.replace("\n", "\\n")
    }
}

fun List<CombinedTextNode>.asCombinedText() = CombinedText(this)

sealed interface CombinedTextNode {
    val text: String

    fun asSingle() = CombinedText(listOf(this))
}

data class PlainText(override val text: String) : CombinedTextNode
data class NotatedText(override val text: String, val notation: String) : CombinedTextNode

fun String.toPlainText() = PlainText(this)
fun String.toNotatedText(notation: String) = NotatedText(this, notation)