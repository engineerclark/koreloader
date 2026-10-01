package org.mmclark.koreloader.infrastructure.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders

/** HTTP client for catalog and package downloads. */
internal fun createHttpClient(userAgent: String): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 20_000
        socketTimeoutMillis = 60_000 // between packets; large downloads may take minutes overall
    }
    defaultRequest {
        headers.append(HttpHeaders.UserAgent, userAgent)
    }
}
