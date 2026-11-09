package com.astral.client

import android.content.Context

data class Module(val id: String, val name: String, val hint: String)

/**
 * Список модулей и их настройки.
 * Пока это только сохранённые значения: на игру они ещё не влияют.
 */
object Modules {
    val all = listOf(
        Module("armor", "Armor HUD", "Броня и прочность на экране"),
        Module("sprint", "Auto Sprint", "Бег без удержания"),
        Module("chunks", "Chunk Borders", "Границы чанков"),
        Module("clock", "Clock", "Время на экране"),
        Module("combo", "Combo Counter", "Счётчик серии ударов"),
        Module("compass", "Compass", "Компас на экране"),
        Module("coords", "Coordinates", "XYZ на экране"),
        Module("cps", "CPS", "Счётчик кликов"),
        Module("fps", "FPS", "Счётчик кадров"),
        Module("hitbox", "Hitbox", "Показ хитбоксов"),
        Module("keys", "Keystrokes", "Нажатия на экране"),
        Module("nightvision", "Night Vision", "Яркость в темноте"),
        Module("ping", "Ping", "Задержка сети"),
        Module("potion", "Potion Status", "Эффекты зелий"),
        Module("sneak", "Toggle Sneak", "Присед по нажатию"),
        Module("zoom", "Zoom", "Приближение камеры"),
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences("astral", Context.MODE_PRIVATE)

    fun isOn(context: Context, id: String): Boolean = prefs(context).getBoolean(id, false)

    fun set(context: Context, id: String, value: Boolean) {
        prefs(context).edit().putBoolean(id, value).apply()
    }

    fun activeCount(context: Context): Int = all.count { isOn(context, it.id) }

    // Настройки модуля: масштаб (%), прозрачность (0-100), HUD, шрифт Minecraft
    fun scale(context: Context, id: String): Int = prefs(context).getInt("${id}_scale", 100)
    fun setScale(context: Context, id: String, v: Int) = prefs(context).edit().putInt("${id}_scale", v).apply()

    fun opacity(context: Context, id: String): Int = prefs(context).getInt("${id}_opacity", 60)
    fun setOpacity(context: Context, id: String, v: Int) = prefs(context).edit().putInt("${id}_opacity", v).apply()

    fun hud(context: Context, id: String): Boolean = prefs(context).getBoolean("${id}_hud", true)
    fun setHud(context: Context, id: String, v: Boolean) = prefs(context).edit().putBoolean("${id}_hud", v).apply()

    fun font(context: Context, id: String): Boolean = prefs(context).getBoolean("${id}_font", true)
    fun setFont(context: Context, id: String, v: Boolean) = prefs(context).edit().putBoolean("${id}_font", v).apply()

    /** Сбросить модуль: выключить и вернуть настройки по умолчанию. */
    fun reset(context: Context, id: String) {
        prefs(context).edit()
            .remove(id)
            .remove("${id}_scale")
            .remove("${id}_opacity")
            .remove("${id}_hud")
            .remove("${id}_font")
            .apply()
    }
}
