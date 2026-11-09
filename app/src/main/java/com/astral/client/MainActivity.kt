package com.astral.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.random.Random

const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"

private val Bg = Color(0xFF0B0A14)
private val Card = Color(0xFF15132A)
private val Border = Color(0xFF2E2A5C)
private val TextMain = Color(0xFFF2F0FF)
private val TextMuted = Color(0xFFA9A5CC)
private val Accent = Color(0xFF8B7CFF)
private val Cyan = Color(0xFF4FD1E8)
private val Warn = Color(0xFFFFB86B)

class MainActivity : ComponentActivity() {

    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        setContent {
            // Читаем tick, чтобы экран обновлялся при возврате из настроек.
            @Suppress("UNUSED_VARIABLE") val refresh = tick
            LauncherScreen(
                installed = isMinecraftInstalled(),
                overlayOk = Settings.canDrawOverlays(this),
                usageOk = Access.hasUsage(this),
                active = Modules.activeCount(this),
                onLaunch = ::onLaunch,
                onOverlay = ::openOverlaySettings,
                onUsage = ::openUsageSettings,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        tick++
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun isMinecraftInstalled(): Boolean = try {
        packageManager.getPackageInfo(MINECRAFT_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun openOverlaySettings() {
        Toast.makeText(this, "Включи «Astral Client» в списке", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun openUsageSettings() {
        Toast.makeText(this, "Найди «Astral Client» и включи доступ", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun onLaunch() {
        when {
            !isMinecraftInstalled() ->
                Toast.makeText(this, "Minecraft не установлен", Toast.LENGTH_LONG).show()
            !Settings.canDrawOverlays(this) -> openOverlaySettings()
            !Access.hasUsage(this) -> openUsageSettings()
            else -> {
                ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
                val intent = packageManager.getLaunchIntentForPackage(MINECRAFT_PACKAGE)
                if (intent != null) startActivity(intent)
            }
        }
    }
}

@Composable
private fun LauncherScreen(
    installed: Boolean,
    overlayOk: Boolean,
    usageOk: Boolean,
    active: Int,
    onLaunch: () -> Unit,
    onOverlay: () -> Unit,
    onUsage: () -> Unit,
) {
    val ready = installed && overlayOk && usageOk

    Box(Modifier.fillMaxSize().background(Bg)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Accent.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(size.width * 0.78f, size.height * 0.30f),
                    radius = size.height * 1.15f,
                )
            )
            val rnd = Random(7)
            repeat(70) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f + rnd.nextFloat() * 0.45f),
                    radius = 0.6f + rnd.nextFloat() * 1.4f,
                    center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                )
            }
        }

        Row(
            Modifier
                .fillMaxSize()
                .displayCutoutPadding()
                .padding(horizontal = 36.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarLogo(26.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("ASTRAL CLIENT", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                }
                Spacer(Modifier.height(14.dp))
                Text("Minecraft", color = TextMain, fontSize = 46.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
                Text("Bedrock Edition", color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(22.dp))

                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.horizontalGradient(listOf(Accent, Cyan)))
                        .clickable(onClick = onLaunch)
                        .height(58.dp)
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayIcon()
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (ready) "Запустить" else "Продолжить настройку",
                            color = Bg,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (ready) "Иконка появится поверх игры" else "Нужно выдать пару разрешений справа",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.width(28.dp))

            Column(
                Modifier
                    .width(280.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Card)
                    .border(1.dp, Border, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Подготовка", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                CheckRow("Minecraft Bedrock", if (installed) "Установлена" else "Не найдена", installed, null)
                CheckRow("Поверх других окон", "Для иконки и меню", overlayOk, onOverlay)
                CheckRow("Статистика использования", "Чтобы скрывать иконку вне игры", usageOk, onUsage)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Модулей включено", color = TextMuted, fontSize = 12.sp)
                    Text("$active из ${Modules.all.size}", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CheckRow(title: String, subtitle: String, ok: Boolean, onGrant: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatusBadge(ok)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        if (!ok && onGrant != null) {
            Text(
                "Выдать",
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Accent, RoundedCornerShape(10.dp))
                    .clickable(onClick = onGrant)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun StatusBadge(ok: Boolean) {
    val tint = if (ok) Cyan else Warn
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
            if (ok) {
                val p = Path().apply {
                    moveTo(w * 0.28f, w * 0.52f)
                    lineTo(w * 0.44f, w * 0.68f)
                    lineTo(w * 0.74f, w * 0.34f)
                }
                drawPath(p, tint, style = stroke)
            } else {
                drawLine(tint, Offset(w * 0.5f, w * 0.26f), Offset(w * 0.5f, w * 0.55f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                drawCircle(tint, radius = w * 0.06f, center = Offset(w * 0.5f, w * 0.72f))
            }
        }
    }
}

@Composable
private fun PlayIcon() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val p = Path().apply {
            moveTo(w * 0.15f, w * 0.05f)
            lineTo(w * 0.95f, w * 0.5f)
            lineTo(w * 0.15f, w * 0.95f)
            close()
        }
        drawPath(p, Bg)
    }
}

@Composable
private fun StarLogo(logoSize: Dp) {
    Canvas(Modifier.size(logoSize)) {
        val w = size.width
        val c = w / 2f
        val r = w * 0.45f
        val k = 0.22f
        val star = Path().apply {
            moveTo(c, c - r)
            lineTo(c + r * k, c - r * k)
            lineTo(c + r, c)
            lineTo(c + r * k, c + r * k)
            lineTo(c, c + r)
            lineTo(c - r * k, c + r * k)
            lineTo(c - r, c)
            lineTo(c - r * k, c - r * k)
            close()
        }
        drawPath(star, Accent)
        rotate(-25f, Offset(c, c)) {
            drawOval(
                color = Cyan,
                topLeft = Offset(c - w * 0.44f, c - w * 0.16f),
                size = Size(w * 0.88f, w * 0.32f),
                style = Stroke(width = w * 0.05f),
            )
        }
    }
}
