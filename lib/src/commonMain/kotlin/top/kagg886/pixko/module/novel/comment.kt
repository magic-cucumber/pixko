package top.kagg886.pixko.module.novel

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import top.kagg886.pixko.PixivAccount
import top.kagg886.pixko.module.illust.Comment
import top.kagg886.pixko.module.illust.CommentListResult
import kotlin.properties.Delegates

/**
 * # 获取评论列表
 *
 * @param novelId 作品id
 * @param page 页码，默认值为1
 * @return 评论列表
 *
 */
suspend fun PixivAccount.getNovelComment(novelId: Long, page: Int = 1): List<Comment> {
    return client.get("v3/novel/comments") {
        parameter("novel_id", novelId)
        parameter("offset", (page - 1) * 30)
    }.body<CommentListResult>().comments
}

/**
 * # 获取回复列表
 *
 * @param commentId 评论id
 * @param page 页码，默认值为1
 * @return 回复列表
 *
 */
suspend fun PixivAccount.getNovelCommentReply(commentId: Long, page: Int = 1): List<Comment> {
    return client.get("v2/novel/comment/replies") {
        parameter("comment_id", commentId)
        parameter("offset", (page - 1) * 30)
    }.body<CommentListResult>().comments
}

/**
 * # 发送评论的配置
 *
 * @property novelId 作品id
 * @property comment 评论内容
 * @property parentCommentId 父评论id，若为null则表示发送回复，否则为发送评论
 */
class NovelComment {
    internal sealed interface CommentContent
    internal data class PlainText(internal val comment: String) : CommentContent
    internal data class Stamp(internal val id: Long) : CommentContent

    var novelId by Delegates.notNull<Long>()
    @Deprecated("use comment() method.", replaceWith = ReplaceWith(expression = "comment()"))
    var comment: String = ""
        get() = (data as? PlainText)?.comment ?: throw NullPointerException()
        set(value) {
            field = value
            this.data = PlainText(field)
        }

    var parentCommentId: Long? = null

    internal var data by Delegates.notNull<CommentContent>()

    fun comment(data: String) {
        this.data = PlainText(data)
    }

    fun comment(id: Long) {
        this.data = Stamp(id)
    }
}

/**
 * # 发送评论
 *
 * @param block 配置
 * @return 发送的评论
 * @see NovelComment
 */
suspend fun PixivAccount.sendNovelComment(block: NovelComment.() -> Unit): Comment {
    @Serializable
    data class CommentHolder(
        val comment: Comment
    )

    val illustComment = NovelComment().apply(block)

    val resp = client.post("v1/novel/comment/add") {
        contentType(ContentType.Application.FormUrlEncoded)
        setBody(FormDataContent(
            Parameters.build {
                append("novel_id", illustComment.novelId.toString())
                when (val it = illustComment.data) {
                    is NovelComment.PlainText -> append("comment",it.comment)
                    is NovelComment.Stamp -> append("stamp_id",it.id.toString())
                }
                illustComment.parentCommentId?.let {
                    append("parent_comment_id", it.toString())
                }
            }
        ))
    }.body<CommentHolder>()
    return resp.comment
}

/**
 * # 删除评论
 *
 * @param commentId 评论id
 *
 */
suspend fun PixivAccount.deleteNovelComment(commentId: Long) {
    client.post("v1/novel/comment/delete") {
        contentType(ContentType.Application.FormUrlEncoded)
        setBody(FormDataContent(
            Parameters.build {
                append("comment_id", commentId.toString())
            }
        ))
    }
}
