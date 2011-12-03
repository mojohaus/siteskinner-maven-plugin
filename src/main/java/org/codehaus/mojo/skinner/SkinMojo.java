package org.codehaus.mojo.skinner;

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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.doxia.site.decoration.DecorationModel;
import org.apache.maven.doxia.site.decoration.io.xpp3.DecorationXpp3Reader;
import org.apache.maven.doxia.site.decoration.io.xpp3.DecorationXpp3Writer;
import org.apache.maven.doxia.tools.SiteTool;
import org.apache.maven.doxia.tools.SiteToolException;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Call <code>mvn skinner:skin</code> on a maven project. This will check out the latest releases project. Next it will
 * add/replace the skin of the site.xml with the skin of the current project. Finally it will invoke a
 * <code>mvn site</code> on the checked out project. Now you can verify the pages and run a <code>mvn site-deploy</code>
 * on the checked out project.
 * 
 * @goal skin
 */
public class SkinMojo
    extends AbstractMojo
{
    private static final String MAVEN_SITE_PLUGIN_KEY = "org.apache.maven.plugins:maven-site-plugin";

    /**
     * Force a checkout instead of an update when the sources have already been checked out during a previous run.
     * 
     * @parameter expression="${forceCheckout}" default-value="false"
     */
    private boolean forceCheckout;

    /**
     * @parameter default-value="(,${project.version})"
     * @readonly
     */
    private String releasedVersion;

    /**
     * The working directory for this plugin.
     * 
     * @parameter default-value="${project.build.directory}/skinner"
     * @readonly
     */
    private File workingDirectory;

    /**
     * The reactor projects.
     * 
     * @parameter default-value="${reactorProjects}"
     * @required
     * @readonly
     */
    private List<MavenProject> reactorProjects;

    /**
     * Specifies the input encoding.
     * 
     * @parameter expression="${encoding}" default-value="${project.build.sourceEncoding}"
     */
    private String inputEncoding;

    /**
     * Specifies the output encoding.
     * 
     * @parameter expression="${outputEncoding}" default-value="${project.reporting.outputEncoding}"
     */
    private String outputEncoding;

    /**
     * Gets the input files encoding.
     * 
     * @return The input files encoding, never <code>null</code>.
     */
    private String getInputEncoding()
    {
        return ( inputEncoding == null ) ? ReaderFactory.ISO_8859_1 : inputEncoding;
    }

    /**
     * Gets the effective reporting output files encoding.
     * 
     * @return The effective reporting output file encoding, never <code>null</code>.
     */
    private String getOutputEncoding()
    {
        return ( outputEncoding == null ) ? ReaderFactory.UTF_8 : outputEncoding;
    }

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject currentProject;

    /**
     * The local repository where the artifacts are located.
     * 
     * @parameter expression="${localRepository}"
     * @readonly
     * @required
     */
    private ArtifactRepository localRepository;

    /**
     * The remote repositories where artifacts are located.
     * 
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @readonly
     * @required
     */
    private List<ArtifactRepository> remoteRepositories;

    /** @component */
    private MavenProjectBuilder mavenProjectBuilder;

    /** @component */
    private ScmManager scmManager;

    /** @component */
    private ArtifactMetadataSource metadataSource;

    /** @component */
    private ArtifactFactory factory;

    /** @component */
    private SiteTool siteTool;

    /** @component */
    private Invoker invoker;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        MavenProject releasedProject;
        try
        {
            releasedProject = resolveProject( releasedVersion );
        }
        catch ( ProjectBuildingException e )
        {
            throw new MojoExecutionException( e.getMessage() );
        }

        Xpp3Dom releasedConfig = getSitePluginConfiguration( releasedProject );
        String releasedSiteDirectory =
            releasedConfig.getChild( "siteDirectory" ) == null ? "src/site"
                            : releasedConfig.getChild( "siteDirectory" ).getValue();
        String releasedLocales =
            releasedConfig.getChild( "locales" ) == null ? null : releasedConfig.getChild( "locales" ).getValue();

        Xpp3Dom currentConfig = getSitePluginConfiguration( currentProject );
        String currentSiteDirectory =
            currentConfig.getChild( "siteDirectory" ) == null ? "src/site"
                            : currentConfig.getChild( "siteDirectory" ).getValue();
        try
        {
            DecorationXpp3Writer writer = new DecorationXpp3Writer();
            DecorationXpp3Reader reader = new DecorationXpp3Reader();

            for ( Locale locale : siteTool.getAvailableLocales( releasedLocales ) )
            {
                DecorationModel currentModel =
                    siteTool.getDecorationModel( currentProject, reactorProjects, localRepository, remoteRepositories,
                                                 currentSiteDirectory, locale, getInputEncoding(), getOutputEncoding() );

                File releasedSiteXml =
                    siteTool.getSiteDescriptorFromBasedir( releasedSiteDirectory, releasedProject.getBasedir(), locale );

                DecorationModel releasedModel = null;
                if ( releasedSiteXml.exists() )
                {
                    releasedModel = reader.read( new FileInputStream( releasedSiteXml ) );
                }
                releasedModel.setSkin( currentModel.getSkin() );

                writer.write( new FileOutputStream( releasedSiteXml ), releasedModel );
            }
        }
        catch ( SiteToolException e )
        {
            throw new MojoExecutionException( e.getMessage() );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( e.getMessage() );
        }
        catch ( XmlPullParserException e )
        {
            throw new MojoExecutionException( e.getMessage() );
        }

        InvocationRequest request = new DefaultInvocationRequest();
        request.setGoals( Collections.singletonList( "site" ) );
        request.setPomFile( releasedProject.getFile() );
        try
        {
            InvocationResult invocationResult = invoker.execute( request );
            if ( invocationResult.getExitCode() != 0 )
            {
                throw new MojoExecutionException( invocationResult.getExecutionException().getMessage() );
            }
        }
        catch ( MavenInvocationException e )
        {
            throw new MojoExecutionException( e.getMessage() );
        }
    }

    private Xpp3Dom getSitePluginConfiguration( MavenProject releasedProject )
    {
        Plugin sitePlugin = (Plugin) releasedProject.getBuild().getPluginsAsMap().get( MAVEN_SITE_PLUGIN_KEY );
        if ( sitePlugin == null )
        {
            sitePlugin =
                (Plugin) releasedProject.getBuild().getPluginManagement().getPluginsAsMap().get( MAVEN_SITE_PLUGIN_KEY );
        }
        return (Xpp3Dom) sitePlugin.getConfiguration();
    }

    private MavenProject resolveProject( String versionSpec )
        throws MojoFailureException, MojoExecutionException, ProjectBuildingException
    {
        MavenProject result;
        Artifact artifact = resolveArtifact( versionSpec );
        MavenProject externalProject =
            mavenProjectBuilder.buildFromRepository( artifact, remoteRepositories, localRepository );

        fetchSources( workingDirectory, externalProject );

        result = mavenProjectBuilder.build( new File( workingDirectory, "pom.xml" ), localRepository, null );
        return result;
    }

    private String getConnection( MavenProject mavenProject )
        throws MojoFailureException
    {
        if ( mavenProject.getScm() == null )
        {
            throw new MojoFailureException( "SCM Connection is not set in your pom.xml." );
        }

        String connection = mavenProject.getScm().getConnection();

        if ( connection != null )
        {
            if ( connection.length() > 0 )
            {
                return connection;
            }
        }
        connection = mavenProject.getScm().getDeveloperConnection();

        if ( StringUtils.isEmpty( connection ) )
        {
            throw new MojoFailureException( "SCM Connection is not set in your pom.xml." );
        }
        return connection;
    }

    private Artifact resolveArtifact( String versionSpec )
        throws MojoFailureException, MojoExecutionException
    {
        // Find the previous version JAR and resolve it, and it's dependencies
        VersionRange range;
        try
        {
            range = VersionRange.createFromVersionSpec( versionSpec );
        }
        catch ( InvalidVersionSpecificationException e )
        {
            throw new MojoFailureException( "Invalid comparison version: " + e.getMessage() );
        }

        Artifact previousArtifact;
        try
        {
            previousArtifact =
                factory.createDependencyArtifact( currentProject.getGroupId(), currentProject.getArtifactId(), range,
                                                  currentProject.getPackaging(), null, Artifact.SCOPE_COMPILE );

            if ( !previousArtifact.getVersionRange().isSelectedVersionKnown( previousArtifact ) )
            {
                getLog().debug( "Searching for versions in range: " + previousArtifact.getVersionRange() );
                List<ArtifactVersion> availableVersions =
                    metadataSource.retrieveAvailableVersions( previousArtifact, localRepository,
                                                              currentProject.getRemoteArtifactRepositories() );
                filterSnapshots( availableVersions );
                ArtifactVersion version = range.matchVersion( availableVersions );
                if ( version != null )
                {
                    previousArtifact.selectVersion( version.toString() );
                }
            }
        }
        catch ( OverConstrainedVersionException e1 )
        {
            throw new MojoFailureException( "Invalid comparison version: " + e1.getMessage() );
        }
        catch ( ArtifactMetadataRetrievalException e11 )
        {
            throw new MojoExecutionException( "Error determining previous version: " + e11.getMessage(), e11 );
        }

        if ( previousArtifact.getVersion() == null )
        {
            getLog().info( "Unable to find a previous version of the project in the repository" );
        }
        else
        {
            getLog().debug( "Previous version: " + previousArtifact.getVersion() );
        }

        return previousArtifact;
    }

    private void filterSnapshots( List<ArtifactVersion> versions )
    {
        for ( Iterator<ArtifactVersion> versionIterator = versions.iterator(); versionIterator.hasNext(); )
        {
            if ( "SNAPSHOT".equals( versionIterator.next().getQualifier() ) )
            {
                versionIterator.remove();
            }
        }
    }

    private void fetchSources( File checkoutDir, MavenProject mavenProject )
        throws MojoExecutionException
    {
        try
        {
            if ( forceCheckout && checkoutDir.exists() )
            {
                FileUtils.deleteDirectory( checkoutDir );
            }

            if ( checkoutDir.mkdirs() )
            {

                getLog().info( "Performing checkout to " + checkoutDir );

                new ScmCommandExecutor( scmManager, getConnection( mavenProject ), getLog() ).checkout( checkoutDir.getPath() );
            }
            else
            {
                getLog().info( "Performing update to " + checkoutDir );

                new ScmCommandExecutor( scmManager, getConnection( mavenProject ), getLog() ).update( checkoutDir.getPath() );
            }
        }
        catch ( Exception ex )
        {
            throw new MojoExecutionException( "checkout failed.", ex );
        }
    }
}