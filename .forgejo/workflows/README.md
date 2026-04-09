This CI workflow runs on a forgejo git server (https://forgejo.org/)

It uses maven to build and package fairSIM. Both maven and node.js (for the actions) need to
be available on the runner. It specifies linux-java as a name for the runner.

To auto-upload the resulting jar files as a package, it also expects an access token to be
set as a secret variable (see the last step of the workflow). If package upload is not required,
this can be ommitted.
