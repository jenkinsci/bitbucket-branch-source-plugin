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
package com.cloudbees.jenkins.plugins.bitbucket.hooks;

import com.cloudbees.jenkins.plugins.bitbucket.api.endpoint.BitbucketEndpoint;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookProcessor;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookProcessorException;
import com.cloudbees.jenkins.plugins.bitbucket.api.webhook.BitbucketWebhookProcessorListener;
import hudson.ExtensionList;
import hudson.triggers.SafeTimerTask;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebhookProcessorListenersHandler implements BitbucketWebhookProcessorListener {
    private static ExecutorService executorService;
    private static final Logger logger = Logger.getLogger(WebhookProcessorListenersHandler.class.getName());

    // We need a single thread executor to run webhooks operations in background
    // but in order.
    private static synchronized ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor(new NamingThreadFactory(new DaemonThreadFactory(), WebhookProcessorListenersHandler.class.getName()));
        }
        return executorService;
    }

    private List<BitbucketWebhookProcessorListener> listeners;

    public WebhookProcessorListenersHandler() {
        listeners = ExtensionList.lookup(BitbucketWebhookProcessorListener.class);
    }

    @Override
    public void onStart(Class<? extends BitbucketWebhookProcessor> processorClass) {
        execute(listener -> listener.onStart(processorClass));
    }

    @Override
    public void onFailure(BitbucketWebhookProcessorException e) {
        execute(listener -> listener.onFailure(e));
    }

    @Override
    public void onProcess(String eventType, String body, BitbucketEndpoint endpoint) {
        execute(listener -> listener.onProcess(eventType, body, endpoint));
    }

    private void execute(Consumer<BitbucketWebhookProcessorListener> predicate) {
        getExecutorService().submit(new SafeTimerTask() {
            @Override
            public void doRun() {
                listeners.forEach(listener -> {
                    String listenerName = listener.getClass().getName();
                    logger.log(Level.FINEST, () -> "Processing listener " + listenerName);
                    try {
                        predicate.accept(listener);
                        logger.log(Level.FINEST, () -> "Processing listener " + listenerName + " completed");
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, e, () -> "Processing failed on listener " + listenerName);
                    }
                });
            }
        });
    }
}
