package ru.trueweb.vpn

object AppConfig {
    // Mobile API is served by the existing TrueWeb aiohttp backend through the
    // same HTTPS bridge that already exposes /open-app.
    const val API_BASE = "https://trueweb24.ru:8443/api/mobile"

    // The backend starts Telegram OIDC and redirects back to the app through
    // trueweb://auth/callback?code=ONE_TIME_CODE.
    const val TELEGRAM_AUTH_START = "$API_BASE/auth/telegram/start"

    // Backend redirect keeps the current MTProto host/port/secret out of the APK.
    const val TELEGRAM_PROXY_URL = "$API_BASE/proxy/open"

    const val TELEGRAM_GROUP_URL = "https://t.me/proxi_vpn_bs"
    const val SUPPORT_URL = "https://t.me/TrueWebHelp"

    const val APP_UPDATE_MANIFEST_URL = "https://trueweb24.ru:8443/android/latest.json"

    const val PRIVACY_URL = "https://trueweb24.ru/legal/privacy"
    const val TERMS_URL = "https://trueweb24.ru/legal/terms"
    const val RULES_URL = "https://trueweb24.ru/legal/rules"
    const val DELETION_URL = "https://trueweb24.ru/legal/deletion"
}
