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
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;

/**
 * Based on the SelectorUtils from the maven-invoker-plugin
 * 
 * @author Robert Scholte
 * @since 1.1
 */
public final class SelectorUtils
{

    private SelectorUtils()
    {
    }
    
    /**
     * Retrieves the current Maven version.
     * @return The current Maven version.
     */
    static String getMavenVersion()
    {
        try
        {
            // This relies on the fact that MavenProject is the in core classloader
            // and that the core classloader is for the maven-core artifact
            // and that should have a pom.properties file
            // if this ever changes, we will have to revisit this code.
            Properties properties = new Properties();
            properties.load( MavenProject.class.getClassLoader().getResourceAsStream(
                "META-INF/maven/org.apache.maven/maven-core/pom.properties" ) );
            return StringUtils.trim( properties.getProperty( "version" ) );
        }
        catch ( Exception e )
        {
            return null;
        }
    }
    
    static String getMavenVersion( File mavenHome )
    {
        File mavenLib = new File( mavenHome, "lib" );
        File[] jarFiles = mavenLib.listFiles( new FilenameFilter()
        {
            public boolean accept( File dir, String name )
            {
                return name.endsWith( ".jar" );
            }
        } );

        for ( File file : jarFiles )
        {
            try
            {
                @SuppressWarnings( "deprecation" )
                URL url =
                    new URL( "jar:" + file.toURL().toExternalForm()
                        + "!/META-INF/maven/org.apache.maven/maven-core/pom.properties" );

                Properties properties = new Properties();
                properties.load( url.openStream() );
                String version = StringUtils.trim( properties.getProperty( "version" ) );
                if ( version != null )
                {
                    return version;
                }
            }
            catch ( MalformedURLException e )
            {
                // ignore
            }
            catch ( IOException e )
            {
                // ignore
            }
        }
        return null;
    }
}
