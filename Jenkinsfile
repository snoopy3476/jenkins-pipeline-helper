/***** Demo Spring + Gradle cycle JenkinsFile for Tmax HE CI/CD *****/

/***************************************************
 *                                                 *
 *                   [ Config ]                    *
 *                                                 *
 ***************************************************/

gradleHomePath = '/home/jenkins/.gradle'





/***************************************************
 *                                                 *
 *                [ Pod Template ]                 *
 *                                                 *
 ***************************************************/

podTemplateInfo = [
	label: 'jenkins-slave-pod', 
	containers: [
		containerTemplate(
			name: 'build-container',
			image: 'openjdk:11',
			command: 'cat',
			ttyEnabled: true,
		),
		containerTemplate(
			name: 'push-container',
			image: 'docker',
			command: 'cat',
			ttyEnabled: true
		),
	],
	envVars: [
		// gradle home to store dependencies
		envVar(key: 'GRADLE_HOME', value: gradleHomePath),
	],
	volumes: [ 
		// for docker
		hostPathVolume(mountPath: '/var/run/docker.sock',
				hostPath: '/var/run/docker.sock'), 
		// gradle home caching: mount local host path to 'gradleHomePath'
		hostPathVolume(mountPath: gradleHomePath,
				hostPath: '/tmp/jenkins/.gradle'),
	],
]





/***************************************************
 *                                                 *
 *                   [ Stages ]                    *
 *                                                 *
 ***************************************************/

pipelineStages = [



/***** Checkout Stage *****/

	'Checkout': {
		checkout scm
	},



/***** Build Stage *****/

	'Build': {
		container('build-container') {

			try {
				// parallel build
				parallel 'build-submodule-01' : {

					sh '''#!/bin/bash
						./gradlew -g "$GRADLE_HOME" clean build --stacktrace -x test
					'''

				}, 'build-submodule-02 (dummy)' : {

					sh '''#!/bin/bash
						./gradlew -g "$GRADLE_HOME" -v
						echo dummy build script 1
					'''

				}, 'build-submodule-03 (dummy)' : {

					sh '''#!/bin/bash
						./gradlew -g "$GRADLE_HOME" -v
						echo dummy build script 2
					'''

				}
			} catch (error) {
				throw error
			} finally {
				archiveArtifacts artifacts: 'build/libs/**/*.jar, build/libs/**/*.war', fingerprint: true
			}

		}
	},



/***** Test Stage *****/

	'Test': {
		container('build-container') {

			try {
				sh './gradlew -g "$GRADLE_HOME" test --stacktrace --parallel'
			} catch (error) {
				throw error
			} finally {
				junit 'build/test-results/**/*.xml'
			}

//			// parallel test
//			parallel 'test-ApiTest' : {
//
//				sh './gradlew -g "$GRADLE_HOME" test --stacktrace --tests="net.hwkim.apigw.ApiTest"'
//
//			}, 'test-HelloSpringTest' : {
//
//				sh './gradlew -g "$GRADLE_HOME" test --stacktrace --tests="net.hwkim.apigw.HelloSpringTest"'
//
//			}
		}
	},



/***** Push Stage *****/

	'Push': {
		container('push-container') {
			echo '- Push'
			sleep 5
		}
	}

]





/***************************************************
 *                                                 *
 *              [ Stage Processing ]               *
 *                                                 *
 ***************************************************/

// run stage with some pre/post jobs for the stage
def runStage(stageName, stageCode) {

	onStageRunning(stageName)

	stage(stageName) {
		try {
			stageCode()

		} catch (error) {
			onStageFailure(stageName)
			throw error
		}			
	}

	onStageSuccess(stageName)
}


// run when a stage starts to run
def onStageRunning(stageName) {

	// notify gitlab //
	updateGitlabCommitStatus name: gitlabStageStrs[stageName], state: 'running'
}


// run when a stage succeeded
def onStageSuccess(stageName) {

	// notify gitlab //
	updateGitlabCommitStatus name: gitlabStageStrs[stageName], state: 'success'
}


// run when a stage failed
def onStageFailure(stageName) {

	// notify gitlab //

	// notify cur stage as failed first
	updateGitlabCommitStatus name: gitlabStageStrs[stageName], state: 'failed'

	// notify stages after the cur stage as canceled
	def stageToBeCanceled = false
	pipelineStages.each { key, value ->

		if (stageToBeCanceled) {
			updateGitlabCommitStatus name: gitlabStageStrs[key], state: 'canceled'
		} else if (key == stageName) {
			// stages after the cur stage will be notify as canceled
			stageToBeCanceled = true
		}
	}
}





/***************************************************
 *                                                 *
 *                [ Main Routine ]                 *
 *                                                 *
 ***************************************************/

def main() {

	// make array of gitlab stages (format: ##. stagename)
	gitlabStageStrs = ['podinit':'00. PodInit']
	pipelineStages.eachWithIndex { key, value, idx ->
		gitlabStageStrs.put(key, (idx+1).toString().padLeft(2, '0') + '. ' + key)
	}

	// notify gitlab pending stages
	gitlabBuilds(builds: gitlabStageStrs.values() as ArrayList) {


		// init pod, and iterate for defined stages
		onStageRunning('podinit')
		def podinitsuccess = false
		try {

			
			podTemplate(podTemplateInfo) {


				node('jenkins-slave-pod') {
					// podinit finished
					onStageSuccess('podinit')
					podinitsuccess = true



					pipelineStages.each{ key, value ->
						runStage(key, value)
					}


				}

			}

		} catch (error) {
			
			if (! podinitsuccess) {
				onStageFailure('podinit')
			}
			throw error
		}
	}
}


// exec main()
main()
