import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "com.moa.pbl"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.moa.pbl"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        val transcriptionBaseUrl = providers.gradleProperty("transcriptionApiBaseUrl").getOrElse("http://10.0.2.2:8000")
        buildConfigField("String", "TRANSCRIPTION_API_BASE_URL", "\"$transcriptionBaseUrl\"")
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { input -> localProperties.load(input) }
        }
        val supabaseUrl = localProperties.getProperty("SUPABASE_URL", "")
        val supabaseKey = localProperties.getProperty("SUPABASE_ANON_KEY", "")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseKey\"")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.activity:activity:1.10.0")
    implementation("androidx.core:core:1.15.0")
}
