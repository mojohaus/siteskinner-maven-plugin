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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.doxia.site.decoration.Body;
import org.apache.maven.doxia.site.decoration.DecorationModel;
import org.apache.maven.doxia.site.decoration.PublishDate;
import org.apache.maven.doxia.site.decoration.io.xpp3.DecorationXpp3Reader;
import org.apache.maven.doxia.site.decoration.io.xpp3.DecorationXpp3Writer;
import org.apache.maven.doxia.tools.SiteTool;
import org.apache.maven.execution.RuntimeInformation;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
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
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Call <code>mvn siteskinner:skin</code> on a maven project. This will check out the latest releases project. Next it
 * will add/replace the skin of the site.xml with the skin of the current project. Finally it will invoke a
 * <code>mvn site</code> on the checked out project. Now you can verify the pages and run <code>mvn site:deploy</code>
 * on the checked out project.
 */
@Mojo( name = "skin", requiresDirectInvocation = true, aggregator = true )
public class SkinMojo
    extends AbstractMojo
{
    private static final String MAVEN_SITE_PLUGIN_KEY = "org.apache.maven.plugins:maven-site-plugin";

    /**
     * Force a checkout instead of an update when the sources have already been checked out during a previous run.
     */
    @Parameter( property = "forceCheckout", defaultValue = "false" )
    private boolean forceCheckout;
    
    /**
     * If {@code true}, all the elements of the body in the {@code site.xml} will be merged, except the menu items.
     * Set to {@false} if you don't want to merge the body.
     */
    @Parameter( property = "mergeBody", defaultValue = "true" )
    private boolean mergeBody;

    /**
     * If {@code false} the plugin should only generate the site, 
     * else if {@code true} the site should be published immediately too.
     */
    @Parameter( property = "siteDeploy", defaultValue = "false" )
    private boolean siteDeploy;

    /**
     * Versionrange to calculate latest released version
     */
    @Parameter( defaultValue = "(,${project.version})", readonly = true )
    private String releasedVersion;

    /**
     * The working directory for this plugin.
     */
    @Parameter( defaultValue = "${project.build.directory}/siteskinner", readonly = true )
    private File workingDirectory;

    @Component
    private MavenProject currentProject;

    /**
     * The local repository where the artifacts are located.
     */
    @Parameter ( defaultValue="${localRepository}", readonly = true, required = true )
    private ArtifactRepository localRepository;

    /**
     * The remote repositories where artifacts are located.
     */
    @Parameter( defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true ) 
    private List<ArtifactRepository> remoteRepositories;
    
    @Component
    private RuntimeInformation runtimeInformation;

    @Component
    private MavenProjectBuilder mavenProjectBuilder;

    @Component
    private ScmManager scmManager;

    @Component
    private ArtifactMetadataSource metadataSource;

    @Component
    private ArtifactFactory factory;

    @Component
    private ArtifactResolver resolver;

    @Component
    private SiteTool siteTool;

    @Component
    private Invoker invoker;

    /** {@inheritDoc} */
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        MavenProject releasedProject;
        Artifact releasedArtifact;
        try
        {
            releasedArtifact = resolveArtifact( releasedVersion );
            MavenProject externalProject =
                mavenProjectBuilder.buildFromRepository( releasedArtifact, remoteRepositories, localRepository );

            fetchSources( workingDirectory, externalProject );

            releasedProject =
                mavenProjectBuilder.build( new File( workingDirectory, "pom.xml" ), localRepository, null );
        }
        catch ( ProjectBuildingException e )
        {
            throw new MojoExecutionException( e.getMessage() );
        }
        
        verifyVersionCompatibility( releasedProject );

        Xpp3Dom releasedConfig = getSitePluginConfiguration( releasedProject );
        String releasedSiteDirectory =
            releasedConfig == null || releasedConfig.getChild( "siteDirectory" ) == null ? "src/site"
                            : releasedConfig.getChild( "siteDirectory" ).getValue();
        String releasedLocales = ( releasedConfig == null || releasedConfig.getChild( "locales" ) == null 
            ? null : releasedConfig.getChild( "locales" ).getValue() );

        Xpp3Dom currentConfig = getSitePluginConfiguration( currentProject );
        String currentSiteDirectory =
            currentConfig == null || currentConfig.getChild( "siteDirectory" ) == null ? "src/site"
                            : currentConfig.getChild( "siteDirectory" ).getValue();
        try
        {
            DecorationXpp3Writer writer = new DecorationXpp3Writer();
            DecorationXpp3Reader reader = new DecorationXpp3Reader();

            for ( Locale locale : siteTool.getAvailableLocales( releasedLocales ) )
            {
                File currentSiteXml =
                    siteTool.getSiteDescriptorFromBasedir( currentSiteDirectory, currentProject.getBasedir(), locale );
                
                if ( !currentSiteXml.exists() )
                {
                    throw new MojoFailureException( "No 'site.xml' defined, can't apply a new skin on the old site." );
                }
                
                DecorationModel currentModel = readDecorationModel( reader, currentSiteXml );

                if ( currentModel.getSkin() == null )
                {
                    throw new MojoFailureException(
                                "No skin defined in the current 'site.xml', can't apply a new skin on the old site." );
                }

                File releasedSiteXml =
                  siteTool.getSiteDescriptorFromBasedir( releasedSiteDirectory, releasedProject.getBasedir(), locale );

                DecorationModel releasedModel = null;
                if ( releasedSiteXml.exists() )
                {
                    releasedModel = readDecorationModel( reader, releasedSiteXml );
                }
                else
                {
                    // already create folders to be sure we can write to this file
                    releasedSiteXml.getParentFile().mkdirs();
                    releasedModel = new DecorationModel();
                }
                
                // MOJO-1827: Copy all layout-specific content
                releasedModel.setBannerLeft( currentModel.getBannerLeft() );
                releasedModel.setBannerRight( currentModel.getBannerRight() );
                releasedModel.setGoogleAnalyticsAccountId( currentModel.getGoogleAnalyticsAccountId() );
                releasedModel.setModelEncoding( currentModel.getModelEncoding() );
                releasedModel.setName( currentModel.getName() );
                releasedModel.setPoweredBy( currentModel.getPoweredBy() );
                releasedModel.setPublishDate( currentModel.getPublishDate() );
                releasedModel.setSkin( currentModel.getSkin() );
                releasedModel.setVersion( currentModel.getVersion() );

                if ( mergeBody && currentModel.getBody() != null )
                {
                    if ( releasedModel.getBody() == null )
                    {
                        releasedModel.setBody( new Body() );
                    }
                    releasedModel.getBody().setBreadcrumbs( currentModel.getBody().getBreadcrumbs() );
                    releasedModel.getBody().setFooter( currentModel.getBody().getFooter() );
                    releasedModel.getBody().setHead( currentModel.getBody().getHead() );
                    releasedModel.getBody().setLinks( currentModel.getBody().getLinks() );
                }    

                Xpp3Dom mergedCustom =
                  Xpp3DomUtils.mergeXpp3Dom( (Xpp3Dom) currentModel.getCustom(), (Xpp3Dom) releasedModel.getCustom() );
                
                if ( mergedCustom == null )
                {
                    mergedCustom = new Xpp3Dom( "custom" );
                }
                
                Xpp3Dom publishDateChild = new Xpp3Dom( "publishDate" );
                
                long preResolveDate = System.currentTimeMillis();

                try
                {
                    resolver.resolveAlways( releasedArtifact, remoteRepositories, localRepository );
                }
                catch ( ArtifactResolutionException e )
                {
                    throw new MojoExecutionException( e.getMessage() );
                }
                catch ( ArtifactNotFoundException e )
                {
                    throw new MojoExecutionException( e.getMessage() );
                }

                long deployDate;
                if ( releasedArtifact.getFile().lastModified() < preResolveDate )
                {
                    //we can assume that the ArtifactResolver changed the lastModified value
                    deployDate = releasedArtifact.getFile().lastModified();
                }
                else
                {
                 // Use the modified-date from the first entry of the jar as releaseDate
                    JarFile jarFile = new JarFile( releasedArtifact.getFile() );
                    JarEntry entry = jarFile.entries().nextElement();   
                    
                    deployDate = entry.getTime();
                }

                Date releaseDate = new Date( deployDate );
                String publishDateFormat;
                if ( releasedModel.getPublishDate() != null )
                {
                    publishDateFormat = releasedModel.getPublishDate().getFormat();
                }
                else
                {
                    publishDateFormat = new PublishDate().getFormat();
                }
                publishDateChild.setValue( new SimpleDateFormat( publishDateFormat ).format( releaseDate ) );
                mergedCustom.addChild( publishDateChild );
                releasedModel.setCustom( mergedCustom );

                FileOutputStream fileOutputStream = new FileOutputStream( releasedSiteXml );
                try
                {
                    writer.write( fileOutputStream, releasedModel );
                }
                finally
                {
                    IOUtil.close( fileOutputStream );
                }
                
            }
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
        request.setGoals( Collections.singletonList( siteDeploy ? "site-deploy" : "site" ) );
        request.setPomFile( releasedProject.getFile() );
        request.setShowErrors( true );
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

    private DecorationModel readDecorationModel( DecorationXpp3Reader reader, File currentSiteXml )
        throws IOException, XmlPullParserException
    {
        DecorationModel currentModel;
        FileInputStream fileInputStream = new FileInputStream( currentSiteXml );
        try
        {
            currentModel = reader.read( fileInputStream, false );
        }
        finally
        {
            IOUtil.close( fileInputStream );
        }
        return currentModel;
    }

    /**
     * Verify is the Maven version can be used with the specified maven-site-plugin version, 
     *   since the maven-site-plugin is not compatible with every Maven version.
     * 
     * @param releasedProject
     * @throws MojoFailureException
     */
    private void verifyVersionCompatibility( MavenProject releasedProject )
        throws MojoFailureException
    {
        //MOJO-1825: verify site-plugin-version with maven-version
        ArtifactVersion sitePluginVersion = getSitePluginVersion( releasedProject );
        ArtifactVersion mavenVersion = getMavenVersion();
        
        if ( sitePluginVersion != null )
        {
            try
            {
                if ( VersionRange.createFromVersionSpec( "(,3.0-alpha-1)" ).containsVersion( sitePluginVersion )
                    && VersionRange.createFromVersionSpec( "[3.0,)" ).containsVersion( mavenVersion ) )
                {
                    throw new MojoFailureException( "maven-site-plugin:" + sitePluginVersion
                        + " can only be executed with Maven 2.x" );
                }
                else if ( VersionRange.createFromVersionSpec( "[3.0-alpha-1,3.0)" ).containsVersion( sitePluginVersion )
                    && VersionRange.createFromVersionSpec( "(, 3.0)" ).containsVersion( mavenVersion ) )
                {
                    throw new MojoFailureException( "maven-site-plugin:" + sitePluginVersion
                        + " can only be executed with Maven 3.x+" );
                }
            }
            catch ( InvalidVersionSpecificationException e )
            {
                throw new MojoFailureException( e.getMessage() );
            }
        }
    }

    private Xpp3Dom getSitePluginConfiguration( MavenProject releasedProject )
    {
        Plugin sitePlugin = (Plugin) releasedProject.getBuild().getPluginsAsMap().get( MAVEN_SITE_PLUGIN_KEY );
        if ( sitePlugin == null )
        {
            sitePlugin = (Plugin) 
               releasedProject.getBuild().getPluginManagement().getPluginsAsMap().get( MAVEN_SITE_PLUGIN_KEY );
        }
        return (Xpp3Dom) sitePlugin.getConfiguration();
    }
    
    private ArtifactVersion getSitePluginVersion( MavenProject releasedProject )
    {
        ArtifactVersion sitePluginVersion = null;
        Plugin sitePlugin = (Plugin) releasedProject.getBuild().getPluginsAsMap().get( MAVEN_SITE_PLUGIN_KEY );
        if ( sitePlugin == null )
        {
            sitePlugin = (Plugin) 
               releasedProject.getBuild().getPluginManagement().getPluginsAsMap().get( MAVEN_SITE_PLUGIN_KEY );
        }

        if ( sitePlugin != null && sitePlugin.getVersion() != null )
        {
            sitePluginVersion = new DefaultArtifactVersion( sitePlugin.getVersion() ); 
        }
        return sitePluginVersion;
    }
    
    private ArtifactVersion getMavenVersion()
    {
       return runtimeInformation.getApplicationVersion();   
    }

    private String getConnection( MavenProject mavenProject )
        throws MojoFailureException
    {
        if ( mavenProject.getScm() == null )
        {
            throw new MojoFailureException( "SCM is not set in your pom.xml." );
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

                new ScmCommandExecutor( scmManager, getConnection( mavenProject ), getLog() )
                    .checkout( checkoutDir.getPath() );
            }
            else
            {
                getLog().info( "Performing update to " + checkoutDir );

                new ScmCommandExecutor( scmManager, getConnection( mavenProject ), getLog() )
                    .update( checkoutDir.getPath() );
            }
        }
        catch ( Exception ex )
        {
            throw new MojoExecutionException( "checkout failed.", ex );
        }
    }
}