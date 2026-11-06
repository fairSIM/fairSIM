This CI workflow runs on a forgejo git server (https://forgejo.org/)

It uses maven to build and package fairSIM. The job runs in the build image defined in
ci/fairsim-build/Dockerfile (JDK 8, Maven, and Node.js for the actions). The workflow pulls
that image from the registry named by the CI_REGISTRY variable, which has to be set on the
forgejo server (repository or organization settings, Actions -> Variables), with the image
pushed there as fairsim/fairsim-build:<tag>.

To auto-upload the resulting jar files as a package, it also expects an access token to be
set as a secret variable (see the last step of the workflow). If package upload is not required,
this can be ommitted.
