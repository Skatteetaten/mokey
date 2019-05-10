# Mokey
<img align="right" src="https://vignette.wikia.nocookie.net/muppet/images/2/28/Mokey.jpg/revision/latest/scale-to-width-down/280?cb=2012123116482">

Mokey is a crawler that crawls an OpenShift cluster caching information about all applications in the cluster. 

The component is named after the Mokey Fraggle (http://muppet.wikia.com/wiki/Mokey_Fraggle). 

 ## Setup
 
 In order to use this project you must set repositories in your `~/.gradle/init.gradle` file
 
     allprojects {
         ext.repos= {
             mavenCentral()
             jcenter()
         }
         repositories repos
         buildscript {
          repositories repos
         }
     }

In addition to this, we use a local repository for the gradlewrapper, so in order to use the gradlew command you need to change the distributionUrl in gradle-wrapper.properties to a public repo with the corresponding gradle version.

## How to run locally
 - Log into your OpenShift cluster with `oc`
 - Start the Main class
 - If you want to turn off caching set the mokey.cache property to false
 
## Test locally
Create a file  src/main/http/rest-client.env.json 

 ```
 {
    "local":{
      "apiUrl":"http://localhost:8080",
      "token":""
    },
    "utv-dev": {
      "apiUrl": "http://url-to-mokey-on-your-cluster"
      "token": ""
    }
  }
 ```
  
Fill in the token value with a valid ocp token from `oc whoami -t`
Run the http commands from Intellij


## OpenShift

Mokey must be run as a clust-admin user; example oc command to grant the privileges:

    oc adm policy add-cluster-role-to-user cluster-admin system:serviceaccount:paas-mokey:mokey
