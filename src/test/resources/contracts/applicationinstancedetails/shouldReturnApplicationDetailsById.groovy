package applicationinstancedetails

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  request {
    method 'GET'
    url $(
        stub(~/\/api\/applicationinstancedetails\/\w+/),
        test('/api/applicationinstancedetails/123')
    )
  }
  response {
    status 200
    headers {
      contentType(applicationJson())
    }
    body(file('responses/applicationinstancedetails.json'))
  }
}