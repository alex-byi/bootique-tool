/*
 *   Licensed to ObjectStyle LLC under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ObjectStyle LLC licenses
 *   this file to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 */

package io.bootique.tools.shell.content;

import javax.inject.Inject;
import java.nio.file.Path;

import io.bootique.tools.shell.Shell;
import io.bootique.tools.shell.template.Properties;
import io.bootique.tools.shell.template.TemplatePipeline;
import io.bootique.tools.shell.template.processor.MustacheTemplateProcessor;
import io.bootique.tools.shell.template.processor.ParentPomProcessor;
import io.bootique.tools.shell.template.processor.TemplateProcessor;

public class MavenModuleHandler extends ModuleHandler {

    private static final String BUILD_FILE = "pom.xml";
    private static final String BUILD_SYSTEM = "Maven";

    @Inject
    private Shell shell;

    private final PomParser parentPomParser;

    public MavenModuleHandler() {
        super();
        this.parentPomParser = new PomParser();
        // pom.xml
        addPipeline(TemplatePipeline.builder()
                .source("pom.xml")
                .processor(new MustacheTemplateProcessor())
        );
    }

    @Override
    protected String getBuildFileName() {
        return BUILD_FILE;
    }

    @Override
    protected String getBuildSystemName() {
        return BUILD_SYSTEM;
    }

    @Override
    protected Properties.Builder buildProperties(NameComponents components, Path outputRoot, Path parentFile) {
        Properties.Builder builder = super.buildProperties(components, outputRoot, parentFile)
                .with("input.path", "templates/maven-module/");

        if(parentFile != null) {
            NameComponents parentNameComponents = parentPomParser.parse(parentFile);
            builder.with("parent.group", parentNameComponents.getJavaPackage())
                    .with("parent.name", parentNameComponents.getName())
                    .with("parent.version", parentNameComponents.getVersion());
        }

        return builder;
    }

    @Override
    protected TemplateProcessor getTemplateProcessorForParent() {
        return new ParentPomProcessor(shell);
    }

}
