package applicationinstancedetails

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  request {
    method 'GET'
    url $(
        stub(~/\/api\/applicationinstancedetails\?affiliation=.*/),
        test('/api/applicationinstancedetails?affiliation=paas')
    )
  }
  response {
    status 200
    headers {
      contentType(applicationJson())
    }
    body(file('responses/applicationinstancedetailsarray.json'))
  }
}