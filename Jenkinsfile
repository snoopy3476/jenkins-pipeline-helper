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


	// pod init stage name
	POD_INIT_STAGE_NAME: 'Pod Initialize',

	// newline char
	NLCHAR: '''
''',

]) }


// convert map to withEnv arguments
def withEnvMap (envMap, innerCode) {
	withEnv ( envMap.collect({ key, value -> "${key}=${value}" }) ) {
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



/***** Checkout Stage *****/

	'Checkout': {
		checkout (scm)
	},



/***** Build Stage *****/

	'Build': {
		container ('build-container') {

			// parallel build
			parallel ([
				'[0] Actial gradle build': {

					sh (
						label: 'Gradle Build',
						script: """
							./gradlew -g ${env.GRADLE_HOME_PATH} --parallel \\
								clean build --stacktrace -x test
						"""
					)

				},

				'[1] Dummy for submodule': {

					sh (
						label: 'Dummy Submodule Build',
						script: """
							./gradlew -g ${env.GRADLE_HOME_PATH} -v
						"""
					)

				},

				'[2] Dummy for submodule': {

					sh (
						label: 'Dummy Submodule Build',
						script: """
							./gradlew -g ${env.GRADLE_HOME_PATH} -v
						"""
					)

				},
			])

		}

		// archive built results
		archiveArtifacts (
			label: 'Archiving Artifacts',
			artifacts: 'build/libs/**/*.jar, build/libs/**/*.war',
			fingerprint: true
		)

	},



/***** Test Stage *****/

	'Test': {
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

		} catch (e) {
			throw (e)
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
							error ("Test failed: '${path}'")
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

	'Push': {
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

// describe state of stages
enum StageState {

	Pending (false),
	Running (false),
	Passed (true),
	Failed (true),
	Canceled (true),
	Aborted (true)

	final finished

	public StageState (finished) {
		this.finished = finished
	}
}





// run stage with some pre/post jobs for the stage
def runStage (stageElem, pipelineStages) {

	// check if finished stage before run
	if (stageElem.value.state.finished) {
		return (false)
	}

	onStageRunning (stageElem, pipelineStages)

	stage (stageElem.key) {
		(stageElem.value.code) ()
	}

	onStagePassed (stageElem, pipelineStages)
	return (true)
}


// run when a stage pending
def onStagePending (stageElem, pipelineStages) {

	// set stage state
	stageElem.value.state = StageState.Pending

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageElem.key}", state: 'pending'])

}


// run when a stage starts to run
def onStageRunning (stageElem, pipelineStages) {

	// set stage state
	stageElem.value.state = StageState.Running

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageElem.key}", state: 'running'])

	// notify slack, if slack is botUser mode (when editing already-sent msg is possible)
	if (env.SLACK_MSG_TS != null) {
		slackSendWrapper (slackEmoji(stageElem.value.state) + ' Stage Running...' + env.NLCHAR
			+ env.NLCHAR
			+ getSlackStageProgressMsg(pipelineStages))
	}
}


// run when a stage passed
def onStagePassed (stageElem, pipelineStages) {

	// set stage state
	stageElem.value.state = StageState.Passed

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageElem.key}", state: 'success'])

}


// run when a stage failed
def onStageFailed (stageElem, pipelineStages) {

	// set stage state
	stageElem.value.state = StageState.Failed

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageElem.key}", state: 'failed'])

	// notify slack
	slackSendWrapper ( slackEmoji(stageElem.value.state) + ' Job *FAILED*!' + env.NLCHAR
		+ env.NLCHAR
		+ getSlackStageProgressMsg(pipelineStages)
		, 'danger' )
}


// run when a stage canceled
def onStageCanceled (stageElem, pipelineStages) {

	// set stage state
	stageElem.value.state = StageState.Canceled

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageElem.key}", state: 'canceled'])

}


// run when a stage aborted by user
def onStageAborted (stageElem, pipelineStages) {

	// set stage state
	stageElem.value.state = StageState.Aborted

	// notify gitlab
	callIfExist ('updateGitlabCommitStatus',
		[name: env."GITLABSTAGE__${stageElem.key}", state: 'canceled'])

	// notify slack
	slackSendWrapper ( slackEmoji(stageElem.value.state) + ' Job Aborted!' + env.NLCHAR
		+ env.NLCHAR
		+ getSlackStageProgressMsg(pipelineStages)
		, 'warning' )
}





/***************************************************
 *                                                 *
 *               [ Plugin Methods ]                *
 *                                                 *
 ***************************************************/


/***** Common *****/


// call plugin method if exists
def callIfExist (func, args, bodyCode=null) {
	try {
		if (bodyCode == null) {
			return ("$func" (args))
		} else {
			return ("$func" (args) { bodyCode() })
		}
	} catch (NoSuchMethodError e) { // catch & ignore 'no method found' exception only
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
def getSlackEnv (slackSendRes) {
	def returnEnv = [:]


	// SLACK_BUILD_TIME_TS, SLACK_BUILD_TIME_STR
	def buildDate = (new Date())
	returnEnv.putAll ([
		SLACK_BUILD_TIME_TS: (Long) (buildDate.getTime() / 1000),
		SLACK_BUILD_TIME_STR: buildDate.toString(),
	])
	

	// SLACK_MSG_CH, SLACK_MSG_TS
	withEnvMap (returnEnv) {
		returnEnv.putAll ([
			SLACK_MSG_CH: slackSendRes.channelId,
			SLACK_MSG_TS: slackSendRes.ts,
		])
	}


	return (returnEnv)
}



// get slack emoji for a stage state
def slackEmoji (stageState) {
	switch (stageState) {
		case StageState.Pending:
			return (':double_vertical_bar:')
		case StageState.Running:
			return (':arrow_down:')
		case StageState.Passed:
			return (':white_check_mark:')
		case StageState.Failed:
			return (':x:')
		case StageState.Canceled:
			return (':black_large_square:')
		case StageState.Aborted:
			return (':black_square_for_stop:')
		default:
			return (':small_orange_diamond:')
	}
}


// get slack msg header
def getSlackMsgHeader () { return (
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
) }



// get slack stage msg
def getSlackStageProgressMsg (pipelineStages) {
	def returnStr = '===== Stage Progress =====' + env.NLCHAR
	pipelineStages.each ({ stageName, stageElemVal ->
		// bold stageName if running
		stageNameSlack = (
			stageElemVal.state == StageState.Running ?
			"*${stageName}*" :
			"${stageName}"
		)
		returnStr += "${slackEmoji(stageElemVal.state)} ${stageNameSlack}" + env.NLCHAR
	})
	return (returnStr)
}



// slack send
def slackSendWrapper (msg, msgColor=null) { return (
	callIfExist ('slackSend', [
		channel: env.SLACK_MSG_CH,
		timestamp: env.SLACK_MSG_TS,
		message: getSlackMsgHeader() + msg,
		color: msgColor
	])
) }





/***** GitLab *****/


// get gitlab specific env
def getGitlabEnv (pipelineStages) {
	def returnEnv = [:]


	// GITLABSTAGE__*
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


// executed routine when no error
def normalRoutine (pipelineStages) {

	// set all stages as pending stages
	pipelineStages.each ({ stageElem -> onStagePending(stageElem, pipelineStages) })

	// set Initialize stage as running
	onStageRunning ([
		key: env.POD_INIT_STAGE_NAME,
		value: pipelineStages[env.POD_INIT_STAGE_NAME]
	], pipelineStages)

	// ready pod & run stages in node
	podTemplate (getPodTemplateInfo()) {
		node ('jenkins-slave-pod') {


			// set Initialize stage as passed
			onStagePassed ([
				key: env.POD_INIT_STAGE_NAME,
				value: pipelineStages[env.POD_INIT_STAGE_NAME]
			], pipelineStages)


			// iterate stages
			pipelineStages.each ({ stageElem ->
				runStage (stageElem, pipelineStages)
			})


		}
	}

}


// executed routine when error
def errorRoutine (e, pipelineStages) {

	// set all non-finished stages as finished //

	// pending -> canceled
	pipelineStages.each ({ stageElem ->
		if (stageElem.value.state == StageState.Pending) {
			onStageCanceled (stageElem, pipelineStages)
		}
	})

	// running -> (failed/aborted)
	pipelineStages.each ({ stageElem ->
		if (stageElem.value.state == StageState.Running) {
			if (e instanceof InterruptedException) { echo ('Interrupted')
				onStageAborted (stageElem, pipelineStages) // when user stopped
			} else { echo ('Failed')
				onStageFailed (stageElem, pipelineStages) // when error during stage
			}
		}
	})

}


// script entry point
def main () {

	withEnvMap ( getConstEnv() + getGitEnv() ) {


		// notify slack - job triggered
		def slackRes = slackSendWrapper (slackEmoji(null) + " Job Triggered")


		// get pipeline stages, with adding init stage first
		def pipelineStages = ( [(env.POD_INIT_STAGE_NAME): null] + getPipelineStages() )
			.collectEntries ({
				stageName, stageCode ->
				[ (stageName): [ code: stageCode, state: null ] ]
			})


		// use additional env from above
		withEnvMap ( getSlackEnv(slackRes) + getGitlabEnv(pipelineStages) ) {

			// run normal routine and if exception catched, run error routine
			try {
				normalRoutine (pipelineStages)
			} catch (e) {
				errorRoutine (e, pipelineStages)
				throw (e)
			}

			// notify slack - job passed
			slackSendWrapper (slackEmoji(StageState.Passed) + " Job Passed", 'good')

		} // inner withEnvMap


	} // withEnvMap

}




// exec main
main ()
