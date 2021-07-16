/***** Demo Spring + Gradle cycle JenkinsFile *****/
// Required plugins (Need for the script): Kubernetes, Docker
// Recommended plugins (Used in the script, but not required): GitLab, Slack





/***************************************************
 *                                                 *
 *             [ Config Environments ]             *
 *                                                 *
 ***************************************************/

// get constant env vars
def getConstEnv () { return ([


	// gradle config
	GRADLE_HOME_PATH: '/home/jenkins/.gradle',
	GRADLE_LOCAL_CACHE_PATH: '/tmp/jenkins/.gradle',

	// docker private registry config
	PRIVATE_REG_URL: 'https://k8s-docker-registry',
	PRIVATE_REG_PORT: '30000',
	DEPLOY_IMG_NAME: env.JOB_NAME, // Job name should be docker-img-name-compatible
	DEPLOY_IMG_TAG: 'build-' + env.BUILD_NUMBER,
	PRIVATE_REG_CRED_ID: 'inner-private-registry-cred', // Jenkins credential


	// init stage name
	INIT_STAGE_NAME: 'Initialize',

	// newline char
	NLCHAR: '''
''',

]) }


// convert map to withEnv arguments
def withEnvMap (map, innerCode) {
	withEnv ( map.collect({ key, value -> "${key}=${value}" }) ) {
		innerCode ()
	}
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


]) }





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

enum StageState {

	Pending (false),
	Running (false),
	Passed (true),
	Failed (true),
	Canceled (true)

	final finished

	public StageState (finished) {
		this.finished = finished
	}
}





// run stage with some pre/post jobs for the stage
def runStage (stageElem) {

	// check if finished stage before run
	if (stageElem.value.state.finished) {
		return (false)
	}

	onStageRunning (stageElem)

	stage (stageElem.key) {
		try {

			(stageElem.value.code) ()

		} catch (error) {
			onStageFailed (stageElem)
			throw (error)
		}
	}

	onStagePassed (stageElem)
	return (true)
}


// run when a stage pending
def onStagePending (stageElem) {

	// set stage state
	stageElem.value.state = StageState.Pending

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageElem.key}", state: 'pending'])

}


// run when a stage starts to run
def onStageRunning (stageElem) {

	// set stage state
	stageElem.value.state = StageState.Running

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageElem.key}", state: 'running'])

	// notify slack, if slack is botUser mode (when editing already-sent msg is possible)
	if (env.SLACK_MSG_TS != null) {
		slackSendWrapper (slackStateEmoji(stageElem.value.state)
			+ " Stage Running... ( ${stageElem.key} )")
	}
}


// run when a stage passed
def onStagePassed (stageElem) {

	// set stage state
	stageElem.value.state = StageState.Passed

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageElem.key}", state: 'success'])

}


// run when a stage failed
def onStageFailed (stageElem) {

	// set stage state
	stageElem.value.state = StageState.Failed

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageElem.key}", state: 'failed'])

	// notify slack
	slackSendWrapper (slackStateEmoji(stageElem.value.state)
		+ " Stage *FAILED!* ( ${stageElem.key} )", 'danger')

}


// run when a stage failed
def onStageCanceled (stageElem) {

	// set stage state
	stageElem.value.state = StageState.Canceled

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageElem.key}", state: 'canceled'])

}





/***************************************************
 *                                                 *
 *               [ Plugin Methods ]                *
 *                                                 *
 ***************************************************/


/***** Common *****/


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





/***** Git ****/


// get git specific env
def getGitEnv () {
	def returnEnv = [:]


	// GIT_BRANCH_COUNT
	returnEnv.put ('GIT_BRANCH_COUNT', scm.branches.size())


	// GIT_BRANCH__*
	def gitBranchEnvStr = ""
	(0..<returnEnv['GIT_BRANCH_COUNT']).each ({ idx ->
		gitBranchEnvStr += (idx ? ' ' : '') + scm.branches[idx].name
		returnEnv.put (
			"GIT_BRANCH__${idx}",
			scm.branches[idx].name
                )
        })
	returnEnv.put ('GIT_BRANCH', gitBranchEnvStr)


	return (returnEnv)
}






/***** Slack *****/


// get slack specific env
def getSlackEnv () {
	def returnEnv = [:]


	// SLACK_BUILD_TIME_TS, SLACK_BUILD_TIME_STR
	def buildDate = (new Date())
	returnEnv.putAll ([
		SLACK_BUILD_TIME_TS: (Long) (buildDate.getTime() / 1000),
		SLACK_BUILD_TIME_STR: buildDate.toString(),
	])
	

	// SLACK_MSG_CH, SLACK_MSG_TS
	withEnvMap (returnEnv) {
		def slackRes = slackSendWrapper (slackStateEmoji(null) + " Job Triggered")
		returnEnv.putAll ([
			SLACK_MSG_CH: slackRes.channelId,
			SLACK_MSG_TS: slackRes.ts,
		])
	}


	return (returnEnv)
}



// get slack emoji for a stage state
def slackStateEmoji (stageState) {
	switch (stageState) {
		case StageState.Pending:
			return (':double_vertical_bar:')
		case StageState.Running:
			return (':arrow_forward:')
		case StageState.Passed:
			return (':white_check_mark:')
		case StageState.Failed:
			return (':warning:')
		case StageState.Canceled:
			return (':black_square_for_stop:')
		default:
			return (':black_large_square:')
	}
}


// get slack msg header
def getSlackMsgHeader () {
	return (
		(
			env.SLACK_BUILD_TIME_TS != null ?
			"[<!date^${env.SLACK_BUILD_TIME_TS}^{date_short_pretty} {time_secs}|"
				+ "${env.SLACK_BUILD_TIME_STR}>] " :
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





/***** GitLab *****/


// get gitlab specific env
def getGitlabEnv () {
	def returnEnv = [:]


	// GITLABSTAGE__*
	def pipelineStages = getPipelineStages ()
	def stageCountMaxDigitLen = pipelineStages.size().toString().length() // 0 pad count
	pipelineStages.eachWithIndex ({ key, value, idx ->
		returnEnv.put (
			"GITLABSTAGE__${key}",
			"${(idx).toString().padLeft(stageCountMaxDigitLen,'0')}. ${key}"
		)
	})
	

	return (returnEnv)
}





/***************************************************
 *                                                 *
 *                [ Main Routine ]                 *
 *                                                 *
 ***************************************************/

def main () {

	withEnvMap ( getConstEnv() + getGitEnv() ) {
		withEnvMap ( getSlackEnv() + getGitlabEnv() ) {


			// get pipeline stages
			def pipelineStages = getPipelineStages().collectEntries ({
				stageName, stageCode ->
				[ (stageName): [ code: stageCode, state: null ] ]
			})

			// get Initialize stage elem
			def initStageElem = [
				key: env.INIT_STAGE_NAME, value: pipelineStages[env.INIT_STAGE_NAME]
			]



			// init pod, and iterate for defined stages
			try {

				// set all stages as pending stages
				pipelineStages.each ({ stageElem -> onStagePending(stageElem) })

				// set Initialize stage as running
				onStageRunning (initStageElem)

				// ready pod
				podTemplate (getPodTemplateInfo()) {
					node ('jenkins-slave-pod') {

						// checkout src
						checkout (scm)

						// set Initialize stage as passed
						onStagePassed (initStageElem)


						// iterate stages
						pipelineStages.each ({ stageElem ->
							runStage (stageElem)
						})


						// notify slack - job passed
						slackSendWrapper (
							slackStateEmoji(StageState.Passed)
							+ " Job Passed", 'good'
						)

					}
				}


				
			} catch (error) { // if any error occurred during stage run

				//withEnvMap (gitEnv) {

					// set all non-finished stages as finished
					pipelineStages.each ({ stageElem ->
						switch (stageElem.value.state) {

							// pending -> canceled
							case StageState.Pending:
								onStageCanceled (stageElem)
								break

							// running -> failed
							case StageState.Running:
								onStageFailed (stageElem)
								break

							default:
								break
						}
					})

					throw (error)

				//}
			}

		} // withEnvMap
	} // withEnvMap

}




// exec main
main ()
