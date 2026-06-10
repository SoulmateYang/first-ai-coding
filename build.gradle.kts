plugins {
    java
    application
    jacoco
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.firstcode"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.assertj:assertj-core:3.25.1")
}

application {
    mainClass = "com.firstcode.app.Main"
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("all")
    mergeServiceFiles()
    manifest {
        attributes(
            "Main-Class" to "com.firstcode.app.Main"
        )
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// 行覆盖率门禁:解析 jacoco XML,若 < 80% 则 fail。
// 通过根任务覆盖行统计累加求比例,简单可靠,避免引入企业版插件。
val coverageMin = 0.80
tasks.register("verifyCoverage") {
    group = "verification"
    description = "校验 JaCoCo 行覆盖率不低于 80%"
    dependsOn(tasks.jacocoTestReport)
    doLast {
        val reportFile = file("$buildDir/reports/jacoco/test/jacocoTestReport.xml")
        require(reportFile.exists()) { "JaCoCo XML not found at $reportFile" }
        val text = reportFile.readText()
        val regex = Regex("""counter type="LINE" missed="(\d+)" covered="(\d+)"""")
        var missed = 0L
        var covered = 0L
        for (m in regex.findAll(text)) {
            missed += m.groupValues[1].toLong()
            covered += m.groupValues[2].toLong()
        }
        val total = missed + covered
        require(total > 0) { "no LINE counters found in $reportFile" }
        val rate = covered.toDouble() / total
        println("JaCoCo line coverage: ${"%.1f".format(rate * 100)}% (${covered}/${total})")
        require(rate >= coverageMin) {
            "line coverage ${"%.1f".format(rate * 100)}% < ${(coverageMin * 100).toInt()}%"
        }
    }
}

tasks.check {
    dependsOn("verifyCoverage")
}
