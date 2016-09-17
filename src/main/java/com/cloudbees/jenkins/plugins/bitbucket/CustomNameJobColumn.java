package com.cloudbees.jenkins.plugins.bitbucket;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.views.JobColumn;
import hudson.views.ListViewColumnDescriptor;
import jenkins.model.Jenkins;
import jenkins.util.NonLocalizable;

public class CustomNameJobColumn extends JobColumn {

    /**
     * Resource bundle name.
     */
    private final String bundle;
    private final String key;

    private transient Localizable loc;

    @DataBoundConstructor
    public CustomNameJobColumn(String bundle, String key) {
        this.bundle = bundle;
        this.key = key;
        readResolve();
    }

    public CustomNameJobColumn(Class bundle, Localizable loc) {
        this.bundle = bundle.getName();
        this.key = loc.getKey();
        this.loc = loc;
    }

    public String getBundle() {
        return bundle;
    }

    public String getKey() {
        return loc.getKey();
    }

    public String getMessage() {
        return loc.toString();
    }

    private Object readResolve() {
        try {
            loc = new Localizable(
                ResourceBundleHolder.get(Jenkins.getActiveInstance().pluginManager.uberClassLoader.loadClass(bundle)),
                key);
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, "No such bundle: " + bundle);
            loc = new NonLocalizable(bundle + ':' + key);
        }
        return this;
    }

    @Extension
    public static class DescriptorImpl extends ListViewColumnDescriptor {
        @Override
        public String getDisplayName() {
            return "Repositories";
        }

        @Override
        public boolean shownByDefault() {
            return false;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CustomNameJobColumn.class.getName());
}
