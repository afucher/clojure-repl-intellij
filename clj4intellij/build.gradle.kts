fun properties(key: String) = project.findProperty(key).toString()

plugins {
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("dev.clojurephant.clojure") version "0.7.0"
    id("org.jetbrains.intellij") version "1.15.0"
}

group = properties("group")
version = properties("version")

publishing {
    repositories {
        maven {
            // change to point to your repo, e.g. http://my.org/repo
            url = uri("https://repo.clojars.org")
            credentials {
                username = System.getenv("CLOJARS_USERNAME")
                password = System.getenv("CLOJARS_PASSWORD")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("http://www.opensource.org/licenses/mit-license.php")
                    }
                }
            }
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "Clojars"
        url = uri("https://repo.clojars.org")
    }
}

dependencies {
    implementation ("org.clojure:clojure:1.11.1")
    implementation ("com.rpl:proxy-plus:0.0.9")
    implementation ("camel-snake-kebab:camel-snake-kebab:0.4.3")
    implementation ("nrepl:nrepl:1.0.0")
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src/main/java"))
        resources.setSrcDirs(listOf("src/main/resources"))
    }
}

// Useful to override another IC platforms from env
val platformVersion = System.getenv("PLATFORM_VERSION") ?: properties("platformVersion")
val platformPlugins = System.getenv("PLATFORM_PLUGINS") ?: properties("platformPlugins")

intellij {
    pluginName.set(properties("name"))
    version.set(platformVersion)
    type.set(properties("platformType"))
    updateSinceUntilBuild.set(false)
}

java {
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
}


tasks.register("classpath") {
    doFirst {
        println(sourceSets["main"].compileClasspath.asPath)
    }
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "11"
            apiVersion = "1.5"
            languageVersion = "1.5"
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
    }

    buildSearchableOptions {
        enabled = false
    }
}

clojure.builds.named("main") {
    classpath.from(sourceSets.main.get().runtimeClasspath.asPath)
    checkAll()
    aotAll()
    reflection.set("fail")
}
