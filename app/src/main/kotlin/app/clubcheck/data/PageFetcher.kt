package app.clubcheck.data

import app.clubcheck.core.parsePageMeta
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Reads a club page's title and summary. Offline or failing: returns nulls, and the URL is kept for a later retry. */
class PageFetcher(private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS).build()) {
    fun fetch(normalisedUrl: String): Pair<String?, String?> = try {
        client.newCall(Request.Builder().url("https://$normalisedUrl").build()).execute().use { r ->
            if (!r.isSuccessful) null to null else parsePageMeta(r.body?.source()?.let { s -> s.request(256 * 1024); s.buffer.clone().readUtf8() } ?: "")
        }
    } catch (e: java.io.IOException) { null to null }
}
