def jenkinsfile


def overrides = [
    scriptVersion  : 'feature/AOS-2708',
    pipelineScript: 'https://git.aurora.skead.no/scm/ao/aurora-pipeline-scripts.git',
    openShiftBaseImage: 'yeaster',
    openShiftBaseImageVersion: '1',
    piTests: false,
    checkstyle: false,
    docs: false,
    credentialsId: "github",
    suggestVersionAndTagReleases: [
      [branch: 'master', versionHint: '1']
    ]
]

fileLoader.withGit(overrides.pipelineScript,, overrides.scriptVersion) {
   jenkinsfile = fileLoader.load('templates/leveransepakke')
}
jenkinsfile.gradle(overrides.scriptVersion, overrides)
