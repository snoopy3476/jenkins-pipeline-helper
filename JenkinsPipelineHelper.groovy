/***** JenkinsFile Pipeline Helper *****/
// Required plugins (Need for the script)
//   - Pipeline: Groovy





/***************************************************
 *                                                 *
 *                [ Main Routine ]                 *
 *                                                 *
 ***************************************************/


/**
 *   Script entry point - make the script callable
 *
 *       @param     pipelineData    Pipeline data (Map<String,Closure>) to execute
 *       @param     callbackData    Callback data (Map<String,List<Closure>>) to execute
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
		callbackData.each { Map.Entry curStateCallbacks ->
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
		(0 ..< pipeline.size()).each { int idx ->
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
	[ pipelineData?.collect{it}, (1 .. (pipelineData?.size())?:1) ].transpose().collect { List stageElem ->
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
			(stageIdx+1 ..< pipeline.size()).each { int idx ->
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
	callbackData?.collectEntries { Map.Entry curStateCallbacks -> [
		(curStateCallbacks?.key): immutableList(
			curStateCallbacks?.value?.collect { Closure curCallback ->
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
 *       @param     stageStateList    Current states for all stages: stageStateList[idx] is a state for pipeline[idx]
 */
void onStageCallback (Map<String,List<Closure>> callbackData, String stageState, List<Map> pipeline, int stageIdx, List<String> stageStateList = []) {


	// exit if pipeline/stageIdx is invalid
	if (((pipeline?.size())?:0) <= stageIdx) {
		echo (" - onStageCallback_${stageState}: Pipeline is null or stage index out of bound! Exit without doing nothing.")
		return (null)
	}

	//echo (" - onStageCallback_${stageState} [${stageIdx+1}/${pipeline.size()}] ('${pipeline[stageIdx].stageDisplayName}')")


	callbackData?.get(stageState)?.eachWithIndex { Closure curCallback, int idx ->
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
 *       @param     args   Vararg - Closure arguments (all args, except for the last) + closure instance (last arg)
 *       @return           Return of closure
 */
def withVar (... args) {
	// check if at least 2 args exists (closure args + closure), and last argument is closure
	assert (args.length >= 2 || args[args.length-1] instanceof Closure)
	
	(args[args.length-1]) (args)
}


/**
 *   Convert map to unmodifiable map
 *
 *       @param     input   Source input
 *       @return            Unmodifiable map
 */
Map immutableMap (Map input) { Collections.unmodifiableMap (
	input?:[:]
) }


/**
 *   Convert list to unmodifiable list
 *
 *       @param     input   Source input
 *       @return            Unmodifiable list
 */
List immutableList (List input) { Collections.unmodifiableList (
	input?:[]
) }





// return this:
//   can be loaded with:   def helper = load ('JenkinsPipelineHelper.groovy')
//   and called with:      helper (pipelineData, callbackData) // or with helper.call(...)
//   on other groovy script
return (this)
