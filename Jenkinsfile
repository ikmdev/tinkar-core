pipeline {

    agent any

    stages{

        stage("build"){

            steps {
                echo 'test building application'
            }
        }

        stage("test"){

            steps {
                echo 'test testing application'
            }
        }

        stage("deploy"){

            /*when {
                expression {
                    currentBuild.result == null || currentBuild.result == 'SUCCESS' 
                }
            }*/
            
            steps {

            }
        }

        stage('Upload to Nexus'){
            steps{
                echo 'Uploading to Nexus..."
                withCredentials([usernamePassword(credentialsId: 'NEXUS_CREDS',
                passwordVariable: 'NEXUS_PASSWORD', usernameVariable: 'NEXUS_USERNAME')]){
                    sh "mvn deploy:deploy-file -Durl=https://nexus.build.tinkarbuild.com/repository/maven-releases/ -DrepositoryId=maven-releases -Dfile=target/activej-1.0.15.jar -DpomFile=pom.xml -DgroupId=org.hl7.tinkar -DartifactId=activej -Dpackaging=jar -Dversion=1.0.15 -Dusername=${NEXUS_USERNAME} -Dpassword={NEXUS_PASSWORD}"
                }
            }
        }

    }
}
