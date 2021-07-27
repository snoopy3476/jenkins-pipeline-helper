/***** JenkinsFile Pipeline Helper *****/
// Required plugins (Need for the script)
//   - Pipeline: Groovy
// Recommended plugins (Used in the script, but not required)
//   - GitLab
//   - Slack





/***************************************************
 *                                                 *
 *             [ Pipeline Processing ]             *
 *                                                 *
 ***************************************************/


/**
 *   Create new pipeline
 *
 *       @return    Pipeline
 */
List<Map> newPipeline (Map<String,Closure> pipelineData) { (
	[ pipelineData?.collect{it}, (1 .. (pipelineData?.size())?:1) ].transpose().collect { stageElem ->
		immutableMap ([
			stageName: stageElem?.get(0)?.key,
			stageDisplayName: "${stageElem?.get(1)}. ${stageElem?.get(0)?.key}",
			stageClosure: stageElem?.get(0)?.value?.clone()
		])
	}
) }


/**
 *   Run pipeline
 *
 *       @return    Result of pipeline run (Passed - true, Failed - false)
 */
boolean runPipelineStage (List<Map> pipeline, int stageIdx = 0, List<StageState> stageStateList = []) {


	// validity check
	if (
		// check if stageIdx is in boundary
		stageIdx < ((pipeline?.size())?:0)

		// check if pipeline[stageIdx] is valid stage
		&& withVar (pipeline[stageIdx]) { Map it ->
			it?.stageName && it?.stageDisplayName && it?.stageClosure instanceof Closure
		}
	) {

		echo (" - runPipelineStage [${stageIdx+1}/${pipeline.size()}] ('${pipeline[stageIdx].stageDisplayName}')")

		try {

			// run stage
			onStageRunning (pipeline, stageIdx, stageStateList)
			stage (pipeline[stageIdx].stageName) {
				pipeline[stageIdx].stageClosure ()
			}
			onStagePassed (pipeline, stageIdx, stageStateList)

		} catch (e) {

			// process error
			if (e instanceof InterruptedException) {
				onStageAborted (pipeline, stageIdx, stageStateList)
			} else {
				onStageFailed (pipeline, stageIdx, stageStateList)
			}

			// return false if failed
			return (false)
		}

		// run next stage, creating new pipeline starting from it
		return ( runPipelineStage ( pipeline, stageIdx+1, stageStateList + [StageState.Passed] ) )


	} else {
		// return true if nothing to do
		return (true)
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
void onStageRunning (List<Map> pipeline, int stageIdx, List<StageState> stageStateList = []) {


	// exit if pipeline/stageIdx is invalid
	if (((pipeline?.size())?:0) <= stageIdx) {
		return (null)
	}

	echo (" - onStageRunning [${stageIdx+1}/${pipeline.size()}] ('${pipeline[stageIdx].stageDisplayName}')")

	try {

		// notify gitlab //

		// notify current stage as running
		callIfExist ('updateGitlabCommitStatus',
			[name: pipeline[stageIdx].stageDisplayName, state: 'running'])

		// notify stages after curent stage as pending
		runWithEachStages ( pipeline.subList(stageIdx+1, (pipeline.size()?:0)) ) { Map stageElem ->
			callIfExist ('updateGitlabCommitStatus',
				[name: stageElem.stageDisplayName, state: 'pending'])
		}


		// notify slack // (if slack is botUser mode)

		if (env.SLACK_MSG_TS != null) {
			slackSendWrapper (
				slackEmoji(StageState.Running) + ' Stage Running...' + env.NLCHAR
					+ slackStageProgressMsg (pipeline, stageStateList + [StageState.Running])
			)
		}


	} catch (e) {
		echo (" - onStageRunning: Exception on stage callback: ${e.message}")
	}
}


/**
 *   Run when a stage passed
 */
void onStagePassed (List<Map> pipeline, int stageIdx, List<StageState> stageStateList = []) {


	// exit if pipeline/stageIdx is invalid
	if (((pipeline?.size())?:0) <= stageIdx) {
		return (null)
	}

	echo (" - onStagePassed [${stageIdx+1}/${pipeline.size()}] ('${pipeline[stageIdx].stageDisplayName}')")

	try {

		// notify gitlab //

		callIfExist ('updateGitlabCommitStatus',
			[name: pipeline[stageIdx].stageDisplayName, state: 'success'])


	} catch (e) {
		echo (" - onStagePassed: Exception on stage callback: ${e.message}")
	}
}


/**
 *   Run when a stage failed
 */
void onStageFailed (List<Map> pipeline, int stageIdx, List<StageState> stageStateList = []) {


	// exit if pipeline/stageIdx is invalid
	if (((pipeline?.size())?:0) <= stageIdx) {
		return (null)
	}

	echo (" - onStageFailed [${stageIdx+1}/${pipeline.size()}] ('${pipeline[stageIdx].stageDisplayName}')")

	try {

		// notify gitlab //

		// notify current stage as failed
		callIfExist ('updateGitlabCommitStatus',
			[name: pipeline[stageIdx].stageDisplayName, state: 'failed'])

		// notify stages after curent stage as canceled
		runWithEachStages ( pipeline.subList(stageIdx+1, pipeline.size()) ) { Map stageElem ->
			callIfExist ('updateGitlabCommitStatus',
				[name: stageElem.stageDisplayName, state: 'canceled'])
		}


		// notify slack //

		slackSendWrapper (
			slackEmoji(StageState.Failed) + ' Job *FAILED*!' + env.NLCHAR
				+ slackStageProgressMsg (pipeline, stageStateList + [StageState.Failed], StageState.Canceled)
			, 'danger'
		)


	} catch (e) {
		echo (" - onStageFailed: Exception on stage callback: ${e.message}")
	}
}


/**
 *   Run when a stage aborted by user
 */
void onStageAborted (List<Map> pipeline, int stageIdx, List<StageState> stageStateList = []) {


	// exit if pipeline/stageIdx is invalid
	if (((pipeline?.size())?:0) <= stageIdx) {
		return (null)
	}

	echo (" - onStageAborted [${stageIdx+1}/${pipeline.size()}] ('${pipeline[stageIdx].stageDisplayName}')")

	try {

		// notify gitlab //

		// notify current stage as failed
		callIfExist ('updateGitlabCommitStatus',
			[name: pipeline[stageIdx].stageDisplayName, state: 'canceled'])

		// notify stages after curent stage as canceled
		runWithEachStages ( pipeline.subList(stageIdx+1, pipeline.size()) ) { Map stageElem ->
			callIfExist ('updateGitlabCommitStatus',
				[name: stageElem.stageDisplayName, state: 'canceled'])
		}


		// notify slack //

		slackSendWrapper (
			slackEmoji(StageState.Aborted) + ' Job Aborted!' + env.NLCHAR
				+ slackStageProgressMsg (pipeline, stageStateList + [StageState.Aborted], StageState.Canceled)
			, 'warning'
		)


	} catch (e) {
		echo (" - onStageAborted: Exception on stage callback: ${e.message}")
	}
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
LinkedHashMap runWithEachStages (List<Map> stageList, Closure closure) {

	if ( stageList ) {
		return (
			[
				(stageList[0]?.stageName):
					closure (stageList[0]) // ret of closure
			]
			+ runWithEachStages (
				stageList.tail(),
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
String slackStageProgressMsg (	List<Map> pipeline, List<StageState> stageStateList,
				StageState stageStateRemaining = StageState.Pending) { String.join (

	env.NLCHAR, (

		// progress msg header
		['', '===== Stage Progress =====']

		// stage names and their current state
		+(
			[
				pipeline,
				stageStateList + (0..<(pipeline.size() - stageStateList.size())).collect {stageStateRemaining}
			].transpose().collect { List curElem ->
				"${slackEmoji(curElem[1])} " + curElem[0].stageDisplayName
			}

		)
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
	input?:[:]
) }






/***************************************************
 *                                                 *
 *                [ Main Routine ]                 *
 *                                                 *
 ***************************************************/

/**
 *   Script entry point
 *
 *       @param     pipelineData    Pipeline data (Map<String,Closure>) to execute
 *       @return                    Result of pipeline run (Passed - true, Failed - false)
 */
boolean call (pipelineData) {

	// check if arg is valid
	try {
		assert (pipelineData) // check if null or 0 element if Collection
		(Map<String,Closure>) pipelineData // check if object is Map<String,Closure>
	} catch (e) {
		echo (' - jenkinsPipelineHelper: Pipeline argument is not valid (Map<String,Closure>) !')
		return (false)
	}
	echo (' - jenkinsPipelineHelper: Pipeline triggered')


	// set base env vars before all other works
	withEnvMap ( baseEnv() ) {

		// set additional env vars
		withEnvMap (
			gitEnv ()
			+ slackEnv ( slackSendWrapper (slackEmoji() + " Pipeline Triggered") ) // with notify slack
		) {

			// run all stages after init stage
			if ( runPipelineStage(newPipeline(pipelineData)) ) {

				// notify slack - job passed
				slackSendWrapper (slackEmoji(StageState.Passed) + " Pipeline Passed", 'good')

				return (true)

			} else {
				return (false)
			}

		} // inner withEnvMap
	} // withEnvMap

}



// return this: can be loaded (load('jenkinsPipelineHelper.groovy')) and called on other groovy script
return (this)
