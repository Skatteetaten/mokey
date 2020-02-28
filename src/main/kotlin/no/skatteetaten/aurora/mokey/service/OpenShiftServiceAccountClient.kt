package no.skatteetaten.aurora.mokey.service

import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.kubernetes.newReplicationController
import com.fkorotkov.kubernetes.newService
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
import com.fkorotkov.openshift.newImageStreamTag
import com.fkorotkov.openshift.newRoute
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStreamTag
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.api.model.Route
import no.skatteetaten.aurora.kubernetes.ClientTypes
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.TargetClient
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.newApplicationDeployment
import org.springframework.stereotype.Service

@Service
class OpenShiftServiceAccountClient(
    @TargetClient(ClientTypes.SERVICE_ACCOUNT) val client: KubernetesCoroutinesClient
) {
    suspend fun getServices(metadata: ObjectMeta) =
        client.getMany(newService { this.metadata = metadata })

    suspend fun getRoutes(metadata: ObjectMeta): List<Route> = client.getMany(newRoute { this.metadata = metadata })

    suspend fun getRouteOrNull(route: Route) = client.getOrNull(route)

    suspend fun getAllProjects(): List<Project> = client.getMany(null)

    suspend fun getApplicationDeployments(namespace: String): List<ApplicationDeployment> {
        return client.getMany(newApplicationDeployment {
            metadata {
                this.namespace = namespace
            }
        })
    }

    suspend fun getApplicationDeployment(name: String, namespace: String): ApplicationDeployment {
        return client.get(newApplicationDeployment {
            metadata {
                this.name = name
                this.namespace = namespace
            }
        })
    }

    suspend fun getReplicationControllers(namespace: String, labels: Map<String, String>): List<ReplicationController> {
        return client.getMany(newReplicationController {
            metadata {
                this.namespace = namespace
                this.labels = labels
            }
        })
    }

    suspend fun getDeploymentConfig(namespace: String?, openShiftName: String?): DeploymentConfig? {
        return client.getOrNull(newDeploymentConfig {
            metadata {
                this.namespace = namespace
                this.name = openShiftName
            }
        })
    }

    suspend fun getImageStreamTag(namespace: String, name: String, tagName: String): ImageStreamTag? {
        return client.getOrNull(newImageStreamTag {
            metadata {
                this.namespace = namespace
                this.name = "$name:$tagName"
            }
        })
    }

    suspend fun getPods(namespace: String, labels: Map<String, String?>): List<Pod> {
        return client.getMany(newPod {
            metadata {
                this.namespace = namespace
                this.labels = labels
            }
        })
    }
}
