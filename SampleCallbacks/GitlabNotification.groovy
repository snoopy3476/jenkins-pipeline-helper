/***** GitLab notification callback *****/





/**
 *   Get callbacks for GitLab
 *
 *       @return   Callbacks Map
 */
Map<String,Closure> call () { [


	pending: { List<Map> pipeline, int stageIdx, List<String> stageStateList ->

		updateGitlabCommitStatusWrapper (pipeline[stageIdx].stageDisplayName, 'pending')

	},



	running: { List<Map> pipeline, int stageIdx, List<String> stageStateList ->

		updateGitlabCommitStatusWrapper (pipeline[stageIdx].stageDisplayName, 'running')

	},



	passed: { List<Map> pipeline, int stageIdx, List<String> stageStateList ->

		updateGitlabCommitStatusWrapper (pipeline[stageIdx].stageDisplayName, 'success')

	},



	failed: { List<Map> pipeline, int stageIdx, List<String> stageStateList ->

		updateGitlabCommitStatusWrapper (pipeline[stageIdx].stageDisplayName, 'failed')

	},



	aborted: { List<Map> pipeline, int stageIdx, List<String> stageStateList ->

		updateGitlabCommitStatusWrapper (pipeline[stageIdx].stageDisplayName, 'canceled')

	},



	canceled: { List<Map> pipeline, int stageIdx, List<String> stageStateList ->

		updateGitlabCommitStatusWrapper (pipeline[stageIdx].stageDisplayName, 'canceled')

	},



].asImmutable() }





/**
 *   Wrapper for updateGitlabCommitStatus
 *
 *       @param    name    Stage name to notify
 *       @param    state   Stage state to notify
 *       @return           Return value of updateGitlabCommitStatus if the method exists, false otherwise
 */
def updateGitlabCommitStatusWrapper (String name, String state) {

	try {
		updateGitlabCommitStatus (name: name, state: state)

	} catch (NoSuchMethodError e) { // catch & ignore 'no method found' exception only
		echo ("GitLab plugin method not found")
		return (false)
	}

}





return (this)
