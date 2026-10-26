import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

// リリース署名の鍵情報。鍵とパスワードはソース管理に入れない（plan.md §8）。
// プロジェクト直下の keystore.properties（.gitignore 済み）が無ければ、リリースは未署名でビルドする。
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
    Properties().apply { f.inputStream().use { load(it) } }
}

android {
    namespace = "com.rou39.bentohome"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rou39.bentohome"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Shizuku のシェル権限プロセスとの通信（システムモニター）
        aidl = true
        buildConfig = true
    }
}

room {
    // マイグレーションテストの基準としてスキーマ JSON をリポジトリに入れる
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// plan.md §6: リリース APK に INTERNET 権限を含めない。マージ後のマニフェストで検査する。
val checkNoInternetPermission = tasks.register("checkNoInternetPermission") {
    val manifest = layout.buildDirectory.file("intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml")
    dependsOn("processReleaseMainManifest")
    inputs.file(manifest)
    doLast {
        val text = manifest.get().asFile.readText()
        check("android.permission.INTERNET" !in text) {
            "リリースのマージ済みマニフェストに INTERNET 権限が含まれています: ${manifest.get().asFile}"
        }
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { dependsOn(checkNoInternetPermission) }
