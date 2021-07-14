/***** Demo Spring + Gradle cycle JenkinsFile *****/
// Required plugins (Need for the script): Kubernetes, Docker
// Recommended plugins (Used in the script, but not required): GitLab, Slack





/***************************************************
 *                                                 *
 *                   [ Config ]                    *
 *                                                 *
 ***************************************************/

// get constant env vars
def getConstEnv () {

	def returnEnv = [

		// gradle config
		GRADLE_HOME_PATH: '/home/jenkins/.gradle',
		GRADLE_LOCAL_CACHE_PATH: '/tmp/jenkins/.gradle',

		// docker private registry config
		PRIVATE_REG_URL: 'https://k8s-docker-registry',
		PRIVATE_REG_PORT: '30000',
		DEPLOY_IMG_NAME: env.JOB_NAME, // Job name should be docker-img-name-compatible
		DEPLOY_IMG_TAG: 'build-' + env.BUILD_NUMBER,
		PRIVATE_REG_CRED_ID: 'inner-private-registry-cred', // Jenkins credential


		// newline char
		NLCHAR: '''
''',

		// init stage name
		INIT_STAGE_NAME: 'Initialize',
	]


	return (mapToEnv(returnEnv))
}





// get dynamic env vars
def getDynEnv () {

	def returnEnv = [:]


	// BUILD_START_TIME_TS, BUILD_START_TIME_STR
	def buildDate = (new Date())
	def buildStartTimeEnv = [
		BUILD_START_TIME_TS: (Long) (buildDate.getTime() / 1000),
		BUILD_START_TIME_STR: buildDate.toString(),
	]
	returnEnv.putAll (buildStartTimeEnv)
	

	// SLACK_MSG_CH, SLACK_MSG_TS
	withEnv (mapToEnv(buildStartTimeEnv)) {
		def slackRes = slackSendWrapper ("- Job Triggered!\n")
		returnEnv.putAll ([
			SLACK_MSG_CH: slackRes.channelId,
			SLACK_MSG_TS: slackRes.ts,
		])
	}


	// GITLABSTAGE__*
	def pipelineStages = getPipelineStages ()
	def gitlabStages = [:]
	def stageCountMaxDigitLen = pipelineStages.size().toString().length() // 0 pad count
	pipelineStages.eachWithIndex ({ key, value, idx ->
		gitlabStages.put (
			"GITLABSTAGE__${key}",
			"${(idx).toString().padLeft(stageCountMaxDigitLen,'0')}. ${key}"
		)
	})
	returnEnv.putAll (gitlabStages)
	


	return (mapToEnv(returnEnv))
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

// Initialize Stage: for init stage (k8s pod init, checkout), should not be removed
	(env.INIT_STAGE_NAME): { assert (false) }, 



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


// run when a stage pending
def onStagePending (stageName) {

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageName}", state: 'pending'])

}


// run when a stage starts to run
def onStageRunning (stageName) {

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageName}", state: 'running'])

	// notify slack, if slack is botUser mode (when editing already-sent msg is possible)
	if (env.SLACK_MSG_TS != null) {
		slackSendWrapper ("- Stage Running... ( ${stageName} )\n")
	}
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
	slackSendWrapper ("- Stage *FAILED!* ( ${stageName} )\n", 'danger')

}


// run when a stage failed
def onStageCancel (stageName) {

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageName}", state: 'canceled'])

}





/***************************************************
 *                                                 *
 *               [ Plugin Methods ]                *
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


// get slack msg header
def getSlackMsgHeader () {
	return (
		(
			env.BUILD_START_TIME_TS != null ?
			"[<!date^${env.BUILD_START_TIME_TS}^{date_short_pretty} {time_secs}|${env.BUILD_START_TIME_STR}>] " :
			""
		)
		+ "<${env.RUN_DISPLAY_URL}|"
			+ "${env.JOB_NAME} (${env.BUILD_NUMBER}"
			+ (env.GIT_BRANCH != null ? ": ${env.GIT_BRANCH}" : "")
		+ ")>" + env.NLCHAR
	)
}


// slack send
def slackSendWrapper (msg, msgColor=null) {

	return (
		callIfExist ('slackSend', [
			channel: env.SLACK_MSG_CH,
			timestamp: env.SLACK_MSG_TS,
			message: getSlackMsgHeader() + msg,
			color: msgColor
		])
	)

}






/***************************************************
 *                                                 *
 *                [ Main Routine ]                 *
 *                                                 *
 ***************************************************/

def main () {

	withEnv (getConstEnv()) { withEnv(getDynEnv()) {

		// get pipeline stages
		def pipelineStages = getPipelineStages ()


		// init pod, and iterate for defined stages
		try {
			// notify gitlab pending stages
			pipelineStages.each ({ stageName, stageCode -> onStagePending(stageName) })

			// set Initialize stage as running
			onStageRunning (env.INIT_STAGE_NAME) // init stage

			// ready pod
			podTemplate (getPodTemplateInfo()) {
				node ('jenkins-slave-pod') {

					// checkout src
					def scmVars = checkout (scm)
					def gitEnv = mapToEnv(['GIT_COMMIT': scmVars.GIT_COMMIT, 'GIT_BRANCH': scmVars.GIT_BRANCH])
					withEnv (gitEnv) {

						// set Initialize stage as success
						onStageSuccess (env.INIT_STAGE_NAME) // init stage
						pipelineStages.remove (env.INIT_STAGE_NAME)


						// iterate stages
						pipelineStages.keySet().each ({ stageName ->

							runStage (stageName, pipelineStages[stageName])
							pipelineStages.remove (stageName) // remove from pending list
						})
					}
				}
			}


			
		} catch (error) { // if any error occurred during stage run

			// if Initialize stage not finished at the time error occurred, set it as failed
			if ( pipelineStages[env.INIT_STAGE_NAME] != null ) {
				onStageFailure (env.INIT_STAGE_NAME)
				pipelineStages.remove (env.INIT_STAGE_NAME)
			}

			// set all remaining stages as canceled
			pipelineStages.each ({ stageName, stageCode -> onStageCancel(stageName) })

			throw (error)
		}

		// notify slack - job succeeded
		slackSendWrapper ("- Job Succeeded!\n", 'good')

	} } // nested withEnv

}




// exec main
main ()
