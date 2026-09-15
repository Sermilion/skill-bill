import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    alias(libs.plugins.kotlin.jvm)

    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit)

    intellijPlatform {

        intellijIdea(providers.gradleProperty("platformVersion"))

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        id = providers.gradleProperty("pluginId")
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("version")
        description = """
            Shows Skill Bill feature-work status in IntelliJ IDEA. Consumes the
            versioned <code>skill-bill work status</code> contract, and from the
            status details popup invokes exactly two mutating verbs,
            <code>skill-bill goal stop</code> and <code>skill-bill goal pause</code>,
            for the active goal. It reads no Skill Bill databases and terminates no
            processes itself.
        """.trimIndent()

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }

        vendor {
            name = providers.gradleProperty("pluginVendor")
            url = providers.gradleProperty("pluginVendorUrl")
        }
    }

    pluginVerification {
        ides {

            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2.5")
            create(IntelliJPlatformType.IntellijIdea, "2026.2")
        }
        failureLevel.set(
            listOf(
                VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
                VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
            ),
        )
    }
}

tasks {
    wrapper {
        gradleVersion = "9.3.0"
    }

    withType<Test> {


        useJUnit()
    }
}

tasks.register("printOwnedTasks") {
    group = "help"
    description = "Lists packaging and verification entry points for contributors."
    doLast {
        println("check, buildPlugin, runIde, verifyPlugin")
    }
}
