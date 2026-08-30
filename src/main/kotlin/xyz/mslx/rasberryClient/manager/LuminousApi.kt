package xyz.mslx.rasberryClient.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.mslx.rasberryClient.model.SMPData
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

enum class ApiError { NETWORK, UNAUTHORIZED, FORBIDDEN, QUOTA, CONFLICT, NOT_FOUND, BAD_REQUEST, SERVER, OTHER }

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val kind: ApiError, val message: String) : ApiResult<Nothing>()
}

@Serializable
data class SmpSettings(
    val version: String = "1.21",
    val world_type: String = "default",
    val hardcore: Boolean = false,
    val max_players: Int = 20,
    val motd: String = ""
)

@Serializable
data class SmpCreateBody(
    @SerialName("smp_id") val smpId: String,
    val name: String,
    val settings: SmpSettings
)

@Serializable
data class SmpServerRes(
    @SerialName("smp_id") val smpId: String? = null,
    val name: String? = null,
    val status: String? = null,
    @SerialName("ip_port") val ipPort: String? = null,
    val error: String? = null
)

fun SMPData.toSmpSettings(): SmpSettings = SmpSettings(
    version = serverVersion,
    world_type = worldType.name.lowercase(),
    hardcore = hardcore,
    max_players = maxPlayers,
    motd = smpName
)

// HTTP client for the Luminous panel /org/{token}/... API (no new dependencies:
// java.net.http.HttpClient + kotlinx.serialization which were already bundled).
class LuminousApi(private val baseUrl: String, private val orgToken: String) {

    private val json = Json { ignoreUnknownKeys = true }

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()

    // blocking calls — always invoke from pluginScope coroutines (IO pool)
    fun createSmp(smpId: String, name: String, settings: SmpSettings): ApiResult<SmpServerRes> =
        send("POST", "/org/$orgToken/smp/servers", json.encodeToString(SmpCreateBody(smpId, name, settings)))

    fun getSmpStatus(smpId: String): ApiResult<SmpServerRes> =
        send("GET", "/org/$orgToken/smp/servers/$smpId", null)

    fun deleteSmp(smpId: String): ApiResult<SmpServerRes> =
        send("DELETE", "/org/$orgToken/smp/servers/$smpId", null)

    private fun send(method: String, path: String, body: String?): ApiResult<SmpServerRes> {
        val response: HttpResponse<String> = try {
            val builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
            when (method) {
                "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body ?: "{}"))
                "DELETE" -> builder.DELETE()
                else -> builder.GET()
            }
            http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            return ApiResult.Failure(ApiError.NETWORK, "cannot reach panel: ${e.message}")
        }

        val payload = try {
            json.decodeFromString<SmpServerRes>(response.body())
        } catch (e: Exception) {
            SmpServerRes(error = response.body().take(200))
        }

        return when {
            response.statusCode() in 200..299 -> ApiResult.Success(payload)
            response.statusCode() == 401 -> ApiResult.Failure(ApiError.UNAUTHORIZED, payload.error ?: "invalid org_token")
            response.statusCode() == 403 -> ApiResult.Failure(ApiError.FORBIDDEN, payload.error ?: "no active enterprise license")
            response.statusCode() == 409 -> ApiResult.Failure(ApiError.QUOTA, payload.error ?: "concurrent SMP limit reached")
            response.statusCode() == 404 -> ApiResult.Failure(ApiError.NOT_FOUND, payload.error ?: "not found")
            response.statusCode() == 400 -> ApiResult.Failure(ApiError.BAD_REQUEST, payload.error ?: "bad request")
            response.statusCode() == 502 -> ApiResult.Failure(ApiError.SERVER, payload.error ?: "host node rejected the job")
            else -> ApiResult.Failure(ApiError.OTHER, "HTTP ${response.statusCode()}: ${payload.error ?: payload.toString().take(120)}")
        }
    }
}