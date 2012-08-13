/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
def projectNode = new XmlSlurper().parse( new File( basedir, 'target/siteskinner/src/site/site.xml') )
assert projectNode.skin.size() == 1
assert projectNode.custom.publishDate.size() == 1
assert projectNode.bannerLeft.size() == 0
assert projectNode.bannerRight.size() == 0
assert projectNode.publishDate.size() == 0
assert projectNode.version.size() == 0

assert projectNode.body.breadcrumbs.size() == 0
assert projectNode.body.footer.size() == 0
assert projectNode.body.head.size() == 0
assert projectNode.body.links.size() == 0