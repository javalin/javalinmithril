/*
 *  Copyright 2021 Tareq Kirresh
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javalin.plugin.rendering.mithril;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author tareq
 */
public class MithrilDependencyResolver {

    private final MithrilFiles mithrilFiles = new MithrilFiles();
    private final Map<String, String> mithrilFilesCache = new TreeMap<>();
    private static final Pattern IMPORT_PATTERN = Pattern.compile("@import\\s+([\\w|\\d|\\.|_]+);?");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("@package\\s+([\\w|\\d|\\.|_]+);?");
    private static final Pattern CLASS_PATTERN = Pattern.compile("class\\s*(\\S+)\\s*\\{");

    public MithrilDependencyResolver(Set<Path> paths) {
        paths.stream().forEach(path -> {
            try {
                String fileContent = new String(Files.readAllBytes(path));
                mithrilFiles.parse(fileContent);
            } catch (IOException ex) {

            }
        });
        mithrilFiles.resolveAll();
    }

    /**
     * Resolves the component using its Fully Qualified Class Name
     *
     * @param componentName the component FQCN
     * @return the component plus any dependencies
     */
    public String resolve(final String componentName) {
        String componentId = componentName.replaceAll("\\.", "_");
        if (!mithrilFiles.containsClass(componentId)) {
            throw new IllegalArgumentException(String.format("Component Class %s not found ", componentName));
        }
        if (mithrilFilesCache.containsKey(componentId)) {
            return mithrilFilesCache.get(componentId);
        }
        String component = mithrilFiles.get(componentId).content();
        mithrilFilesCache.put(componentId, component);
        return component;
    }

    private class MithrilFiles {

        public final Map<String, MithrilFile> fullClassNameToMithrilClass = new TreeMap<>();

        private void parse(String fileContent) {
            MithrilFile m = new MithrilFile(fileContent);
            m.fileClasses.forEach(fileClass -> {
                fullClassNameToMithrilClass.put(m.fullClassName(fileClass), m);
            });

        }

        private void resolveAll() {
            fullClassNameToMithrilClass.values().forEach(MithrilFile::resolveDependencies);
        }

        private MithrilFile get(String match) {
            return fullClassNameToMithrilClass.get(match);
        }

        private boolean containsClass(String componentId) {
            return fullClassNameToMithrilClass.containsKey(componentId);
        }
    }

    private class MithrilFile {

        String filePackage;
        Set<String> fileClasses = new HashSet<>();
        String originalFileContent;
        Set<MithrilFile> declaredDependencies = new HashSet<>();

        public MithrilFile(String fileContent) {
            this.originalFileContent = fileContent;
            this.filePackage = findPackageName(fileContent);
            this.fileClasses = findFileClasses(fileContent);

        }

        String fullClassName(String name) {
            return filePackage.replaceAll("\\.", "_").concat("_").concat(name);
        }

        public String content() {
            String content = originalFileContent;
            for (String fileClass : fileClasses) {
                content = content.replaceAll(fileClass, fullClassName(fileClass));
            }
            for (MithrilFile dependency : declaredDependencies) {
                for (String dependencyClass : dependency.fileClasses) {
                    content = content.replaceAll(fullWord(dependencyClass), dependency.fullClassName(dependencyClass));
                }
            }

            for (MithrilFile dependency : declaredDependencies) {
                String dependencyContent = dependency.content();
                content = content.contains(dependencyContent) ? content : content.concat(dependencyContent);
            }
            return content.replaceAll("@import\\s*\\S+\\s*", "").replaceAll("@package\\s*\\S+\\s*", "");
        }

        private String findPackageName(String fileContent) {
            String packageName = "";
            Matcher packageMatcher = PACKAGE_PATTERN.matcher(fileContent);
            if (packageMatcher.find()) {
                packageName = packageMatcher.group(1);
            }
            return packageName;
        }

        private Set<String> findFileClasses(String fileContent) {
            Set<String> classes = new HashSet<>();
            Matcher classNameMatcher = CLASS_PATTERN.matcher(fileContent);
            while (classNameMatcher.find()) {
                classes.add(classNameMatcher.group(1));
            }
            return classes;
        }

        private void resolveDependencies() {
            Matcher matcher = IMPORT_PATTERN.matcher(originalFileContent); // match for HTML tags
            while (matcher.find()) {
                String match = matcher.group(1).replaceAll("\\.", "_");
                MithrilFile dependency = mithrilFiles.get(match);
                if (dependency != null && dependency != this) { // if it isn't the component itself, and its in the component map
                    declaredDependencies.add(dependency);
                }
            }
        }

        private String fullWord(String dependencyClass) {
            return String.format("\\b%s\\b",dependencyClass);
        }

    }
}
