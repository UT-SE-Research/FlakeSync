package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlakeSyncPluginTest {

    @Test
    void registersExtensionWithDefaults() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");

        FlakeSyncExtension extension = project.getExtensions().getByType(FlakeSyncExtension.class);

        assertEquals(".flakesync", extension.getFlakesyncDir().get());
        assertEquals("CONFIG", extension.getLoggingLevel().get());
    }

    @Test
    void registersResolvableAgentConfiguration() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("edu.utexas.ece.flakesync");

        Configuration agentConfiguration = project.getConfigurations()
                .getByName(FlakeSyncPlugin.AGENT_CONFIGURATION_NAME);

        assertNotNull(agentConfiguration);
        assertTrue(agentConfiguration.isCanBeResolved());
        assertTrue(agentConfiguration.getDependencies().stream()
                .anyMatch(d -> "flakesync-core".equals(d.getName())));
    }
}
