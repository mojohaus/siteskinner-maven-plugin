---

title: Frequently Asked Questions
---------------------------------

<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Frequently Asked Questions

1. [The maven-checkstyle-plugin caused the skinning to fail. What do I have to do?](#maven_checkstyle_plugin)
2. [The maven-changes-plugin caused the skinning to fail. What do I have to do?](#maven_changes_plugin)
3. [A report caused the skinning to fail. What do I have to do?](#maven_site_plugin)

<a id="maven_checkstyle_plugin"></a>

### The maven-checkstyle-plugin caused the skinning to fail. What do I have to do?

It can happen that the skinning fails with the message like `FileLength is not allowed as a child in Checker`

This means you're using a newer checkstyle configuration file which is not supported by the used version of the maven-checkstyle-plugin.
This can happen if the `configLocation` refers to file under version control.

The easiest way to solve this is to add/override the [maven-checkstyle-plugin](http://maven.apache.org/plugins/maven-checkstyle-plugin/) version in the reports-section of the `target/siteskinner/pom.xml`

<a id="maven_changes_plugin"></a>

### The maven-changes-plugin caused the skinning to fail. What do I have to do?

Sometimes a `NullPointerException` occurs if the maven-changes-plugin tries to download a JIRA report.

The plugin fails to parse the XML returned by a newer version of JIRA.

The easiest way to solve this is to add/override the [maven-changes-plugin](http://maven.apache.org/plugins/maven-changes-plugin/) version in the reports-section of the `target/siteskinner/pom.xml`

<a id="maven_site_plugin"></a>

### A report caused the skinning to fail. What do I have to do?

A `NoClassDefFoundError` can occur if the version of Maven is not compatable with the specified maven-site-plugin.
Often you won't see this directly under the maven-site-plugin, but under one the reports.

You can either downgrade the version of [Maven](http://maven.apache.org/download.html) (since it has been successfully deployed in the past) or upgrade the version of the [maven-site-plugin](http://maven.apache.org/plugins/maven-site-plugin/).
