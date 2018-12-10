package com.novoda.staticanalysis.internal.spotbugs

import com.github.spotbugs.SpotBugsExtension
import com.github.spotbugs.SpotBugsPlugin
import com.github.spotbugs.SpotBugsTask
import com.novoda.staticanalysis.Violations
import com.novoda.staticanalysis.internal.CodeQualityConfigurator
import com.novoda.staticanalysis.internal.findbugs.CollectFindbugsViolationsTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSet

import java.nio.file.Path

class SpotBugsConfigurator extends CodeQualityConfigurator<SpotBugsTask, SpotBugsExtension> {

    SpotBugsConfigurator(Project project, Violations violations, Task evaluateViolations) {
        super(project, violations, evaluateViolations)
    }

    @Override
    protected String getToolName() {
        'spotbugs'
    }

    @Override
    protected Object getToolPlugin() {
        SpotBugsPlugin
    }

    @Override
    protected Class<SpotBugsExtension> getExtensionClass() {
        SpotBugsExtension
    }

    @Override
    protected Class<?> getTaskClass() {
        SpotBugsTask
    }

    @Override
    protected void configureAndroidVariant(variant) {
        SpotBugsTask task = project.tasks.maybeCreate("spotbugs${variant.name.capitalize()}", SpotBugsTask)
        List<File> androidSourceDirs = variant.sourceSets.collect { it.javaDirectories }.flatten()
        task.with {
            description = "Run SpotBugs analysis for ${variant.name} classes"
            source = androidSourceDirs
            classpath = variant.javaCompile.classpath
            exclude '**/*.kt'
        }
        sourceFilter.applyTo(task)
        task.conventionMapping.map("classes") {
            List<String> includes = createIncludePatterns(task.source, androidSourceDirs)
            getAndroidClasses(variant, includes)
        }
        task.dependsOn variant.javaCompile
    }

    private FileCollection getAndroidClasses(Object variant, List<String> includes) {
        includes.isEmpty() ? project.files() : project.fileTree(variant.javaCompile.destinationDir).include(includes)
    }

    @Override
    protected void configureJavaProject() {
        project.afterEvaluate {
            project.sourceSets.each { SourceSet sourceSet ->
                String taskName = sourceSet.getTaskName(toolName, null)
                SpotBugsTask task = project.tasks.findByName(taskName)
                if (task != null) {
                    sourceFilter.applyTo(task)
                    task.conventionMapping.map("classes", {
                        List<File> sourceDirs = sourceSet.allJava.srcDirs.findAll {
                            it.exists()
                        }.toList()
                        List<String> includes = createIncludePatterns(task.source, sourceDirs)
                        getJavaClasses(sourceSet, includes)
                    })
                    task.exclude '**/*.kt'
                }
            }
        }
    }

    private static List<String> createIncludePatterns(FileCollection sourceFiles, List<File> sourceDirs) {
        List<Path> includedSourceFilesPaths = sourceFiles.matching { '**/*.java' }.files.collect {
            it.toPath()
        }
        List<Path> sourceDirsPaths = sourceDirs.collect { it.toPath() }
        createRelativePaths(includedSourceFilesPaths, sourceDirsPaths)
                .collect { Path relativePath -> (relativePath as String) - '.java' + '*' }
    }

    private static List<Path> createRelativePaths(List<Path> includedSourceFiles, List<Path> sourceDirs) {
        includedSourceFiles.collect { Path sourceFile ->
            sourceDirs
                    .findAll { Path sourceDir -> sourceFile.startsWith(sourceDir) }
                    .collect { Path sourceDir -> sourceDir.relativize(sourceFile) }
        }
        .flatten()
    }

    private FileCollection getJavaClasses(SourceSet sourceSet, List<String> includes) {
        includes.isEmpty() ? project.files() : createClassesTreeFrom(sourceSet).include(includes)
    }

    /**
     * The simple "classes = sourceSet.output" may lead to non-existing resources directory
     * being passed to FindBugs Ant task, resulting in an error
     * */
    private ConfigurableFileTree createClassesTreeFrom(SourceSet sourceSet) {
        project.fileTree(sourceSet.output.classesDir, {
            it.builtBy(sourceSet.output)
        })
    }

    @Override
    protected void configureReportEvaluation(SpotBugsTask task, Violations violations) {
        task.ignoreFailures = true
        task.reports.xml.enabled = true
        task.reports.html.enabled = false

        def collectViolations = createViolationsCollectionTask(task, violations)

        evaluateViolations.dependsOn collectViolations
        collectViolations.dependsOn task
    }

    private CollectFindbugsViolationsTask createViolationsCollectionTask(SpotBugsTask spotBugs, Violations violations) {
        def task = project.tasks.maybeCreate("collect${spotBugs.name.capitalize()}Violations", CollectFindbugsViolationsTask)
        task.xmlReportFile = spotBugs.reports.xml.destination
        task.violations = violations
        task
    }
}
