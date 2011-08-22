/*
 * Copyright 2011 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.japi.checker.maven.plugin;


import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import com.googlecode.japi.checker.BCChecker;
import com.googlecode.japi.checker.MuxReporter;
import com.googlecode.japi.checker.Reporter;
import com.googlecode.japi.checker.Rule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Goal which touches a timestamp file.
 *
 * @goal check
 * 
 * @phase verify
 */
public class BackwardCompatibilityCheckerMojo
    extends AbstractMojo
{
    /**
     * Location of the file.
     * @parameter expression="${project.build.directory}"
     * @required
     */
    private File outputDirectory;

    /**
     * @parameter default-value="${project.artifact}"
     * @required
     * @readonly
     */
    private Artifact artifact;
    
    /**
     * @parameter
     * @required
     */
    private List<String> rules;
    
    /**
     * Reference version
     * @parameter
     * @required
     */
    private ArtifactItem reference;

    /** @parameter default-value="${project}" */
    private MavenProject project;

    /**
     * Used to look up Artifacts in the remote repository.
     *
     * @component
     */
    private ArtifactFactory factory;
    
    /**
     * Used to look up Artifacts in the remote repository.
     *
     * @component
     */
    private ArtifactResolver resolver;

    /**@parameter default-value="${localRepository}" */
    private org.apache.maven.artifact.repository.ArtifactRepository localRepository;

    /**
     * {@inheritDoc}
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (artifact == null) {
            throw new MojoExecutionException("Artifact is null.");
        }
        if (!"jar".equals(artifact.getType())) {
            throw new MojoExecutionException("The artifact is not of type jar.");
        }
        
        if (artifact.getFile() != null && artifact.getFile().exists()) {
            
            // Retrieving the reference artifact.
            updateArtifact(reference);
            Artifact referenceArtifact = reference.getArtifact();

            // Creating a new checker which compare the generated artifact against the provided reference.
            BCChecker checker = new BCChecker(referenceArtifact.getFile(), artifact.getFile());

            // configuring the reporting redirection
            MuxReporter mux = new MuxReporter();
            mux.add(new LogReporter(this.getLog()));
            ErrorCountReporter ec = new ErrorCountReporter();
            mux.add(ec);
            
            try {
                // Running the check...
                this.getLog().info("Checking backward compatibility of " + artifact.toString() + " against " + referenceArtifact.toString());
                checker.checkBacwardCompatibility(mux, getRuleInstances());
                if (ec.hasError()) {
                    getLog().error("You have " + ec.getCount() + " backward compatibility issues.");
                    throw new MojoFailureException("You have " + ec.getCount() + " backward compatibility issues.");
                } else {
                    getLog().info("No backward compatibility issue found.");
                }
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        } else {
            throw new MojoExecutionException("Could not find the artifact: " + artifact.toString());
        }
        
    }

    private List<Rule> getRuleInstances() throws MojoExecutionException {
        List<Rule> rules = new ArrayList<Rule>();
        for (String classname : this.rules) {
            try {
                @SuppressWarnings("unchecked")
                Class<Rule> clazz = (Class<Rule>)this.getClass().getClassLoader().loadClass(classname);
                rules.add(clazz.newInstance());
            } catch (ClassNotFoundException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } catch (InstantiationException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } catch (IllegalAccessException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
            
        }
        return rules;
    }
    
    /**
     * @return Returns the factory.
     */
    public ArtifactFactory getFactory()
    {
        return this.factory;
    }

    /**
     * @param factory The factory to set.
     */
    public void setFactory( ArtifactFactory factory )
    {
        this.factory = factory;
    }

    /**
     * @return Returns the resolver.
     */
    public ArtifactResolver getResolver()
    {
        return this.resolver;
    }

    /**
     * @param resolver The resolver to set.
     */
    public void setResolver( ArtifactResolver resolver )
    {
        this.resolver = resolver;
    }

    /**
     * Resolves the Artifact from the remote repository if necessary. If no version is specified, it will be retrieved
     * from the dependency list or from the DependencyManagement section of the pom.
     * 
     * @param artifactItem containing information about artifact from plugin configuration.
     * @return Artifact object representing the specified file.
     * @throws MojoExecutionException with a message if the version can't be found in DependencyManagement.
     */
    protected void updateArtifact( ArtifactItem artifactItem )
        throws MojoExecutionException {
        
        if (artifactItem.getArtifact() != null) {
            return;
        }
        

        VersionRange vr;
        try {
            vr = VersionRange.createFromVersionSpec( artifactItem.getVersion() );
        } catch ( InvalidVersionSpecificationException e ) {
            vr = VersionRange.createFromVersion( artifactItem.getVersion() );
        }

        Artifact artifact = getFactory().createDependencyArtifact(artifactItem.getGroupId(), artifactItem.getArtifactId(), vr,
                                                  artifactItem.getType(), null, Artifact.SCOPE_COMPILE);

        try {
            getResolver().resolve(artifact, project.getRepositories(), localRepository);
        } catch ( ArtifactResolutionException e ) {
            throw new MojoExecutionException( "Unable to resolve artifact.", e );
        } catch ( ArtifactNotFoundException e ) {
            throw new MojoExecutionException( "Unable to find artifact.", e );
        }

        artifactItem.setArtifact(artifact);
    }

    public void setProject(MavenProject project) {
        this.project = project;
    }

    public MavenProject getProject() {
        return this.project;
    }
    
    class ErrorCountReporter implements Reporter {
        private int count;

        public void report(Report report) {
            if (Level.ERROR == report.level) {
                count++;
            }
        }
        
        public int getCount() {
            return count;
        }

        public boolean hasError() {
            return count > 0;
        }
        
    }
}
