import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.*

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { stream ->
            load(stream)
        }
    }
}

fun String.toBuildConfigLiteral(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val agentServerBaseUrl: String = localProperties.getProperty("AGENT_SERVER_BASE_URL", "")
val agentServerApiKey: String = localProperties.getProperty("AGENT_SERVER_API_KEY", "")
// deviceId 路径开关：true=WS 走 /adb/{deviceId}、/system/{deviceId}（对齐协议）；
// false=WS 走无参 /adb、/system（兼容尚未改造的现网后端，便于提前联调）。默认 true。
val deviceIdInPath: String = localProperties.getProperty("DEVICE_ID_IN_PATH", "true")
val showTechDebugUi: String = localProperties.getProperty("SHOW_TECH_DEBUG_UI", "false")
val traceV1RenderEnabled = localProperties
    .getProperty("TRACE_V1_RENDER_ENABLED", "false")
    .trim()
    .lowercase()
    .also {
        require(it == "true" || it == "false") {
            "TRACE_V1_RENDER_ENABLED must be true or false"
        }
    }

android {
    namespace = "com.example.blueheartv"
    compileSdk = 36
//    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.example.blueheartv"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "AGENT_SERVER_BASE_URL", agentServerBaseUrl.toBuildConfigLiteral())
        buildConfigField("String", "AGENT_SERVER_API_KEY", agentServerApiKey.toBuildConfigLiteral())
        buildConfigField("boolean", "DEVICE_ID_IN_PATH", deviceIdInPath.trim().ifBlank { "true" })
        buildConfigField("boolean", "SHOW_TECH_DEBUG_UI", showTechDebugUi.trim().ifBlank { "false" })
        buildConfigField("boolean", "TRACE_V1_RENDER_ENABLED", traceV1RenderEnabled)

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        buildConfig = true
        compose = true
        aidl = true
    }
    sourceSets {
        getByName("androidTest").assets.srcDir(file("$projectDir/schemas"))
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    // Room 2.8.4 的 schema 序列化运行时依赖 1.8.1；显式对齐应用与
    // androidTest classpath，避免受 lifecycle 的旧传递版本影响。
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("io.insert-koin:koin-android:4.2.1")
    implementation("io.insert-koin:koin-androidx-compose:4.2.1")

    testImplementation("junit:junit:4.13.2")
    // Android 平台 org.json 在 JVM 单测中是方法桩；为流协议解析测试提供真实实现。
    testImplementation("org.json:json:20230227")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    androidTestImplementation(platform("androidx.compose:compose-bom:2026.05.01"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
