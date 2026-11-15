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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.doxia.site.decoration.Banner;
import org.apache.maven.doxia.site.decoration.Body;
import org.apache.maven.doxia.site.decoration.DecorationModel;
import org.apache.maven.doxia.site.decoration.LinkItem;
import org.apache.maven.doxia.site.decoration.Logo;
import org.apache.maven.doxia.site.decoration.Menu;
import org.apache.maven.doxia.site.decoration.PublishDate;
import org.apache.maven.doxia.site.decoration.Skin;
import org.apache.maven.doxia.site.decoration.Version;
import org.apache.maven.doxia.site.decoration.io.xpp3.DecorationXpp3Reader;
import org.apache.maven.doxia.site.decoration.io.xpp3.DecorationXpp3Writer;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinMojoTest {

    @Nested
    class VersionCheck {

        @ParameterizedTest
        @CsvSource({
            "2.3, 3.9.16, Maven 2.x",
            "2.0-beta-6, 3.0, Maven 2.x",
            "3.0-beta-3, 2.2.1, Maven 3.x+",
            "3.0-alpha-1, 2.0.11, Maven 3.x+"
        })
        void rejectsSitePluginThatCannotRunOnMaven(String sitePlugin, String maven, String expected) {
            MojoFailureException e = assertThrows(
                    MojoFailureException.class,
                    () -> SkinMojo.verifyVersionCompatibility(new DefaultArtifactVersion(sitePlugin), maven));
            assertEquals("maven-site-plugin:" + sitePlugin + " can only be executed with " + expected, e.getMessage());
        }

        @ParameterizedTest
        @CsvSource({"2.3, 2.2.1", "3.0, 3.9.16", "3.12.1, 3.9.16", "3.12.1, 3.10.0", "3.0-beta-3, 3.0"})
        void acceptsSitePluginThatRunsOnMaven(String sitePlugin, String maven) {
            assertDoesNotThrow(
                    () -> SkinMojo.verifyVersionCompatibility(new DefaultArtifactVersion(sitePlugin), maven));
        }

        @Test
        void acceptsProjectWithoutSitePluginVersion() {
            assertDoesNotThrow(() -> SkinMojo.verifyVersionCompatibility(null, "3.9.16"));
        }

        @Test
        void readsSitePluginVersionFromPlugins() {
            MavenProject project = project();
            project.getBuild().addPlugin(sitePlugin("3.3"));
            project.getBuild().getPluginManagement().addPlugin(sitePlugin("2.3"));

            assertEquals(new DefaultArtifactVersion("3.3"), SkinMojo.getSitePluginVersion(project));
        }

        @Test
        void readsSitePluginVersionFromPluginManagement() {
            MavenProject project = project();
            project.getBuild().getPluginManagement().addPlugin(sitePlugin("2.3"));

            assertEquals(new DefaultArtifactVersion("2.3"), SkinMojo.getSitePluginVersion(project));
        }

        @Test
        void noSitePlugin() {
            assertNull(SkinMojo.getSitePluginVersion(project()));
        }

        @Test
        void noSitePluginVersion() {
            MavenProject project = project();
            project.getBuild().addPlugin(sitePlugin(null));

            assertNull(SkinMojo.getSitePluginVersion(project));
        }

        private MavenProject project() {
            Model model = new Model();
            model.setBuild(new Build());
            model.getBuild().setPluginManagement(new PluginManagement());
            return new MavenProject(model);
        }

        private Plugin sitePlugin(String version) {
            Plugin plugin = new Plugin();
            plugin.setArtifactId("maven-site-plugin");
            plugin.setVersion(version);
            return plugin;
        }
    }

    @Nested
    class Merge {

        private final Skin skin = skin("1.12.0");

        @Test
        void copiesSkinAndLayoutAndKeepsReleasedContent() {
            DecorationModel current = new DecorationModel();
            current.setBannerLeft(banner("current-left"));
            current.setBannerRight(banner("current-right"));
            current.setGoogleAnalyticsAccountId("UA-1");
            current.setName("Current");
            current.setModelEncoding("ISO-8859-1");
            current.setPoweredBy(Collections.singletonList(logo("current-logo")));

            DecorationModel released = new DecorationModel();
            released.setBannerLeft(banner("released-left"));
            released.setName("Released");
            released.setBody(body("released-link"));
            released.getBody().addMenu(new Menu());

            SkinMojo.mergeDecorationModels(current, skin, released, false);

            assertSame(skin, released.getSkin());
            assertEquals("current-left", released.getBannerLeft().getName());
            assertEquals("current-right", released.getBannerRight().getName());
            assertEquals("UA-1", released.getGoogleAnalyticsAccountId());
            assertEquals("Current", released.getName());
            assertEquals("ISO-8859-1", released.getModelEncoding());
            assertEquals("current-logo", released.getPoweredBy().get(0).getName());
            assertEquals("released-link", released.getBody().getLinks().get(0).getName());
            assertEquals(1, released.getBody().getMenus().size());
        }

        @Test
        void layoutUnsetInCurrentIsClearedInReleased() {
            DecorationModel released = new DecorationModel();
            released.setBannerLeft(banner("released-left"));
            released.setPublishDate(publishDate("right"));

            SkinMojo.mergeDecorationModels(new DecorationModel(), skin, released, false);

            assertNull(released.getBannerLeft());
            assertTrue(released.isDefaultPublishDate());
            assertTrue(released.isDefaultVersion());
        }

        @Test
        void copiesPublishDateAndVersionOnlyWhenSet() {
            DecorationModel current = new DecorationModel();
            current.setPublishDate(publishDate("right"));
            Version version = new Version();
            version.setPosition("none");
            current.setVersion(version);
            DecorationModel released = new DecorationModel();

            SkinMojo.mergeDecorationModels(current, skin, released, false);

            assertEquals("right", released.getPublishDate().getPosition());
            assertEquals("none", released.getVersion().getPosition());
        }

        @Test
        void mergesBodyOnRequest() {
            DecorationModel current = new DecorationModel();
            current.setBody(body("current-link"));
            current.getBody().setHead("<meta name=\"current\"/>");
            current.getBody().setFooter("current footer");
            current.getBody().addBreadcrumb(link("current-crumb"));
            DecorationModel released = new DecorationModel();
            released.setBody(body("released-link"));
            released.getBody().addMenu(new Menu());

            SkinMojo.mergeDecorationModels(current, skin, released, true);

            assertEquals("current-link", released.getBody().getLinks().get(0).getName());
            assertEquals("<meta name=\"current\"/>", released.getBody().getHead());
            assertEquals("current footer", released.getBody().getFooter());
            assertEquals(
                    "current-crumb", released.getBody().getBreadcrumbs().get(0).getName());
            assertEquals(1, released.getBody().getMenus().size());
        }

        @Test
        void leavesBodyWithoutMergeBody() {
            DecorationModel current = new DecorationModel();
            current.setBody(body("current-link"));
            DecorationModel released = new DecorationModel();
            released.setBody(body("released-link"));

            SkinMojo.mergeDecorationModels(current, skin, released, false);

            assertEquals("released-link", released.getBody().getLinks().get(0).getName());
        }

        @Test
        void mergeBodyCreatesReleasedBody() {
            DecorationModel current = new DecorationModel();
            current.setBody(body("current-link"));
            DecorationModel released = new DecorationModel();

            SkinMojo.mergeDecorationModels(current, skin, released, true);

            assertEquals("current-link", released.getBody().getLinks().get(0).getName());
        }

        @Test
        void mergeBodyWithoutCurrentBodyLeavesReleasedBody() {
            DecorationModel released = new DecorationModel();
            released.setBody(body("released-link"));

            SkinMojo.mergeDecorationModels(new DecorationModel(), skin, released, true);

            assertEquals("released-link", released.getBody().getLinks().get(0).getName());
        }

        @Test
        void mergesCustomWithCurrentValuesFirst() throws Exception {
            DecorationModel current = new DecorationModel();
            current.setCustom(dom("<custom><fluidoSkin><topBarEnabled>true</topBarEnabled></fluidoSkin></custom>"));
            DecorationModel released = new DecorationModel();
            released.setCustom(dom("<custom><fluidoSkin><topBarEnabled>false</topBarEnabled>"
                    + "<sideBarEnabled>false</sideBarEnabled></fluidoSkin><old>kept</old></custom>"));

            Xpp3Dom custom = SkinMojo.mergeDecorationModels(current, skin, released, false);

            assertEquals(
                    "true",
                    custom.getChild("fluidoSkin").getChild("topBarEnabled").getValue());
            assertEquals(
                    "false",
                    custom.getChild("fluidoSkin").getChild("sideBarEnabled").getValue());
            assertEquals("kept", custom.getChild("old").getValue());
        }

        @Test
        void customIsNeverNull() {
            Xpp3Dom custom = SkinMojo.mergeDecorationModels(new DecorationModel(), skin, new DecorationModel(), false);

            assertNotNull(custom);
            assertEquals("custom", custom.getName());
            assertEquals(0, custom.getChildCount());
        }
    }

    @Nested
    class Write {

        @TempDir
        Path dir;

        @Test
        void omitsUnsetPublishDateAndVersion() throws Exception {
            DecorationModel model = new DecorationModel();
            model.setSkin(skin("1.12.0"));

            String xml = write(model);

            assertFalse(xml.contains("<publishDate"), xml);
            assertTrue(xml.contains("<version>1.12.0</version>"), xml);
            assertFalse(xml.contains("<version />") || xml.contains("<version/>"), xml);
            DecorationModel read = new DecorationXpp3Reader().read(new StringReader(xml));
            assertTrue(read.isDefaultPublishDate());
            assertTrue(read.isDefaultVersion());
        }

        @Test
        void keepsSetPublishDateAndVersion() throws Exception {
            DecorationModel model = new DecorationModel();
            model.setPublishDate(publishDate("right"));
            Version version = new Version();
            version.setPosition("left");
            model.setVersion(version);

            DecorationModel read = new DecorationXpp3Reader().read(new StringReader(write(model)));

            assertEquals("right", read.getPublishDate().getPosition());
            assertEquals("left", read.getVersion().getPosition());
        }

        private String write(DecorationModel model) throws Exception {
            File siteXml = dir.resolve("site.xml").toFile();
            SkinMojo.writeDecorationModel(new DecorationXpp3Writer(), model, siteXml);
            return new String(Files.readAllBytes(siteXml.toPath()), StandardCharsets.UTF_8);
        }
    }

    private static Skin skin(String version) {
        Skin skin = new Skin();
        skin.setGroupId("org.apache.maven.skins");
        skin.setArtifactId("maven-fluido-skin");
        skin.setVersion(version);
        return skin;
    }

    private static Banner banner(String name) {
        Banner banner = new Banner();
        banner.setName(name);
        return banner;
    }

    private static Logo logo(String name) {
        Logo logo = new Logo();
        logo.setName(name);
        return logo;
    }

    private static LinkItem link(String name) {
        LinkItem link = new LinkItem();
        link.setName(name);
        return link;
    }

    private static Body body(String linkName) {
        Body body = new Body();
        body.addLink(link(linkName));
        return body;
    }

    private static PublishDate publishDate(String position) {
        PublishDate publishDate = new PublishDate();
        publishDate.setPosition(position);
        return publishDate;
    }

    private static Xpp3Dom dom(String xml) throws Exception {
        return Xpp3DomBuilder.build(new StringReader(xml));
    }
}
