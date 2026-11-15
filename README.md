# SiteSkinner Maven Plugin

[![Maven Central](https://img.shields.io/maven-central/v/org.codehaus.mojo/siteskinner-maven-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.codehaus.mojo/siteskinner-maven-plugin)
[![GitHub CI](https://github.com/mojohaus/siteskinner-maven-plugin/actions/workflows/maven.yml/badge.svg)](https://github.com/mojohaus/siteskinner-maven-plugin/actions/workflows/maven.yml)

The SiteSkinner Maven Plugin re-renders the site of the latest release of a project with the skin of the current
project. It checks out the released sources through the `scm` connection of the released POM, merges the current
skin and layout settings into the released `site.xml`, and runs `mvn site` on the checkout.

It needs Maven 3.9.0 or later and Java 8 or later. The released project must use a maven-site-plugin that runs on
the Maven doing the rendering; the plugin checks this before it starts.

## Usage

Run it from the current project, which must already use the new skin:

```sh
mvn siteskinner:skin
```

Check the result under `target/siteskinner/target/site`, then deploy it from there with `mvn site:deploy`, or deploy
it directly:

```sh
mvn siteskinner:skin -DsiteDeploy=true
```

Keep the released body (breadcrumbs, links, head and footer) instead of copying the current one:

```sh
mvn siteskinner:skin -DmergeBody=false
```

See the [plugin site](https://www.mojohaus.org/siteskinner-maven-plugin/) for the
[goal parameters](https://www.mojohaus.org/siteskinner-maven-plugin/skin-mojo.html) and the
[usage notes](https://www.mojohaus.org/siteskinner-maven-plugin/usage.html).

## Contributing

Build conventions, the IT setup and their pitfalls are in [AGENTS.md](AGENTS.md). The PR title and its label become the release note, so write the title as one.

## Releasing

* Make sure `gpg-agent` is running.
* Run `mvn -B release:prepare release:perform`.

To publish the site:

```sh
cd target/checkout
mvn verify site site:stage scm-publish:publish-scm
```

