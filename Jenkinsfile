/***** Demo Spring + Gradle cycle JenkinsFile for Tmax HE CI/CD *****/
// Required plugins (Need for the script): Kubernetes, Docker
// Recommended plugins (Used in the script, but not required): GitLab

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
							./gradlew -g "$GRADLE_HOME" \\
								clean build --stacktrace -x test
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
							&& find "build/test-results" \\
								-name "*.xml" \\
							|| true
						'''
				) .readLines () .sort ()

			// list of parallel jobs
			junitParallelSteps = [:]
			junitXmlList. eachWithIndex { path, idx ->

				// get file basename
				def posFrom = path .lastIndexOf ('/') + 1 // no occurence: 0
				def posTo = path .lastIndexOf ('.')
				def basename = path .substring (posFrom, posTo)

				junitParallelSteps .put (
					'[' + idx .toString () + '] ' + basename,
					{
						def res = junit (path)
						if (res .failCount == 0) {
							echo ('Test results of \'' + basename + '\': '
								+ '[ Total ' + res .totalCount
								+ ', Passed ' + res .passCount
								+ ', Failed ' + res .failCount
								+ ', Skipped ' + res .skipCount
								+ ' ]')
						} else {
							throw (new Exception ('Test failed: \''
								+ path + '\'') )
						}

					}
				)
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
			sh ('docker build --tag demo-app:0.0.1 .')
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
		}
	}

	onStageSuccess (stageName)
}


// run when a stage starts to run
def onStageRunning (stageName) {

	// notify gitlab
	if (gitlabStagesRemaining[stageName] != null) {
		callIfExist ('updateGitlabCommitStatus',
			[name: gitlabStagesRemaining[stageName], state: 'running'])
	}
}


// run when a stage succeeded
def onStageSuccess (stageName) {

	// notify gitlab
	if (gitlabStagesRemaining[stageName] != null) {
		callIfExist ('updateGitlabCommitStatus',
			[name: gitlabStagesRemaining[stageName], state: 'success'])
		gitlabStagesRemaining .remove (stageName)
	}
}


// run when a stage failed
def onStageFailure (stageName) {

	if (gitlabStagesRemaining[stageName] != null) {

		// notify gitlab
		callIfExist ('updateGitlabCommitStatus',
			[name: gitlabStagesRemaining[stageName], state: 'failed'])
		gitlabStagesRemaining .remove (stageName)

		// notify slack
		def buildUrl = (env.BUILD_URL + '/display/redirect')
			.replaceAll (/(?<!:)\/+/, '/')
		callIfExist ('slackSend',
			[
				color: 'danger',
				message: 'Job \'' + env.JOB_NAME
					+ '\' Failed (' + buildUrl + ')'
			]
		)
	}
}





/***************************************************
 *                                                 *
 *             [ Plugin Method Check ]             *
 *                                                 *
 ***************************************************/

def callIfExist (func, args, bodyCode=null) {
	try {
		if (bodyCode == null) {
			return ("$func" (args))
		} else {
			return ("$func" (args) { bodyCode () })
		}
	} catch (NoSuchMethodError error) { // catch & ignore 'no method found' exception only
		echo ('callIfExist: Method \'' + func + '\' not found, skip running')
		return (false)
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
	gitlabStagesRemaining = ['podinit': '0' .padLeft (padCount, '0') + '. PodInit']
	pipelineStages .eachWithIndex { key, value, idx ->
		gitlabStagesRemaining .put (
			key,
			(idx+1) .toString () .padLeft (padCount, '0') + '. ' + key
		)
	}


	// init pod, and iterate for defined stages
	try {
		// notify gitlab pending stages
		callIfExist ('gitlabBuilds',
			[builds: gitlabStagesRemaining .values () as ArrayList],
			{} )

		// set podinit stage as running
		onStageRunning ('podinit')

		podTemplate (podTemplateInfo) {
			node ('jenkins-slave-pod') {

				// set podinit stage as success
				onStageSuccess ('podinit')

				// iterate stages
				pipelineStages .each{ key, value ->
					runStage (key, value)
				}
			}
		}

	} catch (error) {

		// set podinit stage as failed, if pending
		onStageFailure ('podinit')

		// notify all remaining gitlab stages as canceled
		gitlabStagesRemaining .each { key, value ->
			callIfExist ('updateGitlabCommitStatus',
				[name: value, state: 'canceled'])
		}

		throw (error)
	}
}


// exec main ()
main ()
