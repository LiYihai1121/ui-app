package com.qingqi.adskip.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object ServerEndpoint {

    fun isValid(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.scheme == "https" && parsed.host.isNotBlank()
    }
}
