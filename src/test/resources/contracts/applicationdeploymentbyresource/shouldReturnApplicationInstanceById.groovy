package applicationdeploymentbyresource

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  request {
    method 'POST'
    url "/api/auth/applicationdeploymentbyresource/databases"
    headers {
      contentType(applicationJson())
    }
    body(
        '''["123", "456"]'''
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