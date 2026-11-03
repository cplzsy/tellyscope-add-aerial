// SPDX-License-Identifier: GPL-3.0-only

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Desktop smoke test for :adb against a real TV; not shipped.
dependencies {
    implementation(project(":adb"))
    runtimeOnly(libs.conscrypt.openjdk.uber) // Kadb pairing on the JVM
}

application {
    mainClass = "app.tellyscope.cli.MainKt"
}
