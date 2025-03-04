/*
 * While this is not a plugin, it is much simpler to reuse the pipeline code for CI. This allows for
 * easy Linux/Windows testing and produces incrementals. The only feature that relates to plugins is
 * allowing one to test against multiple Jenkins versions.
 */
pipeline {
    agent none
    stages {
        stage('Build') {
            matrix {
                axes {
                    axis {
                        name 'NATIVE_CONFIG'
                        values 'Release', 'Debug'
                    }
                    axis {
                        name 'JDK'
                        values '21', '17'
                    }
                }
                agent {
                    label "${PLATFORM == 'windows' ? 'windows' : 'linux'}"
                }
                stages {
                    stage('Build') {
                        steps {
                            script {
                                def mavenProps = "-Dnative.configuration=${NATIVE_CONFIG}"

                                sh "mvn clean install ${mavenProps}"
                            }
                        }
                    }
                }
            }
        }
    }
}