import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

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

apply(plugin = "jacoco")

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

private val jacocoExclusions = listOf(
    "**/R.class",
    "**/R\$*.class",
    "**/BuildConfig.class",
    "**/Manifest*.*",
    "**/*_Factory.class",
    "**/*_MembersInjector.class",
    "**/*_HiltModules*.class",
    "**/Hilt_*.class",
    "**/*_GeneratedInjector.class",
    "**/*_Impl*.class",
    "**/*Factory.class",
    "**/*Factory\$*.class",
    "**/*\$DefaultImpls.class",
    "**/hilt_aggregated_deps/**",
    "**/dagger/hilt/**",
    "**/*_HiltComponents*.class",
    "**/*_ComponentTreeDeps.class",
    // Android/Compose entry points require instrumented or screenshot tests rather than JVM tests.
    "**/MainActivity*",
    "**/OratorApplication*",
    "**/OratorApp*",
    "**/ComposableSingletons*",
    "**/ui/theme/**",
    // These classes are platform-owned lifecycle/notification adapters around tested playback logic.
    "**/NarrationService*",
    "**/OratorNotificationProvider*"
)

private fun Project.jacocoClassDirectories(): FileTree = files(
    layout.buildDirectory.dir("classes/kotlin/main"),
    layout.buildDirectory.dir("classes/java/main"),
    layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
    layout.buildDirectory.dir("intermediates/classes/debug/transformDebugClassesWithAsm/dirs"),
    layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")
).asFileTree.matching {
    exclude(jacocoExclusions)
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        apply(plugin = "jacoco")
    }
    pluginManager.withPlugin("com.android.application") {
        apply(plugin = "jacoco")
    }
    pluginManager.withPlugin("com.android.library") {
        apply(plugin = "jacoco")
    }

    plugins.withId("jacoco") {
        extensions.configure<JacocoPluginExtension> {
            toolVersion = "0.8.14"
        }
        tasks.withType<Test>().configureEach {
            extensions.configure<JacocoTaskExtension> {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }
    }
}

val coverageProjects = listOf(":app", ":data", ":domain", ":playback").map(::project)
val coverageTestTasks = coverageProjects.map { module ->
    if (module.path == ":domain") "${module.path}:test" else "${module.path}:testDebugUnitTest"
}
val coverageExecutionData = coverageProjects.flatMap { module ->
    if (module.path == ":domain") {
        listOf(module.layout.buildDirectory.file("jacoco/test.exec"))
    } else {
        listOf(
            module.layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"),
            module.layout.buildDirectory.file(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
            )
        )
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates an aggregate JaCoCo report for JVM and Android unit tests."
    dependsOn(coverageTestTasks)
    jacocoClasspath = project.configurations.getByName("jacocoAnt")

    sourceDirectories.from(coverageProjects.flatMap { module ->
        listOf(
            module.file("src/main/kotlin"),
            module.file("src/main/java")
        )
    })
    classDirectories.from(coverageProjects.map(Project::jacocoClassDirectories))
    executionData.from(coverageExecutionData)

    reports {
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoTestReport/html"))
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoTestReport/jacocoTestReport.xml"))
        csv.required.set(false)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    group = "verification"
    description = "Verifies that aggregate unit-test line coverage is at least 50%."
    dependsOn(coverageTestTasks)
    jacocoClasspath = project.configurations.getByName("jacocoAnt")

    sourceDirectories.from(coverageProjects.flatMap { module ->
        listOf(
            module.file("src/main/kotlin"),
            module.file("src/main/java")
        )
    })
    classDirectories.from(coverageProjects.map(Project::jacocoClassDirectories))
    executionData.from(coverageExecutionData)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}
