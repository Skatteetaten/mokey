package applicationdeploymentbyresource

import org.springframework.cloud.contract.spec.Contract

Contract.make {
  request {
    method 'POST'
    url "/api/auth/applicationdeploymentbyresource/databases"
    headers {
      header 'Content-Type': 'application/json'
    }
    body(
        """["123", "456"]"""
    )
    bodyMatchers {
      jsonPath('$[*]', byRegex(nonEmpty()))
    }

  }
  response {
    status 200
    headers {
      contentType(applicationJson())
    }
    body(file('responses/applicationdeploymentwithdb.json'))
  }
}