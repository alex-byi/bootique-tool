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

import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;

import io.bootique.command.CommandOutcome;
import io.bootique.tools.shell.ConfigService;
import io.bootique.tools.shell.template.BinaryFileLoader;
import io.bootique.tools.shell.template.EmptyTemplateLoader;
import io.bootique.tools.shell.template.Properties;
import io.bootique.tools.shell.template.SafeBinaryContentSaver;
import io.bootique.tools.shell.template.TemplateDirOnlySaver;
import io.bootique.tools.shell.template.TemplatePipeline;
import io.bootique.tools.shell.template.processor.BQModuleProviderProcessor;
import io.bootique.tools.shell.template.processor.BqModuleNameProcessor;
import io.bootique.tools.shell.template.processor.BqModulePathProcessor;
import io.bootique.tools.shell.template.processor.JavaPackageProcessor;
import io.bootique.tools.shell.template.processor.MustacheTemplateProcessor;
import io.bootique.tools.shell.template.processor.TemplateProcessor;
import io.bootique.tools.shell.util.Utils;

public abstract class ModuleHandler extends ContentHandler {

    @Inject
    private ConfigService configService;

    public ModuleHandler() {
        // java sources
        addPipeline(TemplatePipeline.builder()
                .source("src/main/java/example/MyModule.java")
                .source("src/main/java/example/MyModuleProvider.java")
                .source("src/test/java/example/MyModuleProviderTest.java")
                .processor(new JavaPackageProcessor())
                .processor(new BqModulePathProcessor())
                .processor(new BqModuleNameProcessor())
                .processor(new MustacheTemplateProcessor())
        );

        // folders
        addPipeline(TemplatePipeline.builder()
                .source("src/main/resources")
                .source("src/test/resources")
                .loader(new EmptyTemplateLoader())
                .saver(new TemplateDirOnlySaver())
        );

        addPipeline(TemplatePipeline.builder()
                .source("src/main/resources/META-INF/services/io.bootique.BQModuleProvider")
                .processor(new BQModuleProviderProcessor())
        );

        // .gitignore
        addPipeline(TemplatePipeline.builder()
                .filter((s, properties) -> !properties.get("parent", false))
                .source("gitignore")
                .processor((tpl, p) -> tpl.withPath(tpl.getPath().getParent().resolve(".gitignore")))
        );

        // parent build file
        addPipeline(TemplatePipeline.builder()
                .filter((s, properties) -> properties.get("parent", false))
                .source(p -> p.get("parent.path", ""))
                // lazy processor as shell is not set by the creation time
                .processor((t, p) -> getTemplateProcessorForParent().process(t, p))
                .loader(new BinaryFileLoader())
                .saver(new SafeBinaryContentSaver()));
    }

    protected abstract String getBuildSystemName();

    protected abstract String getBuildFileName();

    protected abstract TemplateProcessor getTemplateProcessorForParent();

    protected Properties.Builder buildProperties(NameComponents components, Path outputRoot, Path parentFile) {
        String bqVersion = configService.get(ConfigService.BQ_VERSION);
        return Properties.builder()
                .with("java.package", components.getJavaPackage())
                .with("project.version", components.getVersion())
                .with("project.name", components.getName())
                .with("module.name", Utils.moduleNameFromArtifactName(components.getName()))
                .with("bq.di", bqVersion.startsWith("2."))
                .with("output.path", outputRoot)
                .with("bq.version", bqVersion)
                .with("bq.di", bqVersion.startsWith("2."))
                .with("java.version", configService.get(ConfigService.JAVA_VERSION));
    }

    @Override
    public CommandOutcome handle(NameComponents components) {
        log("Generating new " + getBuildSystemName() + " module @|bold " + components.getName() + "|@ ...");

        Path parentFile = shell.workingDir().resolve(getBuildFileName());
        boolean parentFileExists = Files.exists(parentFile);
        if(parentFileExists && !Files.isWritable(parentFile)) {
            return CommandOutcome.failed(-1, "Parent " + getBuildFileName() +
                    " file is not writable.");
        }

        Path outputRoot = shell.workingDir().resolve(components.getName());
        if(Files.exists(outputRoot)) {
            return CommandOutcome.failed(-1, "Directory '" + components.getName() + "' already exists");
        }

        Properties properties = buildProperties(components, outputRoot, parentFileExists ? parentFile : null)
                .with("parent", parentFileExists)
                .with("parent.path", parentFile.toString())
                .build();
        pipelines.forEach(p -> p.process(properties));

        log("done.");
        return CommandOutcome.succeeded();
    }
}
