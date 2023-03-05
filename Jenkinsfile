@Library("titan-library") _ 

//run the build at 03:10 on every day-of-week from Monday through Friday but only on the main branch
String cron_string = BRANCH_NAME == "main" ? "10 3 * * 1-5" : ""

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
                        mvn clean install -s '${MAVEN_SETTINGS}'   \
                            --batch-mode -DuniqueVersion=false \
                            -e \
                            -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
                        """
                    }
                }
            }
        }

        stage('SonarQube Scan') {

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
                                mvn clean verify sonar:sonar  -X -Dsonar.login=${SONAR_AUTH_TOKEN} -s '${MAVEN_SETTINGS}' --batch-mode
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

        stage("Quality Gate"){
            timeout(time: 1, unit: 'HOURS') {
                def qg = waitForQualityGate()
                if (qg.status != 'OK') {
                    error "Pipeline aborted due to quality gate failure: ${qg.status}"
                }
            }
        }

        stage("Publish to Nexus Repository Manager") {

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
                    mvn -version
                    """

//                     sh """
//                         mvn deploy   \
//                         --batch-mode \
//                         -e \
//                         -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn \
//                         -DskipTests \
//                         -DskipITs \
//                         -Dmaven.main.skip \
//                         -Dmaven.test.skip \
// 						-DuniqueVersion=false \
//                         -s '${MAVEN_SETTINGS}' \
//                         -P inject-application-properties \
//                         -DrepositoryId='${repositoryId}'
//                     """
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
