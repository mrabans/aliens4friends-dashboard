pipeline {
    agent any

    options {
        ansiColor('xterm')
    }

    environment {
		COMPOSE_PROJECT_NAME = "solda-dashboard"
        DOCKER_IMAGE = '755952719952.dkr.ecr.eu-west-1.amazonaws.com/solda-dashboard'
        DOCKER_TAG = "test-$BUILD_NUMBER"
		ANSIBLE_LIMIT = "test"
		SERVER_PORT = 1077
    }

    stages {
        stage('Configure') {
            steps {
                sh """
                    rm -f .env
                    echo 'COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME}' >> .env
                    echo 'DOCKER_IMAGE=${DOCKER_IMAGE}' >> .env
                    echo 'DOCKER_TAG=${DOCKER_TAG}' >> .env
					echo 'SERVER_PORT=${SERVER_PORT}' >> .env
				"""
			}
		}
		stage('Test') {
            steps {
                sh '''
                    docker network create authentication || true
					docker-compose --no-ansi build --pull
                    docker-compose --no-ansi run --rm --no-deps \
                        -u $(id -u jenkins):$(id -g jenkins) \
                        --entrypoint 'bash -c' \
                        app \
                            'php artisan test'
                '''
            }
        }
        stage('Build') {
            steps {
                sh '''
                    aws ecr get-login --region eu-west-1 --no-include-email | bash
                    docker-compose --no-ansi -f infrastructure/docker/docker-compose.build.yml build --pull
                    docker-compose --no-ansi -f infrastructure/docker/docker-compose.build.yml push
                '''
            }
        }
        stage('Deploy') {
            steps {
               sshagent(['jenkins-ssh-key']) {
                    sh """
                        cd infrastructure/ansible
                        ansible-galaxy install -f -r requirements.yml
                        ansible-playbook --limit=${ANSIBLE_LIMIT} deploy.yml --extra-vars "release_name=${BUILD_NUMBER}"
                    """
                }
            }
        }
	}
}
