import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Upload-key signing from key.properties (gitignored); falls back to the
// debug key so clean clones still build a runnable release variant.
val keyProps = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// AdMob ids come from local.properties (gitignored). The fallbacks are
// Google's official *test* ids, on purpose: a missing or malformed AdMob
// application id crashes the app on startup, so a clean clone has to build
// into something that runs. Real ids never enter the repository.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun adId(key: String, testDefault: String): String =
    localProps.getProperty(key)?.takeIf { it.isNotBlank() } ?: testDefault

val admobAppId = adId("admob.appId", "ca-app-pub-3940256099942544~3347511713")
val admobBannerId = adId("admob.bannerUnitId", "ca-app-pub-3940256099942544/6300978111")
val admobInterstitialId =
    adId("admob.interstitialUnitId", "ca-app-pub-3940256099942544/1033173712")

android {
    namespace = "com.vocatim.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vocatim.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 49
        versionName = "1.15.1"

        ndk {
            abiFilters += "arm64-v8a"
        }

        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"" + admobBannerId + "\"")
        buildConfigField(
            "String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"" + admobInterstitialId + "\""
        )
        // Lets the UI warn during testing that these are Google's test ads.
        buildConfigField(
            "boolean", "ADMOB_TEST_IDS", (admobAppId.contains("3940256099942544")).toString()
        )
    }

    signingConfigs {
        if (keyProps.isNotEmpty()) {
            create("upload") {
                storeFile = rootProject.file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds are signed with the debug key, so they can never
            // update a Play install — Android refuses on signature mismatch,
            // and the only way to force it is uninstalling, which destroys
            // every transcript on the device. A separate id sidesteps that:
            // test builds live beside the real app instead of fighting it.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Distinct launcher label: two identical icons side by side is a
            // good way to delete the wrong app's data by mistake.
            manifestPlaceholders["appLabel"] = "Vocatim Debug"
            // Dev builds never touch the production ad units: impressions from
            // your own devices are an AdMob policy violation. Debug always
            // uses Google's official test units, which are guaranteed to fill.
            manifestPlaceholders["admobAppId"] =
                "ca-app-pub-3940256099942544~3347511713"
            buildConfigField(
                "String", "ADMOB_BANNER_UNIT_ID",
                "\"ca-app-pub-3940256099942544/6300978111\"",
            )
            buildConfigField(
                "String", "ADMOB_INTERSTITIAL_UNIT_ID",
                "\"ca-app-pub-3940256099942544/1033173712\"",
            )
            buildConfigField("boolean", "ADMOB_TEST_IDS", "true")
        }
        release {
            manifestPlaceholders["appLabel"] = "Vocatim"
            isMinifyEnabled = true
            isShrinkResources = true
            // Ships whisper/llama/sherpa symbols in the AAB so native crashes
            // in Play Vitals come back with function names instead of raw
            // addresses. SYMBOL_TABLE rather than FULL: it is what Vitals
            // symbolicates with, at a fraction of the upload size.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keyProps.isNotEmpty()) {
                signingConfigs.getByName("upload")
            } else {
                signingConfigs.getByName("debug")
            }
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
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":whisper"))
    implementation(project(":llm"))
    // Official sherpa-onnx Android AAR (static-linked onnxruntime), vendored
    // from github.com/k2-fsa/sherpa-onnx releases v1.13.4.
    // SHA-256: dc5ac19a28dee3bffc5e5a5d50cb6afa977703fc4a7ee535a308506990fdd295
    implementation(files("libs/sherpa-onnx-1.13.4.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.splashscreen)
    implementation(libs.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // Real org.json for JVM tests (Android SDK stubs throw otherwise).
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
}
