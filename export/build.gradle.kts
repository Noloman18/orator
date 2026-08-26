plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.noloxtreme.tts.reader.export"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":playback"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.extractor)
    implementation(libs.androidx.hilt.android)
    ksp(libs.androidx.hilt.compiler)
    // androidx.hilt's KSP processor: registers @HiltWorker classes with
    // HiltWorkerFactory. Without it the worker is silently never wired up.
    ksp(libs.androidx.hilt.work.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
