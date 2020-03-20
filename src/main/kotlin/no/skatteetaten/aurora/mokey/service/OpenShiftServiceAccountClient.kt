package no.skatteetaten.aurora.mokey.service

import com.fkorotkov.kubernetes.apps.metadata
import com.fkorotkov.kubernetes.apps.newDeployment
import com.fkorotkov.kubernetes.apps.newReplicaSet
import com.fkorotkov.kubernetes.extensions.newIngress
import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.kubernetes.newService
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.api.model.extensions.Ingress
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

    suspend fun getIngresses(metadata: ObjectMeta): List<Ingress> =
        client.getMany(newIngress { this.metadata = metadata })

    suspend fun getAllNamespaces(): List<Namespace> = client.getMany(null)

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

    suspend fun getPods(namespace: String, labels: Map<String, String?>): List<Pod> {
        return client.getMany(newPod {
            metadata {
                this.namespace = namespace
                this.labels = labels
            }
        })
    }

    suspend fun getDeployment(namespace: String?, openShiftName: String?): Deployment? {
        return client.getOrNull(newDeployment {
            metadata {
                this.namespace = namespace
                this.name = openShiftName
            }
        })
    }

    suspend fun getReplicaSets(namespace: String, labels: Map<String, String>): List<ReplicaSet> {
        return client.getMany(newReplicaSet {
            metadata {
                this.namespace = namespace
                this.labels = labels
            }
        })
    }
}
