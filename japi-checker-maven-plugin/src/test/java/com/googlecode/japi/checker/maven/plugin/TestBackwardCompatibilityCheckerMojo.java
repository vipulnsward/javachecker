/*
 * Copyright 2011 William Bernardet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.japi.checker.maven.plugin;

import java.io.File;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;

public class TestBackwardCompatibilityCheckerMojo extends AbstractMojoTestCase {
    private BackwardCompatibilityCheckerMojo mojo;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ArtifactRepositoryLayout localRepositoryLayout = (ArtifactRepositoryLayout) lookup( ArtifactRepositoryLayout.ROLE, "default" );
        String path = "src/test/repository";
        ArtifactRepository localRepository = new DefaultArtifactRepository( "local", "file://" + new File( path ).getAbsolutePath(), localRepositoryLayout );
                
        mojo = (BackwardCompatibilityCheckerMojo)this.lookupMojo("check",
                new File("target/test-classes/unit/plugin-config.xml"));
        MavenProjectStub project = new MavenProjectStub();
        project.setGroupId("com.googlecode.japi-checker");
        project.setArtifactId("reference-test-jar");
        project.setVersion("0.1.1-SNAPSHOT");

        setVariableValueToObject(mojo, "project", project);
        setVariableValueToObject(mojo, "localRepository", localRepository);
    }
    
    public void testValidationWithSameJar() throws MojoExecutionException, IllegalAccessException, MojoFailureException {
        ArtifactStub artifact = new ArtifactStub();
        artifact.setGroupId(mojo.getProject().getGroupId());
        artifact.setArtifactId(mojo.getProject().getArtifactId());
        artifact.setVersion(mojo.getProject().getVersion());
        artifact.setType("jar");
        artifact.setScope(Artifact.SCOPE_RUNTIME);
        artifact.setFile(new File( "src/test/repository/com/googlecode/japi-checker/reference-test-jar/0.1.0/reference-test-jar-0.1.0.jar"));
        mojo.getProject().setArtifact(artifact);
        setVariableValueToObject(mojo, "artifact", artifact);
        mojo.execute();
    }
    
    public void testValidationWithNewJar() throws MojoExecutionException, IllegalAccessException, MojoFailureException {
        
        ArtifactStub artifact = new ArtifactStub();
        artifact.setGroupId(mojo.getProject().getGroupId());
        artifact.setArtifactId(mojo.getProject().getArtifactId());
        artifact.setVersion(mojo.getProject().getVersion());
        artifact.setType("jar");
        artifact.setScope(Artifact.SCOPE_RUNTIME);
        artifact.setFile(new File("src/test/repository/com/googlecode/japi-checker/reference-test-jar/0.1.1-SNAPSHOT/reference-test-jar-0.1.1-SNAPSHOT.jar"));
        mojo.getProject().setArtifact(artifact);
        setVariableValueToObject(mojo, "artifact", artifact);

        try {
            mojo.execute();
            fail("The validation must fail.");
        } catch (MojoFailureException e) {
            // should be there
        }

    }
}
