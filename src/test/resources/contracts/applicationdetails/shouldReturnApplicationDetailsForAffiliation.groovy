package applicationdetails

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  request {
    method 'GET'
    url $(
        consumer(~/\/api\/applicationdetails\?affiliation=.*/),
        producer('/api/applicationdetails?affiliation=paas')
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