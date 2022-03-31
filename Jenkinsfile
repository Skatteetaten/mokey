def jenkinsfile

def overrides = [
    scriptVersion  : 'v7',
    iqOrganizationName: "Team AOS",
    iqEmbedded: true,
    iqBreakOnUnstable: true,
    pipelineScript: 'https://git.aurora.skead.no/scm/ao/aurora-pipeline-scripts.git',
    credentialsId: "github",
    checkstyle : false,
    javaVersion: 17,
    jiraFiksetIKomponentversjon: true,
    compilePropertiesIq: "-x test",
    chatRoom: "#aos-notifications",
    uploadLeveransepakke: true,
    versionStrategy: [
      [ branch: 'master', versionHint: '3' ]
    ]
]

fileLoader.withGit(overrides.pipelineScript,, overrides.scriptVersion) {
   jenkinsfile = fileLoader.load('templates/leveransepakke')
}
jenkinsfile.gradle(overrides.scriptVersion, overrides)
