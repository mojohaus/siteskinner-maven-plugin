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

import javax.inject.Inject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.doxia.site.Body;
import org.apache.maven.doxia.site.PublishDate;
import org.apache.maven.doxia.site.SiteModel;
import org.apache.maven.doxia.site.Skin;
import org.apache.maven.doxia.site.io.xpp3.SiteXpp3Writer;
import org.apache.maven.doxia.tools.SiteTool;
import org.apache.maven.doxia.tools.SiteToolException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.InvokerLogger;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

/**
 * Call <code>mvn siteskinner:skin</code> on a maven project. This will check out the latest releases project. Next it
 * will add/replace the skin of the site.xml with the skin of the current project. Finally it will invoke a
 * <code>mvn site</code> on the checked out project. Now you can verify the pages and run <code>mvn site:deploy</code>
 * on the checked out project.
 */
@Mojo(name = "skin", aggregator = true)
public class SkinMojo extends AbstractMojo {
    private static final String MAVEN_SITE_PLUGIN_KEY = "org.apache.maven.plugins:maven-site-plugin";

    /** Site plugins that read the 2.0.0 site model. */
    private static final String DOXIA_2_SITE_PLUGINS = "[3.20.0,4.0.0-M1),[4.0.0-M10,)";

    /** Site plugins that read the 2.1.0 site model; older ones reject its new publishDate attributes. */
    private static final String SITE_MODEL_2_1_SITE_PLUGINS = "[3.22.0,4.0.0-M1)";

    /**
     * The Maven installation that renders the released site. Defaults to the Maven that runs this plugin.
     * @since 2.0.0
     */
    @Parameter(property = "mavenHome")
    private File mavenHome;

    /**
     * Addition arguments, accepts:
     * <ul>
     *   <li>-D,--define &lt;arg&gt;</li>
     *   <li>-P,--activate-profiles &lt;arg&gt;</li>
     *   <li>-X,--debug</li>
     * </ul>
     * @since 2.0.0
     */
    @Parameter(property = "arguments")
    private String arguments;

    /**
     * Force a checkout instead of an update when the sources have already been checked out during a previous run.
     * @since 1.0
     */
    @Parameter(property = "forceCheckout", defaultValue = "false")
    private boolean forceCheckout;

    /**
     * If {@code true}, all the elements of the body in the {@code site.xml} will be merged, except the menu items. Set
     * to {@code false} if you don't want to merge the body.
     * @since 1.0
     */
    @Parameter(property = "mergeBody", defaultValue = "true")
    private boolean mergeBody;

    /**
     * If {@code false} the plugin should only generate the site, else if {@code true} the site should be published
     * immediately too.
     * @since 1.0
     */
    @Parameter(property = "siteDeploy", defaultValue = "false")
    private boolean siteDeploy;

    /**
     * In most cases this plugin can discover the original publishDate. You could set this value for those cases when this fails
     * @since 2.0.0
     */
    @Parameter(property = "siteskinner.publishDate")
    private String publishDate;

    /**
     * Specifies the input encoding.
     * @since 1.0
     * @deprecated ignored since 2.0.0: the site descriptor is read with the encoding its XML declaration names.
     */
    @Deprecated
    @Parameter(defaultValue = "${project.build.sourceEncoding}", property = "encoding")
    private String inputEncoding;

    /**
     * Specifies the output encoding.
     * @since 1.0
     * @deprecated ignored since 2.0.0: the re-rendered site uses the encoding of the released project.
     */
    @Deprecated
    @Parameter(defaultValue = "${project.reporting.outputEncoding}", property = "outputEncoding")
    private String outputEncoding;

    /* Read-only parmaters */

    /**
     * Versionrange to calculate latest released version
     */
    @Parameter(defaultValue = "(,${project.version})", readonly = true)
    private String releasedVersion;

    /**
     * The working directory for this plugin.
     */
    @Parameter(defaultValue = "${project.build.directory}/siteskinner", readonly = true)
    private File workingDirectory;

    /**
     * The reactor projects.
     */
    @Parameter(defaultValue = "${reactorProjects}", readonly = true, required = true)
    private List<MavenProject> reactorProjects;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject currentProject;

    /**
     * @since 1.0
     */
    @Parameter(property = "settingsFile")
    private File settingsFile;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    private final ProjectBuilder projectBuilder;

    private final RepositorySystem repositorySystem;

    private final ScmManager scmManager;

    private final SiteTool siteTool;

    private final Invoker invoker;

    @Inject
    public SkinMojo(
            ProjectBuilder projectBuilder,
            RepositorySystem repositorySystem,
            ScmManager scmManager,
            SiteTool siteTool,
            Invoker invoker) {
        this.projectBuilder = projectBuilder;
        this.repositorySystem = repositorySystem;
        this.scmManager = scmManager;
        this.siteTool = siteTool;
        this.invoker = invoker;
    }

    /** {@inheritDoc} */
    public void execute() throws MojoExecutionException, MojoFailureException {
        MavenProject releasedProject;
        Artifact releasedArtifact;
        try {
            releasedArtifact = resolveArtifact(releasedVersion);
            MavenProject externalProject = projectBuilder
                    .build(
                            RepositoryUtils.toArtifact(releasedArtifact),
                            newProjectBuildingRequest(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL, false))
                    .getProject();

            fetchSources(workingDirectory, externalProject);

            releasedProject = projectBuilder
                    .build(
                            new File(workingDirectory, "pom.xml"),
                            newProjectBuildingRequest(ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_2_0, true))
                    .getProject();
        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException(e.getMessage());
        }

        verifySitePluginVersion(releasedProject);

        Xpp3Dom releasedConfig = getSitePluginConfiguration(releasedProject);
        String releasedSiteDirectory = releasedConfig == null || releasedConfig.getChild("siteDirectory") == null
                ? "src/site"
                : releasedConfig.getChild("siteDirectory").getValue();
        String releasedLocales = (releasedConfig == null || releasedConfig.getChild("locales") == null
                ? null
                : releasedConfig.getChild("locales").getValue());

        Xpp3Dom currentConfig = getSitePluginConfiguration(currentProject);
        String currentSiteDirectory = currentConfig == null || currentConfig.getChild("siteDirectory") == null
                ? "src/site"
                : currentConfig.getChild("siteDirectory").getValue();
        try {
            SiteXpp3Writer writer = new SiteXpp3Writer();

            File currentSiteDir = resolveSiteDirectory(currentProject.getBasedir(), currentSiteDirectory);
            File releasedSiteDir = resolveSiteDirectory(releasedProject.getBasedir(), releasedSiteDirectory);

            for (Locale locale : siteTool.getSiteLocales(releasedLocales)) {
                SiteModel resolvedCurrentModel;
                try {
                    resolvedCurrentModel = siteTool.getSiteModel(
                            currentSiteDir,
                            locale,
                            session.getRequest(),
                            currentProject,
                            reactorProjects,
                            session.getRepositorySession(),
                            currentProject.getRemoteProjectRepositories());
                } catch (SiteToolException e) {
                    getLog().warn(e.getMessage(), e);
                    continue;
                }

                if (resolvedCurrentModel.getSkin() == null) {
                    throw new MojoFailureException(
                            "No skin defined in the current project, neither inherited; Can't apply a new skin on the old site.");
                }

                File currentSiteXml = siteTool.getSiteDescriptor(currentSiteDir, locale);

                SiteModel currentModel;
                if (currentSiteXml.exists()) {
                    currentModel = SiteModels.read(currentSiteXml);
                } else {
                    currentModel = new SiteModel();
                }

                File releasedSiteXml = siteTool.getSiteDescriptor(releasedSiteDir, locale);

                SiteModel releasedModel;
                if (releasedSiteXml.exists()) {
                    releasedModel = SiteModels.read(releasedSiteXml);
                } else {
                    // already create folders to be sure we can write to this file
                    releasedSiteXml.getParentFile().mkdirs();
                    releasedModel = new SiteModel();
                }

                Xpp3Dom mergedCustom =
                        mergeSiteModels(currentModel, resolvedCurrentModel.getSkin(), releasedModel, mergeBody);

                String publishDateFormat;
                if (releasedModel.getPublishDate() != null) {
                    publishDateFormat = releasedModel.getPublishDate().getFormat();
                } else {
                    publishDateFormat = new PublishDate().getFormat();
                }

                Xpp3Dom publishDateChild = new Xpp3Dom("publishDate");

                String publishDateValue;

                if (publishDate == null) {
                    long preResolveDate = System.currentTimeMillis();

                    try {
                        DefaultRepositorySystemSession alwaysUpdate =
                                new DefaultRepositorySystemSession(session.getRepositorySession());
                        alwaysUpdate.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
                        releasedArtifact = repositorySystem
                                .resolveArtifact(
                                        alwaysUpdate,
                                        new ArtifactRequest(
                                                releasedArtifact, currentProject.getRemoteProjectRepositories(), null))
                                .getArtifact();
                    } catch (ArtifactResolutionException e) {
                        throw new MojoExecutionException(e.getMessage());
                    }

                    long deployDate;
                    if (releasedArtifact.getFile().lastModified() < preResolveDate) {
                        // we can assume that the ArtifactResolver changed the lastModified value
                        deployDate = releasedArtifact.getFile().lastModified();
                    } else {
                        // Use the modified-date from the first entry of the jar as releaseDate
                        JarFile jarFile = new JarFile(releasedArtifact.getFile());
                        JarEntry entry = jarFile.entries().nextElement();

                        deployDate = entry.getTime();
                    }
                    Date releaseDate = new Date(deployDate);

                    publishDateValue = new SimpleDateFormat(publishDateFormat).format(releaseDate);
                } else {
                    // verify that specified publishDate matches the publishDateFormat
                    try {
                        new SimpleDateFormat(publishDateFormat).parse(publishDate);
                    } catch (java.text.ParseException e) {
                        throw new MojoExecutionException(e.getMessage());
                    }
                    publishDateValue = publishDate;
                }

                publishDateChild.setValue(publishDateValue);
                mergedCustom.addChild(publishDateChild);
                releasedModel.setCustom(mergedCustom);

                if (!readsSiteModel21(getSitePluginVersion(releasedProject))) {
                    dropSiteModel21Attributes(releasedModel);
                }
                writeSiteModel(writer, releasedModel, releasedSiteXml);
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (XmlPullParserException e) {
            throw new MojoExecutionException(e.getMessage());
        }

        InvocationRequest request = buildInvokerRequest(releasedProject);

        invoker.setLocalRepositoryDirectory(
                session.getRepositorySession().getLocalRepository().getBasedir());
        invoker.setMavenHome(mavenHome);

        if (getLog().isDebugEnabled()) {
            invoker.getLogger().setThreshold(InvokerLogger.DEBUG);
        } else if (getLog().isInfoEnabled()) {
            invoker.getLogger().setThreshold(InvokerLogger.INFO);
        } else if (getLog().isWarnEnabled()) {
            invoker.getLogger().setThreshold(InvokerLogger.WARN);
        } else if (getLog().isErrorEnabled()) {
            invoker.getLogger().setThreshold(InvokerLogger.ERROR);
        }

        try {
            InvocationResult invocationResult = invoker.execute(request);
            if (invocationResult.getExecutionException() != null) {
                throw new MojoExecutionException(
                        invocationResult.getExecutionException().getMessage(),
                        invocationResult.getExecutionException());
            }
            if (invocationResult.getExitCode() != 0) {
                throw new MojoExecutionException("The site build of the released project failed with exit code "
                        + invocationResult.getExitCode());
            }
        } catch (MavenInvocationException e) {
            throw new MojoExecutionException(e.getMessage());
        }
    }

    private InvocationRequest buildInvokerRequest(MavenProject releasedProject) throws MojoFailureException {
        InvocationRequest request = new DefaultInvocationRequest();
        request.addArg(siteDeploy ? "site-deploy" : "site");
        request.setPomFile(releasedProject.getFile());
        request.setShowErrors(true);
        request.setUserSettingsFile(settingsFile);

        if (arguments != null) {
            try {
                String[] args = CommandLineUtils.translateCommandline(arguments);
                CLIManager cliManager = new CLIManager();
                CommandLine cl = cliManager.parse(args);
                request.setDebug(cl.hasOption(CLIManager.DEBUG));

                // ----------------------------------------------------------------------
                // Profile Activation
                // ----------------------------------------------------------------------
                List<String> profiles = new ArrayList<String>();

                if (cl.hasOption(CLIManager.ACTIVATE_PROFILES)) {
                    String[] profileOptionValues = cl.getOptionValues(CLIManager.ACTIVATE_PROFILES);
                    if (profileOptionValues != null) {
                        for (String profileOptionValue : profileOptionValues) {
                            StringTokenizer profileTokens = new StringTokenizer(profileOptionValue, ",");

                            while (profileTokens.hasMoreTokens()) {
                                profiles.add(profileTokens.nextToken().trim());
                            }
                        }
                    }
                    request.setProfiles(profiles);
                }

                if (cl.hasOption(CLIManager.SET_SYSTEM_PROPERTY)) {
                    Properties userProperties = new Properties();
                    String[] defStrs = cl.getOptionValues(CLIManager.SET_SYSTEM_PROPERTY);

                    if (defStrs != null) {
                        for (String defStr : defStrs) {
                            setCliProperty(defStr, userProperties);
                        }
                    }
                    request.setProperties(userProperties);
                }
            } catch (ParseException e) {
                throw new MojoFailureException("Unsupported option: " + e.getMessage());
            } catch (Exception e) {
                throw new MojoFailureException(e.getMessage());
            }
        }
        return request;
    }

    /**
     * Copies the current project's skin and layout settings into the released project's site descriptor.
     *
     * @param currentModel the current project's own site descriptor, neither inherited nor interpolated
     * @param skin the current project's skin, inheritance included
     * @param releasedModel the released project's site descriptor, changed in place
     * @param mergeBody whether to copy the breadcrumbs, head, footer and links of the body
     * @return the merged {@code custom} element, where the current project's values win; never {@code null}
     */
    static Xpp3Dom mergeSiteModels(SiteModel currentModel, Skin skin, SiteModel releasedModel, boolean mergeBody) {
        releasedModel.setSkin(skin);
        // MOJO-1827: Copy all layout-specific content
        releasedModel.setBannerLeft(currentModel.getBannerLeft());
        releasedModel.setBannerRight(currentModel.getBannerRight());
        releasedModel.setModelEncoding(currentModel.getModelEncoding());
        releasedModel.setName(currentModel.getName());
        releasedModel.setPoweredBy(currentModel.getPoweredBy());
        // the getters never return null: copy only what the current descriptor sets, so that the
        // released site still inherits these from its parent
        releasedModel.setPublishDate(currentModel.isDefaultPublishDate() ? null : currentModel.getPublishDate());
        releasedModel.setVersion(currentModel.isDefaultVersion() ? null : currentModel.getVersion());

        if (mergeBody && currentModel.getBody() != null) {
            if (releasedModel.getBody() == null) {
                releasedModel.setBody(new Body());
            }
            releasedModel.getBody().setBreadcrumbs(currentModel.getBody().getBreadcrumbs());
            releasedModel.getBody().setFooter(currentModel.getBody().getFooter());
            releasedModel.getBody().setHead(currentModel.getBody().getHead());
            releasedModel.getBody().setLinks(currentModel.getBody().getLinks());
        }

        Xpp3Dom mergedCustom =
                Xpp3DomUtils.mergeXpp3Dom((Xpp3Dom) currentModel.getCustom(), (Xpp3Dom) releasedModel.getCustom());

        if (mergedCustom == null) {
            mergedCustom = new Xpp3Dom("custom");
        }
        return mergedCustom;
    }

    /**
     * Writes the site descriptor without the empty {@code publishDate} and {@code version} elements that the writer
     * always emits: the site plugin would read them as set and stop inheriting the parent's values.
     */
    static void writeSiteModel(SiteXpp3Writer writer, SiteModel model, File siteXml) throws IOException {
        StringWriter xml = new StringWriter();
        writer.write(xml, model);
        String content = xml.toString();
        if (model.isDefaultPublishDate()) {
            content = content.replaceFirst("\\s*<publishDate\\s*/>", "");
        }
        if (model.isDefaultVersion()) {
            content = content.replaceFirst("\\s*<version\\s*/>", "");
        }
        try (OutputStream out = new FileOutputStream(siteXml)) {
            out.write(content.getBytes(model.getModelEncoding()));
        }
    }

    private static File resolveSiteDirectory(File basedir, String siteDirectory) {
        File dir = new File(siteDirectory);
        return dir.isAbsolute() ? dir : new File(basedir, siteDirectory);
    }

    private void verifySitePluginVersion(MavenProject releasedProject) throws MojoFailureException {
        ArtifactVersion sitePluginVersion = getSitePluginVersion(releasedProject);
        if (sitePluginVersion == null) {
            getLog().warn("The released project does not set a maven-site-plugin version; the site plugin that Maven "
                    + "binds by default must be based on Doxia 2 (3.20.0 or later)");
        } else {
            verifySitePluginVersion(sitePluginVersion);
        }
    }

    /**
     * The merged site descriptor is written in the 2.0.0 site model, which only a maven-site-plugin based on Doxia 2
     * reads: 3.20.0 or later, or 4.0.0-M13 or later.
     *
     * @param sitePluginVersion the released project's maven-site-plugin version
     * @throws MojoFailureException if that version is based on Doxia 1
     */
    static void verifySitePluginVersion(ArtifactVersion sitePluginVersion) throws MojoFailureException {
        try {
            if (!VersionRange.createFromVersionSpec(DOXIA_2_SITE_PLUGINS).containsVersion(sitePluginVersion)) {
                throw new MojoFailureException("maven-site-plugin:" + sitePluginVersion
                        + " of the released project is based on Doxia 1 and cannot read the re-skinned site"
                        + " descriptor; re-skinning needs maven-site-plugin 3.20.0 or later");
            }
        } catch (InvalidVersionSpecificationException e) {
            throw new MojoFailureException(e.getMessage());
        }
    }

    /**
     * @param sitePluginVersion the released project's maven-site-plugin version, or {@code null} when it sets none
     * @return whether that site plugin reads the 2.1.0 site model; {@code false} when unknown
     */
    static boolean readsSiteModel21(ArtifactVersion sitePluginVersion) {
        try {
            return sitePluginVersion != null
                    && VersionRange.createFromVersionSpec(SITE_MODEL_2_1_SITE_PLUGINS)
                            .containsVersion(sitePluginVersion);
        } catch (InvalidVersionSpecificationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Removes what only the 2.1.0 site model has, so that a site plugin based on Doxia Sitetools 2.0.0 can read the
     * descriptor: its reader is strict and fails on the publishDate attributes added in 2.1.0.
     *
     * @param model the site model to change in place
     */
    static void dropSiteModel21Attributes(SiteModel model) {
        if (!model.isDefaultPublishDate()) {
            PublishDate publishDate = model.getPublishDate();
            publishDate.setValue(null);
            publishDate.setPrefix(null);
            publishDate.setFallbackValue(null);
            publishDate.setFallbackPrefix(null);
        }
    }

    private Xpp3Dom getSitePluginConfiguration(MavenProject releasedProject) {
        Plugin sitePlugin =
                (Plugin) releasedProject.getBuild().getPluginsAsMap().get(MAVEN_SITE_PLUGIN_KEY);
        if (sitePlugin == null) {
            sitePlugin = (Plugin) releasedProject
                    .getBuild()
                    .getPluginManagement()
                    .getPluginsAsMap()
                    .get(MAVEN_SITE_PLUGIN_KEY);
        }
        return (Xpp3Dom) sitePlugin.getConfiguration();
    }

    static ArtifactVersion getSitePluginVersion(MavenProject releasedProject) {
        ArtifactVersion sitePluginVersion = null;
        Plugin sitePlugin =
                (Plugin) releasedProject.getBuild().getPluginsAsMap().get(MAVEN_SITE_PLUGIN_KEY);
        if (sitePlugin == null) {
            sitePlugin = (Plugin) releasedProject
                    .getBuild()
                    .getPluginManagement()
                    .getPluginsAsMap()
                    .get(MAVEN_SITE_PLUGIN_KEY);
        }

        if (sitePlugin != null && sitePlugin.getVersion() != null) {
            sitePluginVersion = new DefaultArtifactVersion(sitePlugin.getVersion());
        }
        return sitePluginVersion;
    }

    private String getConnection(MavenProject mavenProject) throws MojoFailureException {
        if (mavenProject.getScm() == null) {
            throw new MojoFailureException("SCM is not set in your pom.xml.");
        }

        String connection = mavenProject.getScm().getConnection();

        if (connection != null) {
            if (connection.length() > 0) {
                return connection;
            }
        }
        connection = mavenProject.getScm().getDeveloperConnection();

        if (StringUtils.isEmpty(connection)) {
            throw new MojoFailureException("SCM Connection is not set in your pom.xml.");
        }
        return connection;
    }

    private Artifact resolveArtifact(String versionSpec) throws MojoFailureException, MojoExecutionException {
        // Find the newest released version in the range
        VersionRange range;
        try {
            range = VersionRange.createFromVersionSpec(versionSpec);
        } catch (InvalidVersionSpecificationException e) {
            throw new MojoFailureException("Invalid comparison version: " + e.getMessage());
        }

        String extension = currentProject.getArtifact().getArtifactHandler().getExtension();
        Artifact rangeArtifact = new DefaultArtifact(
                currentProject.getGroupId(), currentProject.getArtifactId(), extension, versionSpec);

        getLog().debug("Searching for versions in range: " + range);
        VersionRangeResult rangeResult;
        try {
            rangeResult = repositorySystem.resolveVersionRange(
                    session.getRepositorySession(),
                    new VersionRangeRequest(rangeArtifact, currentProject.getRemoteProjectRepositories(), null));
        } catch (VersionRangeResolutionException e) {
            throw new MojoExecutionException("Error determining previous version: " + e.getMessage(), e);
        }

        List<ArtifactVersion> availableVersions = new ArrayList<>();
        for (Version version : rangeResult.getVersions()) {
            availableVersions.add(new DefaultArtifactVersion(version.toString()));
        }
        filterSnapshots(availableVersions);
        ArtifactVersion version = range.matchVersion(availableVersions);

        if (version == null) {
            throw new MojoFailureException("Unable to find a previous version of the project in the repository");
        }
        getLog().debug("Previous version: " + version);

        return rangeArtifact.setVersion(version.toString());
    }

    private void filterSnapshots(List<ArtifactVersion> versions) {
        for (Iterator<ArtifactVersion> versionIterator = versions.iterator(); versionIterator.hasNext(); ) {
            if ("SNAPSHOT".equals(versionIterator.next().getQualifier())) {
                versionIterator.remove();
            }
        }
    }

    /**
     * Released POMs are often old, so they are built at the validation levels maven-compat used for them, not at
     * the strict default of a project building request.
     */
    private ProjectBuildingRequest newProjectBuildingRequest(int validationLevel, boolean processPlugins) {
        ProjectBuildingRequest request = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        request.setProject(null);
        request.setResolveDependencies(false);
        request.setValidationLevel(validationLevel);
        request.setProcessPlugins(processPlugins);
        request.setRemoteRepositories(currentProject.getRemoteArtifactRepositories());
        return request;
    }

    private void fetchSources(File checkoutDir, MavenProject mavenProject) throws MojoExecutionException {
        try {
            if (forceCheckout && checkoutDir.exists()) {
                FileUtils.deleteDirectory(checkoutDir);
            }

            if (checkoutDir.mkdirs()) {

                getLog().info("Performing checkout to " + checkoutDir);

                new ScmCommandExecutor(scmManager, getConnection(mavenProject), getLog())
                        .checkout(checkoutDir.getPath());
            } else {
                getLog().info("Performing update to " + checkoutDir);

                new ScmCommandExecutor(scmManager, getConnection(mavenProject), getLog()).update(checkoutDir.getPath());
            }
        } catch (Exception ex) {
            throw new MojoExecutionException("checkout failed.", ex);
        }
    }

    private static void setCliProperty(String property, Properties properties) {
        String name;

        String value;

        int i = property.indexOf("=");

        if (i <= 0) {
            name = property.trim();

            value = "true";
        } else {
            name = property.substring(0, i).trim();

            value = property.substring(i + 1);
        }

        properties.setProperty(name, value);
    }
}
