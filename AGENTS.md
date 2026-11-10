# AGENTS.md

Guidance for AI coding agents (and humans) working on fairSIM.

## What this is

fairSIM reconstructs structured illumination microscopy (SIM) data. It is written in Java and ships as an ImageJ/Fiji plugin.

- `sim_algorithm/` holds the reconstruction: parameter estimation, band separation, OTF handling and filtering. It has a 2D path ("vers.1") and a 3D path.
- `linalg/` holds vectors and FFTs.
- `fiji/` and `sim_gui/` hold the plugin entry points and the GUI.
- `utils/` holds configuration and logging.
- Menu wiring is in `plugins.config`.

## Build

- `mvn package` builds the plugin jar into `target/`. The shipped jar is built with a 1.8 JDK: it targets the Java 8 runtime of Fiji stable.
- On JDK 21 and newer, the `modern-jdk` profile compiles for the running JDK against a current ImageJ and adds a `-jdkNN` suffix to the jar. CI uses it to check the code on Java 21 (Fiji latest) and 25 (current LTS); these jars are not for Fiji stable.
- The `Makefile` is a legacy build. It needs the jars that `external/get-dependencies.sh` fetches.
- Both builds produce a self-contained jar. Maven bundles JTransforms and its dependencies, relocated to `org.fairsim.extern.shaded`; the Makefile bundles the fairSIM JTransforms fork. Their licence notices are in `src/main/resources/third-party-licenses.txt` and the about dialog. Keep both build paths working.
- `doc/` is javadoc output only, and `make clean` deletes it. Never put hand-written files there.
- CI (`.forgejo/workflows/ci.yml`) builds on JDK 8, 21 and 25, in images built from `ci/fairsim-build/` with `--build-arg JDK=<n>`, tagged `jdk<n>-<revision>`. Only the JDK 8 job publishes the jar. With every Dockerfile change, bump the revision for all three, push the images, and update `ci.yml`.
- `mvn test` runs the JUnit tests in `src/test/`, including a full 2D reconstruction of simulated data. Coverage is still thin: a successful build says little about correctness.

## Changing reconstruction code

- A logic error in `sim_algorithm/` or `linalg/` usually does not crash. It silently changes the reconstructed image.
  - Treat structural changes (refactors, reordering, "cleanups") with the same care as numerical ones.
  - Compare output before and after on real data.
  - If you could not run a reconstruction, say so explicitly.
- Verify sign, axis and Fourier-shift conventions against the code rather than assuming them.
- Test with non-square, asymmetric images. Square, symmetric data hides transposes and flips.
- Vectors: after writing to the array returned by `vectorData()`, call `syncBuffer()`.

## Code style

- Match the style of the file you edit.
- Comments are terse. Javadoc is mostly a single line; `@param` lines are short and state units and conventions inline. Inline comments are short step labels (`// check some inputs`).
- Where an algorithm or design needs explaining, a longer comment block at that place is fine. Otherwise, no long explanatory blocks.
- New code uses container types, not plain arrays, unless arrays are needed for performance.
- Vector operations that work on any vector type are default methods on the vector interfaces, not static helpers.

## Provenance and licensing

fairSIM is GPL-licensed (see `LICENSE`). Keep provenance clean:

- Contribute only code you wrote yourself, or code from permissively licensed sources (MIT, BSD, Apache-2.0, public domain), with attribution.
- Do not port or translate code from copyleft or unlicensed projects. That includes other SIM reconstruction packages. Describing their behaviour or comparing outputs is fine.
- New dependencies need a permissive licence and the maintainers' agreement.

## Git

- Versions are git tags, semver-ish: `vMAJOR.MINOR.PATCH`, pre-releases like `v1.4.2-beta1`. Both builds record the tag on the built commit (first of `git tag --points-at HEAD`), or `n/a` if there is none. The pom version is a placeholder and is not maintained.
- Branch model:
  - Specific work branches split off from more general ones (e.g. `develop` → `develop-3D-SIM`).
  - Merge regularly from the more general branch.
  - Merge back at the end.
  - Do not rebase or rewrite branches others have pulled.
- Commits:
  - One logical change per commit.
  - Short log-style messages: a subject line and at most a few lines of body. Explanations go in code comments, not in the message.
  - Stage files explicitly.
  - Build before committing.
  - Mark AI-assisted commits with a `Co-Authored-By:` trailer.
  - No `[topic]` tags in commit messages.
  - Bundle small documentation updates with the next substantive commit instead of committing them on their own.
- This repository is public. Never commit host names, server addresses, absolute local paths, credentials or personal data. Check the diff before each commit.
- Do not commit build output (`target/`, `build/`, jars).

## Documentation

Keep project documentation sparse: the README, this file, and javadoc. Ask the maintainers before adding doc files, design notes or larger doc sections. Code comments explain *why*, not *what*.

## Working style for agents

- Ask before large refactors, new dependencies, or API changes visible to plugins and scripts.
- Report what you verified (built, ran, compared) separately from what you only read or compiled.
