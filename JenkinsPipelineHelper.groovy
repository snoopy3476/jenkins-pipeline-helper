/***** JenkinsFile Pipeline Helper *****/
// Required plugins (Need for the script)
//   - Pipeline: Groovy





/***************************************************
 *                                                 *
 *                [ Main Routine ]                 *
 *                                                 *
 ***************************************************/


/**
 *   Script entry point
 *
 *       @param     pipelineData    Pipeline data (Map<String,Closure>) to execute
 *       @param     callbackData    Callback data (Map<String,List>) to execute
 *       @return                    Result of pipeline run (Passed - true, Failed - false)
 */
boolean call (pipelineData, callbackData = null) {

	// check if arg is valid

	// pipelineData
	try {
		assert (pipelineData) // check if null or 0 element if Collection
		(Map<String,Closure>) pipelineData // check if object is Map<String,Closure>
	} catch (e) {
		echo (" - jenkinsPipelineHelper: Pipeline argument is not valid type 'Map<String,Closure>'! \n"
			+ "${e.message}")
		return (false)
	}

	// callbackData
	try {
		(Map<String,List>) callbackData // check if object is Map<String,List>

		// check if all elem in list arg is closure
		callbackData.each { curStateCallbacks ->
			if (curStateCallbacks != null) {
				(String) curStateCallbacks.key
				(List) curStateCallbacks.value

				// check if closure for all elems in the list
				curStateCallbacks.value.each { (Closure) it }
			}
		}
	} catch (e) {
		echo (" - jenkinsPipelineHelper: Callback argument is not valid type 'Map<String,List<Closure>>'! \n"
			+ "${e.message}")
		return (false)
	}



	withVar (newPipeline(pipelineData), newCallback(callbackData)) { List<Map> pipeline, Map<String,List> callbacks ->

		// notify as pending
		echo (' - jenkinsPipelineHelper: Pipeline triggered')
		(0 ..< pipeline.size()).each { idx ->
			onStageCallback (callbacks, 'pending', pipeline, idx)
		}

		// run all stages
		runPipelineStage( pipeline, 0, [], callbacks )

	}

}





/***************************************************
 *                                                 *
 *             [ Pipeline Processing ]             *
 *                                                 *
 ***************************************************/


/**
 *   Create new pipeline
 *
 *       @param     pipelineData    Pipeline data (Map<String,Closure>) to execute
 *       @return                    Pipeline
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
 *       @param     pipeline          Target pipeline
 *       @param     stageIdx          Stage index to execute
 *       @param     stageStateList    Current states for each stages
 *       @param     callbackData      Callback data to execute
 *       @return                      Result of pipeline run (Passed - true, Failed - false)
 */
boolean runPipelineStage (List<Map> pipeline, int stageIdx, List<String> stageStateList, Map<String,List<Closure>> callbackData = [:]) {


	// validity check
	if (
		// check if stageIdx is in boundary
		stageIdx < ((pipeline?.size())?:0)

		// check if pipeline[stageIdx] is valid stage
		&& pipeline[stageIdx]?.stageName && pipeline[stageIdx]?.stageDisplayName
		&& pipeline[stageIdx]?.stageClosure instanceof Closure
	) {

		echo (" - runPipelineStage [${stageIdx+1}/${pipeline.size()}] ('${pipeline[stageIdx].stageDisplayName}')")

		try {

			// run stage
			onStageCallback (callbackData, 'running', pipeline, stageIdx, stageStateList)
			stage (pipeline[stageIdx].stageName) {
				pipeline[stageIdx].stageClosure ()
			}
			onStageCallback (callbackData, 'passed', pipeline, stageIdx, stageStateList)

		} catch (e) {

			// print error msg
			echo (" - runPipelineStage: Exception on stage run \n${e.message}")

			// process error
			onStageCallback (callbackData, (e instanceof InterruptedException ? 'aborted' : 'failed'), pipeline, stageIdx, stageStateList)

			// remaining as canceled
			(stageIdx+1 .. pipeline.size()).each { idx ->
				onStageCallback (callbackData, 'canceled', pipeline, idx, stageStateList)
			}

			// return false if failed
			return (false)
		}

		// run next stage, creating new pipeline starting from it
		return ( runPipelineStage ( pipeline, stageIdx+1, stageStateList + ['passed'], callbackData ) )


	} else {
		// return true if nothing to do
		return (true)
	}
}





/**
 *   Create new callback
 *
 *       @param     callbackData    Callback data to execute
 *       @return                    Callback
 */
Map<String,List<Closure>> newCallback (Map<String,List<Closure>> callbackData) { immutableMap(
	callbackData?.collectEntries { curStateCallbacks -> [
		(curStateCallbacks?.key): immutableList(
			curStateCallbacks?.value?.collect { curCallback ->
				curCallback?.clone ()
			}
		)
	] }
) }


/**
 *   Run all calbacks in callbackData[stageState]
 *
 *       @param     callbackData      Callback data to execute
 *       @param     pipeline          Target pipeline
 *       @param     stageIdx          Stage index to execute
 *       @param     stageStateList    Current states for each stages
 */
void onStageCallback (Map<String,List<Closure>> callbackData, String stageState, List<Map> pipeline, int stageIdx, List<String> stageStateList = []) {


	// exit if pipeline/stageIdx is invalid
	if (((pipeline?.size())?:0) <= stageIdx) {
		return (null)
	}

	//echo (" - onStageCallback_${stageState} [${stageIdx+1}/${pipeline.size()}] ('${pipeline[stageIdx].stageDisplayName}')")


	callbackData?.get(stageState)?.eachWithIndex { curCallback, idx ->
		try {
			curCallback?.call (pipeline, stageIdx, stageStateList)
		} catch (e) {
			echo (" - onStageCallback_${stageState}: Exception on stage callbackData[${idx}] \n(${e.message})")
		}
	}

}





/***************************************************
 *                                                 *
 *               [ General Methods ]               *
 *                                                 *
 ***************************************************/


/**
 *   Run closure using var
 *
 *       @param     var        Variable to define
 *       @param     closure    Closure to run
 *       @return               Return of closure
 */
def withVar (... args) {
	assert (args.length >= 2 || args[args.length-1] instanceof Closure)
	
	(args[args.length-1]) (args)
}


/**
 *   Convert map to unmodifiable map
 *
 *       @return    Unmodifiable map
 */
Map immutableMap (Map input) { Collections.unmodifiableMap (
	input?:[:]
) }


/**
 *   Convert list to unmodifiable list
 *
 *       @return    Unmodifiable list
 */
List immutableList (List input) { Collections.unmodifiableList (
	input?:[]
) }





// return this: can be loaded (load('JenkinsPipelineHelper.groovy')) and called on other groovy script
return (this)
