package applicationinstancedetails

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  request {
    method 'GET'
    url $(
        stub(~/\/api\/applicationinstance\/\w+/),
        test('/api/applicationinstance/123')
    )
  }
  response {
    status 200
    headers {
      contentType(applicationJson())
    }
    body(file('responses/applicationinstance.json'))
  }
}