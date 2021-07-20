/***** Demo Spring + Gradle cycle JenkinsFile *****/
// Required plugins (Need for the script): Kubernetes, Docker
// Recommended plugins (Used in the script, but not required): GitLab, Slack





/***************************************************
 *                                                 *
 *             [ Config Environments ]             *
 *                                                 *
 ***************************************************/


/**
 *   Get base env vars
 *
 *       @return    Immutable Map of env vars
 */
Map stageEnv () { immutableMap ( [

	// gradle config
	GRADLE_HOME_PATH: '/home/jenkins/.gradle', // path in containers
	GRADLE_LOCAL_CACHE_PATH: '/tmp/jenkins/.gradle', // path of host machine

	// docker private registry config
	PRIVATE_REG_URL: 'https://k8s-docker-registry',
	PRIVATE_REG_PORT: '30000',
	DEPLOY_IMG_NAME: env.JOB_NAME, // Job name should be docker-img-name-compatible
	DEPLOY_IMG_TAG: 'build-' + env.BUILD_NUMBER,
	PRIVATE_REG_CRED_ID: 'inner-private-registry-cred', // Jenkins credential

] ) }


/**
 *   Get podTemplate args
 *
 *       @return    Immutable Map of podtemplate arguments
 */
Map podTemplateArgs () { immutableMap ( [

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


] ) }





/***************************************************
 *                                                 *
 *                  [ Pipeline ]                   *
 *                                                 *
 ***************************************************/


/**
 *   Get pipeline data
 *
 *       @return    Map of [(stageName): (stageClosure)]
 */
Map pipelineData () { immutableMap ( [

	PodInitialize: { assert(false) }, // dummy entry for init stage: should not be removed



/***** Checkout Stage *****/

	Checkout: {
		checkout (scm)
	},



/***** Build Stage *****/

	Build: {
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

					stage ('SubDummy 1') {
						sh (
							label: 'Dummy Submodule Build - 1',
							script: """
								./gradlew -g ${env.GRADLE_HOME_PATH} -v
							"""
						)
					}
					stage ('SubDummy 2') {
						sh (
							label: 'Dummy Submodule Build - 2',
							script: """
								./gradlew -g ${env.GRADLE_HOME_PATH} -v
							"""
						)
					}

				},

				'[2] Dummy for submodule': {

					stage ('SubDummy 1') {
						sh (
							label: 'Dummy Submodule Build - 1',
							script: """
								./gradlew -g ${env.GRADLE_HOME_PATH} -v
							"""
						)
					}
					stage ('SubDummy 2') {
						sh (
							label: 'Dummy Submodule Build - 2',
							script: """
								./gradlew -g ${env.GRADLE_HOME_PATH} -v
							"""
						)
					}
					stage ('SubDummy 3') {
						sh (
							label: 'Dummy Submodule Build - 3',
							script: """
								./gradlew -g ${env.GRADLE_HOME_PATH} -v
							"""
						)
					}

				},
			])

		}

		// archive built results
		archiveArtifacts (
			artifacts: 'build/libs/**/*.jar, build/libs/**/*.war',
			fingerprint: true
		)

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

		} catch (e) {
			throw (e)
		} finally {

			// list of xml file list
			def junitXmlList =
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
			def junitParallelSteps = [:]
			junitXmlList.eachWithIndex { String path, int idx ->

				// get file basename
				def posFrom = path.lastIndexOf ('/') + 1 // no occurence: 0
				def posTo = path.lastIndexOf ('.')
				def basename = path.substring (posFrom, posTo)

				junitParallelSteps << [ "[${idx}] ${basename}": {
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

					} ]
			}

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




] ) } // end of pipelineData ()





/***************************************************
 *                                                 *
 *             [ Pipeline Processing ]             *
 *                                                 *
 ***************************************************/




/**
 *   Get stagename env vars - STAGE*
 *
 *       @return    Immutable Map of env vars
 */
Map pipelineEnv () { withVar((newPipeline() ?: null), {it}) { List<Map> curPipeline -> immutableMap (

	// STAGENAME__*
	curPipeline.collectEntries { Map stageElem -> [
		"STAGENAME__${stageElem.stageName}":
			"${curPipeline.indexOf(stageElem)}. ${stageElem.stageName}",
	] }

	+

	// STAGENEXT__*
	[ "STAGENEXT__": curPipeline?.get(0).stageName ] // next stage of head: first stage
	+
	curPipeline?.tail()?.collectEntries { Map stageElem -> [
		"STAGENEXT__${curPipeline[curPipeline.indexOf(stageElem)-1].stageName}":
			"${stageElem.stageName}",
	] }

) } ?: [:] } // return empty map if null


/**
 *   Create new pipeline
 *
 *       @return    Pipeline
 */
List newPipeline () { pipelineData().collect { key, value ->
	immutableMap ([stageName: key, stageClosure: value])
} }


/**
 *   Run pipeline
 *
 *       @return    (null)
 */
void runPipeline (List<Map> curPipeline) {

	// pipeline first elem as curStage, only when not null/empty
	withVar (
		(curPipeline ?: null)?.get(0),
	        { it?.stageName && it?.stageClosure instanceof Closure }
	) { Map curStage ->

		try {
			// run stage
			onStageRunning (curStage.stageName)
			stage (curStage.stageName) {
				curStage.stageClosure.call()
			}
			onStagePassed (curStage.stageName)

		} catch (e) {

			// process error
			if (e instanceof InterruptedException) {
				onStageAborted (curStage.stageName)
			} else {
				onStageFailed (curStage.stageName)
			}

			throw (e)
		}

		// run next stage, creating new pipeline starting from it
		runPipeline ( curPipeline.tail() )
	}
}





/**
 *   Describe state of stages
 */
enum StageState {

	None, Pending, Running, Passed, Failed, Canceled, Aborted

	public StageState () {}

	public getNextState () {
		switch (this) {
			case StageState.Pending:
				return (StageState.Pending)
			case StageState.Running:
				return (StageState.Pending)
			case StageState.Passed:
				return (StageState.Pending)
			case StageState.Failed:
				return (StageState.Canceled)
			case StageState.Canceled:
				return (StageState.Canceled)
			case StageState.Aborted:
				return (StageState.Canceled)
			default:
				return (StageState.None)
		}
	}
}


/**
 *   Run when a stage starts to run
 */
void onStageRunning (String stageName) {

	if (! stageName?.length()) { return (null) } // exit if null/empty


	// notify gitlab //

	// notify current stage as running
	callIfExist ('updateGitlabCommitStatus',
		[name: env."STAGENAME__${stageName}", state: 'running'])

	// notify stages after curent stage as pending
	runWithStagenames (stageName) { String stageNameArg ->
		callIfExist ('updateGitlabCommitStatus',
			[name: env."STAGENAME__${stageNameArg}", state: 'pending'])
	}


	// notify slack // (if slack is botUser mode)

	if (env.SLACK_MSG_TS != null) {
		slackSendWrapper (
			slackEmoji(StageState.Running) + ' Stage Running...' + env.NLCHAR
				+ slackStageProgressMsg (stageName)
		)
	}
}


/**
 *   Run when a stage passed
 */
void onStagePassed (String stageName) {

	if (! stageName?.length()) { return (null) } // exit if null/empty


	// notify gitlab //

	callIfExist ('updateGitlabCommitStatus',
		[name: env."STAGENAME__${stageName}", state: 'success'])

}


/**
 *   Run when a stage failed
 */
void onStageFailed (String stageName) {

	if (! stageName?.length()) { return (null) } // exit if null/empty


	// notify gitlab //

	// notify current stage as failed
	callIfExist ('updateGitlabCommitStatus',
		[name: env."STAGENAME__${stageName}", state: 'failed'])

	// notify stages after curent stage as canceled
	runWithStagenames (stageName) { String stageNameArg ->
		callIfExist ('updateGitlabCommitStatus',
			[name: env."STAGENAME__${stageNameArg}", state: 'canceled'])
	}


	// notify slack //

	slackSendWrapper (
		slackEmoji(StageState.Failed) + ' Job *FAILED*!' + env.NLCHAR
			+ slackStageProgressMsg (stageName, StageState.Failed)
		, 'danger'
	)
}


/**
 *   Run when a stage aborted by user
 */
void onStageAborted (String stageName) {

	if (! stageName?.length()) { return (null) } // exit if null/empty


	// notify gitlab //

	// notify current stage as failed
	callIfExist ('updateGitlabCommitStatus',
		[name: env."STAGENAME__${stageName}", state: 'canceled'])

	// notify stages after curent stage as canceled
	runWithStagenames (stageName) { String stageNameArg ->
		callIfExist ('updateGitlabCommitStatus',
			[name: env."STAGENAME__${stageNameArg}", state: 'canceled'])
	}


	// notify slack //

	slackSendWrapper (
		slackEmoji(StageState.Aborted) + ' Job Aborted!' + env.NLCHAR
			+ slackStageProgressMsg (stageName, StageState.Aborted)
		, 'warning'
	)
}





/**
 *   Run closure, using each stage name as arg,
 *   for all stages after 'stageNameStart' sequentially (before 'stageNameStop').
 *   Argument stages are not included in runlist: Ex) 'stageNameStart'(X) > midStage1(O) > 'stageNameStop'(X)
 *
 *       @param     stageNameStart    Stage to start from. If '' (empty str), then start from first stage
 *       @param     stageNameStop     Stage to stop at. If null, then run all stages after 'stageNameStart'
 *       @return                      Map of [(stageName): (return value of stageName)]
 */
LinkedHashMap runWithStagenames (String stageNameStart, String stageNameStop = null, Closure closure) {

	if (
		stageNameStart != null
		&& (env."STAGENEXT__${stageNameStart}")?.length() // not end of pipeline
		&& env."STAGENEXT__${stageNameStart}" != stageNameStop // next stage: stopstage
	) {
		return (
			[
				(env."STAGENEXT__${stageNameStart}"):
					closure (env."STAGENEXT__${stageNameStart}") // ret of closure
			]
			+ runWithStagenames (
				env."STAGENEXT__${stageNameStart}",
				stageNameStop,
				closure
			) // recursive
		)
	} else {
		return ([:])
	}

}






/***************************************************
 *                                                 *
 *               [ Plugin Methods ]                *
 *                                                 *
 ***************************************************/


/***** Common *****/


/**
 *   Call plugin method if exists
 *
 *       @return    Return value of func
 */
def callIfExist (String func, Map args = null, Closure bodyClosure = null) {

	if (! func?.length()) { return (null) } // return null if func name is null/empty

	try {
		if (bodyClosure == null) {
			return ("$func" (args))
		} else {
			return ("$func" (args) { bodyClosure() })
		}

	} catch (NoSuchMethodError e) { // catch & ignore 'no method found' exception only
		echo ("callIfExist: Method '${func}' not found, skip call")
		return (null)
	}
}


/**
 *   Get base env vars
 *
 *       @return    Immutable Map of env vars
 */
Map baseEnv () { immutableMap ( [

	// newline char
	NLCHAR: '''
''',

] ) }





/***** Git ****/


/**
 *   Get git specific env
 *
 *       @return    Immutable Map of env vars
 */
Map gitEnv () { immutableMap ( [

	'GIT_BRANCH__COUNT': scm?.branches?.size(),
	'GIT_BRANCH': String.join(' ', scm?.branches?.collect { elem -> elem.name })

] ) }





/***** Slack *****/


/**
 *   Get slack specific env
 *
 *       @return    Immutable Map of env vars
 */
Map slackEnv (slackResponse) { immutableMap ( [

	SLACK_BUILD_TIME_STR: (new Date()).toString(),
	SLACK_MSG_CH: slackResponse?.channelId,
	SLACK_MSG_TS: slackResponse?.ts,

] ) }


/**
 *   Get slack emoji for a stage state
 *
 *       @return    String of slack emoji for the state
 */
String slackEmoji (StageState stageState = StageState.None) {

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


/**
 *   Get slack msg header
 *
 *       @return    Slack msg header
 */
String slackMsgHeader () { (

	// Leading Timestamp (with slack date format grammar)
	//   '[TimeStamp]'
	(
		env.SLACK_MSG_TS
		? "[<!date"
			+ "^${ env.SLACK_MSG_TS.substring( 0, (env.SLACK_MSG_TS + '.').indexOf('.') ) }" // ts
			+ "^{date_short_pretty} {time_secs}" // ts format
			+ "|${env.SLACK_BUILD_TIME_STR}" // alt text
			+ ">] "
		: ""
	)

	// Link to jenkins job info (with slack link format grammar)
	//   'jobname (build#: branch)'
	+(
		"<"
		+ "${env.RUN_DISPLAY_URL}" // pipeline page link
		+ "|${env.JOB_NAME}" // job name

		// build # + branch
		+(
			" (${env.BUILD_NUMBER}" // build #
			+(
				env.GIT_BRANCH
				? ": ${env.GIT_BRANCH}" // git branch
				: ""
			)
			+ ")"
		)
		+ ">"
	)

) }


/**
 *   Get slack stage progress msg
 *
 *       @return    Slack progress msg
 */
String slackStageProgressMsg (String stageName, StageState state = StageState.Running) { String.join (

	env.NLCHAR, (

		// progress msg header
		['', '===== Stage Progress =====']

		// stage names and their current state
		+(

			// prev stages (first ~ stageName)
			runWithStagenames ('', stageName) { String stageNameArg ->
				"${slackEmoji(StageState.Passed)} " + env."STAGENAME__${stageNameArg}"
			}

			// current stage
			+ [(stageName): "${slackEmoji(state)} *" + env."STAGENAME__${stageName}" + "*" ]

			// next stages (stageName ~ last)
			+ runWithStagenames (stageName) { String stageNameArg ->
				"${slackEmoji(state.getNextState())} " + env."STAGENAME__${stageNameArg}"
			}

		).values()
	)

) }


/**
 *   Slack send
 *
 *       @return    SlackSend result values
 */
def slackSendWrapper (String msg, String msgColor = null) { callIfExist (

	'slackSend', [

		channel: env.SLACK_MSG_CH,
		timestamp: env.SLACK_MSG_TS,
		message: slackMsgHeader() + env.NLCHAR + msg,
		color: msgColor

	]
) }





/***************************************************
 *                                                 *
 *            [ Script-wide Wrappers ]             *
 *                                                 *
 ***************************************************/

/**
 *   Run closure using var, if pred is true.
 *
 *       @param     var        Variable to define
 *       @param     pred       Predicate for run (boolean/Closure). If true, then run closure
 *       @param     closure    Closure to run
 *       @return               Return of closure if pred=true, null if pred=false
 */
def withVar (var, pred = true, Closure closure) {

	if (
		(pred instanceof Closure)
		? pred (var)
		: pred
	) {
		return (closure (var))
	} else {
		return (null)
	}

}


/**
 *   Convert map to env list, and run withEnv
 *
 *       @return    Return value of closure
 */
def withEnvMap (Map<String,String> envMap, Closure closure) { withEnv (
	envMap?.collect { key, value -> "${key}=${value}" },
	closure
) }


/**
 *   Convert map to unmodifiable map
 *
 *       @return    Unmodifiable map
 */
def immutableMap (Map input) { Collections.unmodifiableMap (
	input ?: [:]
) }






/***************************************************
 *                                                 *
 *                [ Main Routine ]                 *
 *                                                 *
 ***************************************************/

/**
 *   Script entry point
 */
void main () {

	// set base env vars before all other works
	withEnvMap ( baseEnv() ) {

		// set additional env vars
		withEnvMap (
			pipelineEnv ()
			+ gitEnv ()
			+ slackEnv ( slackSendWrapper (slackEmoji() + " Job Triggered") ) // with notify slack
		) {


			def initStagePassed = false
			try {
				// set Initialize stage as running
				onStageRunning ('PodInitialize')

				// ready pod & run stages in node
				podTemplate (podTemplateArgs()) {
					node ('jenkins-slave-pod') {

						// set Initialize stage as passed
						onStagePassed ('PodInitialize')
						initStagePassed = true

						// run all stages after init stage
						withEnvMap (stageEnv()) {
							runPipeline ( (newPipeline() ?: null)?.tail() )
						}
					}
				}

				// notify slack - job passed
				slackSendWrapper (slackEmoji(StageState.Passed) + " Job Passed", 'good')

			} catch (e) {

				// set init stage as failed / aborted
				if (! initStagePassed) {
					if (e instanceof InterruptedException) {
						onStageAborted ('PodInitialize')
					} else {
						onStageFailed ('PodInitialize')
					}
				}

				throw (e)
			}



		} // inner withEnvMap
	} // withEnvMap

}





// exec main
main ()
