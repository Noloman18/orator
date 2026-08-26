import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import java.io.File
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystoreEnv = providers.environmentVariable("ANDROID_BUILD_KEYSTORE")
val keystoreAliasEnv = providers.environmentVariable("ANDROID_BUILD_KEYSTORE_ALIAS")
val keystorePasswordEnv = providers.environmentVariable("ANDROID_BUILD_KEYSTORE_PASSWORD")
val keystoreConfigured = keystoreEnv.isPresent && keystoreAliasEnv.isPresent && keystorePasswordEnv.isPresent

fun resolveKeystoreFile(raw: String): File {
    val asPath = File(raw)
    if (asPath.isFile) return asPath
    val decoded = try {
        Base64.getDecoder().decode(raw.trim())
    } catch (e: IllegalArgumentException) {
        throw GradleException("ANDROID_BUILD_KEYSTORE is neither an existing file nor valid base64 content", e)
    }
    return layout.buildDirectory.file("keystores/upload-keystore.jks").get().asFile.also { out ->
        out.parentFile.mkdirs()
        out.writeBytes(decoded)
    }
}

android {
    namespace = "com.noloxtreme.tts.reader"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.noloxtreme.tts.reader"
        minSdk = 24
        targetSdk = 37
        versionCode = 7
        versionName = "1.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (keystoreConfigured) {
                signingConfig = signingConfigs.create("release").apply {
                    storeFile = resolveKeystoreFile(keystoreEnv.get())
                    storePassword = keystorePasswordEnv.get()
                    keyAlias = keystoreAliasEnv.get()
                    keyPassword = keystorePasswordEnv.get()
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":playback"))
    implementation(project(":export"))
    implementation(project(":designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.android)
    implementation(libs.androidx.coroutines.android)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val copySignedBundle = tasks.register<Copy>("copySignedBundle") {
    dependsOn(tasks.named("bundleRelease"))
    from(layout.buildDirectory.file("outputs/bundle/release/app-release.aab"))
    into(layout.projectDirectory.dir("release"))
    rename { "app-release.aab" }
}

tasks.register("buildSignedBundle") {
    group = "release"
    description = "Builds a signed release bundle and copies it to app/release/app-release.aab"
    if (keystoreConfigured) {
        dependsOn(copySignedBundle)
    } else {
        doLast {
            throw GradleException(
                "ANDROID_BUILD_KEYSTORE, ANDROID_BUILD_KEYSTORE_ALIAS and ANDROID_BUILD_KEYSTORE_PASSWORD " +
                    "environment variables must be set"
            )
        }
    }
}

// The ReaderScreenshotTest only captures screenshots when requested:
// ./gradlew :app:testDebugUnitTest --tests "*ReaderScreenshotTest" -Porator.screenshot=true
tasks.withType<Test>().configureEach {
    systemProperty("orator.screenshot", project.findProperty("orator.screenshot") ?: "")
}
