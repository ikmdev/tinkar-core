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

                

                sh '''#!/bin/bash
                    mvn deploy:deploy -Durl=https://nexus.build.tinkarbuild.com/repository/maven-releases/ -DartifactId=activej -DrepositoryId=maven-releases
                '''
            }
        }

    }
}
