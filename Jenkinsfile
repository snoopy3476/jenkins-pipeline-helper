/***** Demo Spring + Gradle cycle JenkinsFile *****/
// Required plugins (Need for the script): Kubernetes, Docker
// Recommended plugins (Used in the script, but not required): GitLab





/***************************************************
 *                                                 *
 *                   [ Config ]                    *
 *                                                 *
 ***************************************************/

// local env vars
def localEnv = [

	// gradle config
	GRADLE_HOME_PATH: '/home/jenkins/.gradle',
	GRADLE_LOCAL_CACHE_PATH: '/tmp/jenkins/.gradle',

	// docker private registry config
	PRIVATE_REG_URL: 'https://k8s-docker-registry',
	PRIVATE_REG_PORT: '30000',
	DEPLOY_IMG_NAME: env.JOB_NAME, // Job name should be docker-img-name-compatible
	DEPLOY_IMG_TAG: 'build-' + env.BUILD_NUMBER + '_commit-' + env.GIT_COMMIT,
	PRIVATE_REG_CRED_ID: 'inner-private-registry-cred', // Jenkins credential

	// slackSend config
	BUILD_TIME_STR: getCurrentTimeStr ('Asia/Seoul'),

]



// get current time string, with timezone
def getCurrentTimeStr (timezone='UTC') {
        def simpleDateFormat = (new java.text.SimpleDateFormat('yyyy.MM.dd-HH:mm:ss-z'))
        simpleDateFormat.setTimeZone (TimeZone.getTimeZone(timezone))
	return (simpleDateFormat.format(new Date()))
}



// convert map to withEnv arguments
def mapToEnv (map) {
	return ( map.collect({ key, value -> "${key}=${value}" }) )
}





/***************************************************
 *                                                 *
 *                [ Pod Template ]                 *
 *                                                 *
 ***************************************************/

def getPodTemplateInfo () { return ([

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
	volumes: [ 
		// for docker
		hostPathVolume (mountPath: '/var/run/docker.sock',
				hostPath: '/var/run/docker.sock'), 
		// gradle home caching: mount local host path to 'env.GRADLE_HOME_PATH'
		hostPathVolume (mountPath: env.GRADLE_HOME_PATH,
				hostPath: env.GRADLE_LOCAL_CACHE_PATH),
	],


])}





/***************************************************
 *                                                 *
 *                   [ Stages ]                    *
 *                                                 *
 ***************************************************/

def getPipelineStages () { return ([

// PodInitialize Stage: for k8s pod init stage, should not be removed
	PodInitialize: { assert (false) }, 



/***** Checkout Stage *****/

	Checkout: {
		checkout (scm)
	},



/***** Build Stage *****/

	Build: {
		try {

			container ('build-container') {

				// parallel build
				parallel ('[0] Actial gradle build': {

					sh (
						label: 'Gradle Build',
						script: """
							./gradlew -g ${env.GRADLE_HOME_PATH} --parallel \\
								clean build --stacktrace -x test
						"""
					)

				}, '[1] Dummy for submodule': {

					sh (
						label: 'Dummy Submodule Build',
						script: """
							./gradlew -g ${env.GRADLE_HOME_PATH} -v
						"""
					)

				}, '[2] Dummy for submodule': {

					sh (
						label: 'Dummy Submodule Build',
						script: """
							./gradlew -g ${env.GRADLE_HOME_PATH} -v
						"""
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

	Test: {
		try {

			container ('build-container') {
				sh (
					label: 'Gradle Parallel Test',
					script: """
						./gradlew -g ${env.GRADLE_HOME_PATH} --parallel \\
							test --stacktrace -x build
					"""
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
				).readLines().sort()

			// list of parallel jobs
			junitParallelSteps = [:]
			junitXmlList.eachWithIndex ({ path, idx ->

				// get file basename
				def posFrom = path.lastIndexOf ('/') + 1 // no occurence: 0
				def posTo = path.lastIndexOf ('.')
				def basename = path.substring (posFrom, posTo)

				junitParallelSteps.put (
					"[${idx}] ${basename}",
					{
						def res = junit (path)
						if (res.failCount == 0) {
							echo (
								"Test results of '${basename}': ["
								+ "Total ${res.totalCount}, "
								+ "Passed ${res.passCount}, "
								+ "Failed ${res.failCount}, "
								+ "Skipped ${res.skipCount}]"
							)
						} else {
							throw (new Exception ("Test failed: '${path}'"))
						}

					}
				)
			})

			// execute parallel junit jobs
			if (junitParallelSteps.size() > 0) {
				parallel (junitParallelSteps)
			}

		}

	},



/***** Push Stage *****/

	Push: {
		container ('push-container') {
			dockerImg = docker.build (env.DEPLOY_IMG_NAME)
			docker.withRegistry ("${env.PRIVATE_REG_URL}:${env.PRIVATE_REG_PORT}",
				env.PRIVATE_REG_CRED_ID) {
				dockerImg.push (env.DEPLOY_IMG_TAG)
				dockerImg.push ()
			}
		}
	}


])}






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
	return (true)
}


// run when a stage starts to run
def onStageRunning (stageName) {

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageName}", state: 'running'])
}


// run when a stage succeeded
def onStageSuccess (stageName) {

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageName}", state: 'success'])

}


// run when a stage failed
def onStageFailure (stageName) {

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageName}", state: 'failed'])

	// notify slack
	callIfExist ('slackSend',
		[
			color: 'danger',
			message: "[${env.BUILD_TIME_STR}] Job FAILED" + """
"""					+ "- ${env.JOB_NAME} "
				+ "(${(env.BRANCH_NAME != null) ? (env.BRANCH_NAME + '/') : ('')}"
				+ "${env.BUILD_NUMBER})"
				+ ": stage '${stageName}'" + """
"""					+ "(Link: ${env.RUN_DISPLAY_URL})"
		]
	)

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
			return ("$func" (args) { bodyCode() })
		}
	} catch (NoSuchMethodError error) { // catch & ignore 'no method found' exception only
		echo ("callIfExist: Method '${func}' not found, skip call")
		return (null)
	}
}





/***************************************************
 *                                                 *
 *                [ Main Routine ]                 *
 *                                                 *
 ***************************************************/

def main () {

	// get pipeline stages
	def pipelineStages = getPipelineStages ()

	// get gitlab stage names
	def gitlabStages = [:]
	def stageCountMaxDigitLen = pipelineStages.size().toString().length() // 0 pad count
	pipelineStages.eachWithIndex ({ key, value, idx -> gitlabStages.put(
		"GITLABSTAGE__${key}",
		"${(idx).toString().padLeft(stageCountMaxDigitLen,'0')}. ${key}"
	) })

	withEnv ( mapToEnv(gitlabStages) ) {

		// set remaining stages
		def stagesRemaining = pipelineStages.keySet ()

		// init pod, and iterate for defined stages
		try {
			// notify gitlab pending stages
			pipelineStages.each ({ key, value ->
				callIfExist ('updateGitlabCommitStatus',
					[name: env."GITLABSTAGE__${key}", state: 'pending'])
			})

			// set Pod Initialize stage as running
			onStageRunning ('PodInitialize') // stage 0

			// notify slack - job start
			callIfExist ('slackSend',
				[
					color: 'warning',
					message: "[${env.BUILD_TIME_STR}] Job Started" + """
"""						+ "- ${env.JOB_NAME} "
						+ "(${(env.BRANCH_NAME != null) ? (env.BRANCH_NAME + '/') : ('')}"
						+ "${env.BUILD_NUMBER})" + """
"""						+ "(Link: ${env.RUN_DISPLAY_URL})"
				]
			)


			// iterate all stages
			podTemplate (getPodTemplateInfo()) {
				node ('jenkins-slave-pod') {

					// set Pod Initialize stage as success
					onStageSuccess ('PodInitialize') // stage 0
					pipelineStages.remove ('PodInitialize')
					stagesRemaining.remove ('PodInitialize')


					// iterate stages
					pipelineStages.keySet().each ({ stageName ->
						runStage (stageName, pipelineStages[stageName])
						pipelineStages.remove (stageName)
						echo (stageName + ': ' + pipelineStages.size().toString())
					})
/*
					// iterate stages
					pipelineStages.each ({ stageElem ->
						runStage (stageElem)
						stagesRemaining.remove (stageElem.key)
					})*/
				}
			}


			// notify slack - job succeeded
			callIfExist ('slackSend',
				[
					color: 'good',
					message: "[${env.BUILD_TIME_STR}] Job Succeeded" + """
"""						+ "- ${env.JOB_NAME} "
						+ "(${(env.BRANCH_NAME != null) ? (env.BRANCH_NAME + '/') : ('')}"
						+ "${env.BUILD_NUMBER})" + """
"""						+ "(Link: ${env.RUN_DISPLAY_URL})"
				]
			)

		} catch (error) {

			// set Pod Initialize stage as failed, if not finished
			if ( stagesRemaining.contains('PodInitialize') ) {
				onStageFailure ('PodInitialize')
				stagesRemaining.remove ('PodInitialize')
			}

			// notify all remaining gitlab stages as canceled
			stagesRemaining.each ({ stageName ->
				callIfExist ('updateGitlabCommitStatus',
					[name: env."GITLABSTAGE__${stageName}", state: 'canceled'])
			})

			throw (error)
		}


	} // withEnv
}





// exec main
withEnv ( mapToEnv(localEnv) ) {
	main ()
}
