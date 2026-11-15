package org.codehaus.mojo.siteskinner;

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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.doxia.site.Menu;
import org.apache.maven.doxia.site.SiteModel;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteModelsTest {

    @TempDir
    Path dir;

    @Test
    void convertsDecorationModel() throws Exception {
        SiteModel model = read("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project name=\"Old\" xmlns=\"http://maven.apache.org/DECORATION/1.8.0\">\n"
                + "  <bannerLeft><name>Left</name><src>left.png</src><href>https://example.org/</href></bannerLeft>\n"
                + "  <publishDate position=\"right\" format=\"yyyy-MM-dd\"/>\n"
                + "  <skin><groupId>org.apache.maven.skins</groupId><artifactId>maven-fluido-skin</artifactId>"
                + "<version>1.12.0</version></skin>\n"
                + "  <body>\n"
                + "    <links><item name=\"Apache\" href=\"https://www.apache.org/\"/></links>\n"
                + "    <menu name=\"Overview\"><item name=\"Introduction\" href=\"index.html\"/></menu>\n"
                + "  </body>\n"
                + "  <custom><fluidoSkin><topBarEnabled>true</topBarEnabled></fluidoSkin></custom>\n"
                + "</project>\n");

        assertEquals("Old", model.getName());
        assertEquals("Left", model.getBannerLeft().getName());
        assertEquals("left.png", model.getBannerLeft().getImage().getSrc());
        assertEquals("https://example.org/", model.getBannerLeft().getHref());
        assertEquals("right", model.getPublishDate().getPosition());
        assertEquals("yyyy-MM-dd", model.getPublishDate().getFormat());
        assertTrue(model.isDefaultVersion());
        assertEquals("1.12.0", model.getSkin().getVersion());
        assertEquals("Apache", model.getBody().getLinks().get(0).getName());
        Menu menu = model.getBody().getMenus().get(0);
        assertEquals("Overview", menu.getName());
        assertEquals("index.html", menu.getItems().get(0).getHref());
        assertEquals(
                "true",
                ((Xpp3Dom) model.getCustom())
                        .getChild("fluidoSkin")
                        .getChild("topBarEnabled")
                        .getValue());
    }

    @Test
    void readsSiteModel() throws Exception {
        SiteModel model = read("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<site name=\"New\" xmlns=\"http://maven.apache.org/SITE/2.0.0\">\n"
                + "  <bannerLeft name=\"Left\" href=\"https://example.org/\"><image src=\"left.png\"/></bannerLeft>\n"
                + "  <skin><groupId>org.apache.maven.skins</groupId><artifactId>maven-fluido-skin</artifactId>"
                + "<version>2.1.0</version></skin>\n"
                + "</site>\n");

        assertEquals("New", model.getName());
        assertEquals("left.png", model.getBannerLeft().getImage().getSrc());
        assertEquals("2.1.0", model.getSkin().getVersion());
        assertTrue(model.isDefaultPublishDate());
        assertNull(model.getBody());
    }

    private SiteModel read(String xml) throws Exception {
        File siteXml = dir.resolve("site.xml").toFile();
        Files.write(siteXml.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        return SiteModels.read(siteXml);
    }
}
