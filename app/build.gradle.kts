plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.appinspector"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.appinspector"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.0.7"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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

    packaging {
        resources {
            // KMP 构件自带的 Kotlin/Native 编译器元数据在多个 jar 中重复，
            // 且 Android 运行时用不到，直接排除
            excludes += "**/*.knm"
            excludes += "commonMain/**"
            excludes += "nonJvmMain/**"
            excludes += "META-INF/kotlin-project-structure-metadata.json"
            // KMP 空 jar 自带的 manifest 占位文件（多个构件重复）
            excludes += "**/manifest"
            excludes += "**/linkdata/**"
        }
    }
}

// 去重：旧 -ktx 构件的类已全部并入新构件（collection-ktx → collection-jvm、
// lifecycle-viewmodel-ktx → lifecycle-viewmodel-android），同时保留会导致
// d8 报 Duplicate class。已验证被排除的类均有新版覆盖（App 未使用的
// 已废弃 CloseableCoroutineScope 除外）。
configurations.all {
    exclude(group = "androidx.collection", module = "collection-ktx")
    exclude(group = "androidx.lifecycle", module = "lifecycle-viewmodel-ktx")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Shizuku：免 Root 执行 pm 级操作（disable-user / uninstall --user 0）
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // 二维码生成：扫码分享精简方案
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
