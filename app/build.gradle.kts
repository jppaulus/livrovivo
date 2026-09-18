import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Configurações locais (não versionadas). Veja o README para as chaves suportadas.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localProperty(key: String): String =
    (localProperties.getProperty(key) ?: "").replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.livrovivo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.livrovivo.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Backend de produção (Supabase Edge Function "ai-gateway"): as chaves de IA ficam no servidor.
        buildConfigField("String", "SUPABASE_URL", "\"${localProperty("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperty("supabase.anonKey")}\"")
    }

    buildTypes {
        debug {
            // Conveniência apenas para desenvolvimento: chaves lidas do local.properties.
            // Nunca vão para o build de release.
            buildConfigField("String", "DEV_GEMINI_API_KEY", "\"${localProperty("gemini.apiKey")}\"")
            buildConfigField("String", "DEV_ELEVENLABS_API_KEY", "\"${localProperty("elevenlabs.apiKey")}\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "DEV_GEMINI_API_KEY", "\"\"")
            buildConfigField("String", "DEV_ELEVENLABS_API_KEY", "\"\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Jetpack DataStore
    implementation(libs.androidx.datastore.preferences)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Networking & Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Google Play Billing
    implementation(libs.play.billing)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
}
