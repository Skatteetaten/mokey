package applicationdeploymentbyresource

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  request {
    method 'GET'
    url $(
        stub(~/\/api\/auth\/applicationdeploymentbyresource\/databases.+/),
        test('/api/auth/applicationdeploymentbyresource/databases?databaseids=123,456')
    )
  }
  response {
    status 200
    headers {
      contentType(applicationJson())
    }
    body(file('responses/applicationdeploymentwithdb.json'))
  }
}