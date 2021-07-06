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
		containerTemplate (
			name: 'build-container',
			image: 'openjdk:11',
			command: 'cat',
			ttyEnabled: true,
		),
		containerTemplate (
			name: 'push-container',
			image: 'docker',
			command: 'cat',
			ttyEnabled: true
		),
	],
	envVars: [
		// gradle home to store dependencies
		envVar (key: 'GRADLE_HOME', value: gradleHomePath),
	],
	volumes: [ 
		// for docker
		hostPathVolume (mountPath: '/var/run/docker.sock',
				hostPath: '/var/run/docker.sock'), 
		// gradle home caching: mount local host path to 'gradleHomePath'
		hostPathVolume (mountPath: gradleHomePath,
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
		checkout (scm)
	},



/***** Build Stage *****/

	'Build': {
		try {

			container ('build-container') {

				// parallel build
				parallel ('[0] Actial gradle build': {

					sh (
						label: 'Gradle Build',
						script: '''#!/bin/bash
							./gradlew -g "$GRADLE_HOME" clean build --stacktrace -x test
							'''
					)

				}, '[1] Dummy for submodule': {

					sh (
						label: 'Dummy Submodule Build',
						script: '''#!/bin/bash
							./gradlew -g "$GRADLE_HOME" -v
							echo dummy build script 1
						'''
					)

				}, '[2] Dummy for submodule': {

					sh (
						label: 'Dummy Submodule Build',
						script: '''#!/bin/bash
							./gradlew -g "$GRADLE_HOME" -v
							echo dummy build script 2
							'''
					)

				})

			}

		} catch (error) {
			throw (error)
		} finally {

			// archive built results
			archiveArtifacts (
				label: 'Archiving Artifacts',
				artifacts: 'build/libs/**/*.jar, build/libs/**/*.war',
				fingerprint: true
			)

		}
	},



/***** Test Stage *****/

	'Test': {
		try {

			container ('build-container') {
				sh (
					label: 'Gradle Parallel Test',
					script: './gradlew -g "$GRADLE_HOME" test --parallel'
				)
			}

		} catch (error) {
			throw (error)
		} finally {

			// list of xml file list
			junitXmlList =
				sh (
					label: 'Getting Junit Xml List',
					returnStdout: true,
					script: '''
							[ -d "build/test-results" ] \\
							&& find "build/test-results" -name "*.xml" \\
							|| true
						'''
				) .readLines () .sort ()

			// list of parallel jobs
			junitParallelSteps = [:]
			junitXmlList. eachWithIndex { path, idx ->

				// get file basename
				def posFrom = path .lastIndexOf ('/') + 1 // if -1 (no occurence), then set to 0
				def posTo = path .lastIndexOf ('.')
				def basename = path .substring (posFrom, posTo)

				junitParallelSteps << [('[' + idx .toString () + '] ' + basename): {

					def summary = junit (path)

					if (summary .failCount == 0) {
						echo ('Test summary of \'' + basename + '\': '
							+ '[ Total ' + summary .totalCount
							+ ', Passed ' + summary .passCount
							+ ', Failed ' + summary .failCount
							+ ', Skipped ' + summary .skipCount + ' ]')
					} else {
						throw (new Exception ('Test failed: \'' + path + '\'') )
					}

				}]
			}

			// execute parallel junit jobs
			if (junitParallelSteps .size () > 0) {
				parallel (junitParallelSteps)
			}

		}

	},



/***** Push Stage *****/

	'Push': {
		container ('push-container') {
			echo ('- Push')
			sleep (5)
		}
	}

]






/***************************************************
 *                                                 *
 *              [ Stage Processing ]               *
 *                                                 *
 ***************************************************/

// run stage with some pre/post jobs for the stage
def runStage (stageName, stageCode) {

	onStageRunning (stageName)

	stage (stageName) {
		try {
			stageCode ()

		} catch (error) {
			onStageFailure (stageName)
			throw (error)
//		} finally {

		}
	}

	onStageSuccess (stageName)
}


// run when a stage starts to run
def onStageRunning (stageName) {

	// notify gitlab //
	updateGitlabCommitStatus (name: gitlabStageStrs[stageName], state: 'running')
}


// run when a stage succeeded
def onStageSuccess (stageName) {

	// notify gitlab //
	updateGitlabCommitStatus (name: gitlabStageStrs[stageName], state: 'success')
}


// run when a stage failed
def onStageFailure (stageName) {

	// notify gitlab //

	// notify cur stage as failed first
	updateGitlabCommitStatus (name: gitlabStageStrs[stageName], state: 'failed')

	// notify stages after the cur stage as canceled
	def stageToBeCanceled = false
	pipelineStages .each { key, value ->

		if (stageToBeCanceled) {
			updateGitlabCommitStatus (name: gitlabStageStrs[key], state: 'canceled')
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

def main () {

	// make array of gitlab stages (format: ##. stagename)
	def stageLen = pipelineStages .size () + 1
	def padCount = stageLen .toString () .length ()
	gitlabStageStrs = ['podinit': '0' .padLeft (padCount, '0') + '. PodInit']
	pipelineStages .eachWithIndex { key, value, idx ->
		gitlabStageStrs .put (key, (idx+1) .toString () .padLeft (padCount, '0') + '. ' + key)
	}

	// notify gitlab pending stages
	gitlabBuilds (builds: gitlabStageStrs .values () as ArrayList) {


		// init pod, and iterate for defined stages
		onStageRunning ('podinit')
		def podinitsuccess = false
		try {

			
			podTemplate (podTemplateInfo) {


				node ('jenkins-slave-pod') {
					// podinit finished
					onStageSuccess ('podinit')
					podinitsuccess = true

					pipelineStages .each{ key, value ->
						runStage (key, value)
					}


				}

			}

		} catch (error) {
			
			if (! podinitsuccess) {
				onStageFailure ('podinit')
			}
			throw (error)
		}
	}
}


// exec main ()
main ()
