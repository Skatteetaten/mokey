package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfig
import no.skatteetaten.aurora.mokey.model.ImageDetails
import org.springframework.stereotype.Service

@Service
class ImageService(val openshiftService: OpenShiftService) {

    fun getImageDetails(dc: DeploymentConfig): ImageDetails? {
        val imageStreamTag = dc.imageStreamTag ?: return null

        val tag = openshiftService.imageStreamTag(dc.metadata.namespace, dc.metadata.name, imageStreamTag)
        val env = tag?.image?.dockerImageMetadata?.containerConfig?.env ?: emptyList()
        val environmentVariables = assignmentStringsToMap(env)
        val imageBuildTime = environmentVariables["IMAGE_BUILD_TIME"]?.let { DateParser.parseString(it) }
        return ImageDetails(tag?.image?.dockerImageReference, imageBuildTime, environmentVariables)
    }

    companion object {
        /**
         * This method does not handle corner cases very well. It is assumed that the caller follows the general
         * contract outlined in the parameter description.
         * @param env a list of String where each String is on the form "NAME=VALUE"
         */
        internal fun assignmentStringsToMap(env: List<String>): Map<String, String> {
            return env.map {
                val (key, value) = it.split("=")
                key to value
            }.toMap()
        }
    }
}