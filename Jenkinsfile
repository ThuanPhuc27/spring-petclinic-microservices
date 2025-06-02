pipeline {
    agent { label 'built-in' }

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

                        echo "Commit ID: ${env.GIT_TAG}"
                    } catch (Exception e) {
                        echo "Failed to retrieve Commit ID: ${e.getMessage()}"
                        env.GIT_TAG = "latest"
                    }
                }
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    env.CHANGED_SERVICES = getChangedServices()
                    if (env.CHANGED_SERVICES == "NONE") {
                        echo "No relevant changes detected. Skipping build."
                        error("No relevant changes detected")
                    } else {
                        echo "Detected changes in services: ${env.CHANGED_SERVICES}"
                    }
                }
            }
        }

        stage('Sonarqube scan') {
            when {
                expression { env.CHANGED_SERVICES && env.CHANGED_SERVICES.trim() }
            }
            steps {
                script {
                    def sonarConfigs = [
                        'spring-petclinic-customers-service': [ projectKey: 'SONAR_PROJECTKEY_CUSTOMERS_SERVICE', tokenCredentialId: 'SONAR_TOKEN_CUSTOMERS_SERVICE' ],
                        'spring-petclinic-vets-service': [ projectKey: 'SONAR_PROJECTKEY_VETS_SERVICE', tokenCredentialId: 'SONAR_TOKEN_VETS_SERVICE' ],
                        'spring-petclinic-visits-service': [ projectKey: 'SONAR_PROJECTKEY_VISITS_SERVICE', tokenCredentialId: 'SONAR_TOKEN_VISITS_SERVICE' ]
                        // Thêm các service khác ở đây
                    ]

                    def services = env.CHANGED_SERVICES.split(',')
                    def parallelSonarScan = [:]

                    services.each { service ->
                        def config = sonarConfigs[service]
                        if (!config) {
                            error "No SonarQube config defined for service: ${service}"
                        }
                        parallelSonarScan[service] = {
                            stage("Sonarqube Scan: ${service}") {
                                withCredentials([
                                    string(credentialsId: 'SONAR_HOST', variable: 'SONAR_HOST'),
                                    string(credentialsId: config.tokenCredentialId, variable: 'SONAR_TOKEN'),
                                    string(credentialsId: config.projectKey, variable: 'SONAR_PROJECTKEY'),
                                ])
                                {
                                    script {
                                            sh """
                                                docker run --rm \
                                                    -v ${WORKSPACE}:/usr/src \
                                                    sonarsource/sonar-scanner-cli:latest \
                                                    sonar-scanner \
                                                    -Dsonar.host.url=${SONAR_HOST} \
                                                    -Dsonar.token=${SONAR_TOKEN} \
                                                    -Dsonar.projectKey=${SONAR_PROJECTKEY}
                                                    -Dsonar.branch.target=security-test \
                                                    -Dsonar.projectBaseDir=${WORKSPACE}/${service}
                                            """
                                    }
                                }
                            }
                        }
                    }

                    parallel parallelSonarScan

                }
            }
        }



        stage('Run Unit Test') {
            when {
                expression { env.CHANGED_SERVICES && env.CHANGED_SERVICES.trim() }
            }
            steps {
                script {
                    sh "apt update && apt install -y maven"
                    def services = env.CHANGED_SERVICES.split(',')
                    def coverageResults = []
                    def servicesToBuild = []
                    def parallelTests = [:]

                    services.each { service ->
                    
                        parallelTests[service] = {
                            stage("Test: ${service}") {
                                
                                try {
                                    sh "mvn test -pl ${service} -DskipTests=false"
                                    sh "mvn jacoco:report -pl ${service}"

                                    def reportPath = "${service}/target/site/jacoco/index.html"
                                    def resultPath = "${service}/target/surefire-reports/*.txt"

                                    def coverage = 0

                                    if (fileExists(reportPath)) {
                                        archiveArtifacts artifacts: resultPath, fingerprint: true
                                        archiveArtifacts artifacts: reportPath, fingerprint: true

                                        coverage = sh(
                                            script: """
                                            grep -oP '(?<=<td class="ctr2">)\\d+%' ${reportPath} | head -1 | sed 's/%//'
                                            """,
                                            returnStdout: true
                                        ).trim()

                                        if (!coverage) {
                                            echo "⚠️ Warning: Coverage extraction failed for ${service}. Setting coverage to 0."
                                            coverage = 0
                                        } else {
                                            coverage = coverage.toInteger()
                                        }
                                    } else {
                                        echo "⚠️ Warning: No JaCoCo report found for ${service}. Setting coverage to 0."
                                    }

                                    echo "📊 Code Coverage for ${service}: ${coverage}%"
                                    coverageResults << "${service}:${coverage}%"

                                    if (coverage < 70) {
                                        error "❌ ${service} has insufficient test coverage: ${coverage}%. Minimum required is 70%."
                                    } else {
                                        servicesToBuild << service
                                    }
                                    
                                } catch (Exception e) {
                                    echo "❌ Error while testing ${service}: ${e.getMessage()}"
                                }
                            }
                        }
                    }

                    parallel parallelTests

                    env.CODE_COVERAGES = coverageResults.join(', ')
                    env.SERVICES_TO_BUILD = servicesToBuild.join(',')
                    echo "Final Code Coverages: ${env.CODE_COVERAGES}"
                    echo "Services to Build: ${env.SERVICES_TO_BUILD}"
                }
            }
        }


        stage('Build Services') {
            when {
                expression { env.SERVICES_TO_BUILD && env.SERVICES_TO_BUILD.trim() }
            }
            steps {
                script {
                    def services = env.SERVICES_TO_BUILD.split(',')
                    def parallelBuilds = [:]

                    services.each { service ->
                        parallelBuilds[service] = {
                            stage("Build: ${service}") {
                                try {
                                    echo "🚀 Building: ${service}"
                                    sh "mvn clean package -pl ${service} -DfinalName=app -DskipTests"
                                    
                                    def jarfile = "${service}/target/app.jar"
                                    archiveArtifacts artifacts: jarfile, fingerprint: true
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
                expression { env.SERVICES_TO_BUILD && env.SERVICES_TO_BUILD.trim() && env.GIT_TAG }
            }
            steps {
                script {
                    def services = env.SERVICES_TO_BUILD.split(',')
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

        stage('Trivy_Image_Scan') {
            when {
                expression { env.SERVICES_TO_BUILD && env.SERVICES_TO_BUILD.trim() && env.GIT_TAG }
            }
            steps {

                script {
                    def services = env.SERVICES_TO_BUILD.split(',')
                    def parallelImageScan = [:]

                    services.each { service ->
                        parallelImageScan[service] = {
                            stage("Scan image: ${service}") {
                                sh """
                                    docker run --rm -v ${WORKSPACE}:/${service} -v \
                                        /var/run/docker.sock:/var/run/docker.sock \
                                        aquasec/trivy image --download-db-only

                                    docker run --rm -v ${WORKSPACE}:/${service} -v /var/run/docker.sock:/var/run/docker.sock \
                                        aquasec/trivy image --format template --template "@contrib/html.tpl" \
                                        --output /${service}/${service}.html ${DOCKER_IMAGE_BASENAME}/${service}:${env.GIT_TAG}
                                """
                                def resultPath = "${WORKSPACE}/${service}.html"
                                archiveArtifacts artifacts: resultPath, fingerprint: true
                            }
                        }
                    }

                    parallel parallelImageScan 
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
                expression { env.SERVICES_TO_BUILD && env.SERVICES_TO_BUILD.trim() && env.GIT_TAG }
            }
            steps {
                script {
                    def services = env.SERVICES_TO_BUILD.split(',')
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
            echo 'Cleaning up...'
            sh "docker logout ${REGISTRY_URL}"
        }
        success {
            publishChecks(
                name: 'PipelineResult',
                title: 'Code Coverage Check Success',
                status: 'COMPLETED',
                conclusion: 'SUCCESS',
                summary: 'Pipeline completed successfully.',
                detailsURL: env.BUILD_URL
            )
        }

        failure {
            publishChecks(
                name: 'PipelineResult',
                title: 'Code Coverage Check Fail',
                status: 'COMPLETED',
                conclusion: 'FAILURE', 
                summary: 'Pipeline failed. Check logs for details.',
                detailsURL: env.BUILD_URL
            )
        }
    }

}

def getChangedServices() {

    def changedFiles = sh(script: "git diff --name-only origin/${env.BRANCH_NAME}~1 origin/${env.BRANCH_NAME}", returnStdout: true).trim().split("\n")

    def services = [
        'spring-petclinic-customers-service', 
        'spring-petclinic-vets-service',
        'spring-petclinic-visits-service'
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