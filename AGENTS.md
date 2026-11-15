# Working on siteskinner-maven-plugin

Notes for contributors and coding agents: what the code and the POMs do not make obvious.

## Layout

One module, one goal. `SkinMojo` (`siteskinner:skin`) resolves the latest released version below the current one,
builds its POM, checks out its sources with Maven SCM into `target/siteskinner`, merges the current project's site
descriptor into the released one and runs `mvn site` there through maven-invoker. `CLIManager` parses the
`arguments` parameter (`-D`, `-P`, `-X`) for that run; `ScmCommandExecutor` wraps the checkout and update.

## Baseline

- Maven API 3.9.x in `provided` scope, Resolver 1.9.x, literal `<prerequisites>` 3.9.0. Dependabot ignores Maven
  artifacts from 3.10.0 on (Resolver 2.x) and plexus-xml 4.x (Maven 4 XML API).
- Doxia sitetools 2.x. The released site is still rendered by the released project's own maven-site-plugin, so
  that plugin must be based on Doxia 2 (3.20.0 or later, 4.0.0-M10 or later): `SkinMojo` writes the merged
  `site.xml` in the 2.x site model and fails early on a Doxia 1 site plugin. Releases built with a Doxia 1 site
  plugin cannot be re-skinned.
- The written descriptor must stay readable by the oldest accepted site plugin, whose reader is strict. Only 3.22.0
  and later read the 2.1.0 model, so for older ones `SkinMojo` drops the publishDate attributes 2.1.0 added. Check
  this again when Dependabot moves Doxia sitetools to a new model version.
- `SiteModels` reads a site descriptor as it is, without inheritance: a 1.x descriptor (root `project`) is converted
  with a copy of the private converter in Doxia's `DefaultSiteTool`.
- The site model never returns `null` from `getPublishDate()` or `getVersion()`, and its writer always emits
  both. `SkinMojo` copies them only when the current descriptor sets them and strips the empty elements, because an
  empty element stops the site plugin from inheriting the parent's values.
- Maven SCM 2.x: the standard provider set is git (gitexe, jgit), hg and svn. The plugin adds
  `maven-scm-provider-local`, which the ITs use. 1.x-only providers (cvs, perforce, ...) no longer exist.
- Components come through the `@Inject` constructor of `SkinMojo`.
- Java 8 bytecode (mojo-parent). Format with `mvn spotless:apply`; it also runs in the build.

## Tests

- Unit tests are JUnit 5. `SkinMojoTest` covers the package-private static methods for the site plugin version
  check and the site descriptor merge, `SiteModelsTest` the reading of 1.x and 2.x descriptors, and `CLIManagerTest` pins the parsing of `arguments`, which must stay as
  GnuParser read it.
- ITs run in the `run-its` profile, on by default: `mvn verify`. One IT:
  `mvn -P run-its verify -Dinvoker.test=msiteskin-2`.
- Each IT under `src/it/projects` is the *current* project. The released versions it skins are POMs in
  `src/it/mrm/repository`, served by mrm, and their sources are under the IT's own `src/it/scm/<name>-<version>`,
  reached through `scm:local|src/it/scm|...`. Keep each SCM fixture inside its IT: Maven 3 normalizes `../` in POM
  URLs and breaks the connection.
- Released fixtures pin maven-site-plugin 3.21.0, and the current projects use fluido 2.x, the only Apache skin with Doxia 2
  releases; the msiteskin-9 parents differ in fluido version so the ITs can tell which parent's skin applied.
  msiteskin-4_m-site-p-2.x checks that a Doxia 1 site plugin is rejected. Maven 3.10 ignores `-D` user properties
  when it builds repository POMs, so fixtures must not depend on them.
- The checkout fixtures under `src/it/projects/*/src/it/scm` model released projects: their site descriptors stay
  in the 1.x model to cover the conversion, and their report plugins keep their old versions. Dependabot excludes `src/it/**`.
- Fixture POMs use CRLF line endings. With `core.autocrlf=input`, add them with
  `git -c core.autocrlf=false add`, or Git rewrites every line.
- After changing a fixture, delete `target/local-repo/localhost` and `target/it`; the invoker installs the mrm
  artifacts and the checkouts there.
- Verify scripts are Groovy assertions; the last expression of a script must be `true` or `null`.

## Pull requests

Draft until CI is green. The PR title and its label become the release note (Release Drafter).
