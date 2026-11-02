package com.example.memorycards.data

import android.content.Context

object Session {

    private const val PREFS = "session"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"

    fun token(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun username(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_USERNAME, null)

    fun isLoggedIn(context: Context): Boolean = token(context) != null

    fun save(context: Context, token: String, username: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOKEN, token).putString(KEY_USERNAME, username).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
