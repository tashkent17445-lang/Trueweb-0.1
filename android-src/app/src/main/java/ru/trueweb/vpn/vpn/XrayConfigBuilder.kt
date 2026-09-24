package ru.trueweb.vpn.vpn

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import ru.trueweb.vpn.model.VpnServer

/**
 * Builds one Xray outbound at a time. TrueWeb 0.8 intentionally removed
 * client-side server balancing: the phone connects to exactly one inbound,
 * while routing and load balancing behind it stay server-side.
 */
object XrayConfigBuilder {
    private const val VPN_OUTBOUND = "tw-vpn"

    data class BuiltConfig(
        val json: String,
        val selectedName: String,
        val nodeCount: Int
    )

    fun build(
        server: VpnServer,
        smartAuto: Boolean = true,
        bypassRu: Boolean = true
    ): BuiltConfig {
        require(server.available && !server.uri.isNullOrBlank()) { "VPN-вход недоступен" }
        require(server.protocol.equals("vless", ignoreCase = true)) { "Поддерживается только VLESS" }

        val vpnOutbound = parseVlessOutbound(server, VPN_OUTBOUND)
        val root = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("policy", JSONObject().put(
                "levels", JSONObject().put(
                    "8", JSONObject()
                        .put("handshake", 4)
                        .put("connIdle", 300)
                        .put("uplinkOnly", 1)
                        .put("downlinkOnly", 1)
                )
            ))
            .put("inbounds", JSONArray().put(buildTunInbound()))
            .put("outbounds", buildOutbounds(vpnOutbound))
            .put("routing", buildRouting(smartAuto, bypassRu))
            .put("dns", buildDns())

        return BuiltConfig(
            json = root.toString(),
            selectedName = "TrueWeb",
            nodeCount = 1
        )
    }

    private fun buildTunInbound(): JSONObject = JSONObject()
        .put("tag", "tun")
        .put("protocol", "tun")
        .put("settings", JSONObject()
            .put("name", "xray0")
            .put("MTU", 1500)
            .put("userLevel", 8)
        )
        .put("sniffing", JSONObject()
            .put("enabled", true)
            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
        )

    private fun buildOutbounds(vpnOutbound: JSONObject): JSONArray = JSONArray().apply {
        put(vpnOutbound)
        put(JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")
            .put("streamSettings", JSONObject().put(
                "sockopt", JSONObject()
                    .put("domainStrategy", "UseIP")
                    .put("happyEyeballs", JSONObject().put("tryDelayMs", 250).put("interleave", 2))
            ))
        )
        put(JSONObject()
            .put("tag", "block")
            .put("protocol", "blackhole")
            .put("settings", JSONObject().put("response", JSONObject().put("type", "http")))
        )
    }

    private fun buildRouting(smartAuto: Boolean, bypassRu: Boolean): JSONObject {
        val routing = JSONObject().put(
            "domainStrategy",
            if (smartAuto && bypassRu) "IPIfNonMatch" else "AsIs"
        )
        val rules = JSONArray()

        if (smartAuto) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("tun"))
                    .put("ip", JSONArray().put("geoip:private"))
                    .put("outboundTag", "direct")
            )

            if (bypassRu) {
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("inboundTag", JSONArray().put("tun"))
                        .put("domain", JSONArray()
                            .put("geosite:category-ru")
                            .put("regexp:\\.(ru|su|xn--p1ai)$")
                        )
                        .put("outboundTag", "direct")
                )
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("inboundTag", JSONArray().put("tun"))
                        .put("ip", JSONArray().put("geoip:ru"))
                        .put("outboundTag", "direct")
                )
            }
        }

        rules.put(
            JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray().put("tun"))
                .put("network", "tcp,udp")
                .put("outboundTag", VPN_OUTBOUND)
        )
        routing.put("rules", rules)
        return routing
    }

    private fun buildDns(): JSONObject = JSONObject()
        .put("queryStrategy", "UseIPv4")
        .put("servers", JSONArray()
            .put(JSONObject()
                .put("address", "77.88.8.8")
                .put("domains", JSONArray()
                    .put("geosite:category-ru")
                    .put("regexp:\\.(ru|su|xn--p1ai)$")
                )
                .put("skipFallback", true)
            )
            .put("1.1.1.1")
            .put("8.8.8.8")
        )

    private fun parseVlessOutbound(server: VpnServer, tag: String): JSONObject {
        val raw = server.uri ?: error("Пустая VLESS-ссылка")
        val uri = Uri.parse(raw)
        require(uri.scheme.equals("vless", ignoreCase = true)) { "Поддерживается только VLESS" }

        val uuid = (uri.userInfo ?: "").substringBefore(':').trim()
        val host = uri.host?.trim().orEmpty()
        val port = uri.port.takeIf { it > 0 } ?: 443
        require(uuid.isNotBlank()) { "В VLESS-ссылке нет UUID" }
        require(host.isNotBlank()) { "В VLESS-ссылке нет адреса сервера" }

        fun q(name: String): String? = uri.getQueryParameter(name)?.trim()?.takeIf { it.isNotBlank() }

        val security = q("security") ?: "none"
        val network = q("type") ?: q("network") ?: "tcp"
        val encryption = q("encryption") ?: "none"
        val flow = q("flow")

        val settings = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("id", uuid)
            .put("encryption", encryption)
            .put("level", 8)
        flow?.let { settings.put("flow", it) }

        val stream = JSONObject().put("network", network)

        when (network.lowercase()) {
            "xhttp" -> {
                val xhttp = JSONObject()
                    .put("path", q("path") ?: "/")
                q("host")?.let { xhttp.put("host", it) }
                q("mode")?.let { xhttp.put("mode", it) }
                q("extra")?.let { extraRaw ->
                    val parsedExtra = runCatching { JSONTokener(extraRaw).nextValue() }.getOrNull()
                        ?: throw IllegalArgumentException("Некорректный XHTTP extra")
                    xhttp.put("extra", parsedExtra)
                }
                stream.put("xhttpSettings", xhttp)
            }
            "ws", "websocket" -> {
                stream.put("network", "ws")
                stream.put("wsSettings", JSONObject()
                    .put("path", q("path") ?: "/")
                    .apply {
                        q("host")?.let { hostValue -> put("host", hostValue) }
                    }
                )
            }
            "grpc" -> {
                stream.put("grpcSettings", JSONObject()
                    .put("serviceName", q("serviceName") ?: q("service_name") ?: "")
                    .apply {
                        q("authority")?.let { put("authority", it) }
                        q("mode")?.let { put("multiMode", it.equals("multi", true)) }
                    }
                )
            }
            "httpupgrade" -> {
                stream.put("httpupgradeSettings", JSONObject()
                    .put("path", q("path") ?: "/")
                    .apply { q("host")?.let { put("host", it) } }
                )
            }
            "h2", "http" -> {
                stream.put("network", "h2")
                stream.put("httpSettings", JSONObject()
                    .put("path", q("path") ?: "/")
                    .apply {
                        q("host")?.let { value ->
                            put("host", JSONArray().apply {
                                value.split(',').map(String::trim).filter(String::isNotBlank).forEach(::put)
                            })
                        }
                    }
                )
            }
            "tcp", "raw" -> {
                stream.put("network", if (network.equals("raw", true)) "raw" else "tcp")
            }
            else -> throw IllegalArgumentException("Транспорт '$network' пока не поддерживается")
        }

        when (security.lowercase()) {
            "reality" -> {
                val reality = JSONObject()
                q("sni")?.let { reality.put("serverName", it) }
                q("fp")?.let { reality.put("fingerprint", it) }
                q("pbk")?.let { reality.put("publicKey", it) }
                q("sid")?.let { reality.put("shortId", it) }
                q("spx")?.let { reality.put("spiderX", it) }
                q("pqv")?.let { reality.put("mldsa65Verify", it) }
                q("alpn")?.let { alpn ->
                    reality.put("alpn", JSONArray().apply {
                        alpn.split(',').map(String::trim).filter(String::isNotBlank).forEach(::put)
                    })
                }
                require(reality.optString("publicKey").isNotBlank()) { "REALITY: нет public key (pbk)" }
                stream.put("security", "reality")
                stream.put("realitySettings", reality)
            }
            "tls" -> {
                val tls = JSONObject()
                q("sni")?.let { tls.put("serverName", it) }
                q("fp")?.let { tls.put("fingerprint", it) }
                q("alpn")?.let { alpn ->
                    tls.put("alpn", JSONArray().apply {
                        alpn.split(',').map(String::trim).filter(String::isNotBlank).forEach(::put)
                    })
                }
                q("pcs")?.let { tls.put("pinnedPeerCertSha256", it) }
                q("vcn")?.let { tls.put("verifyPeerCertByName", it) }
                stream.put("security", "tls")
                stream.put("tlsSettings", tls)
            }
            "none", "" -> stream.put("security", "none")
            else -> throw IllegalArgumentException("Security '$security' пока не поддерживается")
        }

        // XHTTP already multiplexes requests internally; Xray/v2rayNG also disables mux here.
        return JSONObject()
            .put("tag", tag)
            .put("protocol", "vless")
            .put("settings", settings)
            .put("streamSettings", stream)
            .put("mux", JSONObject().put("enabled", false).put("concurrency", -1))
    }
}
