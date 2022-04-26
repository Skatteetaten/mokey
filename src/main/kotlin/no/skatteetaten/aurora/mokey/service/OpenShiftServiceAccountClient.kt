package no.skatteetaten.aurora.mokey.service

import com.fkorotkov.kubernetes.apps.metadata
import com.fkorotkov.kubernetes.apps.newDeployment
import com.fkorotkov.kubernetes.apps.newReplicaSet
import com.fkorotkov.kubernetes.extensions.newIngress
import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.kubernetes.newReplicationController
import com.fkorotkov.kubernetes.newService
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
import com.fkorotkov.openshift.newImageStreamTag
import com.fkorotkov.openshift.newRoute
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStreamTag
import io.fabric8.openshift.api.model.Route
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.StorageGridObjectArea
import no.skatteetaten.aurora.mokey.model.newApplicationDeployment
import no.skatteetaten.aurora.mokey.model.newStorageGridObjectArea
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Service
class OpenShiftServiceAccountClient(@Qualifier("managementCoroutinesClient") val client: KubernetesCoroutinesClient) {
    suspend fun getServices(metadata: ObjectMeta) = client.getMany(newService { this.metadata = metadata })

    suspend fun getIngresses(metadata: ObjectMeta): List<Ingress> = client.getMany(
        newIngress {
            this.metadata = metadata
        }
    )

    suspend fun getRoutes(metadata: ObjectMeta): List<Route> = client.getMany(newRoute { this.metadata = metadata })

    suspend fun getRouteOrNull(route: Route) = client.getOrNull(route)

    suspend fun getAllNamespace(): List<Namespace> = client.getMany(null)

    suspend fun getApplicationDeployments(namespace: String): List<ApplicationDeployment> = client.getMany(
        newApplicationDeployment {
            metadata {
                this.namespace = namespace
            }
        }
    )

    suspend fun getApplicationDeployment(name: String, namespace: String): ApplicationDeployment = client.get(
        newApplicationDeployment {
            metadata {
                this.name = name
                this.namespace = namespace
            }
        }
    )

    suspend fun getReplicationControllers(
        namespace: String,
        labels: Map<String, String>
    ): List<ReplicationController> = client.getMany(
        newReplicationController {
            metadata {
                this.namespace = namespace
                this.labels = labels
            }
        }
    )

    suspend fun getDeploymentConfig(namespace: String?, openShiftName: String?): DeploymentConfig? = client.getOrNull(
        newDeploymentConfig {
            metadata {
                this.namespace = namespace
                this.name = openShiftName
            }
        }
    )

    suspend fun getImageStreamTag(namespace: String, name: String, tagName: String): ImageStreamTag? = client.getOrNull(
        newImageStreamTag {
            metadata {
                this.namespace = namespace
                this.name = "$name:$tagName"
            }
        }
    )

    suspend fun getPods(namespace: String, labels: Map<String, String?>): List<Pod> = client.getMany(
        newPod {
            metadata {
                this.namespace = namespace
                this.labels = labels
            }
        }
    )

    suspend fun getDeployment(namespace: String?, openShiftName: String?): Deployment? = client.getOrNull(
        newDeployment {
            metadata {
                this.namespace = namespace
                this.name = openShiftName
            }
        }
    )

    suspend fun getReplicaSets(namespace: String, labels: Map<String, String>): List<ReplicaSet> = client.getMany(
        newReplicaSet {
            metadata {
                this.namespace = namespace
                this.labels = labels
            }
        }
    )

    suspend fun getStorageGridObjectAreas(namespace: String): List<StorageGridObjectArea> = client.getMany(
        newStorageGridObjectArea {
            metadata {
                this.namespace = namespace
            }
        }
    )
}
