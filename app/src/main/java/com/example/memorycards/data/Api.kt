package com.example.memorycards.data

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {

    // 部署后改成你的服务器地址；模拟器访问本机用 http://10.0.2.2:8080
    const val BASE_URL = "http://10.0.2.2:8080"

    interface Callback {
        fun onSuccess(body: String)
        fun onError(code: Int, message: String)
    }

    fun request(path: String, method: String = "GET", jsonBody: String? = null,
                callback: Callback) {
        Thread {
            var success = false
            var payload = ""
            var code = -1
            try {
                val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (jsonBody != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                }
                code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                payload = stream?.bufferedReader()?.use { it.readText() } ?: ""
                success = code in 200..299
                if (!success && code == 401) payload = errorJson("登录已过期，请重新登录")
            } catch (e: Exception) {
                payload = errorJson("网络错误：${e.message ?: "连接失败"}")
            }
            val ok = success
            val body = payload
            val statusCode = code
            Handler(Looper.getMainLooper()).post {
                if (ok) callback.onSuccess(body) else callback.onError(
                    statusCode,
                    runCatching { JSONObject(body).optString("message") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "请求失败($statusCode)"
                )
            }
        }.start()
    }

    private fun errorJson(message: String): String {
        return JSONObject().put("message", message).toString()
    }
}
