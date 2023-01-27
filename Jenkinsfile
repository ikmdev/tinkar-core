
def pipelineContainerName() {
    "titan-maven"
}

String determineCommand() {
    def command = 'mvn'
    if (fileExists("${WORKSPACE}/mvnw")) {
        command = '${WORKSPACE}/mvnw'
    }
    command
}

def validatePomExists() {
    if (!fileExists("${WORKSPACE}/pom.xml")) {
        sh "ls -ltra ${WORKSPACE}"
        throw new ValidationException("Cannot execute Maven build as pom.xml does not exist at the top level")
    }
}

/**
 * Build the docker-compatible container to Nexus, reading maven properties from the pom file.
 * This will pass all
 *
 * @param mapIn
 *   * (String) registry (optional) - the registry to push to, in the format 'https://registry.example.com'.  The
 *     default is the Titan Registry.
 *   * (String) registryCredId (optional) - the id of the credentials in Jenkins. The default is the Titan
 *     Registry credentials.
 */
def buildContainer(Map mapIn = [:]) {
    Map<String, String> containerProps = readPom(mapIn)
    containerPipeline.build(containerProps + mapIn)
}

/**
 *
 * @param mapIn
 *   * pomLocation (default is "./pom.xml")
 * @return true if the name ends with "-SNAPSHOT"
 */
def isSnapshotVersion() {
    Map<String, String> containerProps = readPom(mapIn)
    return containerProps.get('version').endsWith('-SNAPSHOT')
}

/**
 * Cleans and builds with Maven, utilizing first the [Maven Wrapper][3], or if that doesn't exist, a local version
 * of Maven. This will specifically execute a maven installation, skipping all testing.
 */
def build() {
    validatePomExists()
    sonarQubePipeline.validateProperties()
    // Determine which command to use, depending on if a wrapper is included
    def command = determineCommand()

    configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) {
        sh "${command} -s '${MAVEN_SETTINGS}' ${MavenConstants.CI_OPTIONS} clean install -DskipTests -DskipITs"
        //TODO add + "-D settings.security=${MAVEN_SETTINGS_SECURITY}"
    }
}

/**
 * Executes maven surefire tests. This assumes that you have already executed up to `compile` in the Maven Lifecycle.
 * This will use the test that is defined in the pom.xml, and error if it is not provided.
 */
def unitTests() {
    validatePomExists()

    // Determine which command to use, depending on if a wrapper is included     3
    def command = determineCommand()

    configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) {
        sh "${command} -s '${MAVEN_SETTINGS}' ${MavenConstants.CI_OPTIONS} -U test " +
                "-Dmaven.main.skip " +
                "-s ${MAVEN_SETTINGS}"
        //""-D settings.security=${MAVEN_SETTINGS_SECURITY}"
    }
}


/**
 * Executes maven failsafe tests. This assumes that you have already executed up to `test` in the Maven Lifecycle.
 * This will use the test that is defined in the pom.xml, and error if it is not provided.
 */
def integrationTests() {
    validatePomExists()

    // Determine which command to use, depending on if a wrapper is included
    def command = determineCommand()

    configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) {
        sh "${command} -s '${MAVEN_SETTINGS}' ${MavenConstants.CI_OPTIONS} -U integration-test " +
                "-Dmaven.main.skip " +
                "-s ${MAVEN_SETTINGS}"
        //"-D settings.security=${MAVEN_SETTINGS_SECURITY}"
    }
}

/**
 * Executes a deployment, using the deploy plugin found in the Maven Lifecycle.
 * This assumes that you have already executed up to `install` in the Maven Lifecycle. This will not use the one
 * provided in the pom file, as is is not backwards compatible with previous versions (<3.0).
 *
 * @param mapIn : allows for values ot be overridden by passing it in as a map
 *      * deployVersion - the version of the deploy plugin.  This must be above 3.0.0 to work.
 */
def deliverToNexus(Map mapIn = [:]) {
    validatePomExists()

    // Determine which command to use, depending on if a wrapper is included
    def command = determineCommand()

    configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) {
        sh "${command} ${MavenConstants.CI_OPTIONS} " +
                "help:active-profiles " +
                "deploy " +
                "-DskipTests -DskipITs " +
                "-Dmaven.main.skip " +
                "-Dmaven.test.skip " +
                "-s '${MAVEN_SETTINGS}' " +
                "-P inject-application-properties "
        //-D settings.security=${MAVEN_SETTINGS_SECURITY}"
        //TODO repos need to be defined in settings.xml
    }
}

String buildContainerSectionOfPod(Map mapIn = [:]) {
    //TODO check
    containerName = mapIn.get('containerName')
    image = mapIn.get('image')
    env = mapIn.get('env')
    memRequest = mapIn.get('memRequest') ?: "1Gi"
    memLimit = mapIn.get('memLimit') ?: "2Gi"

    returnVal = "  - name: ${containerName}\n" +
            "    image: ${image}\n" +
            "    securityContext:\n" +
            "      allowPrivilegeEscalation: false\n" +
            "    resources: \n" +
            "      requests: \n" +
            "        memory: ${memRequest} \n" +
            "      limits: \n" +
            "        memory: ${memLimit}\n"

    if (env != null) {
        returnVal += "    env:\n"
        for (entry in env) {
            returnVal += "    - name: '${entry?.key}'\n" +
                    "      value: '${entry?.value}'\n"
        }
    }
    returnVal += "    command:\n"+
            "    - cat\n" +
            "    tty: true\n"
    return returnVal
}

def createScriptedStage(Map map = [:]) {
    stageName = map['stageName']
    containerName = map['containerName']
    execute = map.get('execute')
    postSuccess = map?.get('post')?.get('success') ?: null
    postFailure = map?.get('post')?.get('failure') ?: null
    postAlways = map?.get('post')?.get('always') ?: null

    Validation.validateIsString('stageName', stageName)
    Validation.validateStringIsNotEmpty('stageName', (String) stageName)

    Validation.validateIsString('containerName', containerName)
    Validation.validateStringIsNotEmpty('containerName', (String) containerName)

    Validation.validateIsClosure('execute', execute)

    if (postSuccess != null) {
        Validation.validateIsClosure('post.success', postSuccess)
    }
    if (postFailure != null) {
        Validation.validateIsClosure('post.failure', postFailure)
    }
    if (postAlways != null) {
        Validation.validateIsClosure('post.always', postAlways)
    }

    stage(stageName) {
        try {
            updateGitlabCommitStatus name: stageName, state: 'running'
            container(containerName) {
                if (execute != null) {
                    execute()
                }
                if (postSuccess != null) {
                    postSuccess()
                }
            }
            updateGitlabCommitStatus name: stageName, state: 'success'
        } catch (FlowInterruptedException fe) {
            // failure from interruption
            echo "Interruption Exception Received: $e"
            if (postFailure != null) {
                postFailure()
            }
            updateGitlabCommitStatus name: stageName, state: 'canceled'
            throw(fe)
        } catch (e) {
            // failure
            echo "Failure Exception Received: $e"
            if (postFailure != null) {
                postFailure()
            }
            updateGitlabCommitStatus name: stageName, state: 'failed'
            throw(e)
        } finally {
            // always
            if (postAlways != null) {
                postAlways()
            }
        }
    }
}


podTemplate(
        yaml: podBuilder(
                containers: [
                        pipelineContainer()
                ]
        )
) {
    properties([gitLabConnection('titan-gitlab')])

    node(POD_LABEL) {
        createScriptedStage(
                stageName: buildStages.checkout() + "(Maven)",
                containerName: pipelineContainerName(),
                execute: {
                    checkout scm
                }
        )

        createScriptedStage(
                stageName: buildStages.build(),
                containerName: pipelineContainerName(),
                execute: {
                    build()
                },
                always: {
                    def artifacts = '**/target/*.jar, **/target/*.war, **/target/*.ear, '
                    +'**/target/*.zip, **/target/*.tar, **/target/*.gz'
                    archiveArtifacts(
                            artifacts: artifacts,
                            allowEmptyArchive: true,
                            fingerprint: true,
                            onlyIfSuccessful: true
                    )
                }
        )

        createScriptedStage(
                stageName: "Test",
                containerName: pipelineContainerName(),
                execute: {
                    unitTests()
                    integrationTests()
                },
                always: {
                    //TODO archive tests
                }
        )


        // TODO if a snapshot deliver to snapshots, if a release on a branch, don't deliver. if a release on
        //  main, deliver
        buildStages.createScriptedStage(
                stageName: buildStages.deliver() + " (Maven)",
                containerName: pipelineContainerName(),
                execute: {
                    deliverToNexus()
                },
                always: {
                    //TODO archive code quality checks?
                }
        )


}
