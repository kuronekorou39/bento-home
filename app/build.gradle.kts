plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
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

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
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
val checkNoInternetPermission by tasks.registering {
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
