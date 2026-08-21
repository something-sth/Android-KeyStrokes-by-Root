package com.something.keystrokes.config

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * KeyStrokes 配置仓库。
 *
 * 配置独立于 Compose UI 和悬浮窗存在，后续 Overlay 只需要读取当前配置即可。
 */
class ConfigRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun loadConfigs(): List<KeyStrokesConfig> {
        val raw = prefs.getString(CONFIGS_KEY, null)
            ?: return listOf(defaultConfig())

        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<KeyStrokesConfig>()

            for (index in 0 until array.length()) {
                result += fromJson(array.getJSONObject(index))
            }

            if (result.none { it.id == DEFAULT_ID }) {
                result.add(0, defaultConfig())
            }

            result
        } catch (_: Exception) {
            listOf(defaultConfig())
        }
    }

    fun getActiveConfigId(): String {
        return prefs.getString(ACTIVE_CONFIG_KEY, DEFAULT_ID) ?: DEFAULT_ID
    }

    fun saveConfigs(configs: List<KeyStrokesConfig>) {
        val normalized = if (configs.any { it.id == DEFAULT_ID }) {
            configs
        } else {
            listOf(defaultConfig()) + configs
        }

        val array = JSONArray()
        normalized.forEach { array.put(toJson(it)) }

        prefs.edit()
            .putString(CONFIGS_KEY, array.toString(2))
            .apply()
    }

    fun setActiveConfig(id: String) {
        prefs.edit()
            .putString(ACTIVE_CONFIG_KEY, id)
            .apply()
    }

    fun createFrom(
        source: KeyStrokesConfig,
        name: String,
        description: String
    ): KeyStrokesConfig {
        return source.copyAs(
            newId = UUID.randomUUID().toString(),
            newName = name,
            newDescription = description
        )
    }

    fun resetDefault(): KeyStrokesConfig {
        return defaultConfig()
    }

    fun createDefaultListIfNeeded(): List<KeyStrokesConfig> {
        val configs = loadConfigs()
        saveConfigs(configs)
        if (configs.none { it.id == getActiveConfigId() }) {
            setActiveConfig(DEFAULT_ID)
        }
        return configs
    }

    private fun defaultConfig(): KeyStrokesConfig {
        return KeyStrokesConfig(
            id = DEFAULT_ID,
            name = "Default",
            description = "KeyStrokes 默认配置",
            builtIn = true
        )
    }

    private fun toJson(config: KeyStrokesConfig): JSONObject {
        return JSONObject().apply {
            put("id", config.id)
            put("name", config.name)
            put("description", config.description)
            put("builtIn", config.builtIn)
            put("overlayWidth", config.overlayWidth)
            put("overlayHeight", config.overlayHeight)
            put("uiScalePercent", config.uiScalePercent)
            put("opacity", config.opacity)
            put("animationEnabled", config.animationEnabled)
            put("keySize", config.keySize.toDouble())
            put("keyGap", config.keyGap.toDouble())
            put("normalColor", config.normalColor)
            put("pressedColor", config.pressedColor)
            put("textColor", config.textColor)
            put("pressedTextColor", config.pressedTextColor)
            put("cornerRadiusEnabled", config.cornerRadiusEnabled)
            put("cornerRadius", config.cornerRadius.toDouble())
        }
    }

    private fun fromJson(json: JSONObject): KeyStrokesConfig {
        return KeyStrokesConfig(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", "未命名配置"),
            description = json.optString("description", ""),
            builtIn = json.optBoolean("builtIn", false),
            overlayWidth = json.optInt("overlayWidth", 300),
            overlayHeight = json.optInt("overlayHeight", 420),
            uiScalePercent = json.optInt("uiScalePercent", 100).coerceIn(50, 200),
            opacity = json.optInt("opacity", 70).coerceIn(20, 100),
            animationEnabled = json.optBoolean("animationEnabled", true),
            keySize = json.optDouble("keySize", 80.0).toFloat(),
            keyGap = json.optDouble("keyGap", 10.0).toFloat(),
            normalColor = json.optLong("normalColor", 0xB4000000),
            pressedColor = json.optLong("pressedColor", 0xB4FFFFFF),
            textColor = json.optLong("textColor", 0xFFFFFFFF),
            pressedTextColor = json.optLong("pressedTextColor", 0xFF000000),
            cornerRadiusEnabled = json.optBoolean("cornerRadiusEnabled", false),
            cornerRadius = json.optDouble("cornerRadius", 0.0).toFloat().coerceIn(0f, 50f)
        )
    }

    companion object {
        private const val PREFS_NAME = "keystrokes_configs"
        private const val CONFIGS_KEY = "configs"
        private const val ACTIVE_CONFIG_KEY = "active_config_id"
        const val DEFAULT_ID = "default"
    }
}
