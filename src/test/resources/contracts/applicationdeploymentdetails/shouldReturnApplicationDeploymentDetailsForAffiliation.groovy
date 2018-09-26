package applicationdeploymentdetails

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  request {
    method 'GET'
    url $(
        stub(~/\/api\/applicationdeploymentdetails\?affiliation=.*/),
        test('/api/applicationdeploymentdetails?affiliation=paas')
    )
  }
  response {
    status 200
    headers {
      contentType(applicationJson())
    }
    body(file('responses/applicationdeploymentdetailsarray.json'))
  }
}