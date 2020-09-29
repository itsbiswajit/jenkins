pipeline {
    agent none
    stages {
        stage('INITIALIZE BUILD') {
            steps {
                script {
                    if ("${BUILD_NAME}") {
                        script {
                            currentBuild.displayName = "${BUILD_NAME}_${env.BUILD_NUMBER}"
                        }
                    }
                }
            }
        }
        stage('Check Out Git Code'){
            agent { node { label "${TEST_NODE}" } }
            steps {
                script {
                        def project_git_url = "${PROJECT_GIT_URL}"
                        echo "Cleaning Up the Workspcae ..."
                        cleanWs()
                        dir("python"){
                            downloadCode("${PROJECT_GIT_URL}", "${PROJECT_GIT_REFSPEC}")
                        }
                        stash includes: 'python/**', name: 'python'
                }
            }
        }
        stage('Execution'){
            agent { node { label "${TEST_NODE}" } }
            steps {
                script {

                    test_execution()
                }
            }
        }
    }
}

def downloadCode(git_url, git_refspec){
    echo "Git Repository: " + git_url
    checkout([$class: 'GitSCM',
    branches: [[name: 'FETCH_HEAD']],
    doGenerateSubmoduleConfigurations: false,
    extensions: [],
    submoduleCfg: [],
    userRemoteConfigs: [[credentialsId: "${USER_CREDENTIAL}",
                        refspec: git_refspec,
                        url: git_url]]
    ])
}

def test_execution(){
    echo "Executing Test ..."
    bat """
        set PYTHONPATH=%cd%
        python python/test.py
        """
}
