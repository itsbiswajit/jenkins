@Library('common-jenkins-library-local')

def common = new jenkins.Common()
pipeline {
    agent none
    stages {
        stage('INITIALIZE BUILD') {
            steps {
                script {
                    if ("${BUILD_NAME}") {
                        script {
                            currentBuild.displayName = "${BUILD_NAME}_${TEST_PLATFORM}_${env.BUILD_NUMBER}"
                        }
                    }
                }
            }
        }
        stage('Check Out Git Code'){
            agent { node { label "${TEST_NODE}" } }
            steps {
                script {
                    try {
                        def project_git_url = "${PROJECT_GIT_URL}"
                        echo "Cleaning Up the Workspcae ..."
                        cleanWs()
                        dir("superframework"){
                            common.downloadCode("${FRAMEWORK_GIT_URL}", "${FRAMEWORK_GIT_REFSPEC}")
                        }
                        stash includes: 'superframework/**', name: 'superframework'

                        dir("cobain"){
                            common.downloadCode("${PROJECT_GIT_URL}", "${PROJECT_GIT_REFSPEC}")
                        }
                        stash includes: 'cobain/**', name: 'cobain'
                    }
                    catch (exc) {
                        echo 'Testing failed!'
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
        }
        stage('Update Config File'){
            agent { node { label "${TEST_NODE}" } }
            steps {
                script {
                    try {
                        common.update_config_file("${TEST_PLATFORM}")
                    }
                    catch (Exception exc) {
                        echo "${exc}"
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
        }
        stage('Test Execution'){
            agent { node { label "${TEST_NODE}" } }
            steps {
                script {
                    try {
                        common.test_execution("${TEST_PLATFORM}")
                    }
                    catch (Exception exc) {
                        echo "${exc}"
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
        }
        stage('Results'){
            agent { node { label "${TEST_NODE}" } }
            steps {
                script {
                    common.get_result("${TEST_PLATFORM}", "logs")
                }
            }
        }
        stage('Email Notification'){
            agent { node { label "${TEST_NODE}" } }
            steps {
                script {
                    common.send_email_notification("COBAIN", "${TEST_PLATFORM}")
                }
            }
        }
    }
}
