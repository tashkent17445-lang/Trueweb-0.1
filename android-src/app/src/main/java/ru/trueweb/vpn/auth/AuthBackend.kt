package ru.trueweb.vpn.auth

import ru.trueweb.vpn.api.TrueWebApi

/** Kept as a tiny compatibility facade for MainActivity. */
object AuthBackend {
    fun exchangeCode(code: String): Result<String> = TrueWebApi.exchangeCode(code)
}
