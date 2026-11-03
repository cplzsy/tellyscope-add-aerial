// SPDX-License-Identifier: GPL-3.0-only

pluginManagement {
    repositories {
        // 阿里云镜像优先（本机构建直连官方源不稳定），原仓库保留为回退
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        // Kadb's SPAKE2 dependency (wireless-debugging pairing) is only published on JitPack.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.Flyfish233") }
        }
    }
}

rootProject.name = "tellyscope"
include(":app", ":adb", ":cli")
