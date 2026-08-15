package top.kagg886.pixko

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import top.kagg886.pixko.internal.TokenAutoRefreshPluginV2
import top.kagg886.pixko.module.user.SimpleMeProfile

/**
 * # Token类型
 * @property ACCESS
 * @property REFRESH
 */
enum class TokenType {
    ACCESS, REFRESH
}

/**
 * # Token存储器
 * @see InMemoryTokenStorage
 */
interface TokenStorage {
    suspend fun getToken(type: TokenType): String?
    suspend fun setToken(type: TokenType, token: String)

    suspend fun getExpireTime(): Long?
    suspend fun setExpireTime(expire: Long?)

    suspend fun getProfile(): SimpleMeProfile?
    suspend fun setProfile(profile: SimpleMeProfile)
}

/**
 * # 将token存入内存的Token存储器
 */
class InMemoryTokenStorage : TokenStorage {
    internal val map = mutableMapOf<TokenType, String>()
    private var expireTime: Long? = null
    private var profile: SimpleMeProfile? = null

    override suspend fun getToken(type: TokenType): String? = map[type]
    override suspend fun setToken(type: TokenType, token: String) {
        map[type] = token
    }

    override suspend fun getExpireTime(): Long? = expireTime
    override suspend fun setExpireTime(expire: Long?) {
        this.expireTime = expire
    }

    override suspend fun getProfile(): SimpleMeProfile? = profile
    override suspend fun setProfile(profile: SimpleMeProfile) {
        this.profile = profile
    }
}

/**
 * # Pixiv账号配置
 * @see PixivAccount
 * @property engine http引擎
 * @property storage token存储器
 * @property language 语言配置，置null则为日文。
 */
class PixivAccountConfig<Engine : HttpClientEngineConfig>(val engine: HttpClientEngineFactory<Engine>) {
    var config: HttpClientConfig<Engine>.() -> Unit = {}

    var storage: TokenStorage = InMemoryTokenStorage()
    var language: String? = "zh-CN"
}


typealias PixivAccount = InternalPixivAccount<*>

/**
 * # PixivAPP Client
 * 内部仅包含程序的核心部分，api定义在[top.kagg886.pixko.module]中
 *
 * @see PixivAccountFactory
 */
class InternalPixivAccount<Engine : HttpClientEngineConfig> internal constructor(
    private val config: PixivAccountConfig<Engine>
) : AutoCloseable {
    internal val storage = config.storage

    internal val client = HttpClient(config.engine) {
        config.config(this)

        install(ContentNegotiation) {
            json(top.kagg886.pixko.internal.json)
        }


        install(TokenAutoRefreshPluginV2) {
            this.storage = this@InternalPixivAccount.storage
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            socketTimeoutMillis = 30000
            connectTimeoutMillis = 30000
        }

        defaultRequest {
            url("https://app-api.pixiv.net/")
            header("Accept-Language", config.language)
            header("Referer", "https://app-api.pixiv.net/")
        }
    }

    override fun close() {
        client.close()
    }
}
