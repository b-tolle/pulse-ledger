plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.pulseledger"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.pulseledger"
        minSdk = 28          // Health Connect needs API 28+; on 34+ it's built into the OS
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-rc02")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // Room (local cache + derived metrics)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Background sync
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Charts — re-enable when porting the mockup visuals
    // implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-alpha.28")

    // Google Drive backup — re-enable when you set up an OAuth client ID
    // implementation("com.google.android.gms:play-services-auth:21.2.0")
    // implementation("com.google.api-client:google-api-client-android:2.6.0")
    // implementation("com.google.apis:google-api-services-drive:v3-rev20240914-2.0.0")
}
