plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vocatim.llm"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        minSdk = 26

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                // Summarization runs at low temperature; -O3 matters a lot.
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                cppFlags += "-O3"
                // Android 15+ devices can use 16 KB memory pages, and a 4 KB
                // aligned .so simply will not load there. NDK r27 needs this
                // opt-in; without it Play rejects the release.
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/llm/CMakeLists.txt")
            version = "3.30.5"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
