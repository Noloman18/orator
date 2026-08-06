plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    api(libs.androidx.coroutines.core)
    api(libs.androidx.paging.common)
    testImplementation(libs.junit)
}
