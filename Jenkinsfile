@Library("titan-library") _ 

//run the build at 03:10 on every day-of-week from Monday through Friday but only on the main branch
String cron_string = BRANCH_NAME == "main" ? "10 3 * * 1-5" : ""

properties(
parameters(
[choice(choices: ['unit', 'it', 'testAll'], description: 'Please select test type from the list below', name: 'testType')],
[choice(choices: ['', 'SNAPSHOT', 'Minor', 'Major'], description: 'Please select the release type', name: 'releaseType')]
)

pipeline {
    agent any
    
    environment {

        SONAR_AUTH_TOKEN    = credentials('sonarqube_pac_token')
        SONARQUBE_URL       = "${GLOBAL_SONARQUBE_URL}"
        SONAR_HOST_URL      = "${GLOBAL_SONARQUBE_URL}"
        
        BRANCH_NAME         = "${GIT_BRANCH.split("/").size() > 1 ? GIT_BRANCH.split("/")[1] : GIT_BRANCH}"
    }

    triggers {
        cron(cron_string)
    }

    options {

        // Set this to true if you want to clean workspace during the prep stage
        skipDefaultCheckout(false)

        // Console debug options
        timestamps()
        ansiColor('xterm')
    }
        
    stages {

        stage('Maven Build') {

            when {
                expression { params.releaseType == ''  }
            }

            agent {
                docker {
                    image "maven:3.8.7-eclipse-temurin-19-alpine"
                    args '-u root:root'
                }
            }

            steps {
                script{
                    configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) {
                        
                        sh """
                        mvn clean install  -P ${params.testType} \
                            --batch-mode -DuniqueVersion=false \
                            -e \
                            -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
                        """
                    }
                }
            }
        }

        stage('SonarQube Scan') {

            when {
                expression { params.releaseType == ''  }
            }

            agent { 
                docker {
                    image "maven:3.8.7-eclipse-temurin-19-alpine"
                    args "-u root:root"
                }
            }
            
            steps{
                script{
                    configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) {
                        withSonarQubeEnv(installationName: 'EKS SonarQube', envOnly: true) {
                            // This expands the environment variables SONAR_CONFIG_NAME, SONAR_HOST_URL, SONAR_AUTH_TOKEN that can be used by any script.

                            sh """
                                mvn sonar:sonar -Dsonar.login=${SONAR_AUTH_TOKEN}  -s '${MAVEN_SETTINGS}' --batch-mode
                            """
                        }
                    }
                }
            }
               
            post {
                always {
                    echo "post always SonarQube Scan"
                }
            }            
        }
        
        stage("Publish to Nexus Repository Manager") {

            when {
                expression { params.releaseType == ''  }
            }

            agent { 
                 docker {
                    image "maven:3.8.7-eclipse-temurin-19-alpine"
                    args '-u root:root'
                 }
             }

            steps {

                script {
                    pomModel = readMavenPom(file: 'pom.xml')                    
                    pomVersion = pomModel.getVersion()
                    isSnapshot = pomVersion.contains("-SNAPSHOT")
                    repositoryId = 'maven-releases'

                    if (isSnapshot) {
                        repositoryId = 'maven-snapshots'
                    } 
                }
             
                configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) { 
                    
                    sh """
                        mvn deploy  -P ${params.testType}  \
                        --batch-mode \
                        -e \
                        -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn \
                        -DskipTests \
                        -DskipITs \
                        -Dmaven.main.skip \
                        -Dmaven.test.skip \
						-DuniqueVersion=false \
                        -s '${MAVEN_SETTINGS}' \
                        -P inject-application-properties \
                        -DrepositoryId='${repositoryId}'
                    """              
                }
            }
        }

        stage('Maven Release SNAPSHOT') {
            when {
                expression { params.releaseType == 'SNAPSHOT'  }
            }

            agent {
                docker {
                    image "maven:3.8.7-eclipse-temurin-19-alpine"
                    args '-u root:root'
                }
            }

            steps {
                script{
                    configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) {

                        sh """
                        mvn --batch-mode release:clean release:prepare release:perform \
                        -Darguments='-Dmaven.javadoc.skip=true -Dmaven.test.skipTests=true -Dmaven.test.skip=true'
                        -e  -s '${MAVEN_SETTINGS}' \
                        -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
                        """
                    }
                }
            }
        }

        stage('Maven Release Minor') {
            when {
                expression { params.releaseType == 'Minor'  }
            }

            agent {
                docker {
                    image "maven:3.8.7-eclipse-temurin-19-alpine"
                    args '-u root:root'
                }
            }

            steps {
                script{
                    configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) {

                        sh """
                        mvn --batch-mode build-helper:parse-version versions:set \
                        -DnewVersion=${parsedVersion.majorVersion}.${parsedVersion.nextMinorVersion}.0-SNAPSHOT \
                        -e  -s '${MAVEN_SETTINGS}' \
                        -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn \
                        versions:commit
                        """
                    }
                }
            }
        }

        stage('Maven Release Major') {
            when {
                expression { params.releaseType == 'Major'  }
            }

            agent {
                docker {
                    image "maven:3.8.7-eclipse-temurin-19-alpine"
                    args '-u root:root'
                }
            }

            steps {
                script{
                    configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) {

                        sh """
                        mvn --batch-mode build-helper:parse-version versions:set \
                        -DnewVersion=${parsedVersion.nextMajorVersion}.${parsedVersion.minorVersion}.0-SNAPSHOT \
                        -e  -s '${MAVEN_SETTINGS}' \
                        -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn \
                        versions:commit
                        """
                    }
                }
            }
        }
    }


    post {
        always {
            // Clean the workspace after build
            cleanWs(cleanWhenNotBuilt: false,
                deleteDirs: true,
                disableDeferredWipeout: true,
                notFailBuild: true,
                patterns: [
                [pattern: '.gitignore', type: 'INCLUDE']
            ])
        }
    }
}
