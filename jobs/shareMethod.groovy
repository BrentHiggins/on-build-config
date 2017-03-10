def checkout(String url, String branch, String targetDir){
    checkout(
    [$class: 'GitSCM', branches: [[name: branch]],
    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: targetDir]],
    userRemoteConfigs: [[url: url]]])
}
def checkout(String url, String branch){
    checkout(
    [$class: 'GitSCM', branches: [[name: branch]],
    userRemoteConfigs: [[url: url]]])
}

def checkout(String url){
    checkout(url, "master")
}

def waitForFreeResource(label_name,quantity){
    int free_count=0
    while(free_count<quantity){
        free_count = org.jenkins.plugins.lockableresources.LockableResourcesManager.class.get().getFreeResourceAmount(label_name)
        if(free_count == 0){
            sleep 5
        }
    }
}

def getLockedResourceName(resources,label_name){
    // Get the resource name whose label contains the parameter label_name
    def resource_name=""
    for(int i=0;i<resources.size();i++){
        String labels = resources[i].getLabels();
        List label_names = Arrays.asList(labels.split("\\s+"));
        for(int j=0;j<label_names.size();j++){
            if(label_names[j]==label_name){
                resource_name=resources[i].getName();
                return resource_name
            }
        }
    }
    return resource_name
}

def buildAndPublish(){
    stage("Packages Build"){
        load("jobs/build_debian/build_debian.groovy")
    }
    waitForFreeResource("docker",1)
    // lock a docker resource from build to release
    lock(label:"docker",quantity:1){
        def lock_resources=org.jenkins.plugins.lockableresources.LockableResourcesManager.class.get().getResourcesFromBuild(currentBuild.getRawBuild())       
        docker_resource_name = getLockedResourceName(lock_resources,"docker")
        env.build_docker_node = docker_resource_name

        stage("Images Build"){
            parallel 'vagrant build':{
                load("jobs/build_vagrant/build_vagrant.groovy")
            }, 'ova build':{
                load("jobs/build_ova/build_ova.groovy")
            }, 'build docker':{
                load("jobs/build_docker/build_docker.groovy")
            }
        }

        stage("Post Test"){
            parallel 'vagrant post test':{
                load("jobs/build_vagrant/vagrant_post_test.groovy")
            }, 'ova post test':{
                load("jobs/build_ova/ova_post_test.groovy")
            }, 'docker post test':{
                load("jobs/build_docker/docker_post_test.groovy")
            }
        }
  
        stage("Publish"){
            parallel 'Publish Debian':{
                load("jobs/release/release_debian.groovy")
            }, 'Publish Vagrant':{
                load("jobs/release/release_vagrant.groovy")
            }, 'Publish Docker':{
                load("jobs/release/release_docker.groovy")
            }, 'Publish NPM':{
                load("jobs/release/release_npm.groovy")
            }
        }
    }
}
return this
