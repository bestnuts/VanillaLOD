import groovy.json.JsonSlurper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

plugins {
    java
}

group = "me.bestnuts"
version = "0.1.0"

val minecraftRelease = "26.3"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$minecraftRelease.build.237-stable")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

abstract class InstallPaper : DefaultTask() {

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:Internal
    abstract val runtimeDirectory: DirectoryProperty

    @TaskAction
    fun install() {
        val serverJar = runtimeDirectory.file("paper.jar").get().asFile
        val buildMarker = runtimeDirectory.file("paper.build").get().asFile
        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

        val build = try {
            latestBuild(client)
        } catch (e: Exception) {
            if (serverJar.exists()) {
                logger.warn("Paper build lookup failed, keeping existing paper.jar: ${e.message}")
                return
            }
            throw GradleException("Paper build lookup failed", e)
        }

        val buildId = "${minecraftVersion.get()}-${build.id}"
        if (serverJar.exists() && buildMarker.exists() && buildMarker.readText().trim() == buildId) {
            logger.lifecycle("Paper $buildId already installed")
            return
        }

        logger.lifecycle("Downloading Paper $buildId")
        val bytes = client.send(request(build.url), HttpResponse.BodyHandlers.ofByteArray()).body()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (sha256 != build.sha256) {
            throw GradleException("Paper $buildId checksum mismatch")
        }

        serverJar.parentFile.mkdirs()
        serverJar.writeBytes(bytes)
        buildMarker.writeText(buildId)
    }

    private data class Build(val id: Int, val url: String, val sha256: String)

    private fun latestBuild(client: HttpClient): Build {
        val uri = "https://fill.papermc.io/v3/projects/paper/versions/${minecraftVersion.get()}/builds/latest"
        val response = client.send(request(uri), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw GradleException("Fill API returned ${response.statusCode()}")
        }
        val json = JsonSlurper().parseText(response.body()) as Map<*, *>
        val server = (json["downloads"] as Map<*, *>)["server:default"] as Map<*, *>
        val checksums = server["checksums"] as Map<*, *>
        return Build((json["id"] as Number).toInt(), server["url"] as String, checksums["sha256"] as String)
    }

    private fun request(uri: String): HttpRequest =
        HttpRequest.newBuilder(URI.create(uri)).header("User-Agent", "VanillaLOD-build").GET().build()
}

val serverDirectory = layout.projectDirectory.dir("runtime")
val deployDirectory = layout.projectDirectory.dir("deploy")

val installPaper = tasks.register<InstallPaper>("installPaper") {
    group = "server"
    minecraftVersion = minecraftRelease
    runtimeDirectory = serverDirectory
}

val prepareRuntime = tasks.register<Copy>("prepareRuntime") {
    group = "server"
    dependsOn(installPaper)
    from(deployDirectory) {
        exclude("world*/**")
    }
    val serverRoot = serverDirectory.asFile
    from(deployDirectory) {
        include("world*/**")
        eachFile {
            if (serverRoot.resolve(path).exists()) {
                exclude()
            }
        }
    }
    into(serverDirectory)
}

val deployPlugin = tasks.register<Copy>("deployPlugin") {
    group = "server"
    dependsOn(prepareRuntime)
    from(tasks.jar)
    into(serverDirectory.dir("plugins"))
}

tasks.register<JavaExec>("runServer") {
    group = "server"
    dependsOn(deployPlugin)
    workingDir = serverDirectory.asFile
    classpath = files(serverDirectory.file("paper.jar"))
    mainClass = "io.papermc.paperclip.Main"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    jvmArgs("-Xms2G", "-Xmx2G")
    args("--nogui")
    standardInput = System.`in`
}
