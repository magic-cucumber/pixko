package top.kagg886.pixko.internal

import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import top.kagg886.pixko.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@KtorDsl
class TokenAutoRefreshPluginV2Config {
    lateinit var storage: TokenStorage
}


@OptIn(InternalAPI::class, ExperimentalTime::class)
val TokenAutoRefreshPluginV2 = createClientPlugin("TokenAutoRefreshPluginV2", ::TokenAutoRefreshPluginV2Config) {
    val storage = pluginConfig.storage


    onRequest { request, _ ->
        request.headers["Authorization"] = "Bearer ${storage.getToken(TokenType.ACCESS)}"
    }


    on(Send) { originalRequest ->
        if (originalRequest.url.encodedPath == "/auth/token") {
            return@on proceed(originalRequest)
        }

        val expire = storage.getToken(TokenType.EXPIRE_TIME)?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }
        val tokenHasExpired = expire?.let { Clock.System.now() >= it } ?: false

        if (!tokenHasExpired) {
            val origin = proceed(originalRequest)
            if (origin.response.status == HttpStatusCode.OK) return@on origin

            val body = origin.response.bodyAsBytes().decodeToString()

            val errorMessage = json.parseToJsonElement(body)
                .jsonObject["error"]
                ?.jsonObject
                ?.get("message")
                ?.jsonPrimitive
                ?.content


            if (origin.response.status != HttpStatusCode.BadRequest || errorMessage?.contains("OAuth") != true) {
                throw PixivException(origin.request.url.toString(), body)
            }
        }



        val refreshToken = storage.getToken(TokenType.REFRESH) ?: throw InvaidRefreshTokenException()
        val resp = client.post("https://oauth.secure.pixiv.net/auth/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("client_id", pixiv_client_id)
                        append("client_secret", pixiv_client_secret)
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshToken)
                        append("include_policy", "true")
                    }
                )
            )
        }

        val refreshResponse = resp.body<JsonElement>().jsonObject
        val json = refreshResponse["response"]?.jsonObject ?: refreshResponse

        val accessToken = json["access_token"]?.jsonPrimitive?.content
            ?: throw InvaidRefreshTokenException()
        val newRefreshToken = json["refresh_token"]?.jsonPrimitive?.content
            ?: throw InvaidRefreshTokenException()
        val expiresIn = json["expires_in"]?.jsonPrimitive?.longOrNull
            ?: throw InvaidRefreshTokenException()

        storage.setToken(TokenType.ACCESS, accessToken)
        storage.setToken(TokenType.REFRESH, newRefreshToken)
        storage.setToken(
            TokenType.EXPIRE_TIME,
            Clock.System.now().plus(expiresIn.seconds).toEpochMilliseconds().toString()
        )

        val newRequest = HttpRequestBuilder().takeFromWithExecutionContext(originalRequest).apply {
            headers["Authorization"] = "Bearer $accessToken"
        }
        return@on proceed(newRequest);
    }
}
