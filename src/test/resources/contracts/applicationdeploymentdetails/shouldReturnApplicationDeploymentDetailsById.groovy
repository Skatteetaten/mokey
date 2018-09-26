package applicationdeploymentdetails

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  request {
    method 'GET'
    url $(
        stub(~/\/api\/applicationdeploymentdetails\/\w+/),
        test('/api/applicationdeploymentdetails/123')
    )
  }
  response {
    status 200
    headers {
      contentType(applicationJson())
    }
    body(file('responses/applicationdeploymentdetails.json'))
  }
}