import groovy.json.JsonSlurper

includeTargets << new File("${releasePluginDir}/scripts/_GrailsMaven.groovy")

String bintrayOrg
String bintrayRepo
String bintrayPackage
boolean artifactAlreadyExistsOrIsSnapshot = false

target(uploadToBintray: "uploads artifacts to bintray if conditions are met") {
    //noinspection GroovyAssignabilityCheck
    depends(checkConditions)
    if (!artifactAlreadyExistsOrIsSnapshot) {
        depends(mavenDeploy)
    }
}

target(checkConditions: "check whether or not we can upload an artifact to bintray") {
    depends(checkJavaVersion, checkAndSetBintrayArgs, checkProjectVersion)
}

target(checkAndSetBintrayArgs: "makes sure the repo url is in fact a bintray url") {
    depends(init) //from the maven plugin
    def repoName = argsMap.repository ?: grailsSettings.config.grails.project.repos.default
    def repo = repoName ? distributionInfo.remoteRepos[repoName] : null
    if (!repo) {
        grailsConsole.error "No repository has been set"
        exit(2)
    }

    //set the url that will be used by the rest of the application
    url = repo?.args?.url
    if (!url) {
        grailsConsole.error "url has not been set for repo $repoName"
        exit(3)
    }

    //group 1 is the org / user name, 2 the repo, and 3 the package
    def bintrayMavenApiUrl = /https:\/\/api.bintray.com\/maven\/([^\/]+)\/([^\/]+)\/([^\/]+)$/
    def matcher = url =~ bintrayMavenApiUrl
    if (!matcher.matches()) {
        grailsConsole.error "the url $url must be in the form of $bintrayMavenApiUrl to upload to bintray"
        exit(4)
    }

    bintrayOrg = matcher.group(1)
    bintrayRepo = matcher.group(2)
    bintrayPackage = matcher.group(3)
}

target(checkProjectVersion: "check if the package was already deployed") {
    depends(checkAndSetBintrayArgs)

    def version
    if (isPluginProject) {
        if (!pluginSettings.basePluginDescriptor.filename) {
            grailsConsole.error "PluginDescripter not found to get version"
            exit 5
        }

        File file = new File(pluginSettings.basePluginDescriptor.filename)
        String descriptorContent = file.text

        def pattern = ~/def\s*version\s*=\s*"(.*)"/
        def matcher = (descriptorContent =~ pattern)

        if (matcher.size() > 0) {
            version = matcher[0][1]
        }
        else {
            grailsConsole.error "version not found in plugin"
            exit 6
        }
    }
    else {
        version = metadata.'app.version'
        if (!version) {
            grailsConsole.error "version not found in application"
            exit 6
        }
    }

    if (version.endsWith("SNAPSHOT")) {
        grailsConsole.info "you cannot deploy SNAPSHOTs to bintray, skipping upload"
        artifactAlreadyExistsOrIsSnapshot = true
    }

    if (!artifactAlreadyExistsOrIsSnapshot) {
        def restBuilder = classLoader.loadClass("grails.plugins.rest.client.RestBuilder").newInstance()
        def json = new URL("https://api.bintray.com/packages/$bintrayOrg/$bintrayRepo/$bintrayPackage").text
        def slurper = new JsonSlurper()
        def versions = slurper.parseText(json).versions
        artifactAlreadyExistsOrIsSnapshot = versions.contains(version)

        if (artifactAlreadyExistsOrIsSnapshot) {
            grailsConsole.info "version $version has already been deployed to bintray, skipping upload to bintray"
        }
    }
}

target(checkJavaVersion: "checks the java version") {
    boolean conditionsMet = true
    String javaVersion = System.getProperty("java.version")
    def m = javaVersion =~ /^1\.(\d+)/
    m.lookingAt()
    int majorVersion = m.group(1).toInteger()
    if (majorVersion < 7) {
        grailsConsole.error "you can't upload to bintray unless you are using at least java 7"
        exit(1)
    }
}

target(publishToBintray: "publishes to bintray") {

}

setDefaultTarget(uploadToBintray)