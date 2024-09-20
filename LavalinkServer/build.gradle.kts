import org.apache.tools.ant.filters.ReplaceTokens
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    application
    `maven-publish`
}

apply(plugin = "org.springframework.boot")
apply(plugin = "com.gorylenko.gradle-git-properties")
apply(plugin = "org.ajoberstar.grgit")
apply(plugin = "com.adarshr.test-logger")
apply(plugin = "kotlin")
apply(plugin = "kotlin-spring")
apply(from = "../repositories.gradle")

val archivesBaseName = "Lavalink"
group = "dev.arbjerg.lavalink"

description = "Play audio to discord voice channels"

application {
    mainClass.set("lavalink.server.Launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

configurations {
    compileOnly {
        extendsFrom(annotationProcessor.get())
    }
}

dependencies {
    implementation(projects.protocol)
    implementation(projects.pluginApi) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }

    implementation(libs.bundles.metrics)
    implementation(libs.bundles.spring) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }

    implementation(libs.koe) {
        // This version of SLF4J does not recognise Logback 1.2.3
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.koe.udpqueue) {
        exclude(module = "lava-common")
    }
    implementation(libs.bundles.udpqueue.natives) {
        exclude(group = "com.sedmelluq", module = "lava-common")
    }

    implementation(libs.lavaplayer)
    implementation(libs.lavaplayer.ip.rotator)

    implementation(libs.lavadsp)
    implementation(libs.kotlin.reflect)
    implementation(libs.logback)
    implementation(libs.sentry.logback)
    implementation(libs.oshi) {
        // This version of SLF4J does not recognise Logback 1.2.3
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.json)

    compileOnly(libs.spotbugs)

    testImplementation(libs.spring.boot.test)
}

tasks {
    build {
        doLast {
            println("Version: $version")
        }
    }

    processResources {
        val tokens = mapOf(
            "project.version"    to project.version,
            "project.groupId"    to project.group,
            "project.artifactId" to "Lavalink-Server",
            "env.BUILD_TIME"     to System.currentTimeMillis().toString()
        )

        filter(ReplaceTokens::class, mapOf("tokens" to tokens))
        copy {
            from("application.yml.example")
            into("$buildDir/resources/main")
        }
    }

    // https://stackoverflow.com/questions/41444916/multiple-artifacts-issue-with-deploying-zip-to-nexus
    named<AbstractArchiveTask>("bootDistTar") {
        archiveClassifier.set("bootTar")
    }

    named<AbstractArchiveTask>("bootDistZip") {
        archiveClassifier.set("bootZip")
    }

    named<Test>("test") {
        useJUnitPlatform()
    }

    val nativesJar = create<Jar>("lavaplayerNativesJar") {
        // Only add musl natives
        from(configurations.runtimeClasspath.get().find { it.name.contains("lavaplayer-natives") }?.let { file ->
            zipTree(file).matching {
                include {
                    it.path.contains("musl")
                }
            }
        })

        archiveBaseName.set("lavaplayer-natives")
        archiveClassifier.set("musl")
    }


    withType<BootJar> {
        archiveFileName.set("Lavalink.jar")

        if (findProperty("targetPlatform") == "musl") {
            archiveFileName.set("Lavalink-musl.jar")
            // Exclude base dependency jar
            exclude {
                it.name.contains("lavaplayer-natives-fork") || (it.name.contains("udpqueue-native-") && !it.name.contains("musl"))
            }

            // Add custom jar
            classpath(nativesJar.outputs)
            dependsOn(nativesJar)
        }
    }

    withType<BootRun> {
        dependsOn("compileTestJava")

        //pass in custom jvm args
        // source: https://stackoverflow.com/a/25079415
        // example: ./gradlew bootRun -PjvmArgs="--illegal-access=debug -Dwhatever=value"
        if (project.hasProperty("jvmArgs")) {
            val args = project.property("jvmArgs")
                .toString()
                .split("\\s".toPattern())

            jvmArgs?.addAll(args)
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("LavalinkServer") {
            from(project.components["java"])

            pom {
                name.set("Lavalink Server")
                description.set("Lavalink Server")
                url.set("https://github.com/lavalink-devs/lavalink")

                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://github.com/lavalink-devs/Lavalink/blob/master/LICENSE")
                    }
                }

                developers {
                    developer {
                        id.set("freyacodes")
                        name.set("Freya Arbjerg")
                        url.set("https://www.arbjerg.dev")
                    }
                }

                scm {
                    connection.set("scm:git:ssh://github.com/lavalink-devs/lavalink.git")
                    developerConnection.set("scm:git:ssh://github.com/lavalink-devs/lavalink.git")
                    url.set("https://github.com/lavalink-devs/lavalink")
                }
            }
        }
    }
}
