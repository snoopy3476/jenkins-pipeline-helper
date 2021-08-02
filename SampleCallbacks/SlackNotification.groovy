/***** Slack notification callback *****/





/**
 *   Get callbacks for Slack
 *
 *       @return   Callbacks Map
 */
Map<String,Closure> call () { [


	pending: { List<Map> pipeline, int stageIdx, List<String> stageStateList ->
	},



	running: { List<Map> pipeline, int stageIdx, List<String> stageStateList ->

		if (env.SLACK_MSG_TS != null) { // only if updating already-sent msg is possible
			slackSendWrapper (
				slackEmoji('running') + ' Stage Running...' + '\n'
					+ slackStageProgressMsg (pipeline, stageStateList + ['running'])
			)
		}

	},



	passed: { List<Map> pipeline, int stageIdx, List<String> stageStateList ->

		if (env.SLACK_MSG_TS != null) { // only if updating already-sent msg is possible
			slackSendWrapper (
				slackEmoji('passed') + ' Stage Passed' + '\n'
					+ slackStageProgressMsg (pipeline, stageStateList + ['passed'])
			)
		}

	},



	failed: { List<Map> pipeline, int stageIdx, List<String> stageStateList ->

		slackSendWrapper (
			slackEmoji('failed') + ' Job *FAILED*!\n'
				+ slackStageProgressMsg (pipeline, stageStateList + ['failed'], 'canceled')
			, 'danger'
		)

	},



	aborted: { List<Map> pipeline, int stageIdx, List<String> stageStateList ->

		slackSendWrapper (
			slackEmoji('aborted') + ' Job Aborted!\n'
				+ slackStageProgressMsg (pipeline, stageStateList + ['aborted'], 'canceled')
			, 'warning'
		)

	},



].asImmutable() }





/**
 *   Run closure, wrapping with slack message send
 *
 *       @param     closure   Closure to run
 *       @return              Return value of closure
 */
def runWithSlackMsgWrapper (Closure closure) {

	withEnv ( slackEnv( slackSendWrapper("${slackEmoji()} Pipeline Triggered") ) ) {

		def retVal = closure ()
		if ( retVal ) {
			slackSendWrapper ("${slackEmoji('passed')} Pipeline Passed")
		}
		return (retVal)
	}

}





/**
 *   Get slack specific env
 *
 *       @param     slackResponse   response of slackSend to update
 *       @return                    List of env vars
 */
List<String> slackEnv (slackResponse) { [

	SLACK_BUILD_TIME_STR: (new Date()).toString(),
	SLACK_MSG_CH: slackResponse?.channelId,
	SLACK_MSG_TS: slackResponse?.ts,

].collect {"${it.key}=${it.value}"} }


/**
 *   Get slack emoji for a stage state
 *
 *       @return    String of slack emoji for the state
 */
String slackEmoji (String stageState = null) {

	switch (stageState) {
		case 'pending':
			return (':double_vertical_bar:')
		case 'running':
			return (':arrow_down:')
		case 'passed':
			return (':white_check_mark:')
		case 'failed':
			return (':x:')
		case 'canceled':
			return (':black_large_square:')
		case 'aborted':
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
		+ " (${env.BUILD_NUMBER}: " + String.join(' ', scm?.branches?.collect { elem -> elem.name }) + ")" // build # + branch
		+ ">"
	)

) }


/**
 *   Get slack stage progress msg
 *
 *       @return    Slack progress msg
 */
String slackStageProgressMsg (	List<Map> pipeline, List<String> stageStateList,
				String stageStateRemaining = 'pending') { String.join (

	'\n', (

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
def slackSendWrapper (String msg, String msgColor = null) {
	try {
		return ( slackSend(
			channel: env.SLACK_MSG_CH,
			timestamp: env.SLACK_MSG_TS,
			message: slackMsgHeader() + '\n' + msg,
			color: msgColor
		) )
	} catch (NoSuchMethodError e) {
		echo ('Slack plugin method not found')
		return (null)
	}

}





return (this)
