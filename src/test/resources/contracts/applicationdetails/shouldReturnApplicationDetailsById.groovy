package applicationdetails

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  request {
    method 'GET'
    url $(
        stub(~/\/api\/applicationdetails\/\w+/),
        test('/api/applicationdetails/123')
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
    body(file('responses/applicationdetails.json'))
  }
}