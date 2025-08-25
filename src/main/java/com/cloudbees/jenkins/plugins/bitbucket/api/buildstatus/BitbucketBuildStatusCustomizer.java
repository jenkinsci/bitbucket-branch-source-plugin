/*
 * The MIT License
 *
 * Copyright (c) 2025, Nikolas Falco
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.plugins.bitbucket.api.buildstatus;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketBuildStatus;
import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.EndpointType;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.Run;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMTrait;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

public interface BitbucketBuildStatusCustomizer extends ExtensionPoint {

    /**
     * Returns if this implementation supports the given endpoint type.
     *
     * @param type of the endpoint
     * @return {@code true} if this implementation can manage API for this
     *         endpoint, {@code false} otherwise.
     */
    boolean isApplicable(@NonNull EndpointType type);

    /**
     * Trait instance associate to a {@link SCMSource} where gather extra
     * configuration options.
     * <p>
     * Each {@link BitbucketBuildStatusCustomizer} that would obtain additional
     * configuration options per project must provide an own specific trait
     * implementation.
     *
     * @param trait to apply
     */
    default void apply(SCMSourceTrait trait) {}

    /**
     * A list of traits class that this manager supports to obtain additional
     * configuration options.
     *
     * @return a list of {@link SCMSourceTrait} classes.
     */
    default Collection<Class<? extends SCMSourceTrait>> supportedTraits() {
        return Collections.emptyList();
    }

    /**
     * Apply some customisations to a given build status.
     * <p>
     * Any additional information must be supplied in the
     * {@link BitbucketBuildStatus#getOptionalData()}. For tracing reason any
     * changes if applied by the customizer it will be logged in the console.
     *
     * @param buildStatus to customise
     * @param build current {@link Run} job.
     */
    @Restricted(Beta.class)
    @CheckForNull
    void customize(Run<?, ?> build, @NonNull BitbucketBuildStatus buildStatus);

    /**
     * Convenient method to apply only supported traits to this customiser.
     *
     * @param traits to apply if supported too
     */
    default void withTraits(List<SCMSourceTrait> traits) {
        supportedTraits().forEach(traitClass -> {
            SCMSourceTrait trait = SCMTrait.find(traits, traitClass);
            if (trait != null) {
                apply(trait);
            }
        });
    }

}
