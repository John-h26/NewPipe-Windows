/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.compose.multiplatform)
}

val appVersion = providers.gradleProperty("npw.version").get()

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.coroutines.swing)

    implementation(libs.newpipe.extractor)
    implementation(libs.newpipe.nanojson)
    implementation(libs.squareup.okhttp)
    implementation(libs.jsoup)
    implementation(libs.vlcj)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}

// Embarque un VLC portable dans le MSI si -PvlcDir=<dossier VLC> est fourni
// (par défaut : ../_tools/vlc-*, le VLC portable de développement)
val vlcSource: Provider<Directory> = providers.gradleProperty("vlcDir")
    .map { rootProject.layout.projectDirectory.dir(it) }
    .orElse(provider {
        rootProject.layout.projectDirectory.dir("_tools").asFile
            .listFiles { f -> f.isDirectory && f.name.startsWith("vlc-") }
            ?.firstOrNull()
            ?.let { rootProject.layout.projectDirectory.dir("_tools/${it.name}") }
            ?: rootProject.layout.projectDirectory.dir("_tools/vlc")
    })
val vlcResources = layout.buildDirectory.dir("vlc-resources")

val bundleVlc = tasks.register<Sync>("bundleVlc") {
    from(vlcSource) {
        include("libvlc.dll", "libvlccore.dll", "plugins/**")
    }
    into(vlcResources.map { it.dir("windows/vlc") })
}

compose.desktop {
    application {
        mainClass = "net.newpipe.app.MainKt"
        jvmArgs += listOf(
            "-Dnpw.version=$appVersion",
            // Console en UTF-8 (run.bat passe la console en page de code 65001)
            "-Dstdout.encoding=UTF-8",
            "-Dstderr.encoding=UTF-8"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "NewPipe Windows"
            packageVersion = appVersion
            description = "Client libre YouTube, PeerTube, SoundCloud pour Windows"
            vendor = "NewPipe Windows"
            licenseFile.set(rootProject.file("LICENSE"))
            appResourcesRootDir.set(vlcResources)

            // :desktopApp:suggestRuntimeModules, plus java.scripting (moteur Rhino de l'extracteur)
            // et jdk.localedata (sans lui, dates et nombres restent en anglais : « 2023 Jun 21 »)
            modules(
                "java.instrument",
                "java.net.http",
                "java.prefs",
                "java.scripting",
                "jdk.dynalink",
                "jdk.httpserver",
                "jdk.localedata",
                "jdk.unsupported",
            )

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                menuGroup = "NewPipe Windows"
                upgradeUuid = "6f1d3a52-6c1b-4d3e-9a0e-2b7f9c4e8d11"
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(bundleVlc)
}
