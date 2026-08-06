import org.gradle.api.artifacts.ProjectDependency

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

/** Fails the build if a lower layer starts depending on a presentation or sibling feature module. */
val checkModuleDependencyRules = tasks.register("checkModuleDependencyRules") {
    doLast {
        val forbidden = mapOf(
            ":domain" to setOf(":app", ":data", ":playback", ":designsystem"),
            ":data" to setOf(":app", ":playback", ":designsystem"),
            ":playback" to setOf(":app", ":data", ":designsystem"),
            ":designsystem" to setOf(":app", ":domain", ":data", ":playback")
        )
        val declarationConfigurations = setOf(
            "api", "implementation", "compileOnly", "runtimeOnly",
            "debugImplementation", "releaseImplementation",
            "testImplementation", "androidTestImplementation"
        )
        val violations = forbidden.flatMap { (modulePath, blockedPaths) ->
            project(modulePath).configurations
                .filter { it.name in declarationConfigurations }
                .flatMap { configuration ->
                    configuration.dependencies.withType<ProjectDependency>()
                        .filter { it.path in blockedPaths }
                        .map { dependency -> "$modulePath -> ${dependency.path}" }
                }
        }
        check(violations.isEmpty()) {
            "Forbidden module dependencies detected: ${violations.joinToString() }"
        }
    }
}
