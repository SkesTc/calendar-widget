package com.skestc.calendarwidget.tasks

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 用 Google Identity Services 的 AuthorizationClient 取得 Google Tasks 的存取權杖。
 *
 * 不需要在程式裡放 client ID：Google 會依套件名稱與簽署憑證的 SHA-1，
 * 對應到 Google Cloud Console 裡建立的「Android」OAuth 用戶端（見 docs/google-tasks-setup.md）。
 */
object GoogleTasksAuth {
    /** 讀取與勾選完成都需要這個權限（tasks.readonly 不能勾選完成）。 */
    const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"

    private val request: AuthorizationRequest
        get() = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(TASKS_SCOPE)))
            .build()

    sealed interface Outcome {
        data class Authorized(val accessToken: String) : Outcome

        /** 第一次使用（或權限被撤銷）時要請使用者同意。 */
        data class NeedsConsent(val pendingIntent: PendingIntent) : Outcome

        /** [statusCode] 為 CommonStatusCodes；DEVELOPER_ERROR 通常是 Cloud Console 的 OAuth 用戶端沒設定好。 */
        data class Failed(val statusCode: Int, val message: String?) : Outcome
    }

    suspend fun authorize(context: Context): Outcome = try {
        toOutcome(Identity.getAuthorizationClient(context).authorize(request).await())
    } catch (e: ApiException) {
        Outcome.Failed(e.statusCode, e.message)
    }

    /** 使用者在同意畫面按下允許之後，從回傳的 Intent 取出結果。 */
    fun fromConsentResult(context: Context, data: Intent?): Outcome = try {
        toOutcome(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (e: ApiException) {
        Outcome.Failed(e.statusCode, e.message)
    }

    /** 背景同步用：已經同意過才拿得到權杖，不會跳出畫面。 */
    suspend fun silentToken(context: Context): String? =
        (authorize(context) as? Outcome.Authorized)?.accessToken

    fun isConfigurationError(outcome: Outcome): Boolean =
        outcome is Outcome.Failed && outcome.statusCode == CommonStatusCodes.DEVELOPER_ERROR

    private fun toOutcome(result: AuthorizationResult): Outcome {
        val pendingIntent = result.pendingIntent
        val token = result.accessToken
        return when {
            result.hasResolution() && pendingIntent != null -> Outcome.NeedsConsent(pendingIntent)
            token != null -> Outcome.Authorized(token)
            else -> Outcome.Failed(CommonStatusCodes.ERROR, "No access token")
        }
    }
}

/** 把 Play services 的 Task 轉成 suspend，不另外引入 kotlinx-coroutines-play-services。 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> cont.resumeWithException(error)
            task.isCanceled -> cont.cancel()
            else -> cont.resume(task.result)
        }
    }
}
