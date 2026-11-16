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
import java.time.Instant;
import java.util.Collections;
import java.util.Date;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.doxia.site.Banner;
import org.apache.maven.doxia.site.Body;
import org.apache.maven.doxia.site.LinkItem;
import org.apache.maven.doxia.site.Logo;
import org.apache.maven.doxia.site.Menu;
import org.apache.maven.doxia.site.PublishDate;
import org.apache.maven.doxia.site.SiteModel;
import org.apache.maven.doxia.site.Skin;
import org.apache.maven.doxia.site.Version;
import org.apache.maven.doxia.site.io.xpp3.SiteXpp3Reader;
import org.apache.maven.doxia.site.io.xpp3.SiteXpp3Writer;
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
        @CsvSource({"2.3", "3.0-beta-3", "3.12.1", "3.19.1", "4.0.0-M1", "4.0.0-M9"})
        void rejectsSitePluginOnDoxia1(String sitePlugin) {
            MojoFailureException e = assertThrows(
                    MojoFailureException.class,
                    () -> SkinMojo.verifySitePluginVersion(new DefaultArtifactVersion(sitePlugin)));
            assertTrue(e.getMessage().startsWith("maven-site-plugin:" + sitePlugin + " of the released project"));
        }

        @ParameterizedTest
        @CsvSource({"3.20.0", "3.21.0", "3.22.0", "4.0.0-M10", "4.0.0-M16"})
        void acceptsSitePluginOnDoxia2(String sitePlugin) {
            assertDoesNotThrow(() -> SkinMojo.verifySitePluginVersion(new DefaultArtifactVersion(sitePlugin)));
        }

        @ParameterizedTest
        @CsvSource({"3.22.0, true", "3.23.0, true", "3.21.0, false", "3.20.0, false", "4.0.0-M16, false"})
        void siteModel21Readers(String sitePlugin, boolean expected) {
            assertEquals(expected, SkinMojo.readsSiteModel21(new DefaultArtifactVersion(sitePlugin)));
        }

        @Test
        void unknownSitePluginDoesNotReadSiteModel21() {
            assertFalse(SkinMojo.readsSiteModel21(null));
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
            SiteModel current = new SiteModel();
            current.setBannerLeft(banner("current-left"));
            current.setBannerRight(banner("current-right"));
            current.setName("Current");
            current.setModelEncoding("ISO-8859-1");
            current.setPoweredBy(Collections.singletonList(logo("current-logo")));

            SiteModel released = new SiteModel();
            released.setBannerLeft(banner("released-left"));
            released.setName("Released");
            released.setBody(body("released-link"));
            released.getBody().addMenu(new Menu());

            SkinMojo.mergeSiteModels(current, skin, released, false);

            assertSame(skin, released.getSkin());
            assertEquals("current-left", released.getBannerLeft().getName());
            assertEquals("current-right", released.getBannerRight().getName());
            assertEquals("Current", released.getName());
            assertEquals("ISO-8859-1", released.getModelEncoding());
            assertEquals("current-logo", released.getPoweredBy().get(0).getName());
            assertEquals("released-link", released.getBody().getLinks().get(0).getName());
            assertEquals(1, released.getBody().getMenus().size());
        }

        @Test
        void layoutUnsetInCurrentIsClearedInReleased() {
            SiteModel released = new SiteModel();
            released.setBannerLeft(banner("released-left"));
            released.setPublishDate(publishDate("right"));

            SkinMojo.mergeSiteModels(new SiteModel(), skin, released, false);

            assertNull(released.getBannerLeft());
            assertTrue(released.isDefaultPublishDate());
            assertTrue(released.isDefaultVersion());
        }

        @Test
        void copiesPublishDateAndVersionOnlyWhenSet() {
            SiteModel current = new SiteModel();
            current.setPublishDate(publishDate("right"));
            Version version = new Version();
            version.setPosition("none");
            current.setVersion(version);
            SiteModel released = new SiteModel();

            SkinMojo.mergeSiteModels(current, skin, released, false);

            assertEquals("right", released.getPublishDate().getPosition());
            assertEquals("none", released.getVersion().getPosition());
        }

        @Test
        void mergesBodyOnRequest() {
            SiteModel current = new SiteModel();
            current.setBody(body("current-link"));
            current.getBody().setHead("<meta name=\"current\"/>");
            current.getBody().setFooter("current footer");
            current.getBody().addBreadcrumb(link("current-crumb"));
            SiteModel released = new SiteModel();
            released.setBody(body("released-link"));
            released.getBody().addMenu(new Menu());

            SkinMojo.mergeSiteModels(current, skin, released, true);

            assertEquals("current-link", released.getBody().getLinks().get(0).getName());
            assertEquals("<meta name=\"current\"/>", released.getBody().getHead());
            assertEquals("current footer", released.getBody().getFooter());
            assertEquals(
                    "current-crumb", released.getBody().getBreadcrumbs().get(0).getName());
            assertEquals(1, released.getBody().getMenus().size());
        }

        @Test
        void leavesBodyWithoutMergeBody() {
            SiteModel current = new SiteModel();
            current.setBody(body("current-link"));
            SiteModel released = new SiteModel();
            released.setBody(body("released-link"));

            SkinMojo.mergeSiteModels(current, skin, released, false);

            assertEquals("released-link", released.getBody().getLinks().get(0).getName());
        }

        @Test
        void mergeBodyCreatesReleasedBody() {
            SiteModel current = new SiteModel();
            current.setBody(body("current-link"));
            SiteModel released = new SiteModel();

            SkinMojo.mergeSiteModels(current, skin, released, true);

            assertEquals("current-link", released.getBody().getLinks().get(0).getName());
        }

        @Test
        void mergeBodyWithoutCurrentBodyLeavesReleasedBody() {
            SiteModel released = new SiteModel();
            released.setBody(body("released-link"));

            SkinMojo.mergeSiteModels(new SiteModel(), skin, released, true);

            assertEquals("released-link", released.getBody().getLinks().get(0).getName());
        }

        @Test
        void mergesCustomWithCurrentValuesFirst() throws Exception {
            SiteModel current = new SiteModel();
            current.setCustom(dom("<custom><fluidoSkin><topBarEnabled>true</topBarEnabled></fluidoSkin></custom>"));
            SiteModel released = new SiteModel();
            released.setCustom(dom("<custom><fluidoSkin><topBarEnabled>false</topBarEnabled>"
                    + "<sideBarEnabled>false</sideBarEnabled></fluidoSkin><old>kept</old></custom>"));

            Xpp3Dom custom = SkinMojo.mergeSiteModels(current, skin, released, false);

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
            Xpp3Dom custom = SkinMojo.mergeSiteModels(new SiteModel(), skin, new SiteModel(), false);

            assertNotNull(custom);
            assertEquals("custom", custom.getName());
            assertEquals(0, custom.getChildCount());
        }
    }

    @Nested
    class OutputTimestamp {

        private final Date released = Date.from(Instant.parse("2012-10-05T14:51:21.123Z"));

        @Test
        void passesTheDeployDateWhenTheReleaseHasNoTimestamp() {
            assertEquals("2012-10-05T14:51:21Z", SkinMojo.outputTimestamp(released, false, null));
        }

        @Test
        void keepsTheReleasedTimestamp() {
            assertNull(SkinMojo.outputTimestamp(released, false, "2012-10-05T10:00:00Z"));
        }

        @Test
        void ignoresADisabledReleasedTimestamp() {
            assertEquals("2012-10-05T14:51:21Z", SkinMojo.outputTimestamp(released, false, "a"));
        }

        @Test
        void explicitPublishDateWins() {
            assertEquals("2012-10-05T14:51:21Z", SkinMojo.outputTimestamp(released, true, "2020-01-01T00:00:00Z"));
        }

        @Test
        void parsesInTheSiteTimeZone() throws Exception {
            PublishDate utc = new PublishDate();
            Date date = SkinMojo.publishDateFormat(utc).parse("2001-02-03");
            assertEquals("2001-02-03T00:00:00Z", SkinMojo.outputTimestamp(date, true, null));

            PublishDate warsaw = new PublishDate();
            warsaw.setTimezone("Europe/Warsaw");
            date = SkinMojo.publishDateFormat(warsaw).parse("2001-02-03");
            assertEquals("2001-02-02T23:00:00Z", SkinMojo.outputTimestamp(date, true, null));
        }

        @Test
        void nothingWithoutADate() {
            assertNull(SkinMojo.outputTimestamp(null, true, null));
        }
    }

    @Nested
    class Write {

        @TempDir
        Path dir;

        @Test
        void omitsUnsetPublishDateAndVersion() throws Exception {
            SiteModel model = new SiteModel();
            model.setSkin(skin("1.12.0"));

            String xml = write(model);

            assertFalse(xml.contains("<publishDate"), xml);
            assertTrue(xml.contains("<version>1.12.0</version>"), xml);
            assertFalse(xml.contains("<version />") || xml.contains("<version/>"), xml);
            SiteModel read = new SiteXpp3Reader().read(new StringReader(xml));
            assertTrue(read.isDefaultPublishDate());
            assertTrue(read.isDefaultVersion());
        }

        @Test
        void keepsSetPublishDateAndVersion() throws Exception {
            SiteModel model = new SiteModel();
            model.setPublishDate(publishDate("right"));
            Version version = new Version();
            version.setPosition("left");
            model.setVersion(version);

            SiteModel read = new SiteXpp3Reader().read(new StringReader(write(model)));

            assertEquals("right", read.getPublishDate().getPosition());
            assertEquals("left", read.getVersion().getPosition());
        }

        @Test
        void dropsSiteModel21Attributes() throws Exception {
            SiteModel model = new SiteModel();
            PublishDate publishDate = publishDate("right");
            publishDate.setFormat("yyyy-MM-dd");
            publishDate.setValue("scmLastModified");
            publishDate.setPrefix("Updated: ");
            publishDate.setFallbackValue("buildTimestamp");
            publishDate.setFallbackPrefix("Built: ");
            model.setPublishDate(publishDate);

            SkinMojo.dropSiteModel21Attributes(model);
            String xml = write(model);

            assertFalse(xml.contains("scmLastModified") || xml.contains("Updated") || xml.contains("Built"), xml);
            SiteModel read = new SiteXpp3Reader().read(new StringReader(xml));
            assertEquals("right", read.getPublishDate().getPosition());
            assertEquals("yyyy-MM-dd", read.getPublishDate().getFormat());
        }

        @Test
        void dropSiteModel21AttributesKeepsDefaultPublishDate() {
            SiteModel model = new SiteModel();

            SkinMojo.dropSiteModel21Attributes(model);

            assertTrue(model.isDefaultPublishDate());
        }

        private String write(SiteModel model) throws Exception {
            File siteXml = dir.resolve("site.xml").toFile();
            SkinMojo.writeSiteModel(new SiteXpp3Writer(), model, siteXml);
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
