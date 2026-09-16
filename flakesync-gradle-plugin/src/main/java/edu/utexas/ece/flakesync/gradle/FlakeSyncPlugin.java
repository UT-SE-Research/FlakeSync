package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

public class FlakeSyncPlugin implements Plugin<Project> {

    public static final String AGENT_CONFIGURATION_NAME = "flakesyncAgent";
    public static final String AGENT_COORDINATES = "edu.utexas.ece:flakesync-core:1.0-SNAPSHOT";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java");

        FlakeSyncExtension extension = project.getExtensions().create("flakesync", FlakeSyncExtension.class);
        extension.getFlakesyncDir().convention(".flakesync");
        extension.getLoggingLevel().convention("CONFIG");

        Configuration agentConfiguration = project.getConfigurations().create(AGENT_CONFIGURATION_NAME, c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setDescription("Resolves the flakesync-core agent/launcher jar.");
        });
        project.getDependencies().add(AGENT_CONFIGURATION_NAME, AGENT_COORDINATES);
    }
}
