package com.pantauhttp.export

import kotlinx.serialization.Serializable

/** HAR 1.2 structures (subset sufficient for viewers/importers). */
@Serializable
public data class Har(val log: HarLog)

@Serializable
public data class HarLog(
    val version: String = "1.2",
    val creator: HarCreator = HarCreator(),
    val entries: List<HarEntry>,
)

@Serializable
public data class HarCreator(val name: String = "PantauHTTP", val version: String = PANTAU_HTTP_VERSION)

@Serializable
public data class HarEntry(
    val startedDateTime: String,
    val time: Double,
    val request: HarRequest,
    val response: HarResponse,
    val cache: HarCache = HarCache(),
    val timings: HarTimings,
)

@Serializable
public data class HarRequest(
    val method: String,
    val url: String,
    val httpVersion: String = "HTTP/1.1",
    val cookies: List<HarCookie> = emptyList(),
    val headers: List<HarHeader>,
    val queryString: List<HarHeader>,
    val postData: HarPostData? = null,
    val headersSize: Int,
    val bodySize: Int,
)

@Serializable
public data class HarResponse(
    val status: Int,
    val statusText: String,
    val httpVersion: String = "HTTP/1.1",
    val cookies: List<HarCookie> = emptyList(),
    val headers: List<HarHeader>,
    val content: HarContent,
    val redirectURL: String,
    val headersSize: Int,
    val bodySize: Int,
)

@Serializable
public data class HarHeader(val name: String, val value: String)

@Serializable
public data class HarCookie(val name: String, val value: String)

@Serializable
public data class HarPostData(val mimeType: String, val text: String)

@Serializable
public data class HarContent(val size: Int, val mimeType: String, val text: String? = null)

@Serializable
public class HarCache

@Serializable
public data class HarTimings(val send: Double = -1.0, val wait: Double = -1.0, val receive: Double)

internal const val PANTAU_HTTP_VERSION: String = "2.0.1"
