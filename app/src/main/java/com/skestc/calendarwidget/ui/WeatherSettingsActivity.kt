package com.skestc.calendarwidget.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.data.AirSource
import com.skestc.calendarwidget.data.FetchResult
import com.skestc.calendarwidget.data.LocationReader
import com.skestc.calendarwidget.data.WeatherLocationMode
import com.skestc.calendarwidget.data.WeatherPlaces
import com.skestc.calendarwidget.data.WeatherSource
import com.skestc.calendarwidget.data.WeatherStore
import com.skestc.calendarwidget.ui.theme.CalendarWidgetTheme
import com.skestc.calendarwidget.widget.WeatherWorker
import com.skestc.calendarwidget.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.util.Date

/** 天氣設定：地點（關閉／跟隨 GPS／自訂縣市）與空氣品質的資料來源。 */
class WeatherSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendarWidgetTheme {
                WeatherSettingsScreen(onBack = ::finish)
            }
        }
    }
}

/** 前景更新一次：GPS 模式先取得目前位置，抓完更新所有 widget。 */
internal suspend fun updateWeatherNow(context: Context): FetchResult? {
    val app = context.applicationContext
    val store = WeatherStore(app)
    WeatherWorker.sync(app)
    val result = if (store.enabled) {
        if (store.mode == WeatherLocationMode.GPS) {
            LocationReader.current(app)?.let { store.saveGps(it.latitude, it.longitude) }
        }
        store.fetch()
    } else {
        null
    }
    WidgetUpdater.refreshAll(app)
    return result
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WeatherSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { WeatherStore(context) }
    var mode by remember { mutableStateOf(store.mode) }
    var place by remember { mutableStateOf(store.place) }
    var source by remember { mutableStateOf(store.airSource) }
    var key by remember { mutableStateOf(store.moenvKey) }
    var weatherSource by remember { mutableStateOf(store.weatherSource) }
    var cwaKey by remember { mutableStateOf(store.cwaKey) }
    var busy by remember { mutableStateOf(false) }
    // 更新完重新讀狀態。
    var version by remember { mutableIntStateOf(0) }

    fun update() {
        busy = true
        scope.launch {
            val result = updateWeatherNow(context)
            busy = false
            version++
            when (result) {
                FetchResult.WeatherFailed -> R.string.weather_fetch_failed
                FetchResult.NoLocation -> R.string.weather_no_location
                else -> null
            }?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            mode = WeatherLocationMode.GPS
            store.mode = mode
            update()
        } else {
            Toast.makeText(context, R.string.weather_gps_denied, Toast.LENGTH_LONG).show()
        }
    }

    fun chooseMode(value: WeatherLocationMode) {
        if (value == WeatherLocationMode.GPS && !LocationReader.hasPermission(context)) {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return
        }
        // 第一次選自訂縣市時先帶入臺北市，之後可以改。
        if (value == WeatherLocationMode.PLACE && place == null) {
            place = WeatherPlaces.all.first { it.id == "taipei" }
            store.place = place
        }
        mode = value
        store.mode = value
        update()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weather_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.weather_section_location))
            SettingsGroup {
                ChoiceRow(
                    title = stringResource(R.string.weather_off),
                    selected = mode == WeatherLocationMode.OFF,
                ) { chooseMode(WeatherLocationMode.OFF) }
                GroupDivider()
                ChoiceRow(
                    title = stringResource(R.string.weather_location_gps),
                    subtitle = stringResource(R.string.weather_location_gps_hint),
                    selected = mode == WeatherLocationMode.GPS,
                ) { chooseMode(WeatherLocationMode.GPS) }
                GroupDivider()
                ChoiceRow(
                    title = stringResource(R.string.weather_location_place),
                    subtitle = place?.name(),
                    selected = mode == WeatherLocationMode.PLACE,
                ) { chooseMode(WeatherLocationMode.PLACE) }
                if (mode == WeatherLocationMode.PLACE) {
                    FlowRow(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (option in WeatherPlaces.all) {
                            FilterChip(
                                selected = place?.id == option.id,
                                onClick = {
                                    place = option
                                    store.place = option
                                    update()
                                },
                                label = { Text(option.name()) },
                            )
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.weather_source_title))
            SettingsGroup {
                ChoiceRow(
                    title = stringResource(R.string.air_source_open_meteo),
                    subtitle = stringResource(R.string.weather_source_open_meteo_hint),
                    selected = weatherSource == WeatherSource.OPEN_METEO,
                ) {
                    weatherSource = WeatherSource.OPEN_METEO
                    store.weatherSource = weatherSource
                    if (store.enabled) update()
                }
                GroupDivider()
                ChoiceRow(
                    title = stringResource(R.string.weather_source_cwa),
                    subtitle = stringResource(R.string.weather_source_cwa_hint),
                    selected = weatherSource == WeatherSource.CWA,
                ) {
                    weatherSource = WeatherSource.CWA
                    store.weatherSource = weatherSource
                    if (store.enabled && cwaKey.isNotBlank()) update()
                }
                if (weatherSource == WeatherSource.CWA) {
                    KeyEditor(
                        value = cwaKey,
                        onValueChange = { cwaKey = it },
                        label = stringResource(R.string.cwa_key_label),
                        steps = stringResource(R.string.cwa_key_steps),
                        site = CWA_SITE,
                        busy = busy,
                        onSave = {
                            store.cwaKey = cwaKey
                            update()
                        },
                    )
                }
            }

            SectionTitle(stringResource(R.string.air_source_title))
            SettingsGroup {
                ChoiceRow(
                    title = stringResource(R.string.air_source_open_meteo),
                    subtitle = stringResource(R.string.air_source_open_meteo_hint),
                    selected = source == AirSource.OPEN_METEO,
                ) {
                    source = AirSource.OPEN_METEO
                    store.airSource = source
                    if (store.enabled) update()
                }
                GroupDivider()
                ChoiceRow(
                    title = stringResource(R.string.air_source_moenv),
                    subtitle = stringResource(R.string.air_source_moenv_hint),
                    selected = source == AirSource.MOENV,
                ) {
                    source = AirSource.MOENV
                    store.airSource = source
                    if (store.enabled && key.isNotBlank()) update()
                }
                if (source == AirSource.MOENV) {
                    KeyEditor(
                        value = key,
                        onValueChange = { key = it },
                        label = stringResource(R.string.moenv_key_label),
                        steps = stringResource(R.string.moenv_key_steps),
                        site = MOENV_SITE,
                        busy = busy,
                        onSave = {
                            store.moenvKey = key
                            update()
                        },
                    )
                }
            }

            if (mode != WeatherLocationMode.OFF) {
                SectionTitle(stringResource(R.string.weather_section_status))
                SettingsGroup {
                    WeatherStatus(store, version)
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(enabled = !busy, onClick = ::update) {
                            Text(stringResource(R.string.weather_update_now))
                        }
                        if (busy) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.weather_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private const val MOENV_SITE = "https://data.moenv.gov.tw/"
private const val CWA_SITE = "https://opendata.cwa.gov.tw/user/authkey"

/** 金鑰輸入框、申請步驟、開啟平臺與「儲存並測試」。 */
@Composable
private fun KeyEditor(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    steps: String,
    site: String,
    busy: Boolean,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(steps, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site)))
            }) { Text(stringResource(R.string.moenv_open_site)) }
            Button(enabled = !busy, onClick = onSave) { Text(stringResource(R.string.moenv_save_test)) }
        }
    }
}

/** 上次更新的時間、地點與空氣品質來源；環境部抓不到時說明原因。 */
@Composable
private fun WeatherStatus(store: WeatherStore, version: Int) {
    val context = LocalContext.current
    val fetchedAt = remember(version) { store.fetchedAt() }
    val name = remember(version) { store.cachedName() }
    val air = remember(version) { store.cachedAir() }
    val error = remember(version) { store.airError() }
    val weatherError = remember(version) { store.weatherError() }
    val cwa = remember(version) { store.cachedCwa() }
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            if (fetchedAt > 0 && name != null) {
                stringResource(R.string.weather_last_update, DateFormat.getTimeFormat(context).format(Date(fetchedAt)), name)
            } else {
                stringResource(R.string.weather_not_fetched)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (cwa != null) {
            Text(
                cwa.station?.let { stringResource(R.string.cwa_station, it) } ?: stringResource(R.string.cwa_forecast_county, cwa.county),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (weatherError != null) {
            Text(
                stringResource(R.string.cwa_error, weatherError),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (air != null) {
            Text(
                airSourceText(air) + (air.aqi?.let { " · AQI $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (error != null) {
            Text(
                stringResource(R.string.moenv_error, error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 單選的一列：圓點、標題、說明。 */
@Composable
private fun ChoiceRow(title: String, selected: Boolean, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
