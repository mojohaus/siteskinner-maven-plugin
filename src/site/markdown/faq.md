<?xml version="1.0"?>
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
<faqs id="FAQ" title="Frequently Asked Questions">
 <part id="Failing plugins">
   <faq id="maven_checkstyle_plugin">
     <question>The maven-checkstyle-plugin caused the skinning to fail. What do I have to do?</question>
     <answer>
       <p>It can happen that the skinning fails with the message like <code>FileLength is not allowed as a child in Checker</code></p>
       <p>This means you're using a newer checkstyle configuration file which is not supported by the used version of the maven-checkstyle-plugin.
          This can happen if the <code>configLocation</code> refers to file under version control.
       </p>
       <p>The easiest way to solve this is to add/override the <a href="http://maven.apache.org/plugins/maven-checkstyle-plugin/">maven-checkstyle-plugin</a> version in the reports-section of the <code>target/siteskinner/pom.xml</code></p>
     </answer>
   </faq>
   <faq id="maven_changes_plugin">
     <question>The maven-changes-plugin caused the skinning to fail. What do I have to do?</question>
     <answer>
       <p>Sometimes a <code>NullPointerException</code> occurs if the maven-changes-plugin tries to download a JIRA report.</p>
       <p>The plugin fails to parse the XML returned by a newer version of JIRA.</p>
       <p>The easiest way to solve this is to add/override the <a href="http://maven.apache.org/plugins/maven-changes-plugin/">maven-changes-plugin</a> version in the reports-section of the <code>target/siteskinner/pom.xml</code></p>
     </answer>
   </faq>
   <faq id="maven_site_plugin">
     <question>A report caused the skinning to fail. What do I have to do?</question>
     <answer>
       <p>A <code>NoClassDefFoundError</code> can occur if the version of Maven is not compatable with the specified maven-site-plugin. 
       Often you won't see this directly under the maven-site-plugin, but under one the reports.</p>
       <p>You can either downgrade the version of <a href="http://maven.apache.org/download.html">Maven</a> (since it has been successfully deployed in the past) or upgrade the version of the <a href="http://maven.apache.org/plugins/maven-site-plugin/">maven-site-plugin</a>.</p>
     </answer>
   </faq>
   
 </part>
</faqs>