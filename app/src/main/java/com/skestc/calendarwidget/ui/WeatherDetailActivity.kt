package com.skestc.calendarwidget.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.data.AirQuality
import com.skestc.calendarwidget.data.AirSource
import com.skestc.calendarwidget.data.AqiLevel
import com.skestc.calendarwidget.data.CwaCodes
import com.skestc.calendarwidget.data.CwaPeriod
import com.skestc.calendarwidget.data.DayForecast
import com.skestc.calendarwidget.data.HourForecast
import com.skestc.calendarwidget.data.LocationReader
import com.skestc.calendarwidget.data.WeatherCodes
import com.skestc.calendarwidget.data.WeatherDetail
import com.skestc.calendarwidget.data.WeatherStore
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** 點 widget 的天氣列開啟：現在天氣、接下來 12 小時、空氣品質、未來幾天。 */
class WeatherDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendarWidgetTheme {
                WeatherDetailScreen(onBack = ::finish)
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, WeatherDetailActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherDetailScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { WeatherStore(context) }
    var version by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val detail = remember(version) { store.cachedDetail() }
    val air = remember(version) { store.cachedAir() }
    val name = remember(version) { store.cachedName() }
    val fetchedAt = remember(version) { store.fetchedAt() }

    fun refresh() {
        busy = true
        scope.launch {
            updateWeatherNow(context)
            busy = false
            version++
        }
    }

    // 打開時資料太舊（或 GPS 位置變了）就自動更新。
    LaunchedEffect(Unit) {
        if (store.enabled) LocationReader.refreshForWeather(context) { refresh() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name ?: stringResource(R.string.weather_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = ::refresh, enabled = !busy && store.enabled) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = { context.startActivity(Intent(context, WeatherSettingsActivity::class.java)) }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.weather_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (detail == null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(if (store.enabled) R.string.weather_not_fetched else R.string.weather_off_summary),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        if (store.enabled) refresh() else context.startActivity(Intent(context, WeatherSettingsActivity::class.java))
                    }) {
                        Text(stringResource(if (store.enabled) R.string.weather_update_now else R.string.weather_settings))
                    }
                }
            } else {
                WeatherDetailContent(detail, air, fetchedAt)
            }
        }
    }
}

/** 詳細內容本體（測試直接畫這個）。 */
@Composable
internal fun WeatherDetailContent(detail: WeatherDetail, air: AirQuality?, fetchedAt: Long) {
    val context = LocalContext.current
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "now") { NowCard(detail, timeFormat.format(Date(fetchedAt))) }
        if (detail.hours.isNotEmpty()) {
            item(key = "hours-title") { SectionTitle(stringResource(R.string.weather_next_hours)) }
            item(key = "hours") { HourStrip(detail.hours) }
        }
        val periods = detail.cwa?.periods.orEmpty()
        if (periods.isNotEmpty()) {
            item(key = "cwa-title") { SectionTitle(stringResource(R.string.cwa_periods_title)) }
            item(key = "cwa") { CwaPeriodsCard(periods) }
        }
        if (air?.aqi != null) {
            item(key = "air-title") { SectionTitle(stringResource(R.string.air_quality)) }
            item(key = "air") { AirCard(air) }
        }
        item(key = "extra") { ExtraRow(detail) }
        if (detail.days.isNotEmpty()) {
            item(key = "days-title") { SectionTitle(stringResource(R.string.weather_next_days)) }
            item(key = "days") { DaysCard(detail.days) }
        }
        item(key = "source") {
            Text(
                stringResource(R.string.weather_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun NowCard(detail: WeatherDetail, updated: String) {
    val today = detail.today
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(WeatherCodes.emoji(today.code, today.isDay), fontSize = 48.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.weather_temperature, today.temperature),
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        today.description ?: stringResource(WeatherCodes.description(today.code)),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(R.string.weather_high_low, today.low, today.high),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                listOfNotNull(
                    detail.feelsLike?.let { stringResource(R.string.weather_feels_like, it) },
                    detail.humidity?.let { stringResource(R.string.weather_humidity, it) },
                    detail.windKmh?.let { stringResource(R.string.weather_wind, it) },
                    today.rainChance?.let { stringResource(R.string.weather_rain_chance, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (today.weatherEstimated) {
                Text(
                    stringResource(R.string.weather_estimate_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            detail.cwa?.station?.let { station ->
                Text(
                    listOfNotNull(stringResource(R.string.cwa_station, station), detail.cwa.observedAt).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Text(
                stringResource(R.string.weather_updated_at, updated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun HourStrip(hours: List<HourForecast>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(hours, key = { it.time.toString() }) { hour ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).width(44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        if (hour == hours.first()) stringResource(R.string.now) else stringResource(R.string.weather_hour, hour.time.hour),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(WeatherCodes.emoji(hour.code, hour.isDay), fontSize = 22.sp)
                    Text(
                        stringResource(R.string.weather_temperature, hour.temperature),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        hour.rainChance?.let { stringResource(R.string.percent, it) } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** 「環境部 西屯測站 · 2026/11/03 08:00」或「Open-Meteo 模型推估」。 */
@Composable
internal fun airSourceText(air: AirQuality): String = when (air.source) {
    AirSource.MOENV -> listOfNotNull(
        stringResource(R.string.air_source_moenv_station, air.station.orEmpty()),
        air.time,
    ).joinToString(" · ")
    AirSource.OPEN_METEO -> stringResource(R.string.air_source_open_meteo_estimate)
}

@Composable
private fun AirCard(air: AirQuality) {
    val aqi = air.aqi ?: return
    val level = AqiLevel.of(aqi)
    val onLevel = if (level.darkText) Color(0xFF1F1F1F) else Color.White
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(level.color)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(aqi.toString(), color = onLevel, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("AQI · " + stringResource(level.label), style = MaterialTheme.typography.titleMedium)
                    Text(
                        airSourceText(air),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(stringResource(level.advice), style = MaterialTheme.typography.bodyMedium)
            val ozoneUnit = if (air.source == AirSource.MOENV) "ppb" else "μg/m³"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                air.pm25?.let { Metric("PM2.5", format(it), "μg/m³", Modifier.weight(1f)) }
                air.pm10?.let { Metric("PM10", format(it), "μg/m³", Modifier.weight(1f)) }
                air.ozone?.let { Metric("O₃", format(it), ozoneUnit, Modifier.weight(1f)) }
            }
            air.pollutant?.let {
                Text(
                    stringResource(R.string.air_main_pollutant, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun format(value: Double): String =
    if (value == Math.floor(value)) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

@Composable
private fun Metric(label: String, value: String, unit: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 紫外線、日出、日落。 */
@Composable
private fun ExtraRow(detail: WeatherDetail) {
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        detail.uvIndex?.let {
            Metric(stringResource(R.string.weather_uv), format(Math.round(it * 10) / 10.0), stringResource(uvLevel(it)), Modifier.weight(1f))
        }
        detail.sunrise?.let { Metric(stringResource(R.string.weather_sunrise), timeFormat.format(it), "", Modifier.weight(1f)) }
        detail.sunset?.let { Metric(stringResource(R.string.weather_sunset), timeFormat.format(it), "", Modifier.weight(1f)) }
    }
}

/** 紫外線指數的分級（依中央氣象署）。 */
internal fun uvLevel(index: Double): Int = when {
    index < 3 -> R.string.uv_low
    index < 6 -> R.string.uv_moderate
    index < 8 -> R.string.uv_high
    index < 11 -> R.string.uv_very_high
    else -> R.string.uv_extreme
}

/** 氣象署 36 小時預報的三個時段。 */
@Composable
private fun CwaPeriodsCard(periods: List<CwaPeriod>) {
    val locale = Locale.getDefault()
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MdEEE"), locale) }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    SettingsGroup {
        for ((i, period) in periods.withIndex()) {
            if (i > 0) GroupDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(96.dp)) {
                    Text(dayFormat.format(period.start), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${timeFormat.format(period.start)}–${timeFormat.format(period.end)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val wmo = period.code?.let(CwaCodes::toWmo) ?: 2
                Text(WeatherCodes.emoji(wmo, period.start.hour in 6..17), fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(period.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                period.rainChance?.let {
                    Text(
                        stringResource(R.string.percent, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                if (period.low != null && period.high != null) {
                    Text(stringResource(R.string.weather_high_low, period.low, period.high), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun DaysCard(days: List<DayForecast>) {
    val locale = Locale.getDefault()
    val dateFormat = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "Md"), locale)
    }
    SettingsGroup {
        for ((i, day) in days.withIndex()) {
            if (i > 0) GroupDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(72.dp)) {
                    Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        dateFormat.format(day.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(WeatherCodes.emoji(day.code, true), fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(WeatherCodes.description(day.code)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                day.rainChance?.let {
                    Text(
                        stringResource(R.string.percent, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    stringResource(R.string.weather_high_low, day.low, day.high),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
