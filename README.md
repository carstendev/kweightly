# kweightly [![CircleCI](https://circleci.com/gh/carstendev/kweightly/tree/master.svg?style=svg)](https://circleci.com/gh/carstendev/kweightly/tree/master)
Weight tracking backend written in Kotlin

## Goal
The goal for this project is to demonstrate how to build a modern micro-service cloud-native backend application in kotlin. 

## Tech-Stack

- [Http4k](https://www.http4k.org) the web server framework
- [Auth0](https://auth0.com) for authN and authZ
- [Docker](https://www.docker.com) for containerization
- [Terraform](https://www.terraform.io) for provisioning the infrastructure as code
- [Kubernetes](https://kubernetes.io) for container orchestration
- [GKE](https://cloud.google.com/kubernetes-engine/) for hosted kubernetes services

## Infrastructure
This infrastructure for this app is provisioned to GKE via the [kweightly-infrastructure](https://github.com/carstendev/kweightly-infrastructure) project.
