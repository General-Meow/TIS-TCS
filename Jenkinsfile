@Library('utils@master')_

def utils = new hee.tis.utils()

node {

    if (env.BRANCH_NAME != "master") {
        // PR and branch builds are done by GitHub Actions.
        return
    }

    def service = "tcs"

    deleteDir()

    stage('Checkout Git Repo') {
      checkout scm
    }

    env.GIT_COMMIT=sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
    def mvn = "${tool 'Maven 3.3.9'}/bin/mvn"
    def workspace = pwd()
    def parent_workspace = pwd()
    def repository = "${env.GIT_COMMIT}".split("TIS-")[-1].split(".git")[0]
    def buildNumber = env.BUILD_NUMBER
    def buildVersion = env.GIT_COMMIT
    def imageName = ""
    def imageVersionTag = ""
    boolean isService = false

    println "[Jenkinsfile INFO] Commit Hash is ${GIT_COMMIT}"

    if (fileExists("$workspace/$service-service/pom.xml")) {
        workspace = "$workspace/$service-service"
        env.WORKSPACE= workspace
        sh 'cd "$workspace"'
        isService = true
    }

    try {

        milestone 1


        stage('Build') {
          sh "'${mvn}' clean install -DskipTests"
        }

        stage('Unit Tests') {
          withCredentials([usernamePassword(credentialsId: 'AZUREBLOBTISDEVCRED', usernameVariable: 'BLOBACCOUNTNAME', passwordVariable: 'BLOBACCOUNTKEY')]) {
            env.CLOUD_BLOB_ACCOUNT_NAME = "${BLOBACCOUNTNAME}"
            env.CLOUD_BLOB_ACCOUNT_KEY = "${BLOBACCOUNTKEY}"
            env.CLOUD_BLOB_CONTAINER_NAME = "document-manager"
          }
          try {
            sh "'${mvn}' clean test"
          } finally {
            junit '**/target/surefire-reports/TEST-*.xml'
          }
        }

        milestone 2

        stage('Dockerise') {
          env.VERSION = utils.getMvnToPom(workspace, 'version')
          env.GROUP_ID = utils.getMvnToPom(workspace, 'groupId')
          env.ARTIFACT_ID = utils.getMvnToPom(workspace, 'artifactId')
          env.PACKAGING = utils.getMvnToPom(workspace, 'packaging')
          imageName = env.ARTIFACT_ID
          imageVersionTag = env.GIT_COMMIT

          if (isService) {
              imageName = service
              env.IMAGE_NAME = imageName
          }

          //urghhh
          sh "mvn package -DskipTests"
          sh "cp ./tcs-service/target/tcs-service-*.war ./tcs-service/target/app.jar"

          def dockerImageName = "tcs"
          def containerRegistryLocaltion = "430723991443.dkr.ecr.eu-west-2.amazonaws.com"

          // log into aws docker
          sh "aws ecr get-login-password --region eu-west-2 | docker login --username AWS --password-stdin 430723991443.dkr.ecr.eu-west-2.amazonaws.com"

          sh "docker build -t ${containerRegistryLocaltion}/${dockerImageName}:$buildVersion -f ./tcs-service/Dockerfile ./tcs-service"
          sh "docker push ${containerRegistryLocaltion}/${dockerImageName}:$buildVersion"

          sh "docker tag ${containerRegistryLocaltion}/${dockerImageName}:$buildVersion ${containerRegistryLocaltion}/tcs:latest"
          sh "docker push ${containerRegistryLocaltion}/${dockerImageName}:latest"

          sh "docker rmi ${containerRegistryLocaltion}/${dockerImageName}:latest"
          sh "docker rmi ${containerRegistryLocaltion}/${dockerImageName}:$buildVersion"

          println "[Jenkinsfile INFO] Stage Dockerize completed..."
        }

        if (env.BRANCH_NAME == "master") {
        def healthcheckEndpoint = "/tcs/actuator/health"

          milestone 3

          stage('Staging') {
            node {
              println "[Jenkinsfile INFO] Stage Deploy starting..."

              sh "ansible-playbook -i $env.DEVOPS_BASE/ansible/inventory/stage $env.DEVOPS_BASE/ansible/${service}.yml --extra-vars=\"{\'versions\': {\'${service}\': \'${env.GIT_COMMIT}\'}}\""
            }
          }
         stage('Health check on Staging') {
            withEnv(["endpoint=${healthcheckEndpoint}"]) {
              def counter = 0;
              def httpStatus = "";
              while(counter < 20){
                println "Counter: "+counter;
                try{
                  httpStatus=sh(returnStdout: true, script: 'sleep 15; curl -m 300 -s -o /dev/null -w "%{http_code}" 10.160.0.137:8093${endpoint}').trim()
                  break;
                } catch (hudson.AbortException ae){
                  counter ++;
                }
              }
              if("200" == "${httpStatus}")  println "Status is 200"
                else  throw new Exception("health check failed on Stage with http status: $httpStatus")
            }
          }


          milestone 4

          stage('Approval') {
            timeout(time:5, unit:'HOURS') {
              input message: 'Deploy to production?', ok: 'Deploy!'
            }
          }

          milestone 5

          stage('Production') {
            node {
              sh "ansible-playbook -i $env.DEVOPS_BASE/ansible/inventory/prod $env.DEVOPS_BASE/ansible/${service}.yml --extra-vars=\"{\'versions\': {\'${service}\': \'${env.GIT_COMMIT}\'}}\""
              sh "ansible-playbook -i $env.DEVOPS_BASE/ansible/inventory/nimdta $env.DEVOPS_BASE/ansible/${service}.yml --extra-vars=\"{\'versions\': {\'${service}\': \'${env.GIT_COMMIT}\'}}\""
            }
          }
         stage('Health check on Production') {
            withEnv(["endpoint=${healthcheckEndpoint}"]) {
              def counter = 0;
              def httpStatus = "";
              while(counter < 20){
                println "Counter: "+counter;
                try{
                  httpStatus=sh(returnStdout: true, script: 'sleep 15; curl -m 300 -s -o /dev/null -w "%{http_code}" 10.170.0.137:8093${endpoint}').trim()
                  break;
                } catch (hudson.AbortException ae){
                  counter ++;
                }
              }
              if("200" == "${httpStatus}")  println "Status is 200"
                else  throw new Exception("health check failed on Production with http status: $httpStatus")
            }
          }


        }
    } catch (hudson.AbortException ae) {
      // We do nothing for Aborts.
    } catch (err) {
      currentBuild.result = 'FAILURE'
      throw err
    }
}
