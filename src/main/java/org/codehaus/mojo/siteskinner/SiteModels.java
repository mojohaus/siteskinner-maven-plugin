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
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.doxia.site.Banner;
import org.apache.maven.doxia.site.Body;
import org.apache.maven.doxia.site.Image;
import org.apache.maven.doxia.site.LinkItem;
import org.apache.maven.doxia.site.Logo;
import org.apache.maven.doxia.site.Menu;
import org.apache.maven.doxia.site.MenuItem;
import org.apache.maven.doxia.site.PublishDate;
import org.apache.maven.doxia.site.SiteModel;
import org.apache.maven.doxia.site.Skin;
import org.apache.maven.doxia.site.Version;
import org.apache.maven.doxia.site.decoration.DecorationModel;
import org.apache.maven.doxia.site.decoration.io.xpp3.DecorationXpp3Reader;
import org.apache.maven.doxia.site.io.xpp3.SiteXpp3Reader;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.pull.MXParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Reads a site descriptor as it is, without inheritance or interpolation. A 1.x descriptor (root element
 * {@code project}) is converted to the 2.0.0 site model the way Doxia Sitetools 2.1.0 does in
 * {@code DefaultSiteTool}, whose converter is private.
 */
final class SiteModels {

    private SiteModels() {}

    /**
     * @param siteXml an existing site descriptor
     * @return the site model; a 1.x descriptor converted to 2.0.0
     * @throws IOException if the file cannot be read
     * @throws XmlPullParserException if the file is not a site descriptor
     */
    static SiteModel read(File siteXml) throws IOException, XmlPullParserException {
        String content;
        try (Reader reader = new XmlStreamReader(Files.newInputStream(siteXml.toPath()))) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            for (int n; (n = reader.read(buffer)) >= 0; ) {
                builder.append(buffer, 0, n);
            }
            content = builder.toString();
        }
        if (isOldSiteModel(content)) {
            return convert(new DecorationXpp3Reader().read(new StringReader(content), false));
        }
        return new SiteXpp3Reader().read(new StringReader(content), false);
    }

    static boolean isOldSiteModel(String content) throws XmlPullParserException, IOException {
        XmlPullParser parser = new MXParser();
        parser.setInput(new StringReader(content));
        return parser.getEventType() == XmlPullParser.START_DOCUMENT
                && parser.next() == XmlPullParser.START_TAG
                && "project".equals(parser.getName());
    }

    static SiteModel convert(DecorationModel oldModel) {
        SiteModel newModel = new SiteModel();
        newModel.setName(oldModel.getName());
        newModel.setCombineSelf(oldModel.getCombineSelf());
        if (oldModel.getBannerLeft() != null) {
            newModel.setBannerLeft(convertBanner(oldModel.getBannerLeft()));
        }
        if (oldModel.getBannerRight() != null) {
            newModel.setBannerRight(convertBanner(oldModel.getBannerRight()));
        }
        if (!oldModel.isDefaultPublishDate()) {
            PublishDate newPublishDate = new PublishDate();
            newPublishDate.setFormat(oldModel.getPublishDate().getFormat());
            newPublishDate.setPosition(oldModel.getPublishDate().getPosition());
            newPublishDate.setTimezone(oldModel.getPublishDate().getTimezone());
            newModel.setPublishDate(newPublishDate);
        }
        if (!oldModel.isDefaultVersion()) {
            Version newVersion = new Version();
            newVersion.setPosition(oldModel.getVersion().getPosition());
            newModel.setVersion(newVersion);
        }
        newModel.setEdit(oldModel.getEdit());
        if (oldModel.getSkin() != null) {
            Skin newSkin = new Skin();
            newSkin.setGroupId(oldModel.getSkin().getGroupId());
            newSkin.setArtifactId(oldModel.getSkin().getArtifactId());
            newSkin.setVersion(oldModel.getSkin().getVersion());
            newModel.setSkin(newSkin);
        }
        for (org.apache.maven.doxia.site.decoration.Logo oldLogo : oldModel.getPoweredBy()) {
            Logo newLogo = new Logo();
            newLogo.setName(oldLogo.getName());
            newLogo.setHref(oldLogo.getHref());
            newLogo.setTarget(oldLogo.getTarget());
            if (oldLogo.getImg() != null) {
                newLogo.setImage(convertImage(
                        oldLogo.getImg(),
                        oldLogo.getPosition(),
                        oldLogo.getHeight(),
                        oldLogo.getWidth(),
                        oldLogo.getBorder(),
                        oldLogo.getAlt()));
            }
            newModel.addPoweredBy(newLogo);
        }
        newModel.setLastModified(oldModel.getLastModified());
        if (oldModel.getBody() != null) {
            Body newBody = new Body();
            newBody.setHead(oldModel.getBody().getHead());
            for (org.apache.maven.doxia.site.decoration.LinkItem oldLink :
                    oldModel.getBody().getLinks()) {
                newBody.addLink(convertLinkItem(oldLink));
            }
            for (org.apache.maven.doxia.site.decoration.LinkItem oldBreadcrumb :
                    oldModel.getBody().getBreadcrumbs()) {
                newBody.addBreadcrumb(convertLinkItem(oldBreadcrumb));
            }
            for (org.apache.maven.doxia.site.decoration.Menu oldMenu :
                    oldModel.getBody().getMenus()) {
                Menu newMenu = new Menu();
                newMenu.setName(oldMenu.getName());
                newMenu.setInherit(oldMenu.getInherit());
                newMenu.setInheritAsRef(oldMenu.isInheritAsRef());
                newMenu.setRef(oldMenu.getRef());
                if (oldMenu.getImg() != null) {
                    newMenu.setImage(convertImage(
                            oldMenu.getImg(),
                            oldMenu.getPosition(),
                            oldMenu.getHeight(),
                            oldMenu.getWidth(),
                            oldMenu.getBorder(),
                            oldMenu.getAlt()));
                }
                newMenu.setItems(convertMenuItems(oldMenu.getItems()));
                newBody.addMenu(newMenu);
            }
            newBody.setFooter(oldModel.getBody().getFooter());
            newModel.setBody(newBody);
        }
        newModel.setCustom(oldModel.getCustom());
        newModel.setModelEncoding(oldModel.getModelEncoding());
        return newModel;
    }

    private static Banner convertBanner(org.apache.maven.doxia.site.decoration.Banner oldBanner) {
        Banner newBanner = new Banner();
        newBanner.setName(oldBanner.getName());
        newBanner.setHref(oldBanner.getHref());
        if (oldBanner.getSrc() != null) {
            newBanner.setImage(convertImage(
                    oldBanner.getSrc(),
                    null,
                    oldBanner.getHeight(),
                    oldBanner.getWidth(),
                    oldBanner.getBorder(),
                    oldBanner.getAlt()));
        }
        return newBanner;
    }

    private static Image convertImage(
            String src, String position, String height, String width, String border, String alt) {
        Image newImage = new Image();
        newImage.setSrc(src);
        newImage.setPosition(position);
        newImage.setHeight(height);
        newImage.setWidth(width);
        if (border != null) {
            newImage.setStyle("border: " + border + ";");
        }
        newImage.setAlt(alt);
        return newImage;
    }

    private static LinkItem convertLinkItem(org.apache.maven.doxia.site.decoration.LinkItem oldLinkItem) {
        LinkItem newLinkItem = new LinkItem();
        newLinkItem.setName(oldLinkItem.getName());
        newLinkItem.setHref(oldLinkItem.getHref());
        newLinkItem.setTarget(oldLinkItem.getTarget());
        if (oldLinkItem.getImg() != null) {
            newLinkItem.setImage(convertImage(
                    oldLinkItem.getImg(),
                    oldLinkItem.getPosition(),
                    oldLinkItem.getHeight(),
                    oldLinkItem.getWidth(),
                    oldLinkItem.getBorder(),
                    oldLinkItem.getAlt()));
        }
        return newLinkItem;
    }

    private static List<MenuItem> convertMenuItems(List<org.apache.maven.doxia.site.decoration.MenuItem> oldMenuItems) {
        List<MenuItem> newMenuItems = new ArrayList<>();
        for (org.apache.maven.doxia.site.decoration.MenuItem oldMenuItem : oldMenuItems) {
            MenuItem newMenuItem = new MenuItem();
            newMenuItem.setName(oldMenuItem.getName());
            newMenuItem.setHref(oldMenuItem.getHref());
            newMenuItem.setTarget(oldMenuItem.getTarget());
            newMenuItem.setCollapse(oldMenuItem.isCollapse());
            newMenuItem.setRef(oldMenuItem.getRef());
            newMenuItem.setItems(convertMenuItems(oldMenuItem.getItems()));
            if (oldMenuItem.getImg() != null) {
                newMenuItem.setImage(convertImage(
                        oldMenuItem.getImg(),
                        oldMenuItem.getPosition(),
                        oldMenuItem.getHeight(),
                        oldMenuItem.getWidth(),
                        oldMenuItem.getBorder(),
                        oldMenuItem.getAlt()));
            }
            newMenuItems.add(newMenuItem);
        }
        return newMenuItems;
    }
}
