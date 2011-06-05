/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.ide.idea

import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.gradle.plugins.ide.idea.model.Project
import org.gradle.util.DeprecationLogger

/**
 * Generates an IDEA project file for root project *only*. If you want to fine tune the idea configuration
 * <p>
 * At this moment nearly all configuration is done via {@link IdeaProject}.
 *
 * @author Hans Dockter
 */
public class GenerateIdeaProject extends XmlGeneratorTask<Project> {

    /**
     * idea project model
     */
    IdeaProject ideaProject;

    @Override protected void configure(Project xmlModule) {
        getIdeaProject().mergeXmlProject(xmlModule)
    }

    @Override Project create() {
        def project = new Project(xmlTransformer, ideaProject.pathFactory)
        return project
    }

    /**
     * The subprojects that should be mapped to modules in the ipr file. The subprojects will only be mapped if the Idea plugin has been
     * applied to them.
     * <p>
     * Deprecated. Please use #idea.project.modules. See examples in {@link IdeaProject}.
     */
    @Deprecated
    Set<org.gradle.api.Project> getSubprojects() {
        DeprecationLogger.nagUser("ideaProject.subprojects doesn't do anything at this moment. Please use idea.project.modules instead.")
    }

    @Deprecated
    void setSubprojects(Set<org.gradle.api.Project> subprojects) {
        DeprecationLogger.nagUser("ideaProject.subprojects doesn't do anything at this moment. Please use idea.project.modules instead.")
    }

    /**
     * The java version used for defining the project sdk.
     * <p>
     * Deprecated. Please use #idea.project.javaVersion. See examples in {@link IdeaProject}.
     */
    @Deprecated
    String getJavaVersion() {
        DeprecationLogger.nagUser("ideaProject.javaVersion", "idea.project.javaVersion")
        ideaProject.javaVersion
    }

    @Deprecated
    void setJavaVersion(String javaVersion) {
        DeprecationLogger.nagUser("ideaProject.javaVersion", "idea.project.javaVersion")
        ideaProject.javaVersion = javaVersion
    }

    /**
     * The wildcard resource patterns.
     * <p>
     * Deprecated. Please use #idea.project.wildcards. See examples in {@link IdeaProject}.
     */
    @Deprecated
    Set getWildcards() {
        DeprecationLogger.nagUser("ideaProject.wildcards", "idea.project.wildcards")
        ideaProject.wildcards
    }

    @Deprecated
    void setWildcards(Set wildcards) {
        DeprecationLogger.nagUser("ideaProject.wildcards", "idea.project.wildcards")
        ideaProject.wildcards = wildcards
    }

    /**
     * output *.ipr file
     * <p>
     * Deprecated. Please use #idea.project.outputFile. See examples in {@link IdeaProject}.
     */
    @Deprecated
    File getOutputFile() {
        DeprecationLogger.nagUser("ideaProject.outputFile", "idea.project.outputFile")
        return ideaProject.outputFile
    }

    @Deprecated
    void setOutputFile(File newOutputFile) {
        DeprecationLogger.nagUser("ideaProject.outputFile", "idea.project.outputFile")
        ideaProject.outputFile = newOutputFile
    }
}
