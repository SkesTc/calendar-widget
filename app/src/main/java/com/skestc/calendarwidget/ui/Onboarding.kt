package com.skestc.calendarwidget.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skestc.calendarwidget.R

/** 第一次使用引導是否已經走完（裝 App 後只出現一次）。 */
object Onboarding {
    private const val PREFS = "onboarding"
    private const val KEY_DONE = "done"

    fun done(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun setDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
    }
}

private const val STEPS = 3

/**
 * 第一次使用的三個步驟：授權讀取日曆 → 把小工具加到桌面 → 訂閱台灣假日。
 * 已經授權過就從第二步開始；授權成功會自動跳到下一步。
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier,
    granted: Boolean,
    hasHolidayCalendar: Boolean,
    onGrant: () -> Unit,
    onFinish: () -> Unit,
    widgetGallery: @Composable () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(if (granted) 1 else 0) }
    LaunchedEffect(granted) {
        if (granted && step == 0) step = 1
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepDots(step)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_step, step + 1, STEPS),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        when (step) {
            0 -> {
                StepIntro(
                    icon = R.drawable.ic_event,
                    title = stringResource(R.string.onboarding_welcome_title),
                    body = stringResource(R.string.permission_rationale),
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onGrant) { Text(stringResource(R.string.grant_permission)) }
            }
            1 -> {
                StepIntro(
                    icon = R.drawable.ic_add,
                    title = stringResource(R.string.add_widgets_title),
                    body = stringResource(R.string.onboarding_widget_body),
                )
                Spacer(Modifier.height(20.dp))
                widgetGallery()
                Spacer(Modifier.height(20.dp))
                Button(onClick = { step = 2 }) { Text(stringResource(R.string.onboarding_next)) }
            }
            else -> {
                StepIntro(
                    icon = R.drawable.ic_event,
                    title = stringResource(R.string.holiday_hint_title),
                    body = stringResource(
                        if (hasHolidayCalendar) R.string.onboarding_holiday_done else R.string.holiday_hint_body,
                    ),
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onFinish) { Text(stringResource(R.string.onboarding_finish)) }
            }
        }
        if (step > 0) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { step-- }, enabled = step > 1) { Text(stringResource(R.string.onboarding_back)) }
                if (step < STEPS - 1) {
                    TextButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_skip)) }
                }
            }
        }
    }
}

/** 目前是第幾步：走過的點用主色，目前這步拉長。 */
@Composable
private fun StepDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until STEPS) {
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (i == step) 24.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        }
    }
}

@Composable
private fun StepIntro(icon: Int, title: String, body: String) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(44.dp),
        )
    }
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
