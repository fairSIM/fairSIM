This CI workflow runs on a forgejo git server (https://forgejo.org/)

It uses maven to build, test and package fairSIM on JDK 8, 21 and 25. The JDK 8 job builds the
jar that is published; the JDK 21 and 25 jobs compile natively for their JDK (the 'modern-jdk'
Maven profile) as a check. Each job runs in a build image from ci/fairsim-build/Dockerfile (JDK,
Maven, and Node.js for the actions), built with --build-arg JDK=<n>. The workflow pulls these
images from the registry named by the CI_REGISTRY variable, which has to be set on the forgejo
server (repository or organization settings, Actions -> Variables), with the images pushed there
as fairsim/fairsim-build:jdk<n>-<revision>.

To auto-upload the resulting jar files as a package, it also expects an access token to be
set as a secret variable (see the last step of the workflow). If package upload is not required,
this can be omitted.
