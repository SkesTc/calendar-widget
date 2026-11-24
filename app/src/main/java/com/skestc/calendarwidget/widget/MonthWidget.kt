package com.skestc.calendarwidget.widget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.unit.ColorProvider
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.skestc.calendarwidget.R
import com.skestc.calendarwidget.data.CalendarEvent
import com.skestc.calendarwidget.data.CalendarRepository
import com.skestc.calendarwidget.data.HolidayKind
import com.skestc.calendarwidget.data.DayCell
import com.skestc.calendarwidget.data.MonthBuilder
import com.skestc.calendarwidget.data.MonthLayout
import com.skestc.calendarwidget.data.SchoolWeeks
import com.skestc.calendarwidget.data.WeatherToday
import com.skestc.calendarwidget.data.MonthData
import com.skestc.calendarwidget.settings.WidgetSettings
import com.skestc.calendarwidget.theme.Desaturate
import com.skestc.calendarwidget.theme.LocalWidgetStyle
import com.skestc.calendarwidget.theme.WidgetStyle
import com.skestc.calendarwidget.theme.cardBackground
import com.skestc.calendarwidget.theme.ProvideWidgetStyle
import com.skestc.calendarwidget.theme.widgetBackground
import com.skestc.calendarwidget.ui.DateMenuActivity
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale

// internal：讓 UI 測試可以直接畫出各種狀態。
internal sealed interface MonthState {
    data object NoPermission : MonthState
    data class Loaded(
        val data: MonthData,
        val monthOffset: Int,
        /** 選取的日期與當天行程，顯示在月曆下方（widget 夠高時）。 */
        val selected: LocalDate = data.today,
        val selectedEvents: List<CalendarEvent> = emptyList(),
        /** 已結束的行程用淡色顯示。 */
        val now: Long = System.currentTimeMillis(),
        /** 月曆格子範圍內的所有事件：點日期時直接從這裡挑出當天行程，不必重新查詢。 */
        val events: List<CalendarEvent> = emptyList(),
        /** 有設定開學日時，每一週左邊標上學期週次。 */
        val schoolStart: LocalDate? = null,
        /** 先畫出來的日期，行程還在讀取（圓點與下方行程稍後補上）。 */
        val loading: Boolean = false,
        /** 今天的天氣與空氣品質（首頁開啟天氣時才有）。 */
        val weather: WeatherToday? = null,
    ) : MonthState {
        /** 套用使用者點選的日期（不在這個月的格子裡時改選今天或 1 號）。 */
        fun withSelection(picked: LocalDate?): Loaded {
            val builder = MonthBuilder()
            val date = builder.selectedDate(data.month, data.today, picked)
            if (date == selected) return this
            return copy(selected = date, selectedEvents = builder.eventsOn(events, date))
        }
    }
}

/** 月曆 widget：有事件的日子會打上日曆顏色的小圓點，可以切換月份；配色依外觀設定。 */
class MonthWidget : GlanceAppWidget() {

    companion object {
        /** 顯示農曆／假日小字的最小高度。 */
        val LABELS_MIN_HEIGHT = 260.dp
        /** 下方加上選取日期行程的最小高度。 */
        val PANEL_MIN_HEIGHT = 340.dp
        /** 平板、摺疊機展開：行程改放在月曆右邊。 */
        val WIDE_MIN_WIDTH = 480.dp
        /** 月曆上方顯示天氣列的最小高度（太矮時格子優先）。 */
        val WEATHER_MIN_HEIGHT = 300.dp
        /** 窄於這個寬度時天氣列只留圖示、溫度與 AQI。 */
        val WEATHER_FULL_MIN_WIDTH = 300.dp
    }

    /**
     * 月曆的版面最大（約 40 格，每格有數字、小字、圓點和點擊動作），用 Exact：
     * 只依 widget 的實際大小畫（Android 12+ 直向、橫向各一份），改大小時 Glance 會自動重畫。
     * Responsive 會把每個宣告尺寸的版面一次全部送給桌面，月曆可能超過 Binder 的 1MB 上限而無法更新；
     * 而且 LocalSize 會是宣告的尺寸而不是實際大小，版面門檻很容易對不上。
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = CalendarRepository(context)
        val initialSettings = WidgetSettings.from(
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id),
        )
        val key = initialSettings.monthKey()
        // 切換到沒讀過的月份：先用只有日期的畫面（很快），行程在 composition 裡讀完再補上。
        val initial = MonthCache.peek(key)
            ?: if (MonthCache.takeQuickFrame(id)) datesOnly(context, initialSettings) else null
            ?: MonthCache.get(key) { load(context, repository, it) }

        provideContent {
            val settings = WidgetSettings.from(currentState<Preferences>())
            // 點日期只改選取，不重新查詢：用已載入的整月事件挑出當天行程，畫面馬上更新。
            val state = rememberWidgetData(
                settings.monthKey(),
                initialSettings.monthKey(),
                initial,
                needsLoad = { it is MonthState.Loaded && it.loading },
            ) { key ->
                MonthCache.get(key) { load(context, repository, it) }
            }
            val shown = (state as? MonthState.Loaded)
                ?.withSelection(settings.selectedEpochDay?.let(LocalDate::ofEpochDay))
                // 快取的資料可能是幾分鐘前載入的：「已結束」以現在時間判斷；天氣另外讀。
                ?.copy(
                    now = System.currentTimeMillis(),
                    // 這個 widget 關掉天氣時不顯示。
                    weather = if (settings.showWeather) rememberWeather(context) else null,
                )
                ?: state
            GlanceTheme {
                ProvideWidgetStyle(settings.appearance, settings.headerButtons, settings.hiddenCalendarIds) {
                    MonthContent(shown)
                }
            }
        }
    }

    /** 只有日期、農曆與週次的月曆（不查日曆，幾十毫秒內完成）；行程讀完前先顯示這個。 */
    private fun datesOnly(context: Context, settings: WidgetSettings): MonthState {
        if (!CalendarRepository(context).hasPermission()) return MonthState.NoPermission
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val month = YearMonth.from(today).plusMonths(settings.monthOffset.toLong())
        val builder = MonthBuilder(zone)
        return MonthState.Loaded(
            data = builder.build(month, today, emptyList(), if (settings.showLunar) MonthCache.labeler else null),
            monthOffset = settings.monthOffset,
            selected = builder.selectedDate(month, today, null),
            schoolStart = SchoolWeeks.start(context),
            loading = true,
        )
    }

    private suspend fun load(context: Context, repository: CalendarRepository, settings: WidgetSettings): MonthState =
        withContext(Dispatchers.IO) { loadNow(context, repository, settings) }

    private suspend fun loadNow(context: Context, repository: CalendarRepository, settings: WidgetSettings): MonthState {
        if (!repository.hasPermission()) return MonthState.NoPermission
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val month = YearMonth.from(today).plusMonths(settings.monthOffset.toLong())
        val builder = MonthBuilder(zone)
        val range = builder.gridRange(month)
        // 前後各多抓一天，涵蓋以 UTC 午夜計的全天事件。
        val start = range.start.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = range.endInclusive.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = repository.instances(start, end, settings.hiddenCalendarIds)
        val labeler = if (settings.showLunar) MonthCache.labeler else null
        val selected = builder.selectedDate(month, today, null)
        return MonthState.Loaded(
            data = builder.build(month, today, events, labeler),
            monthOffset = settings.monthOffset,
            selected = selected,
            selectedEvents = builder.eventsOn(events, selected),
            events = events,
            schoolStart = SchoolWeeks.start(context),
        )
    }
}

@Composable
internal fun MonthContent(state: MonthState) {
    val style = LocalWidgetStyle.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .widgetBackground(style)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        when (state) {
            MonthState.NoPermission -> PermissionPrompt()
            is MonthState.Loaded -> {
                val size = LocalSize.current
                val compact = size.width < 250.dp
                val wide = size.width >= MonthWidget.WIDE_MIN_WIDTH
                // 格子夠高才放農曆／假日小字（約每格 40dp 以上）。
                // 再高一點（約 4×4 以上）時，點日期改成在下方列出當天行程。
                val panelBelow = !wide && !compact && size.height >= MonthWidget.PANEL_MIN_HEIGHT
                // 天氣列：平板放在右邊行程上方；一般大小夠高時放在月曆上方。
                val weather = state.weather?.takeIf { wide || size.height >= MonthWidget.WEATHER_MIN_HEIGHT }
                // 文字大小設定加上系統字型縮放：行程區塊的高度要跟著放大，最後一行才不會被切掉。
                val textScale = style.textScale * LocalContext.current.resources.configuration.fontScale
                val weatherHeight = if (weather != null) weatherRowHeight(textScale) else 0f
                val inner = size.height.value - 20f - if (wide) 0f else weatherHeight
                // 下方區塊依當天行程數量長高（最多約一半），其餘留給月曆格子。
                val panelHeight = if (panelBelow) {
                    MonthLayout.panelHeight(inner, state.selectedEvents.size, textScale)
                } else {
                    0f
                }
                val showLabels = !compact &&
                    size.height.value - panelHeight - (if (wide) 0f else weatherHeight) >= MonthWidget.LABELS_MIN_HEIGHT.value
                val selectable = wide || panelBelow
                if (wide) {
                    // 平板、摺疊機：月曆在左、選取日期的行程在右。
                    Row(modifier = GlanceModifier.fillMaxSize()) {
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                            // 右側行程區塊佔 200dp＋間距：標題列只有左邊的寬度。
                            MonthGrid(
                                state, compact = false, showLabels = showLabels, selectable = true,
                                headerWidthDp = size.width.value - 210f,
                            )
                        }
                        Spacer(GlanceModifier.width(10.dp))
                        Column(modifier = GlanceModifier.width(200.dp).fillMaxHeight()) {
                            if (weather != null) {
                                WeatherRow(weather, compact = true)
                                Spacer(GlanceModifier.height(6.dp))
                            }
                            DayPanel(
                                date = state.selected,
                                events = state.selectedEvents,
                                now = state.now,
                                loading = state.loading,
                                // 右側區塊沒有上方間距，算法同下方區塊。
                                maxEvents = MonthLayout.eventsThatFit(inner + 6f - weatherHeight, state.selectedEvents.size, textScale),
                                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                            )
                        }
                    }
                } else {
                    if (weather != null) {
                        WeatherRow(weather, compact = size.width < MonthWidget.WEATHER_FULL_MIN_WIDTH)
                        Spacer(GlanceModifier.height(6.dp))
                    }
                    if (panelBelow) {
                        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                            MonthGrid(state, compact, showLabels, selectable)
                        }
                        Spacer(GlanceModifier.height(6.dp))
                        DayPanel(
                            date = state.selected,
                            events = state.selectedEvents,
                            now = state.now,
                            loading = state.loading,
                            maxEvents = MonthLayout.eventsThatFit(panelHeight, state.selectedEvents.size, textScale),
                            modifier = GlanceModifier.fillMaxWidth().height((panelHeight - 6f).dp),
                        )
                    } else {
                        MonthGrid(state, compact, showLabels, selectable)
                    }
                }
            }
        }
    }
}

/** 天氣列加下方間距大約佔的高度（dp），依文字大小調整。 */
internal fun weatherRowHeight(textScale: Float): Float = 40f * textScale + 6f

/** 標題、星期列與日期格子；放在 Column 裡，週列平分剩下的高度。 */
@Composable
private fun ColumnScope.MonthGrid(
    state: MonthState.Loaded,
    compact: Boolean,
    showLabels: Boolean,
    selectable: Boolean,
    headerWidthDp: Float = LocalSize.current.width.value,
) {
    MonthHeader(state.data.month, state.monthOffset, headerWidthDp)
    // 語音描述的日期格式整個月共用（每格都查一次很花時間）。
    val locale = Locale.getDefault()
    val dateFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMdEEEE"), locale)
    // 有設定開學日、而且格子夠大時，每週最左邊多一欄週次。
    val weekNumbers = !compact && state.schoolStart != null
    WeekdayRow(state.data.firstDayOfWeek, compact, weekNumbers)
    for (week in state.data.weeks) {
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            if (weekNumbers) {
                // 用這一列的週四判斷，週日或週一開頭的月曆都對得上同一週。
                val number = SchoolWeeks.weekOf(week[3].date, state.schoolStart)
                WeekNumberCell(number, GlanceModifier.width(WEEK_NUMBER_WIDTH).fillMaxHeight())
            }
            for (day in week) {
                DayCellView(
                    day = day,
                    today = state.data.today,
                    compact = compact,
                    showLabel = showLabels,
                    selected = selectable && day.date == state.selected,
                    selectInWidget = selectable,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    dateFormat = dateFormat,
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, monthOffset: Int, widthDp: Float) {
    val style = LocalWidgetStyle.current
    val context = LocalContext.current
    val locale = Locale.getDefault()
    fun format(skeleton: String) = DateTimeFormatter
        .ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        .format(month)
    val yearTitle = format("yMMMM")
    val monthTitle = format("MMMM")
    // 標題字級含使用者的文字大小與系統字型縮放，估算寬度時一起算進去。
    val titleSp = 16f * style.textScale * context.resources.configuration.fontScale
    // 不在本月時多一顆「今」按鈕（約 34dp 加間距），標題可用的寬度少一點。
    val fit = MonthLayout.headerFit(
        widthDp = widthDp - if (monthOffset != 0) TODAY_BUTTON_DP else 0f,
        yearTitleDp = MonthLayout.textWidthDp(yearTitle, titleSp),
        monthTitleDp = MonthLayout.textWidthDp(monthTitle, titleSp),
        enabled = style.headerButtons,
    )
    val backToThisMonth = actionRunCallback<ChangeMonthAction>(actionParametersOf(ChangeMonthAction.DeltaKey to 0))
    // 點標題：跳出選單（選擇月份、回到本月、這個月的行程、開自選的日曆 App…）。
    val pickMonth = actionStartIntent(
        DateMenuActivity.monthIntent(context, appWidgetIdOrNull(), monthOffset, style.hiddenCalendarIds),
    )
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 點標題選其他月份；不在本月時標題用主色提示。
        Text(
            text = if (fit.showYear) yearTitle else monthTitle,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight().clickable(pickMonth),
            style = style.text(16, if (monthOffset != 0) style.colors.primary else style.colors.onSurface, FontWeight.Bold),
        )
        // 不在本月時：「今」一鍵回到本月（選取今天）。
        if (monthOffset != 0) {
            Box(
                modifier = GlanceModifier
                    .height(30.dp)
                    .background(style.colors.primary)
                    .cornerRadius(15.dp)
                    .padding(horizontal = 10.dp)
                    .clickable(backToThisMonth),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = glanceString(R.string.today_short),
                    style = style.text(13, style.colors.onPrimary, FontWeight.Bold),
                )
            }
            Spacer(GlanceModifier.width(4.dp))
        }
        NavIconButton(R.drawable.ic_chevron_left, glanceString(R.string.previous_month), delta = -1, size = 30.dp)
        Spacer(GlanceModifier.width(4.dp))
        NavIconButton(R.drawable.ic_chevron_right, glanceString(R.string.next_month), delta = 1, size = 30.dp)
        Spacer(GlanceModifier.width(4.dp))
        // 勾選的按鈕放得下就各一顆，放不下的收進「⋯」選單，避免按鈕被擠出畫面。
        ActionButtons(fit.plan, size = 30.dp, gap = 4.dp)
        if (!fit.plan.isEmpty) Spacer(GlanceModifier.width(4.dp))
        AddEventButton(34.dp)
    }
}

private fun navModifier(style: WidgetStyle, delta: Int, size: Dp) = GlanceModifier
    .size(size)
    .background(style.colors.card)
    .cornerRadius(size / 2)
    .clickable(
        actionRunCallback<ChangeMonthAction>(
            actionParametersOf(ChangeMonthAction.DeltaKey to delta),
        ),
    )

/** 上個月／下個月：向量箭頭，在圓圈裡置中（「‹」「›」文字會因字型留白而偏移）。 */
@Composable
private fun NavIconButton(icon: Int, description: String, delta: Int, size: Dp) {
    val style = LocalWidgetStyle.current
    Box(modifier = navModifier(style, delta, size), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(style.colors.onCard),
            modifier = GlanceModifier.size(size * 0.7f),
        )
    }
}

private val WEEK_NUMBER_WIDTH = 16.dp

/** 格子裡農曆小字（9sp）大約的行高。 */
private val LABEL_HEIGHT = 12.dp

/** 「今」按鈕加間距的寬度（dp）。 */
private const val TODAY_BUTTON_DP = 40f

/** 學期週次（開學前或太久以後留白）。 */
@Composable
private fun WeekNumberCell(number: Int?, modifier: GlanceModifier) {
    val style = LocalWidgetStyle.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (number != null) {
            Text(
                text = number.toString(),
                style = style.text(9, style.colors.outline, FontWeight.Medium, TextAlign.Center),
            )
        }
    }
}

@Composable
private fun WeekdayRow(firstDayOfWeek: DayOfWeek, compact: Boolean, weekNumbers: Boolean = false) {
    val style = LocalWidgetStyle.current
    val locale = Locale.getDefault()
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (weekNumbers) Spacer(GlanceModifier.width(WEEK_NUMBER_WIDTH))
        for (i in 0 until 7) {
            val day = firstDayOfWeek.plus(i.toLong())
            Text(
                text = day.getDisplayName(DayTextStyle.NARROW, locale),
                modifier = GlanceModifier.defaultWeight(),
                style = style.text(
                    size = if (compact) 10 else 11,
                    color = weekendColor(day) ?: style.colors.onSurfaceVariant,
                    weight = FontWeight.Medium,
                    align = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun DayCellView(
    day: DayCell,
    today: LocalDate,
    compact: Boolean,
    showLabel: Boolean,
    selected: Boolean,
    selectInWidget: Boolean,
    modifier: GlanceModifier,
    dateFormat: DateTimeFormatter,
) {
    val style = LocalWidgetStyle.current
    val isToday = day.date == today
    // 今天的圓圈比其他日子大一號，一眼就找得到。
    val circle = when {
        compact -> 22.dp
        showLabel -> 24.dp
        else -> 28.dp
    } + if (isToday) 4.dp else 0.dp

    val textColor = when {
        isToday -> style.colors.onPrimary
        !day.inMonth -> style.colors.outline
        day.holiday == HolidayKind.DAY_OFF -> style.colors.sunday
        // 補班日就算是週末也用一般顏色。
        day.holiday == HolidayKind.MAKEUP_WORKDAY -> style.colors.onSurface
        else -> weekendColor(day.date.dayOfWeek) ?: style.colors.onSurface
    }

    val description = dayDescription(day, isToday, selected, dateFormat)
    // 前後月份的灰色日期：點了也只是回到本月，在可點選的月曆上不加點擊（每一格的點擊都要向系統登記，越少越快）。
    val clickable = day.inMonth || !selectInWidget
    Column(
        modifier = modifier
            // 讓 TalkBack 唸出完整的日期與狀態，而不是只有數字。
            .semantics { contentDescription = description }
            // 今天整格加上卡片色的圓角底，再配上主色實心圓，比只有圓圈更醒目。
            .then(if (isToday) GlanceModifier.background(style.colors.card).cornerRadius(10.dp) else GlanceModifier)
            .then(
                when {
                    !clickable -> GlanceModifier
                    selectInWidget -> GlanceModifier.clickable(
                        actionRunCallback<SelectDayAction>(actionParametersOf(SelectDayAction.EpochDayKey to day.date.toEpochDay())),
                    )
                    else -> GlanceModifier.clickable(openInCalendar(openDayIntent(day.date)))
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(circle)
                .then(
                    when {
                        isToday -> GlanceModifier.background(style.colors.primary).cornerRadius(circle / 2)
                        // 選取的（非今天）日期用卡片色圓底標示。
                        selected -> GlanceModifier.background(style.colors.card).cornerRadius(circle / 2)
                        else -> GlanceModifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = style.text(
                    size = (if (compact) 12 else 14) + if (isToday) 2 else 0,
                    color = textColor,
                    // 有行程的日子數字加粗，搭配下方圓點更好辨識。
                    weight = if (isToday || (day.inMonth && day.eventColors.isNotEmpty())) FontWeight.Bold else FontWeight.Normal,
                    align = TextAlign.Center,
                ).let { base ->
                    // 放假日除了紅字再加底線：色弱或小格子不顯示假日名稱時也分得出來。
                    if (day.inMonth && day.holiday == HolidayKind.DAY_OFF) base.copy(textDecoration = TextDecoration.Underline) else base
                },
            )
        }
        val dot = if (compact) 6.dp else 7.dp
        if (day.inMonth) {
            if (showLabel) DayLabelText(day, isToday)
            EventDots(day.eventColors, dimmed = false, dot = dot)
        } else {
            // 前後月份的日期不畫小字與圓點，用一個元件保留同樣的高度，數字才會和同一列對齊。
            Spacer(GlanceModifier.height(dot + 3.dp + if (showLabel) LABEL_HEIGHT * style.textScale else 0.dp))
        }
    }
}

/** 月曆格子的語音描述，例如「10月10日 星期六，今天，國慶日，有行程」。 */
@Composable
private fun dayDescription(day: DayCell, isToday: Boolean, selected: Boolean, dateFormat: DateTimeFormatter): String {
    val date = dateFormat.format(day.date)
    return listOfNotNull(
        date,
        if (isToday) glanceString(R.string.today_word) else null,
        if (selected) glanceString(R.string.a11y_selected) else null,
        when (day.holiday) {
            HolidayKind.MAKEUP_WORKDAY -> glanceString(R.string.makeup_workday)
            HolidayKind.DAY_OFF -> day.label ?: glanceString(R.string.a11y_day_off)
            else -> day.label?.takeIf { day.labelHighlighted }
        },
        glanceString(if (day.eventColors.isNotEmpty()) R.string.a11y_has_events else R.string.month_day_empty),
    ).joinToString("，")
}

@Composable
private fun DayLabelText(day: DayCell, isToday: Boolean = false) {
    val style = LocalWidgetStyle.current
    val text = when (day.holiday) {
        HolidayKind.MAKEUP_WORKDAY -> glanceString(R.string.makeup_workday)
        else -> day.label
    } ?: return
    val color = when {
        !day.inMonth -> style.colors.outline
        day.holiday == HolidayKind.DAY_OFF -> style.colors.sunday
        day.labelHighlighted || isToday -> style.colors.primary
        else -> style.colors.onSurfaceVariant
    }
    Text(
        text = text,
        maxLines = 1,
        style = style.text(9, color, if (isToday) FontWeight.Bold else null, align = TextAlign.Center),
    )
}

@Composable
private fun EventDots(colors: List<Int>, dimmed: Boolean, dot: Dp) {
    // 沒有事件時也保留一樣的高度，讓每一格的日期數字對齊。
    if (colors.isEmpty()) {
        // 只用一個元件佔位（月曆約 40 格，元件越少，點按後重畫越快）。
        Spacer(GlanceModifier.height(dot + 3.dp))
        return
    }
    Row(
        modifier = GlanceModifier.padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEachIndexed { index, color ->
            if (index > 0) Spacer(GlanceModifier.width(3.dp))
            val dotColor = Color(color).let { if (dimmed) it.copy(alpha = 0.4f) else it }
            Box(
                modifier = GlanceModifier
                    .size(dot)
                    .background(ColorProvider(dotColor))
                    .cornerRadius(dot / 2),
            ) {}
        }
    }
}

/** 選取那天的日期與行程（下方或右側）；點行程開啟該事件、點標題開啟那一天。 */
@Composable
private fun DayPanel(
    date: LocalDate,
    events: List<CalendarEvent>,
    now: Long,
    maxEvents: Int,
    modifier: GlanceModifier,
    loading: Boolean = false,
) {
    val style = LocalWidgetStyle.current
    val locale = Locale.getDefault()
    val title = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMdEEEE"), locale).format(date)
    Column(
        modifier = modifier
            .cardBackground(style)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 日期與「開啟」：點了在日曆 App 打開這一天。
            Row(
                modifier = GlanceModifier.defaultWeight().clickable(openInCalendar(openDayIntent(date))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = GlanceModifier.defaultWeight(),
                    style = style.text(12, style.colors.onCard, FontWeight.Bold),
                )
                Text(
                    text = glanceString(R.string.month_open_day),
                    style = style.text(11, style.colors.onCard),
                )
            }
            // 「＋」：直接在選取的這一天新增行程。
            Spacer(GlanceModifier.width(8.dp))
            AddEventButton(size = 24.dp, date = date)
        }
        if (events.isEmpty()) {
            Text(
                text = glanceString(if (loading) R.string.month_loading else R.string.month_day_empty),
                style = style.text(12, style.colors.onCard),
            )
        }
        val timeFormat = DateFormat.getTimeFormat(LocalContext.current)
        for (event in events.take(maxEvents)) {
            // 已結束的行程：圓點轉灰、文字用次要色。
            val past = !event.allDay && event.end <= now
            val openEvent = Intent(
                Intent.ACTION_VIEW,
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId),
            ).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp).clickable(openInCalendar(openEvent)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(6.dp)
                        .background(ColorProvider(Color(if (past) Desaturate.color(event.color) else event.color)))
                        .cornerRadius(3.dp),
                ) {}
                Text(
                    text = (if (event.allDay) glanceString(R.string.all_day) else timeFormat.format(Date(event.begin))) +
                        "  " + event.title,
                    maxLines = 1,
                    modifier = GlanceModifier.padding(start = 6.dp),
                    style = style.text(12, if (past) style.colors.onSurfaceVariant else style.colors.onCard),
                )
            }
        }
        if (events.size > maxEvents) {
            Text(text = "+${events.size - maxEvents}", style = style.text(11, style.colors.onCard))
        }
    }
}

/** 週日、週六用主題指定的顏色，跟一般紙本月曆一樣。 */
@Composable
private fun weekendColor(day: DayOfWeek): ColorProvider? {
    val colors = LocalWidgetStyle.current.colors
    return when (day) {
        DayOfWeek.SUNDAY -> colors.sunday
        DayOfWeek.SATURDAY -> colors.saturday
        else -> null
    }
}

/** 開啟 Google 日曆（或系統預設日曆 App）的某一天。 */
internal fun openDayIntent(date: LocalDate): Intent {
    val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val uri = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .appendPath(millis.toString())
        .build()
    return Intent(Intent.ACTION_VIEW, uri)
}

/** 決定要不要重新查詢的設定：選取的日期不算（點日期只換下方的行程，不必重查）。 */
internal fun WidgetSettings.monthKey(): WidgetSettings = copy(selectedEpochDay = null)
