import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.gradleup.shadow").version("9.4.0")
}

buildscript {
    dependencies {
        classpath("org.yaml:snakeyaml:2.0")
    }
}

val defaultPaperImplementations = listOf(
    "v1_18_R2",
    "v1_19_R3",
    "v1_20_R1",
    "v1_20_R2",
    "v1_20_R3",
    "v1_20_R4",
    "v1_21_R1",
    "v1_21_R2",
    "v1_21_R3",
    "v1_21_R4",
    "v1_21_R5",
    "v1_21_R6",
    "v1_21_R7",
    "v26_1"
)

val defaultSpigotImplementations = listOf(
    "Spigotv1_21_R6",
    "Spigotv1_21_R7"
)

fun parseImplementationSelection(propertyName: String): List<String>? {
    val raw = findProperty(propertyName)?.toString()?.trim().orEmpty()
    if (raw.isEmpty()) {
        return null
    }

    return raw.split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
}

fun validateImplementationSelection(propertyName: String, selected: List<String>, allowed: List<String>) {
    val unknown = selected.filter { it !in allowed }
    require(unknown.isEmpty()) {
        "Unknown values for -P$propertyName: ${unknown.joinToString(", ")}. " +
        "Allowed values: ${allowed.joinToString(", ")}"
    }
}

val selectedPaperImplementations = parseImplementationSelection("paperImplementations")
    ?: defaultPaperImplementations

val selectedSpigotImplementations = parseImplementationSelection("spigotImplementations")
    ?: defaultSpigotImplementations

validateImplementationSelection(
    "paperImplementations",
    selectedPaperImplementations,
    defaultPaperImplementations
)
validateImplementationSelection(
    "spigotImplementations",
    selectedSpigotImplementations,
    defaultSpigotImplementations
)

dependencies {
    implementation(project(":common"))
    selectedPaperImplementations.forEach { implementation(project(":implementation:$it")) }
    implementation("com.github.AvarionMC:yaml:1.1.7")
	
	if(project.hasProperty("includeSpigot")){
		// Also change the ones in shadowJar. Remember to have --remapped in Buildtools.
        selectedSpigotImplementations.forEach { implementation(project(":implementation:$it")) }
	}
}

tasks.shadowJar {
    //This will break all versions before 1.21.9.
    // Can't do much about that.
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }

	//Make the spigot build shadow itself
	if(project.hasProperty("includeSpigot")){
        selectedSpigotImplementations.forEach {
            dependsOn(":implementation:$it:remap")
        }
	}

    doFirst {
        val yamlFile = file("${rootProject.projectDir}/common/src/main/resources/plugin.yml")
        val yaml = org.yaml.snakeyaml.Yaml()
        val config = yaml.load<Map<String, Any>>(yamlFile.inputStream())

        val timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        )

        archiveBaseName.set(config["name"].toString())
        archiveVersion.set("${config["version"]}-$timestamp")
        archiveClassifier.set("")
    }

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    relocate("io.papermc.lib", "org.terraform.lib")
}

tasks.register<Copy>("deploy") {
    dependsOn(tasks.named("shadowJar"))

    from(layout.buildDirectory.dir("libs"))
    include("*.jar")
    into(rootProject.projectDir)

    doNotTrackState("Disable state tracking due to file access issues")
}
