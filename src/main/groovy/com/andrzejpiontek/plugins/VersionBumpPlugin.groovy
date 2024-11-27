package com.andrzejpiontek.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

class VersionBumpPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.tasks.register("versionBump", VersionBumpTask)
    }
}