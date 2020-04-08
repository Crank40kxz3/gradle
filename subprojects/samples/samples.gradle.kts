import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty

plugins {
    gradlebuild.internal.java
    gradlebuild.classycle
}

dependencies {
    integTestImplementation(project(":baseServices"))
    integTestImplementation(project(":coreApi"))
    integTestImplementation(project(":processServices"))
    integTestImplementation(project(":persistentCache"))
    integTestImplementation(library("groovy"))
    integTestImplementation(library("slf4j_api"))
    integTestImplementation(library("guava"))
    integTestImplementation(library("ant"))
    integTestImplementation(testLibrary("sampleCheck")) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
        exclude(module = "slf4j-simple")
    }
    integTestImplementation(testFixtures(project(":core")))
}
configurations.integTestRuntimeOnly.get().withDependencies {
    addAll(rootProject.configurations.testRuntime.get().allDependencies)
}

val integTestTasks: DomainObjectCollection<IntegrationTest> by extra
integTestTasks.configureEach {
    libsRepository.required = true
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}
