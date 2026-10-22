def call(Map config = [:]) {
    def repoUrl = config.repoUrl ?: 'https://github.com/OT-MICROSERVICES/frontend.git'
    def branchName = config.branch ?: 'main'
    def scannerPath = '/opt/sonar-scanner/bin/sonar-scanner'
    def projectKey = config.sonarProjectKey ?: 'react-frontend'
    def projectName = config.sonarProjectName ?: 'React Frontend'

    node {
        try {
            stage('Checkout') {
                deleteDir()
                git branch: branchName, url: repoUrl
            }

            stage('Verify Environment') {
                sh '''
                    set -eu
                    node --version
                    npm --version
                    test -f package.json
                    test -d src
                    echo "React project detected"
                '''
            }

            stage('Install Dependencies') {
                sh '''
                    set -eu
                    if [ -f package-lock.json ]; then
                        npm ci
                    else
                        npm install
                    fi
                '''
            }

            stage('Test') {
                sh '''
                    set -eu

                    if find src -type f \
                        \\( -name '*.test.js' -o -name '*.test.jsx' \
                        -o -name '*.spec.js' -o -name '*.spec.jsx' \
                        -o -name '*.test.ts' -o -name '*.test.tsx' \
                        -o -name '*.spec.ts' -o -name '*.spec.tsx' \\) \
                        -print -quit | grep -q .; then
                        CI=true npm test -- --watchAll=false
                    else
                        echo "WARNING: No recognized test files found."
                        echo "Tests were skipped, not passed."
                    fi
                '''
            }

            stage('SonarQube Static Analysis') {
                withCredentials([
                    string(
                        credentialsId: 'sonarqube-token',
                        variable: 'SONAR_TOKEN'
                    )
                ]) {
                    withEnv([
                        "SONAR_PROJECT_KEY=${projectKey}",
                        "SONAR_PROJECT_NAME=${projectName}"
                    ]) {
                        sh '''
                            set -eu
                            test -x /opt/sonar-scanner/bin/sonar-scanner

                            /opt/sonar-scanner/bin/sonar-scanner \
                              -Dsonar.projectKey="$SONAR_PROJECT_KEY" \
                              -Dsonar.projectName="$SONAR_PROJECT_NAME" \
                              -Dsonar.sources=src \
                              -Dsonar.exclusions="**/node_modules/**,**/build/**" \
                              -Dsonar.host.url=http://localhost:9000 \
                              -Dsonar.token="$SONAR_TOKEN"
                        '''
                    }
                }
            }

            stage('Build') {
                sh '''
                    set -eu
                    NODE_OPTIONS=--openssl-legacy-provider npm run build
                '''
            }

        } catch (err) {
            currentBuild.result = 'FAILURE'
            throw err
        } finally {
            echo "React Shared Library pipeline finished with status: ${currentBuild.currentResult}"
        }
    }
}
