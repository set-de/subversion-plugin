/*
 * The MIT License
 *
 * Copyright 2017 Tobias Baum.
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
package jenkins.scm.impl.subversion;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.admin.SVNSyncInfo;

import hudson.Extension;
import hudson.model.Run;

/**
 * Provides data from svn admin info.
 */
public final class SvnAdminInfoStep extends Step {

    private final String url;

    @DataBoundConstructor
    public SvnAdminInfoStep(final String url) {
        this.url = url;
    }

    public String getUrl() {
        return this.url;
    }

    @Override
    public StepExecution start(final StepContext context) throws Exception {
        return new Execution(this.url, context);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "svnadmininfo";
        }

        @Override
        public String getDisplayName() {
            return "Provides some data from svn admin info as a map. Give the repo URL as parameter.";
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return Collections.<Class<?>>singleton(Run.class);
        }

    }

    static final class Execution extends SynchronousNonBlockingStepExecution<Map<String, String>> {

        private final String url;

        Execution(final String url, final StepContext context) {
            super(context);
            this.url = url;
        }

        @Override
        protected Map<String, String> run() throws IOException {
            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                final SVNURL url = SVNURL.parseURIEncoded(this.url);
                final SVNSyncInfo info = clientManager.getAdminClient().doInfo(url);
                final Map<String, String> result = new LinkedHashMap<String, String>();
                result.put("LAST_MERGED_REVISION", Long.toString(info.getLastMergedRevision()));
                result.put("SRC_URL", info.getSrcURL().toString());
                result.put("UUID", info.getSourceRepositoryUUID());
                return result;
            } catch (final SVNException e) {
                throw new IOException("failed to get svn infos for " + this.url, e);
            } finally {
                clientManager.dispose();
            }

        }

        private static final long serialVersionUID = 1L;

    }

}
