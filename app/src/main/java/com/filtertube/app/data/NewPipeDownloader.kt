package com.filtertube.app.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloader של NewPipeExtractor — כל בקשות HTTP של NewPipe עוברות דרך כאן.
 *
 * משתמש ב-OkHttp עם user-agent של Chrome כדי להיראות כמו דפדפן רגיל.
 */
class NewPipeDownloader private constructor() : Downloader() {

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11; SM-G973F) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        @Volatile
        private var INSTANCE: NewPipeDownloader? = null

        fun getInstance(): NewPipeDownloader =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NewPipeDownloader().also { INSTANCE = it }
            }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = dataToSend?.toRequestBody()

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, requestBody)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        for ((key, values) in headers.entries) {
            requestBuilder.removeHeader(key)
            for (value in values) {
                requestBuilder.addHeader(key, value)
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBodyToReturn = response.body?.string()
        val latestUrl = response.request.url.toString()

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBodyToReturn,
            latestUrl,
        )
    }
}
