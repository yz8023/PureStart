import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * v15.2 第一阶段：构建脚本简化（对应《PureStart_Review_and_Plan.md》步骤 1）。
 *
 * 改动要点：
 *  1. **源码不再手动映射目录**。Java（Hook 后端）、Kotlin（UI + repository）、res、
 *     AndroidManifest 全部落在 AGP 默认布局 `app/src/main/**` 下，`sourceSets` 里
 *     只剩「追加生成代码」这一项。
 *  2. **不再生成清单副本**。清单里已经去掉了 `package=`（改为 namespace）与 `<uses-sdk>`
 *     （改为 defaultConfig），所以 `app/src/main/AndroidManifest.xml` 可以直接作为 AGP
 *     的输入。原来「配置阶段写一份去 package 的清单」的逻辑整段删除。
 *  3. **不再用任务名字符串匹配挂依赖**。旧实现靠
 *     `tasks.matching { it.name.startsWith("process") && ... }` 猜哪些任务需要生成物，
 *     新增 AGP 任务时很容易漏挂（漏挂的表现就是 `mainManifest doesn't exist`）。
 *     现在只依赖 AGP 的入口任务 `preBuild`，它是所有变体任务图的祖先。
 *  4. 生成任务声明了 inputs / outputs（可增量、可配置缓存），只有 BuildInfo 的时间戳
 *     一项让它必须每次重跑。
 */
val repo = rootProject.projectDir
val generatedDir = layout.buildDirectory.dir("generated/purestart")

val prepareGeneratedSources by tasks.registering {
    group = "build"
    description = "生成 BuildInfo.java 与 META-INF/xposed（module.prop 版本号从清单派生）"
    val output = generatedDir.get().asFile
    val buildInfoFile = output.resolve("java/com/yz8023/purestart/BuildInfo.java")
    val xposedOutputDir = output.resolve("resources/META-INF/xposed")
    val manifestFile = repo.resolve("app/src/main/AndroidManifest.xml")
    val xposedSourceDir = repo.resolve("xposed")

    inputs.file(manifestFile)
    inputs.dir(xposedSourceDir)
    outputs.file(buildInfoFile)
    outputs.dir(xposedOutputDir)
    // BuildInfo 带构建时间戳，任何缓存都会让「构建时间」失真
    outputs.upToDateWhen { false }

    doLast {
        buildInfoFile.parentFile.mkdirs()
        // 允许外部固定时间戳：低内存主机上拆开跑 lint 与 R8 时需要可复现的产物
        val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val timestamp = System.getenv("PURESTART_BUILD_TIME")?.let {
            LocalDateTime.parse(it, timeFormat).format(timeFormat)
        } ?: LocalDateTime.now().format(timeFormat)
        buildInfoFile.writeText(
            "package com.yz8023.purestart; public final class BuildInfo { " +
                "public static final String BUILD_TIME = \"$timestamp\"; private BuildInfo() {} }"
        )

        // 模块元数据（module.prop / scope.list / java_init.list）随 APK 打包到 META-INF/xposed。
        val source = manifestFile.readText()
        val versionName = Regex("android:versionName=\"([^\"]+)\"").find(source)!!.groupValues[1]
        val versionCode = Regex("android:versionCode=\"([^\"]+)\"").find(source)!!.groupValues[1]
        xposedOutputDir.mkdirs()
        xposedSourceDir.listFiles()?.filter { it.isFile }?.forEach { file ->
            if (file.name != "module.prop") {
                file.copyTo(xposedOutputDir.resolve(file.name), overwrite = true)
            }
        }
        // module.prop 的版本必须与清单一致（scripts/check.py 第 2 项守门）
        val prop = xposedSourceDir.resolve("module.prop").readText()
            .replace(Regex("(?m)^version=.*$"), "version=v$versionName")
            .replace(Regex("(?m)^versionCode=.*$"), "versionCode=$versionCode")
        xposedOutputDir.resolve("module.prop").writeText(prop)
    }
}

android {
    namespace = "com.yz8023.purestart"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.yz8023.purestart"
        minSdk = 26
        targetSdk = 34
    }
    // 默认布局已经包含 java / kotlin / res / AndroidManifest.xml，这里只追加生成代码。
    sourceSets.getByName("main") {
        java.srcDir(generatedDir.get().asFile.resolve("java").absolutePath)
        resources.srcDir(generatedDir.get().asFile.resolve("resources").absolutePath)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }

    // 沿用 build.sh 的签名约定：必须显式提供现有密钥，绝不静默生成新的 release key。
    val releaseKeyPath = System.getenv("KEYSTORE_PATH")
    val configuredSigning = if (!releaseKeyPath.isNullOrBlank()) {
        signingConfigs.create("providedRelease") {
            storeFile = repo.resolve(releaseKeyPath)
            storePassword = System.getenv("KEYSTORE_PASS") ?: "android"
            keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("KEY_PASS") ?: "android"
        }
    } else null

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = configuredSigning
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// preBuild 是所有变体任务图的第一个任务：生成物在清单合并、资源合并、编译之前就位。
tasks.named("preBuild") { dependsOn(prepareGeneratedSources) }

dependencies {
    compileOnly(files(repo.resolve("libs/api.jar")))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    testImplementation(libs.junit)
    testImplementation(files(repo.resolve("libs/api.jar")))
}

// 不允许误产出未签名的 release APK（debug 不需要该密钥）。
tasks.matching { it.name == "packageRelease" }.configureEach {
    doFirst {
        val path = System.getenv("KEYSTORE_PATH")
        if (path.isNullOrBlank() || !repo.resolve(path).isFile) {
            throw GradleException("KEYSTORE_PATH must point to the existing release keystore; no new key will be generated")
        }
    }
}
