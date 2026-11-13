plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                // 8080 belongs to the Firestore emulator; the dev server takes 8081.
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer()).copy(port = 8081, open = false)
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))

            implementation(libs.compose.ui)
            implementation(libs.compose.runtime)
        }
    }
}
// A web build meant for hosting must use the real Firebase project, never a local emulator:
// an emulator build makes every visitor's browser try to reach their own computer (localhost).
val firebaseConfigText: Provider<String> = providers
    .fileContents(rootProject.layout.projectDirectory.file("firebase/app-config.json"))
    .asText
    .orElse("")
tasks.matching { it.name in setOf("compileProductionExecutableKotlinJs", "jsBrowserProductionWebpack", "jsBrowserDistribution") }.configureEach {
    val config = firebaseConfigText
    doFirst {
        val host = Regex("\"emulatorHost\"\\s*:\\s*\"([^\"]*)\"").find(config.get())?.groupValues?.get(1).orEmpty()
        if (host.isNotBlank()) {
            throw GradleException(
                "firebase/app-config.json points at the local emulator (\"$host\"). " +
                    "Put the real project's config back before building the web version for hosting.",
            )
        }
    }
}
