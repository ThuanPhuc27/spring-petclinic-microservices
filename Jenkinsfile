
pipeline {
    agent any

    environment {
        CHANGED_SERVICES = getChangedServices()
        REGISTRY_URL = "docker.io"
        DOCKER_IMAGE_BASENAME = "thuanlp"
    }

    stages {
        stage('Checkout source') {
            steps {
                checkout scm

                script {
                    try {
                        def commitId = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                        env.GIT_TAG = commitId
                        echo "using Commit ID: ${env.GIT_TAG}"
                    } catch (Exception e) {
                        error("No Commit ID")
                    }
                }
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    env.CHANGED_SERVICES = getChangedServices()
                    if (env.CHANGED_SERVICES == "NONE") {
                        error("No relevant changes detected")
                    } else {
                        echo "Detected changes in services: ${env.CHANGED_SERVICES}"
                    }
                }
            }
        }

        stage('Build Services') {
            when {
                expression { env.CHANGED_SERVICES.trim() }
            }
            steps {
                script {
                    def services = env.CHANGED_SERVICES.split(',')
                    def parallelBuilds = [:]

                    services.each { service ->
                        parallelBuilds[service] = {
                            stage("Build: ${service}") {
                                try {
                                    echo "🚀 Building: ${service}"
                                    sh "mvn clean package -pl ${service} -DfinalName=app -DskipTests"
                                } catch (Exception e) {
                                    echo "❌ Build failed for ${service}: ${e.getMessage()}"
                                    error("Build failed for ${service}")
                                }
                            }
                        }
                    }

                    parallel parallelBuilds
                }
            }
        }


        stage('Build Docker Image') {
            when {
                expression { env.CHANGED_SERVICES.trim() && env.GIT_TAG }
            }
            steps {
                script {
                    def services = env.CHANGED_SERVICES.split(',')
                    def parallelDockerBuilds = [:]

                    services.each { service ->
                        parallelDockerBuilds[service] = {
                            stage("Docker Build: ${service}") {
                                try {
                                    echo "🐳 Building Docker Image for: ${service}"
                                    sh "docker build --build-arg ARTIFACT_NAME=${service}/target/app -t ${DOCKER_IMAGE_BASENAME}/${service}:${env.GIT_TAG} -f docker/Dockerfile ."
                                } catch (Exception e) {
                                    echo "❌ Docker Build failed for ${service}: ${e.getMessage()}"
                                    error("Docker Build failed for ${service}")
                                }
                            }
                        }
                    }

                    parallel parallelDockerBuilds
                }
            }
        }

        stage('Login to Docker Registry') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh "echo $DOCKER_PASS | docker login ${REGISTRY_URL} -u $DOCKER_USER --password-stdin"
                    }
                }
            }
        }

        stage('Push Docker Image') {
            when {
                expression { env.CHANGED_SERVICES.trim() && env.GIT_TAG }
            }
            steps {
                script {
                    def services = env.CHANGED_SERVICES.split(',')
                    def parallelDockerPush = [:]

                    services.each { service ->
                        parallelDockerPush[service] = {
                            stage("Docker Push: ${service}") {
                                try {
                                    echo "🐳 Push Docker Image for: ${service}"
                                    sh "docker push ${DOCKER_IMAGE_BASENAME}/${service}:${env.GIT_TAG}"
                                } catch (Exception e) {
                                    echo "❌ Docker Push failed for ${service}: ${e.getMessage()}"
                                    error("Docker Push failed for ${service}")
                                }
                            }
                        }
                    }

                    parallel parallelDockerPush 
                }
            }
        }
   }

    post {
        always {
            sh "docker logout ${REGISTRY_URL}"
        }
        success {
            echo "CI pipeline run successfully!"
        }
        failure {
            echo "Build or push failed. Please check the logs."
        }
    }
}

def getChangedServices() {

    def changedFiles = sh(script: "git diff --name-only origin/${env.BRANCH_NAME}~1 origin/${env.BRANCH_NAME}", returnStdout: true).trim().split("\n")
    def services = [
        'spring-petclinic-admin-server', 
        'spring-petclinic-config-server',
        'spring-petclinic-discovery-server',
        'spring-petclinic-genai-service',
        'spring-petclinic-api-gateway',
    ]

    def affectedServices = services.findAll { service ->
        changedFiles.any { file -> file.startsWith(service + "/") }
    }

    if (affectedServices.isEmpty()) {
        return "NONE"
    }
    echo "Changed services: ${affectedServices.join(', ')}"
    return affectedServices.join(',')
}