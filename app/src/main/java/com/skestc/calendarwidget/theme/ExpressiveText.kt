package com.skestc.calendarwidget.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import kotlin.math.ceil

/**
 * 大號數字（日期、倒數）用的圓潤粗體：RemoteViews 不能指定 asset 字型，
 * 所以把文字畫成白色點陣圖，再用 ColorFilter 染色（日夜色仍由系統切換）。
 *
 * 字型是 Google Sans Flex（OFL 1.1）的靜態子集：wght 700、ROND 100、只含 ASCII，
 * 見 assets/fonts/OFL.txt。使用者選了襯線／等寬字型時改回一般文字，以免風格不一致。
 */
@Composable
fun ExpressiveText(
    text: String,
    size: Int,
    color: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
) {
    val style = LocalWidgetStyle.current
    val context = LocalContext.current
    val bitmap = if (style.roundedNumerals) ExpressiveText.render(context, text, style.sp(size).value) else null
    if (bitmap == null) {
        Text(text = text, modifier = modifier, style = style.text(size, color, FontWeight.Bold))
    } else {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = text,
            colorFilter = ColorFilter.tint(color),
            modifier = modifier,
        )
    }
}

object ExpressiveText {
    private const val FONT_ASSET = "fonts/google_sans_flex_rounded_bold.ttf"

    @Volatile
    private var typeface: Typeface? = null

    private fun typeface(context: Context): Typeface? = typeface ?: runCatching {
        Typeface.createFromAsset(context.applicationContext.assets, FONT_ASSET)
    }.getOrNull()?.also { typeface = it }

    /** 字型只有 ASCII；其他字元（例如中文）交給系統字型。 */
    fun supports(text: String): Boolean = text.isNotEmpty() && text.all { it.code in 0x20..0x7E }

    /** 把 [text] 畫成白色點陣圖；字型不可用或含非 ASCII 字元時回傳 null。 */
    fun render(context: Context, text: String, sizeSp: Float): Bitmap? {
        if (!supports(text)) return null
        val face = typeface(context) ?: return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, context.resources.displayMetrics)
            color = android.graphics.Color.WHITE
            // 等寬數字：時間跳動時寬度不變。
            fontFeatureSettings = "'tnum'"
        }
        val fm = paint.fontMetrics
        val width = ceil(paint.measureText(text)).toInt().coerceAtLeast(1)
        val height = ceil(fm.descent - fm.ascent).toInt().coerceAtLeast(1)
        return runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawText(text, 0f, -fm.ascent, paint)
            }
        }.getOrNull()
    }
}
