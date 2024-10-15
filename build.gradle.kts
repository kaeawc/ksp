import com.google.devtools.ksp.configureKtlint
import com.google.devtools.ksp.configureKtlintApplyToIdea
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.security.MessageDigest

val sonatypeUserName: String? by project
val sonatypePassword: String? by project

val kotlinBaseVersion: String? by project
if (extra.has("kspOnlyVersion") && kotlinBaseVersion != null) {
    val kspOnlyVersion = extra.get("kspOnlyVersion") as String
    extra.set("kspVersion", "$kotlinBaseVersion-$kspOnlyVersion")
}

if (!extra.has("kspVersion")) {
    extra.set("kspVersion", "2.0.255")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap/")
}

plugins {
    kotlin("jvm")
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"

    // Adding plugins used in multiple places to the classpath for centralized version control
    id("com.github.johnrengelman.shadow") version "7.1.2" apply false
    id("org.jetbrains.dokka") version "1.9.20" apply false
}

nexusPublishing {
    packageGroup.set("com.google.devtools.ksp")
    repositories {
        sonatype {
            username.set(sonatypeUserName)
            password.set(sonatypePassword)
        }
    }
}

version = rootProject.extra.get("kspVersion") as String

configureKtlintApplyToIdea()
subprojects {
    group = "com.google.devtools.ksp"
    version = rootProject.extra.get("kspVersion") as String
    configureKtlint()
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap/")
        maven("https://www.jetbrains.com/intellij-repository/releases")
    }
    pluginManager.withPlugin("maven-publish") {
        val publishExtension = extensions.getByType<PublishingExtension>()
        publishExtension.repositories {
            if (extra.has("outRepo")) {
                val outRepo = extra.get("outRepo") as String
                maven {
                    url = File(outRepo).toURI()
                }
            } else {
                mavenLocal()
            }
            maven {
                name = "test"
                url = uri("${rootProject.layout.buildDirectory.get().asFile}/repos/test")
            }
        }
        publishExtension.publications.whenObjectAdded {
            check(this is MavenPublication) {
                "unexpected publication $this"
            }
            val publication = this
            publication.pom {
                url.set("https://goo.gle/ksp")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("KSP Team")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/google/ksp.git")
                    developerConnection.set("scm:git:https://github.com/google/ksp.git")
                    url.set("https://github.com/google/ksp")
                }
            }
        }
    }

    val compileJavaVersion = JavaLanguageVersion.of(17)

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        configure<JavaPluginExtension> {
            toolchain.languageVersion.set(compileJavaVersion)
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget = JvmTarget.JVM_11
                languageVersion.set(KotlinVersion.KOTLIN_1_9)
                apiVersion.set(languageVersion)
            }
            jvmToolchain {
                languageVersion = compileJavaVersion
            }
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.freeCompilerArgs.add("-Xskip-prerelease-check")
    }


// Add a task to generate SHA-1 and MD5 checksums for the published files
    tasks.withType<PublishToMavenLocal>().configureEach {
        finalizedBy("generateChecksums")
    }

    tasks.register("generateChecksums") {
        doLast {
            println("Generating checksums for ${project.name}")
            val repoDir = file("${System.getProperty("user.home")}/.m2/repository/com/google/devtools/ksp/${project.name}/2.0.255/")

            repoDir.listFiles()?.filter { it.name.endsWith(".pom") || it.name.endsWith(".jar") }?.forEach { file ->
                // Generate SHA-1 checksum
                val sha1File = File(file.absolutePath + ".sha1")
                sha1File.writeText(file.inputStream().use { MessageDigest.getInstance("SHA-1").digest(it.readBytes()).joinToString("") { byte -> "%02x".format(byte) } })

                // Generate MD5 checksum
                val md5File = File(file.absolutePath + ".md5")
                md5File.writeText(file.inputStream().use { MessageDigest.getInstance("MD5").digest(it.readBytes()).joinToString("") { byte -> "%02x".format(byte) } })
            }
        }
    }
}
