pipeline {
    environment {
        APP_VERSION = ""
    }
    agent none
    options { skipDefaultCheckout() }

    stages {
        stage("Init"){
            agent any
            steps{
                script {
                    sh "oc version"
                }
            }
        }

        stage('Create Image Builder Prometeo') {
            when {
                expression {
                    openshift.withCluster() {
                        openshift.withProject( 'prometeo-dev' ) {
                            return !openshift.selector("bc", "prometeo").exists();
                        }
                    }
                }
            }
            agent any
            steps { 
                script {
                    openshift.withCluster() {
                        openshift.withProject( 'prometeo-dev' ) {
                            openshift.newBuild("--name=prometeo", "--image-stream=prometeo-dev/jansible:latest", "--binary")
                        }
                    }
                }
            }
        }

        stage("Maven build") {
            agent { label 'maven' }
            steps {
                script {
                        checkout scm                    
                        def pom = readMavenPom file: "pom.xml"
                        sh "mvn clean package -DskipTests"
                        APP_VERSION = pom.version
                        artifactId = pom.artifactId
                        groupId = pom.groupId.replace(".", "/")
                        packaging = pom.packaging
                        NEXUS_ARTIFACT_PATH = "${groupId}/${artifactId}/${APP_VERSION}/${artifactId}-${APP_VERSION}.${packaging}"
                        echo "Building container image with artifact = ${NEXUS_ARTIFACT_PATH}"

                        // This is here until we get the Nexus repo setup
                        openshift.withCluster() {
                            openshift.withProject( 'prometeo-dev' ) {
                                openshift.selector("bc", "prometeo").startBuild("--from-file=target/${artifactId}-${APP_VERSION}.${packaging}", "--wait")
                            }
                        }
                    }
                }
        }
        

        // stage('Build Application Image') {
        //     agent { label 'maven' }
        //     steps {
        //         script {
        //             openshift.withCluster() {
        //                 openshift.selector("bc", "prometeo").startBuild("--from-file=target/${artifactId}-${APP_VERSION}.${packaging}", "--wait")
        //             }
        //         }
        //     }
        // }

        stage('Dev Deployment') {
            agent any
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    input 'Do you approve deployment to development environment ?'
                }
            }
        }

        stage('Promote to DEV') {
            agent any
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject( 'prometeo-dev' ) {
                            openshift.tag("prometeo-dev/prometeo:latest", "prometeo-dev/prometeo:dev")
                        }
                    }
                }
            }
        }

        stage('Create app in DEV if not already there') {
            agent any
            when {
                expression {
                    openshift.withCluster() {
                        openshift.withProject( 'prometeo-dev' ) {
                            return !openshift.selector("dc", "prometeo").exists();
                        }
                    }
                }
            }
            steps {
                 script {
                    openshift.withCluster() {
                        openshift.withProject( 'prometeo-dev' ) {
                            openshift.newApp("prometeo:dev", "--name=prometeo").narrow('svc')
                            openshift.raw('set','triggers','deploymentconfig/prometeo', '--manual')
                            openshift.raw('volume','deploymentconfig/prometeo', '--add','-t secret','-m /tmp/secrets --secret-name=mongodb --name=mongodb-secret')
                            openshift.raw('volume','deploymentconfig/prometeo', '--add -t secret -m /app/.ssh/keys --secret-name=sshkey --default-mode=0600')
                            openshift.raw('set','triggers','deploymentconfig/prometeo', '--auto')
                        }
                    }
                }
            }
        }

        stage('Test Deployment') {
            agent any
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    input 'Do you approve deployment to Test environment ?'
                }
            }
        }

        stage('Promote to TEST') {
            agent any
            steps {
                script {
                openshift.withCluster() {
                        openshift.withProject( 'prometeo-dev' ) {
                            openshift.tag("prometeo-dev/prometeo:dev", "prometeo-dev/prometeo:test")
                        }
                    }
                }
            }
        }

        stage('Create app in TEST if not already there') {
            agent any
            when {
                expression {
                    openshift.withCluster() {
                        openshift.withProject( 'prometeo-test' ) {
                            return !openshift.selector("dc", "prometeo").exists();
                        }
                    }
                }
            }
            steps {
                 script {
                    openshift.withCluster() {
                        openshift.withProject( 'prometeo-test' ) {
                            openshift.newApp("prometeo-dev/prometeo:test", "--name=prometeo").narrow('svc')
                            openshift.raw('set','triggers','deploymentconfig/prometeo', '--manual')
                            openshift.raw('volume','deploymentconfig/prometeo', '--add','-t secret','-m /tmp/secrets --secret-name=mongodb --name=mongodb-secret')
                            openshift.raw('volume','deploymentconfig/prometeo', '--add -t secret -m /app/.ssh/keys --secret-name=sshkey --default-mode=0600')
                            openshift.raw('set','triggers','deploymentconfig/prometeo', '--auto')
                        }
                    }
                }
            }
        }


    }
}
