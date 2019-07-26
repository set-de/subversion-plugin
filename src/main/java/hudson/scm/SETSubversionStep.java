package hudson.scm;
/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick, 2019 SET GmbH
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

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.scm.subversion.UpdateWithCleanUpdater;

/**
 * Runs Subversion using {@code SubversionSCM}.
 */
public final class SETSubversionStep extends Step implements Serializable {

    private boolean poll = true;
    private boolean changelog = true;
    private final String url;
    private final String credentialsId;
    private final String localPath;

    private static final long serialVersionUID = 1L;

    @DataBoundConstructor
    public SETSubversionStep(final String url, final String localPath, final String credentialsId) {
        this.url = url;
        this.localPath = localPath;
        this.credentialsId = credentialsId;
    }

    public String getCredentialsId() {
        return this.credentialsId;
    }

    public String getLocalPath() {
        return this.localPath;
    }

    public String getUrl() {
        return this.url;
    }

    public boolean isPoll() {
        return this.poll;
    }

    @DataBoundSetter
    public void setPoll(final boolean poll) {
        this.poll = poll;
    }

    public boolean isChangelog() {
        return this.changelog;
    }

    @DataBoundSetter
    public void setChangelog(final boolean changelog) {
        this.changelog = changelog;
    }

    public final void checkout(final Run<?, ?> run, final FilePath workspace, final TaskListener listener, final Launcher launcher)
            throws Exception {
        File changelogFile = null;
        if (this.changelog) {
            synchronized (run) {
                for (int i = 0;; i++) {
                    changelogFile = new File(run.getRootDir(), "changelog" + i + ".xml");
                    if (!changelogFile.exists()) {
                        Files.createFile(changelogFile.toPath());
                        break;
                    }
                }
            }
        }

        final List<ModuleLocation> locations = new ArrayList<SubversionSCM.ModuleLocation>();
        locations.add(new ModuleLocation(this.url, this.credentialsId, this.localPath, "infinity", true));

        final SubversionRepositoryBrowser browser = null;
        final SubversionSCM scm = new SubversionSCM(
                locations,
                new UpdateWithCleanUpdater(),
                browser,
                "",
                "",
                "",
                "",
                "",
                true,
                true,
                null);

        // Find the last revision state from previous Builds
        SVNRevisionState baseline = null;
        Run<?, ?> prev = run.getPreviousBuild();
        while (prev != null && baseline == null) {
            baseline = prev.getAction(SVNRevisionState.class);
            prev = prev.getPreviousBuild();
        }

        scm.checkout(run, launcher, workspace, listener, changelogFile, baseline);

        SVNRevisionState state = run.getAction(SVNRevisionState.class);
        if (state == null) {
            state = new SVNRevisionState();
            run.addAction(state);
        }

        SVNRevisionState pollingBaseline = null;
        if (this.poll || this.changelog) {
            pollingBaseline = (SVNRevisionState) scm.calcRevisionsFromBuild(run, workspace, launcher, listener);
            state.addRevisions(pollingBaseline.revisions, true);
        }

        // Add all revisions from previous build
        if (baseline != null) {
            state.addRevisions(baseline.revisions, false);
        }

        for (final SCMListener l : SCMListener.all()) {
            l.onCheckout(run, scm, workspace, listener, changelogFile, pollingBaseline);
        }
        scm.postCheckout(run, launcher, workspace, listener);
        // TODO should we call buildEnvVars and return the result?
    }

    @Override
    public StepExecution start(final StepContext context) throws Exception {
        return new SVNStepExecutionImpl(context, this);
    }

    public static final class SVNStepExecutionImpl extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;
        private final SETSubversionStep step;

        protected SVNStepExecutionImpl(final StepContext context, final SETSubversionStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            this.getContext().get(FlowNode.class).addAction(new LabelAction("SVN Checkout: " + this.step.url));
            this.step.checkout(
                    this.getContext().get(Run.class),
                    this.getContext().get(FilePath.class),
                    this.getContext().get(TaskListener.class),
                    this.getContext().get(Launcher.class));
            return null;
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        public DescriptorImpl() throws Exception {
            super();
        }

        @Override
        public String getFunctionName() {
            return "setSvn";
        }

        @Override
        public String getDisplayName() {
            return "SET Subversion";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            final Set<Class<?>> result = new LinkedHashSet<>();
            result.add(Run.class);
            result.add(FilePath.class);
            result.add(Launcher.class);
            result.add(TaskListener.class);
            result.add(FlowNode.class);
            return result;
        }
    }
}
