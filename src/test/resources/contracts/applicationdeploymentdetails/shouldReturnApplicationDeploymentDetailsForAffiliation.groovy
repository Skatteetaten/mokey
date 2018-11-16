package applicationdeploymentdetails

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  ignored()
  request {
    method 'GET'
    url $(
        stub(~/\/api\/auth\/applicationdeploymentdetails\?affiliation=.*/),
        test('/api/auth/applicationdeploymentdetails?affiliation=paas')
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