@Library("titan-library") _ 

//run the build at 03:10 on every day-of-week from Monday through Friday but only on the main branch
String cron_string = BRANCH_NAME == "main" ? "10 3 * * 1-5" : ""

pipeline {
    agent {
        label 'linux'
    }
    
    environment {
        EMAIL_TO = 'no-reply-jenkins@tinkarbuild.com'
        EMAIL_TEXT = 'Check console output at $BUILD_URL to view the results. \n\n ${CHANGES} \n\n -------------------------------------------------- \n'

        SONAR_AUTH_TOKEN    = credentials('sonarqube_pac_token')
        SONARQUBE_URL       = "${GLOBAL_SONARQUBE_URL}"
        SONAR_HOST_URL      = "${GLOBAL_SONARQUBE_URL}"
        
        BRANCH_NAME         = "${GIT_BRANCH.split("/").size() > 1 ? GIT_BRANCH.split("/")[1] : GIT_BRANCH}"
    }

    triggers {
        //cron(cron_string)
        gitlab(triggerOnPush: true, triggerOnMergeRequest: true, branchFilterType: 'All')
    }

    options {

        // Set this to true if you want to clean workspace during the prep stage
        skipDefaultCheckout(false)

        // Console debug options
        timestamps()
        ansiColor('xterm')

        gitLabConnection('gitlab-installer-connection')
    }
        
    stages {
        
        stage('Maven Build') {

            steps {
                updateGitlabCommitStatus name: 'build', state: 'running'
                script{
                    configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) {
                        sh """
                        mvn clean install -s '${MAVEN_SETTINGS}' \
                            --batch-mode \
                            -e \
                            -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
                        """
                    }
                }
            }
        }

        stage('SonarQube Scan') {

            steps{
                configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) {
                    withSonarQubeEnv(installationName: 'EKS SonarQube', envOnly: true) {
                        // This expands the environment variables SONAR_CONFIG_NAME, SONAR_HOST_URL, SONAR_AUTH_TOKEN that can be used by any script.

                        sh """
                            mvn clean install -s '${MAVEN_SETTINGS}'  --batch-mode
                            mvn pmd:pmd -s '${MAVEN_SETTINGS}'  --batch-mode
                            mvn com.github.spotbugs:spotbugs-maven-plugin:4.7.3.2:spotbugs -s '${MAVEN_SETTINGS}'  --batch-mode
                            mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184:sonar -Dsonar.qualitygate.wait=true -X -Dsonar.login=${SONAR_AUTH_TOKEN} -s '${MAVEN_SETTINGS}' --batch-mode
                        """
                        
                    }
                }
                script{
                    configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) {
                        
                        sh """
                        mvn pmd:pmd -s '${MAVEN_SETTINGS}'  --batch-mode
                        mvn com.github.spotbugs:spotbugs-maven-plugin:4.7.3.2:spotbugs -s '${MAVEN_SETTINGS}'  --batch-mode
                        """
                        def pmd = scanForIssues tool: [$class: 'Pmd'], pattern: '**/target/pmd.xml'
                        publishIssues issues: [pmd]

                        def spotbugs = scanForIssues tool: [$class: 'SpotBugs'], pattern: '**/target/spotbugsXml.xml'
                        publishIssues issues:[spotbugs]

                        publishIssues id: 'analysis', name: 'All Issues',
                            issues: [pmd, spotbugs],
                            filters: [includePackage('io.jenkins.plugins.analysis.*')]
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

            steps {

                script {
                    pomModel = readMavenPom(file: 'pom.xml')                    
                    pomVersion = pomModel.getVersion()
                    isSnapshot = pomVersion.contains("-SNAPSHOT")
                    repositoryId = 'maven-releases'

                    if (isSnapshot) {
                        repositoryId = 'maven-snapshots'
                    } 
                
             
                    configFileProvider([configFile(fileId: 'settings.xml', variable: 'MAVEN_SETTINGS')]) { 

                        sh """
                            mvn deploy \
                            --batch-mode \
                            -e \
                            -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn \
                            -DskipTests \
                            -DskipITs \
                            -Dmaven.main.skip \
                            -Dmaven.test.skip \
                            -s '${MAVEN_SETTINGS}' \
                            -P inject-application-properties \
                            -DrepositoryId='${repositoryId}'
                        """              
                    }
                }
            }
        }
    }


    post {
        failure {
            updateGitlabCommitStatus name: 'build', state: 'failed'
            mail body: '${EMAIL_TEXT}\n${BUILD_LOG, maxLines=100, escapeHtml=false}', 
                    to: "${EMAIL_TO}", 
                    subject: 'Build failed in Jenkins: $PROJECT_NAME - #$BUILD_NUMBER'
        }
        aborted {
            updateGitlabCommitStatus name: 'build', state: 'canceled'
        }
        unstable {
            updateGitlabCommitStatus name: 'build', state: 'failed'
            mail body: '${EMAIL_TEXT}\n${BUILD_LOG, maxLines=100, escapeHtml=false}', 
                    to: "${EMAIL_TO}", 
                    subject: 'Unstable build in Jenkins: $PROJECT_NAME - #$BUILD_NUMBER'
        }
        changed {
            updateGitlabCommitStatus name: 'build', state: 'success'
            mail body: '${EMAIL_TEXT}', 
                    to: "${EMAIL_TO}", 
                    subject: 'Jenkins build is back to normal: $PROJECT_NAME - #$BUILD_NUMBER'
        }
        success {
            updateGitlabCommitStatus name: 'build', state: 'success'
        }
        cleanup {
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
