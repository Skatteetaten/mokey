package applicationdeploymentdetails

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  request {
    method 'GET'
    url $(
        stub(~/\/api\/applicationdeployment\/\w+/),
        test('/api/applicationdeployment/123')
    )
  }
  response {
    status 200
    headers {
      contentType(applicationJson())
    }
    body(file('responses/applicationdeployment.json'))
  }
}