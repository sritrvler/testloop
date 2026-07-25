plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization")
}

android {
    namespace = "com.testloop.app"
    compileSdk = 35

    val uploadKeystorePath = providers.gradleProperty("testloopKeystoreFile").orNull
    val uploadKeystorePassword = providers.gradleProperty("testloopKeystorePassword").orNull
    val uploadKeyPassword = providers.gradleProperty("testloopKeyPassword").orNull
    if (uploadKeystorePath != null && uploadKeystorePassword != null && uploadKeyPassword != null) {
        signingConfigs {
            create("internalUpload") {
                storeFile = file(uploadKeystorePath)
                storePassword = uploadKeystorePassword
                keyAlias = "testloop-upload"
                keyPassword = uploadKeyPassword
            }
        }
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("internalUpload")
        }
    }

    defaultConfig {
        applicationId = "com.testloop.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.1.5"
        buildConfigField("String", "SUPABASE_URL", "\"https://ndmamltbrhpehjzcwkwv.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5kbWFtbHRicmhwZWhqemN3a3d2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQ3NzkyNjgsImV4cCI6MjEwMDM1NTI2OH0.3sK3zKL9kFCLd8oR6ZQWiEiDvY8egy-gwu7lh0mUI2o\"")
    }

    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

configurations.configureEach {
    resolutionStrategy.force("androidx.browser:browser:1.8.0")
}

// Android Studio's bundled JDK is used for local and CI builds.
kotlin { jvmToolchain(21) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
