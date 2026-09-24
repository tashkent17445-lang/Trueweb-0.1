package ru.trueweb.vpn.model

import org.json.JSONObject

data class RoutingPolicy(
    val smartAuto: Boolean = true,
    val bypassRu: Boolean = true,
    val excludedPackages: List<String> = emptyList()
) {
    companion object {
        fun fromApi(json: JSONObject): RoutingPolicy {
            val array = json.optJSONArray("excluded_packages")
            val packages = buildList {
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val value = array.optString(i).trim()
                        if (value.isNotBlank()) add(value)
                    }
                }
            }
            return RoutingPolicy(
                smartAuto = json.optBoolean("smart_auto", true),
                bypassRu = json.optBoolean("bypass_ru", true),
                excludedPackages = packages.distinct()
            )
        }
    }
}
