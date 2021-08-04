# Jenkins Pipeline Helper
-   Simple runner for jenkins pipeline, which runs appropriate callbacks at events (on stage pending, running, passed, failed, etc.)
-   Tested with Jenkins 2.289.2
-   Author: snoopy3476@outlook.com

## Usage Example
1.  Load script.
    ```
    // You can skip this checkout
    //   if you place 'JenkinsPipelineHelper.groovy' locally

    checkout ([ $class: 'GitSCM',
      userRemoteConfigs:
        [[url: 'https://github.com/snoopy3476/jenkins-pipeline-helper']],
      branches:
        [[name: '*/master']] // release-X.X branch recommended
    ])

    
    // load helper script
    def pipelineHelper = load ('JenkinsPipelineHelper.groovy')
    ```
1.  Prepare for pipeline data (`Map <String, Closure>`). (Key: Name of stage / Value: Closure of stage to run)
    ```
    // Map <String, Closure>
    //   -> [ stageName: stageClosure, ... ]

    def pipelineData = [

      Stage1: {
        echo('stage-code-1')
      },

      Stage2: {
        echo('stage-code-2')
      },

    ]
    ```
1.  Prepare for callback data (`Map <String, List<Closure>>`).
  
    Currently following stage states are available: `pending`, `running`, `passed`, `failed`, `aborted`, `canceled`
    ```
    // Map <String, List<Closure>>
    //   -> [ state: [ stateCallback1, stateCallback2, ... ], ... ]

    def callbackData = [

      running: [
        { pipeline, stageIdx, stateList ->
          echo ("Stage running (1): '${pipeline[stageIdx].stageName}'") },
            
        { pipeline, stageIdx, stateList ->
          echo ("Stage running (2): '${pipeline[stageIdx].stageName}'") },
      ],

      passed: [
        { pipeline, stageIdx, stateList ->
          echo ("Stage passed: '${pipeline[stageIdx].stageName}'") },
      ],

      failed: [
        { pipeline, stageIdx, stateList ->
          echo ("Stage failed: '${pipeline[stageIdx].stageName}'") },
      ],

    ]
    ```
1.  Run pipeline with data above.
    ```
    // 'callbackData' can be omitted if no callback exists
    //   (Ex. pipelineHelper(pipelineData) )

    assert ( pipelineHelper (pipelineData, callbackData) )
    ```
Further example Jenkinsfile is available on [UsageExample/Jenkinsfile](UsageExample/Jenkinsfile)
