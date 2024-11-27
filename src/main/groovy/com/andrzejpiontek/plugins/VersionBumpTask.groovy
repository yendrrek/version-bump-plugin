package com.andrzejpiontek.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class VersionBumpTask extends DefaultTask {

    @TaskAction
    def bumpVersion() {
        def gitCommand = ["git", "show", "HEAD:build.gradle"]
        def process = new ProcessBuilder(gitCommand).redirectErrorStream(true).start()
        def previousBuildFile = new StringWriter()
        process.waitForProcessOutput(previousBuildFile, System.err)
        if (process.exitValue() == 0) {
            def versionPattern = /version\s*=\s*['"](\d+\.\d+\.\d+)['"]/
            def currentBuildFile = project.file('build.gradle').text
            def currentVersionMatcher = currentBuildFile =~ versionPattern
            def previousVersionMatcher = previousBuildFile.toString() =~ versionPattern
            if (previousVersionMatcher && currentVersionMatcher) {
                def previousVersionMap = createVersionMap(previousVersionMatcher[0][1].tokenize("."))
                def currentVersionMap = createVersionMap(currentVersionMatcher[0][1].tokenize("."))
                def file = new File("${project.rootDir}/.git/blockCommitVar")
                toggleBlockingCommit(file, false)
                if (isMoreThanOnePartOfVersionChanged(previousVersionMap, currentVersionMap)) {
                    println("Only one part of version can be bumped at the same time: Major, Minor or Patch")//
                    toggleBlockingCommit(file, true)
                    return
                }
                def newVersion = getNewVersion(previousVersionMap, currentVersionMap)
                if (newVersion == -1) {
                    toggleBlockingCommit(file, true)
                    return
                }
                println "Bumping version to $newVersion"
                def updatedContent = currentBuildFile.replaceFirst(versionPattern, "version = \"$newVersion\"")
                project.buildFile.text = updatedContent
                return
            }
            println("Version must be written according to pattern x.x.x")
        }
    }

    private static def createVersionMap(versionPartsSeparated) {
        return [
                major: versionPartsSeparated[0].toInteger(),
                minor: versionPartsSeparated[1].toInteger(),
                patch: versionPartsSeparated[2].toInteger()
        ]
    }

    private static def toggleBlockingCommit(file, isSet) {
        file.text = "BLOCK_COMMIT=${isSet}"
    }

    private static def getNewVersion(previousVersionMap, currentVersionMap) {
        if (isMinorChanged(previousVersionMap, currentVersionMap)) {
            if (isBumpedByInvalidNumber(previousVersionMap.minor, currentVersionMap.minor)) {
                println("Minor must be bumped by one")
                return -1
            }
            return resetPatch(currentVersionMap)
        }
        if (isMajorChanged(previousVersionMap, currentVersionMap)) {
            if (isBumpedByInvalidNumber(previousVersionMap.major, currentVersionMap.major)) {
                println("Major must be bumped by one")
                return -1
            }
            return resetPatchAndMinor(currentVersionMap)
        }
        if (isPatchChangedManually(previousVersionMap, currentVersionMap)) {
            if (isBumpedByInvalidNumber(previousVersionMap.patch, currentVersionMap.patch)) {
                println("Patch must be bumped by one")
                return -1
            }
            return incrementPatchManually(currentVersionMap)
        }
        return incrementPatch(currentVersionMap)
    }

    private static def isMinorChanged(previousVersionMap, currentVersionMap) {
        return previousVersionMap.minor != currentVersionMap.minor &&
                previousVersionMap.patch == currentVersionMap.patch &&
                previousVersionMap.major == currentVersionMap.major
    }

    private static def isBumpedByInvalidNumber(previousVersion, currentVersion) {
        return currentVersion - previousVersion != 1
    }

    private static def resetPatch(currentVersionMap) {
        currentVersionMap.patch = 0
        return "$currentVersionMap.major.$currentVersionMap.minor.$currentVersionMap.patch"
    }

    private static def isMajorChanged(previousVersionMap, currentVersionMap) {
        return previousVersionMap.major != currentVersionMap.major &&
                previousVersionMap.minor == currentVersionMap.minor &&
                previousVersionMap.patch == currentVersionMap.patch
    }

    private static def resetPatchAndMinor(currentVersionMap) {
        currentVersionMap.minor = 0
        currentVersionMap.patch = 0
        return "$currentVersionMap.major.$currentVersionMap.minor.$currentVersionMap.patch"
    }

    private static def isPatchChangedManually(previousVersionMap, currentVersionMap) {
        return previousVersionMap.patch != currentVersionMap.patch &&
                previousVersionMap.major == currentVersionMap.major &&
                previousVersionMap.minor == currentVersionMap.minor
    }

    private static def isMoreThanOnePartOfVersionChanged(previousVersionMap, currentVersionMap) {
        def numberOfDifferences = 0
        def isIncorrectVersionBump = false
        previousVersionMap.each { key, value ->
            if (value != currentVersionMap[key]) {
                numberOfDifferences++
            }
            if (numberOfDifferences > 1) {
                isIncorrectVersionBump = true
            }
        }
        return isIncorrectVersionBump
    }

    private static def incrementPatchManually(currentVersionMap) {
        println("You are bumping Patch manually. Patch gets updated automatically when you commit")
        return "$currentVersionMap.major.$currentVersionMap.minor.$currentVersionMap.patch"
    }

    private static def incrementPatch(currentVersionMap) {
        currentVersionMap.patch += 1
        return "$currentVersionMap.major.$currentVersionMap.minor.$currentVersionMap.patch"
    }
}
