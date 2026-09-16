package edu.utexas.ece.flakesync.gradle;

import org.gradle.api.provider.Property;

public interface FlakeSyncExtension {

    Property<String> getFlakesyncDir();

    Property<String> getLoggingLevel();
}
