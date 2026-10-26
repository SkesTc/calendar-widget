plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 上架用的簽署金鑰不放進 repo：本機或 CI 用環境變數提供（見 docs/release.md）。
val releaseKeystore: String? = providers.environmentVariable("CW_KEYSTORE_PATH").orNull

android {
    namespace = "com.skestc.calendarwidget"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.skestc.calendarwidget"
        minSdk = 26
        // Google Play 規定新 App 與更新要以最新一版之前一年內的 API 為目標。
        targetSdk = 36
        versionCode = providers.environmentVariable("CW_VERSION_CODE").orNull?.toInt() ?: 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // 固定的 debug 金鑰（進 repo）：CI 每次建置的 SHA-1 才會一樣，
        // Google Tasks 的 OAuth 用戶端只要登記一次（見 docs/google-tasks-setup.md）。
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("CW_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("CW_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("CW_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 沒有提供金鑰時產生未簽署的版本（CI 用來檢查 R8 是否正常）。
            signingConfig = signingConfigs.findByName("release")
        }
    }

    testOptions {
        // Robolectric 的 widget UI 測試需要讀 App 的資源。
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkDependencies = false
        // 套件新版本提醒交給人工判斷，不讓 CI 失敗。
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.services.auth)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.glance.appwidget.testing)
}
