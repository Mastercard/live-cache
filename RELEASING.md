## Releasing

1. Set in your local *gradle.properties*: 
	* `artifactory_user` to your jFrog user
	* `artifactory_password` to your jFrom password
	* `artifactory_contextUrl` to your jFrog URL
2. Bump the library version in artifactory.gradle
3. Make your changes 
4. Commit and push your changes
4. Run `./gradlew clean assembleRelease artifactoryPublish`
6. If needed, promote your artifact to release