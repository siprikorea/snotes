import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// OAuth 클라이언트 값은 공개 저장소에 올리지 않도록 local.properties(git 제외)에서 읽어 코드로 생성한다.
// (configuration cache 때문에 doLast 안에서는 스크립트 수준 값을 참조하지 않는다.)
val generateOAuthSecrets = tasks.register("generateOAuthSecrets") {
    val keys = mapOf(
        "DESKTOP_CLIENT_ID" to "snotes.oauth.desktopClientId",
        "DESKTOP_CLIENT_SECRET" to "snotes.oauth.desktopClientSecret",
        "IOS_CLIENT_ID" to "snotes.oauth.iosClientId",
        "WEB_CLIENT_ID" to "snotes.oauth.webClientId",
    )
    val values = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText
        .map { text -> Properties().apply { load(StringReader(text)) } }
        .orElse(Properties())
        .map { props -> keys.mapValues { (_, key) -> props.getProperty(key, "").trim() } }
    val outputDir = layout.buildDirectory.dir("generated/oauth/commonMain/kotlin")
    inputs.property("values", values)
    outputs.dir(outputDir)
    doLast {
        fun literal(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        val body = values.get().entries.joinToString("\n") { (k, v) -> "    const val $k = ${literal(v)}" }
        val file = outputDir.get().file("com/siprikorea/snotes/config/OAuthSecrets.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// 자동 생성 파일(:composeApp:generateOAuthSecrets). 값은 local.properties 의 snotes.oauth.* 에서 온다.
            |package com.siprikorea.snotes.config
            |
            |internal object OAuthSecrets {
            |$body
            |}
            |""".trimMargin()
        )
    }
}

kotlin {
    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.siprikorea.snotes.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources { enable = true }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateOAuthSecrets)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.kotlinx.io.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.play.services.auth)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.okhttp)
            }
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(libs.ktor.client.js)
        }
    }
}

compose.resources {
    packageOfResClass = "com.siprikorea.snotes.resources"
}

compose.desktop {
    application {
        mainClass = "com.siprikorea.snotes.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "S Notes"
            packageVersion = "1.0.0"
            macOS { bundleID = "com.siprikorea.snotes" }
        }
    }
}
