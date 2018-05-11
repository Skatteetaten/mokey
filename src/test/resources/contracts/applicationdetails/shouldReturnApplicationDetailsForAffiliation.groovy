package applicationdetails

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  request {
    method 'GET'
    url $(
        stub(~/\/api\/applicationdetails\?affiliation=.*/),
        test('/api/applicationdetails?affiliation=paas')
    )
    headers {
      contentType(applicationJson())
    }
  }
  response {
    status 200
    headers {
      contentType(applicationJson())
    }
    body(file('responses/applicationdetailsarray.json'))
  }
}