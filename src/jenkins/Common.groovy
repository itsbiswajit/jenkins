#!/usr/bin/groovy

package jenkins

/*
This method is to download code from a git repository
Param:
    git_url: Url of the git repository
    git_refspec: Branch name, Example: master
*/
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

/*
This method is to update config yaml file
param:
    test_platform: Win10/Mac_Catalina
*/

def update_config_file(test_platform){
    echo "Updating Utility Config File"
    withCredentials([[$class: 'UsernamePasswordMultiBinding',
                    credentialsId:"${USER_CREDENTIAL}",
                    usernameVariable: 'USER_NAME',
                    passwordVariable: 'USER_PASSWORD']]){
        if ("${test_platform}".toLowerCase().contains('win')){
            powershell """
                set PYTHONPATH=%cd%
                python superframework/resources/utility/update_utility_config.py --username "${USER_NAME}" --password "${USER_PASSWORD}" --studio_link_path "${STUDIO_LINK_PATH}" --scs_path "${STUDIO_LINK_PATH}" --bsp_path "${BSP_PATH}"
            """
        }else if("${test_platform}".toLowerCase().contains('mac')){
            sh """
                export PYTHONPATH="${env.WORKSPACE}"
                python3 superframework/resources/utility/update_utility_config.py --username "${USER_NAME}" --password "${USER_PASSWORD}" --studio_link_path "${STUDIO_LINK_PATH}" --scs_path "${STUDIO_LINK_PATH}" --bsp_path "${BSP_PATH}"
            """
        }
    }
}

/*
This method is to execute a testset from QAC
param:
    test_platform: Win10/Mac_Catalina
*/
def test_execution(test_platform){
    echo "Executing Test ..."
    withCredentials([[$class: 'UsernamePasswordMultiBinding',
                        credentialsId:"${QAC_CREDENTIAL}",
                        usernameVariable: 'QAC_USER_NAME',
                        passwordVariable: 'QAC_PASSWORD']]){
        if ("${test_platform}".toLowerCase().contains('win')){
            bat """
                set PYTHONPATH=%cd%
                python superframework/test_runner/test_runner.py --username "${QAC_USER_NAME}" --password "${QAC_PASSWORD}" --project "${PROJECT}" --testset "${QAC_TEST_SET}" --releasename "${RELEASE_NAME}"
            """
        }else if("${test_platform}".toLowerCase().contains('mac')){
            sh """
                export PYTHONPATH="${env.WORKSPACE}"
                python3 superframework/test_runner/test_runner.py --username "${QAC_USER_NAME}" --password "${QAC_PASSWORD}" --project "${PROJECT}" --testset "${QAC_TEST_SET}" --releasename "${RELEASE_NAME}"
            """
        }
    }
}

/*
This method is to get result as a zip file
param:
    test_platform: Win10/Mac_Catalina
    log_dir: Path of log folder
*/
def get_result(test_platform, log_dir){
    if ("${test_platform}".toLowerCase().contains('win')){
        powershell """
            echo "${env.WORKSPACE}"
            \$source = "${WORKSPACE}\\${log_dir}"
            \$destination = "${WORKSPACE}\\${log_dir}.zip"
            echo \$source > sourcefile
            echo \$destination > destinationfile
            Add-Type -assembly "system.io.compression.filesystem"
            [io.compression.zipfile]::CreateFromDirectory(\$source, \$destination)
        """
    }else if("${test_platform}".toLowerCase().contains('mac')){
        sh """
            zip -r "${env.WORKSPACE}/${log_dir}.zip" ${log_dir}/
        """
    }
}

/*
This Method is to upload a file to Artifactory
param:
    file_name: filename, Example: logs.zip
*/
def upload_artifactory(file_name){
    if("${UPLOAD_ARTIFACTORY}".toBoolean()){
        echo 'Uploading to Artifactory'
        def server = Artifactory.server('edi-art-prod-01')
        server.credentialsId = "${USER_CREDENTIAL}"
        def uploadSpec = """{
          "files": [
            {
                "pattern": "${env.WORKSPACE}/${file_name}",
                "target": "${ARTIFACTORY_UPLOAD_PATH}/${RELEASE_NAME}/${BUILD_NAME}/${BUILD_ID}/"
            }
         ]
        }""".stripIndent()
        def buildInfo = Artifactory.newBuildInfo()
        buildInfo.env.capture = true
        buildInfo=server.upload(uploadSpec)
        server.publishBuildInfo(buildInfo)
    }else{
        echo "Uploading to Artifactory skipped"
    }
}

/*
This method is to send Email Notification
param:
    project: Project Name
    suffix: test_platform/firmware_type, to construct the email subject with test_platform or firmware_type.
*/
def send_email_notification(project, suffix){
    if("${SEND_EMAIL}".toBoolean()){
        echo "Sending email"
        junit "TestResultSummary-*.xml"
        echo "Build completed. currentBuild.result = ${currentBuild.result}"
        emailext body: '''${SCRIPT, template="email_notification_custom.template"}''',
        mimeType: 'text/html',
        subject: "[${project}]Job ${env.JOB_NAME}_${suffix} [Status:${currentBuild.currentResult}]",
        to: "${EMAIL_LIST}"
    }else{
        echo "Email sending skipped"
    }
}
