package affiliation

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  request {
    method 'GET'
    url '/api/auth/affiliation'
  }
  response {
    status 200
    headers {
      contentType(applicationJson())
    }
    body(file('responses/affiliations.json'))
  }
}