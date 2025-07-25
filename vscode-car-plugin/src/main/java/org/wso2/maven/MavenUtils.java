/*
 * Copyright (c) 2024, WSO2 LLC (http://www.wso2.com).
 *
 * WSO2 LLC licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.maven;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.invoker.InvocationOutputHandler;
import org.apache.maven.shared.invoker.Invoker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class MavenUtils {

    /**
     * Retrieves the Maven home directory.
     *
     * @return The Maven home directory path, or null if it cannot be determined.
     * @throws MojoExecutionException if an error occurs while determining the Maven home directory.
     */
    public static String getMavenHome() throws MojoExecutionException {

        // First try to find Maven home using system property
        String mavenHome = System.getProperty("maven.home");
        if (mavenHome != null) {
            return mavenHome;
        }

        // Fallback: Try to find Maven home using environment variable or default paths
        mavenHome = System.getenv("M2_HOME");
        if (mavenHome != null) {
            return mavenHome;
        }

        // Fallback: Try to find Maven home using command line
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (System.getProperty("os.name").toLowerCase().contains(org.wso2.maven.Constants.OS_WINDOWS)) {
            processBuilder.command("cmd.exe", "/c", "mvn -v");
        } else {
            processBuilder.command("sh", "-c", "mvn -v");
        }
        try {
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Maven home: ")) {
                    return line.split("Maven home: ")[1].trim();
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not determine Maven home.", e);
        }

        throw new MojoExecutionException("Could not determine Maven home.");
    }

    /**
     * Sets up the Maven Invoker with the specified Maven home directory.
     *
     * @param projectPath       The project directory.
     * @param invoker           The Maven Invoker to set up.
     */
    public static void setupInvoker(Invoker invoker, String projectPath) throws MojoExecutionException {

        File mvnwFile = new File(projectPath, System.getProperty("os.name").toLowerCase().
                contains("win") ? "mvnw.cmd" : "mvnw");
        boolean useMavenWrapper = mvnwFile.exists() && mvnwFile.canExecute();

        if (useMavenWrapper) {
            invoker.setMavenExecutable(mvnwFile);
            invoker.setMavenHome(new File(projectPath));
        } else {
            invoker.setMavenHome(new File(getMavenHome()));
        }

        invoker.setOutputHandler(new InvocationOutputHandler() {
            @Override
            public void consumeLine(String line) {
                if (!line.contains("BUILD SUCCESS")) {
                    System.out.println(line);
                }
            }
        });
    }

    /**
     * Checks the project runtime version and decides
     * whether to pack connector dependencies or not
     * @param project Maven project
     * @return whether to pack connector dependencies or not
     */
    public static boolean isConnectorPackingSupported(MavenProject project) {
        String runtimeVersion = project.getProperties().getProperty(Constants.PROJECT_RUNTIME_VERSION);

        if (runtimeVersion != null) {
            // Compare the version
            ComparableVersion currentVersion = new ComparableVersion(runtimeVersion);
            ComparableVersion targetVersion = new ComparableVersion(Constants.RUNTIME_VERSION_440);

            return currentVersion.compareTo(targetVersion) >= 0;
        }
        return false;
    }

    public static File createPomFile(List<String> dependencies, List<String> repositories) throws IOException {

        File tempPom = File.createTempFile("temp-pom", ".xml");
        try (FileWriter writer = new FileWriter(tempPom)) {
            writer.write("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                    "    <modelVersion>4.0.0</modelVersion>\n" +
                    "    <groupId>temp</groupId>\n" +
                    "    <artifactId>temp</artifactId>\n" +
                    "    <version>1.0-SNAPSHOT</version>\n" +
                    "    <dependencies>\n");

            // Add dependencies
            for (String dependency : dependencies) {
                String[] parts = dependency.split(":");
                if (parts.length > 3) {
                    writer.write(String.format("        <dependency>\n" +
                            "            <groupId>%s</groupId>\n" +
                            "            <artifactId>%s</artifactId>\n" +
                            "            <version>%s</version>\n" +
                            "            <type>%s</type>\n" +
                            "        </dependency>\n", parts[0], parts[1], parts[2], parts[3]));
                } else {
                    writer.write(String.format("        <dependency>\n" +
                            "            <groupId>%s</groupId>\n" +
                            "            <artifactId>%s</artifactId>\n" +
                            "            <version>%s</version>\n" +
                            "        </dependency>\n", parts[0], parts[1], parts[2]));
                }
            }

            writer.write("    </dependencies>\n");

            // Add repositories
            if (repositories != null && !repositories.isEmpty()) {
                writer.write("    <repositories>\n");
                for (String repository : repositories) {
                    writer.write(String.format("        <repository>\n" +
                            "            <id>repo-%d</id>\n" +
                            "            <url>%s</url>\n" +
                            "        </repository>\n", repositories.indexOf(repository) + 1, repository));
                }
                writer.write("    </repositories>\n");
            }

            writer.write("</project>\n");
        }
        return tempPom;
    }
}
