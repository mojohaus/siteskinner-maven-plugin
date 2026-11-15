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
- Doxia sitetools stay on 1.11.x, the line maven-site-plugin 3.12.1 uses: the plugin re-renders released sites,
  whose descriptors are decoration model 1.x. Doxia 2 is a separate decision (#7); Dependabot ignores it.
- Decoration model 1.11 never returns `null` from `getPublishDate()` or `getVersion()`, and its writer always emits
  both. `SkinMojo` copies them only when the current descriptor sets them and strips the empty elements, because an
  empty element stops the site plugin from inheriting the parent's values.
- Maven SCM 2.x: the standard provider set is git (gitexe, jgit), hg and svn. The plugin adds
  `maven-scm-provider-local`, which the ITs use. 1.x-only providers (cvs, perforce, ...) no longer exist.
- Components come through the `@Inject` constructor of `SkinMojo`. `SiteTool` 1.11 still needs the maven-compat
  components that Maven 3.9 and 3.10 ship in `lib/`.
- Java 8 bytecode (mojo-parent). Format with `mvn spotless:apply`; it also runs in the build.

## Tests

- Unit tests are JUnit 5. `SkinMojoTest` covers the package-private static methods for the site plugin version
  check and the site descriptor merge; `CLIManagerTest` pins the parsing of `arguments`, which must stay as
  GnuParser read it.
- ITs run in the `run-its` profile, on by default: `mvn verify`. One IT:
  `mvn -P run-its verify -Dinvoker.test=msiteskin-2`.
- Each IT under `src/it/projects` is the *current* project. The released versions it skins are POMs in
  `src/it/mrm/repository`, served by mrm, and their sources are under the IT's own `src/it/scm/<name>-<version>`,
  reached through `scm:local|src/it/scm|...`. Keep each SCM fixture inside its IT: Maven 3 normalizes `../` in POM
  URLs and breaks the connection.
- Fixtures pin maven-site-plugin 3.12.1. Maven 3.10.0 otherwise binds 3.22.0 (Doxia 2, rejects Doxia 1 skins), and
  it ignores `-D` user properties when it builds repository POMs, so fixtures must not depend on them.
- msiteskin-4_m-site-p-3.0-beta-x runs on Maven 2 only (`invoker.maven.version = !3+`) and is always skipped.
- The checkout fixtures under `src/it/projects/*/src/it/scm` model released projects: keep their old plugin and
  provider versions. Dependabot excludes `src/it/**`.
- Fixture POMs use CRLF line endings. With `core.autocrlf=input`, add them with
  `git -c core.autocrlf=false add`, or Git rewrites every line.
- After changing a fixture, delete `target/local-repo/localhost` and `target/it`; the invoker installs the mrm
  artifacts and the checkouts there.
- Verify scripts are Groovy assertions; the last expression of a script must be `true` or `null`.

## Pull requests

Draft until CI is green. The PR title and its label become the release note (Release Drafter).
