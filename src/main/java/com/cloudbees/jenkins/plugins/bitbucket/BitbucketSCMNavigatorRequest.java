/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package com.cloudbees.jenkins.plugins.bitbucket;

import com.cloudbees.jenkins.plugins.bitbucket.api.BitbucketRepository;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.trait.SCMNavigatorRequest;

/**
 * The {@link SCMNavigatorRequest} for bitbucket.
 *
 * @since 2.2.0
 */
public class BitbucketSCMNavigatorRequest extends SCMNavigatorRequest {

    /**
     * Map of all repositories found by this request
     */
    private final Map<String, BitbucketRepository> repositoryMap = new TreeMap<>();

    /**
     * keep a reference to the observer so we can cross-reference
     */
    private final SCMSourceObserver observer;

    /**
     * Constructor.
     *
     * @param source   the source.
     * @param context  the context.
     * @param observer the observer.
     */
    protected BitbucketSCMNavigatorRequest(@NonNull SCMNavigator source,
                                           @NonNull BitbucketSCMNavigatorContext context,
                                           @NonNull SCMSourceObserver observer) {
        super(source, context, observer);
        this.observer = observer;
    }

    public void withRepositories(List<? extends BitbucketRepository> repositories) {
        this.repositoryMap.clear();
        for (BitbucketRepository repository : repositories) {
            this.repositoryMap.put(repository.getRepositoryName(), repository);
        }
    }

    public Collection<BitbucketRepository> repositories() {
        final List<String> existingRepositories = this.observer.getContext().getSCMSources().stream()
                                                               .filter(BitbucketSCMSource.class::isInstance)
                                                               .map(BitbucketSCMSource.class::cast)
                                                               .map(BitbucketSCMSource::getRepository)
                                                               .collect(Collectors.toList());
        // process new repositories first
        final Set<BitbucketRepository> newRepositories = this.repositoryMap.entrySet()
                                                                           .stream()
                                                                           .filter(e -> !existingRepositories.contains(e.getKey()))
                                                                           .map(Map.Entry::getValue)
                                                                           .collect(Collectors.toCollection(LinkedHashSet::new));
        // add remaining repositories back in. duplicates will be rejected
        newRepositories.addAll(this.repositoryMap.values());
        return newRepositories;
    }

    public BitbucketRepository getBitbucketRepository(String repositoryName) {
        return this.repositoryMap.get(repositoryName);
    }

}
