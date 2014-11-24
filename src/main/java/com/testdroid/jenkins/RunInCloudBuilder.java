package com.testdroid.jenkins;

import com.testdroid.api.APIException;
import com.testdroid.api.APIQueryBuilder;
import com.testdroid.api.model.*;
import com.testdroid.api.model.APIProject.Type;
import com.testdroid.api.model.APITestRunConfig.Mode;
import com.testdroid.api.model.APITestRunConfig.Scheduler;
import com.testdroid.jenkins.remotesupport.MachineIndependentFileUploader;
import com.testdroid.jenkins.remotesupport.MachineIndependentResultsDownloader;
import com.testdroid.jenkins.utils.AndroidLocale;
import com.testdroid.jenkins.utils.EmailHelper;
import com.testdroid.jenkins.utils.ResultWaiter;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RunInCloudBuilder extends AbstractBuilder {

    transient private static final Logger LOGGER = Logger.getLogger(RunInCloudBuilder.class.getSimpleName());

    transient private static final String POST_HOOK_URL = "/plugin/testdroid-run-in-cloud/api/json/cloud-webhook";

    private String appPath;

    private String clusterId;

    private boolean failBuildIfThisStepFailed;

    private String keyValuePairs;

    private String language;

    private String notificationEmail = "";

    private String notificationEmailType = String.valueOf(APINotificationEmail.Type.ALWAYS);

    private String projectId;

    private String scheduler;

    private String screenshotsDirectory;

    private String testCasesSelect;

    private String testCasesValue;

    private String testPath;

    private String testRunName;

    private String testRunner;

    private WaitForResultsBlock waitForResultsBlock;

    private String withAnnotation;

    private String withoutAnnotation;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public RunInCloudBuilder(
            String projectId, String appPath, String testPath, String testRunName, String scheduler, String testRunner,
            String clusterId, String language, String notificationEmail, String screenshotsDirectory,
            String keyValuePairs, String withAnnotation, String withoutAnnotation, String testCasesSelect,
            String testCasesValue, String notificationEmailType, Boolean failBuildIfThisStepFailed,
            WaitForResultsBlock waitForResultsBlock) {
        this.projectId = projectId;
        this.appPath = appPath;
        this.testPath = testPath;
        this.testRunName = testRunName;
        this.scheduler = scheduler;
        this.testRunner = testRunner;
        this.screenshotsDirectory = screenshotsDirectory;
        this.keyValuePairs = keyValuePairs;
        this.withAnnotation = withAnnotation;
        this.withoutAnnotation = withoutAnnotation;
        this.testCasesSelect = testCasesSelect;
        this.testCasesValue = testCasesValue;
        this.clusterId = clusterId;
        this.language = language;
        this.notificationEmail = notificationEmail;
        this.notificationEmailType = notificationEmailType;
        this.failBuildIfThisStepFailed = failBuildIfThisStepFailed;
        this.waitForResultsBlock = waitForResultsBlock;
    }

    public String getTestRunName() {
        return testRunName;
    }

    public void setTestRunName(String testRunName) {
        this.testRunName = testRunName;
    }

    public String getAppPath() {
        return appPath;
    }

    public void setAppPath(String appPath) {
        this.appPath = appPath;
    }

    public String getTestPath() {
        return testPath;
    }

    public void setTestPath(String testPath) {
        this.testPath = testPath;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getTestRunner() {
        return testRunner;
    }

    public void setTestRunner(String testRunner) {
        this.testRunner = testRunner;
    }

    public String getScreenshotsDirectory() {
        return screenshotsDirectory;
    }

    public void setScreenshotsDirectory(String screenshotsDirectory) {
        this.screenshotsDirectory = screenshotsDirectory;
    }

    public String getKeyValuePairs() {
        return keyValuePairs;
    }

    public void setKeyValuePairs(String keyValuePairs) {
        this.keyValuePairs = keyValuePairs;
    }

    public String getWithAnnotation() {
        return withAnnotation;
    }

    public void setWithAnnotation(String withAnnotation) {
        this.withAnnotation = withAnnotation;
    }

    public String getWithoutAnnotation() {
        return withoutAnnotation;
    }

    public void setWithoutAnnotation(String withoutAnnotation) {
        this.withoutAnnotation = withoutAnnotation;
    }

    public String getTestCasesSelect() {
        return testCasesSelect;
    }

    public void setTestCasesSelect(String testCasesSelect) {
        this.testCasesSelect = testCasesSelect;
    }

    public String getTestCasesValue() {
        return testCasesValue;
    }

    public void setTestCasesValue(String testCasesValue) {
        this.testCasesValue = testCasesValue;
    }

    public String getLanguage() {
        if (language == null) {
            language = String.format("%s-%s", Locale.ENGLISH.getLanguage(), Locale.ENGLISH.getCountry());
        }
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getScheduler() {
        if (scheduler == null) {
            scheduler = Scheduler.PARALLEL.toString();
        }
        return scheduler;
    }

    public void setScheduler(String scheduler) {
        this.scheduler = scheduler.toLowerCase();
    }

    public String getNotificationEmail() {
        return notificationEmail;
    }

    public void setNotificationEmail(String notificationEmail) {
        this.notificationEmail = notificationEmail;
    }

    public String getNotificationEmailType() {
        return notificationEmailType;
    }

    public void setNotificationEmailType(String notificationEmailType) {
        this.notificationEmailType = notificationEmailType;
    }

    public WaitForResultsBlock getWaitForResultsBlock() {
        return waitForResultsBlock;
    }

    public void setWaitForResultsBlock(WaitForResultsBlock waitForResultsBlock) {
        this.waitForResultsBlock = waitForResultsBlock;
    }

    public boolean isFailBuildIfThisStepFailed() {
        return failBuildIfThisStepFailed;
    }

    public void setFailBuildIfThisStepFailed(boolean failBuildIfThisStepFailed) {
        this.failBuildIfThisStepFailed = failBuildIfThisStepFailed;
    }

    public boolean isFullTest() {
        return StringUtils.isNotBlank(testPath);
    }

    public String getEditProjectUrl() {
        return TestdroidCloudSettings.descriptor().getCloudUrl() + "/#service/projects/" + getProjectId();
    }

    private boolean verifyParameters(BuildListener listener) {
        boolean result = true;
        if (StringUtils.isBlank(appPath)) {
            listener.getLogger().println(Messages.ERROR_APP_PATH() + "\n");
            result = false;
        }
        if (StringUtils.isBlank(projectId)) {
            listener.getLogger().println(Messages.EMPTY_PROJECT() + "\n");
            result = false;
        }
        if (isFullTest() && StringUtils.isBlank(testPath)) {
            listener.getLogger().println(Messages.ERROR_INSTRUMENTATION_PATH());
            result = false;
        }
        return result;
    }

    public boolean isWaitForResults() {
        return waitForResultsBlock != null;
    }

    private String evaluateHookUrl() {
        return isWaitForResults() ?
                StringUtils.isNotBlank(waitForResultsBlock.getHookURL()) ? waitForResultsBlock.getHookURL()
                        : String.format("%s%s", Hudson.getInstance().getRootUrl(), POST_HOOK_URL) :
                null;
    }

    private String evaluateResultsPath(AbstractBuild<?, ?> build) {
        return isWaitForResults() ?
                StringUtils.isNotBlank(waitForResultsBlock.getResultsPath()) ? waitForResultsBlock.getResultsPath()
                        : build.getWorkspace().getRemote() :
                null;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        listener.getLogger().println(Messages.RUN_TEST_IN_CLOUD_STARTED());
        boolean result = runTest(build, launcher, listener);
        if (result) {
            listener.getLogger().println(Messages.RUN_TEST_IN_CLOUD_SUCCEEDED());
        } else {
            listener.getLogger().println(Messages.RUN_TEST_IN_CLOUD_FAILED());
        }
        return result || !failBuildIfThisStepFailed;
    }

    private boolean runTest(AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener) {
        // rewrite paths to take variables into consideration
        String appPathFinal = applyMacro(build, listener, appPath);
        String testPathFinal = applyMacro(build, listener, testPath);
        String withAnnotationFinal = applyMacro(build, listener, withAnnotation);
        String testRunnerFinal = applyMacro(build, listener, testRunner);
        String withoutAnnotationFinal = applyMacro(build, listener, withoutAnnotation);

        TestdroidCloudSettings plugin = TestdroidCloudSettings.getInstance();
        boolean releaseDone = false;

        try {
            // make part update and run project "transactional"
            plugin.getSemaphore().acquire();

            if (!verifyParameters(listener)) {
                return false;
            }
            APIUser user = TestdroidCloudSettings.descriptor().getUser();

            final APIProject project = user.getProject(Long.parseLong(this.projectId.trim()));
            if (project == null) {
                listener.getLogger().println(Messages.CHECK_PROJECT_NAME());
                return false;
            }

            updateUserEmailNotifications(user, project);

            APITestRunConfig config = project.getTestRunConfig();
            if (!isFullTest()) {
                config.setMode(APITestRunConfig.Mode.APP_CRAWLER);
            } else {
                config.setMode(projectTypeToConfigMode(project.getType()));
            }

            config.setDeviceLanguageCode(this.language);
            config.setScheduler(Scheduler.valueOf(this.scheduler));
            config.setUsedDeviceGroupId(Long.parseLong(this.clusterId));
            config.setHookURL(evaluateHookUrl());
            config.setScreenshotDir(this.screenshotsDirectory);
            config.setInstrumentationRunner(testRunnerFinal);
            config.setWithoutAnnotation(withoutAnnotationFinal);
            config.setWithAnnotation(withAnnotationFinal);
            setLimitations(build, listener, config);
            deleteExistingParameters(config);
            createProvidedParameters(config);

            config.update();
            printTestJob(project, config, listener);
            getDescriptor().save();

            final FilePath appFile = new FilePath(launcher.getChannel(), getAbsolutePath(build, appPathFinal));

            listener.getLogger().println(String.format(Messages.UPLOADING_NEW_APPLICATION_S(), appPathFinal));

            if (!appFile.act(new MachineIndependentFileUploader(TestdroidCloudSettings.descriptor(), project.getId(),
                    true, listener))) {
                return false;
            }

            if (isFullTest()) {
                FilePath testFile = new FilePath(launcher.getChannel(), getAbsolutePath(build, testPathFinal));

                listener.getLogger().println(String.format(Messages.UPLOADING_NEW_INSTRUMENTATION_S(),
                        testPathFinal));

                if (!testFile.act(new MachineIndependentFileUploader(TestdroidCloudSettings.descriptor(),
                        project.getId(), false, listener))) {
                    return false;
                }
            }
            listener.getLogger().println(Messages.RUNNING_TESTS());

            // run project with proper name set in jenkins if it's set
            String finalTestRunName = applyMacro(build, listener, testRunName);
            APITestRun testRun = (StringUtils.isBlank(finalTestRunName) || finalTestRunName.trim().startsWith("$")) ?
                    project.run() : project.run(finalTestRunName);
            ResultWaiter.getInstance().putToWaitList(testRun.getId(), this);
            String cloudLinkPrefix = TestdroidCloudSettings.descriptor().getPrivateInstanceState() ?
                    TestdroidCloudSettings.descriptor().getCloudUrl() : TestdroidCloudSettings.CLOUD_ENDPOINT;
            build.getActions().add(new CloudLink(build, String.format("%s/#service/testrun/%s/%s",
                    cloudLinkPrefix, testRun.getProjectId(), testRun.getId())));

            plugin.getSemaphore().release();
            releaseDone = true;

            waitForResults(project, testRun, build, launcher, listener);

            return true;
        } catch (APIException e) {
            listener.getLogger().println(
                    String.format("%s: %s", Messages.ERROR_API(), e.getMessage()));
            LOGGER.log(Level.WARNING, Messages.ERROR_API(), e);
        } catch (IOException e) {
            listener.getLogger().println(
                    String.format("%s: %s", Messages.ERROR_CONNECTION(), e.getLocalizedMessage()));
            LOGGER.log(Level.WARNING, Messages.ERROR_CONNECTION(), e);
        } catch (InterruptedException e) {
            listener.getLogger().println(
                    String.format("%s: %s", Messages.ERROR_TESTDROID(), e.getLocalizedMessage()));
            LOGGER.log(Level.WARNING, Messages.ERROR_TESTDROID(), e);
        } finally {
            if (!releaseDone) {
                plugin.getSemaphore().release();
            }
        }

        return false;
    }

    private void waitForResults(
            final APIProject project, final APITestRun testRun, AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) {
        if (isWaitForResults()) {
            try {
                listener.getLogger().println("Waiting for results...");
                boolean testRunToAbort = false;
                try {
                    synchronized (this) {
                        if (waitForResultsBlock.getWaitForResultsTimeout() == null
                                || waitForResultsBlock.getWaitForResultsTimeout() == 0) {
                            this.wait();
                        } else {
                            this.wait(waitForResultsBlock.getWaitForResultsTimeout() * 1000);
                        }
                    }
                    testRun.refresh();
                    if (testRun.getState() == APITestRun.State.FINISHED) {
                        boolean downloadResults = launcher.getChannel().call(
                                new MachineIndependentResultsDownloader(TestdroidCloudSettings.descriptor(), listener,
                                        project.getId(), testRun.getId(), evaluateResultsPath(build),
                                        waitForResultsBlock.isDownloadScreenshots()));

                        if (!downloadResults) {
                            listener.getLogger().println(Messages.DOWNLOAD_RESULTS_FAILED());
                            LOGGER.log(Level.WARNING, Messages.DOWNLOAD_RESULTS_FAILED());
                        }
                    } else {
                        testRunToAbort = true;
                        String msg = String.format(Messages.DOWNLOAD_RESULTS_FAILED_WITH_REASON_S(),
                                "Test run is not finished yet! Forcing to finish test in cloud");
                        listener.getLogger().println(msg);
                        LOGGER.log(Level.WARNING, msg);
                    }
                } catch (InterruptedException e) {
                    testRunToAbort = true;

                    listener.getLogger().println(e.getMessage());
                    LOGGER.log(Level.WARNING, e.getMessage(), e);
                }
                if (testRunToAbort && waitForResultsBlock.forceFinishAfterBreak) {
                    String msg = "Forcing to finish test in cloud";
                    listener.getLogger().println(msg);
                    LOGGER.log(Level.WARNING, msg);

                    testRun.abort();
                }
            } catch (APIException e) {
                listener.getLogger().println(
                        String.format("%s: %s", Messages.ERROR_API(), e.getMessage()));
                LOGGER.log(Level.WARNING, Messages.ERROR_API(), e);
            } catch (IOException e) {
                listener.getLogger().println(
                        String.format("%s: %s", Messages.ERROR_CONNECTION(), e.getLocalizedMessage()));
                LOGGER.log(Level.WARNING, Messages.ERROR_CONNECTION(), e);
            }
        }
    }

    private void setLimitations(AbstractBuild<?, ?> build, final BuildListener listener, APITestRunConfig config)
            throws APIException {
        if (StringUtils.isNotBlank(testCasesValue)) {
            config.setLimitationType(APITestRunConfig.LimitationType.valueOf(testCasesSelect));
            config.setLimitationValue(applyMacro(build, listener, testCasesValue));
        } else {
            config.setLimitationType(null);
            config.setLimitationValue("");
        }
    }

    private void deleteExistingParameters(APITestRunConfig config) throws APIException {
        if (getDescriptor().isAdmin()) {
            List<APITestRunParameter> parameters = config
                    .getParameters(new APIQueryBuilder().limit(Integer.MAX_VALUE)).getEntity().getData();
            for (APITestRunParameter parameter : parameters) {
                config.deleteParameter(parameter.getId());
            }
        }
    }

    private void createProvidedParameters(APITestRunConfig config) throws APIException {
        if (getDescriptor().isAdmin()) {
            if (keyValuePairs != null) {
                String[] splitKeyValuePairs = keyValuePairs.split(";");
                for (String splitKeyValuePair : splitKeyValuePairs) {
                    if (StringUtils.isNotBlank(splitKeyValuePair)) {
                        String[] splitKeyValue = splitKeyValuePair.split(":");
                        if (splitKeyValue.length == 2) {
                            config.createParameter(splitKeyValue[0], splitKeyValue[1]);
                        }
                    }
                }
            }
        }
    }

    private void printTestJob(APIProject project, APITestRunConfig config, BuildListener listener) {
        listener.getLogger().println(Messages.TEST_RUN_CONFIGURATION());
        listener.getLogger().println(String.format("%s: %s", Messages.PROJECT(), project.getName()));
        listener.getLogger().println(
                String.format("%s: %s", Messages.LOCALE(), config.getDeviceLanguageCode()));
        listener.getLogger().println(String.format("%s: %s", Messages.SCHEDULER(), config.getScheduler()));
        listener.getLogger().println(
                String.format("%s: %s", Messages.APP_CRAWLER(), config.getMode() == APITestRunConfig.Mode.APP_CRAWLER));
        listener.getLogger().println(String.format("%s: %s", Messages.PRICE(), config.getCreditsPrice()));
    }

    private String getAbsolutePath(AbstractBuild<?, ?> build, String path) throws IOException, InterruptedException {
        if (StringUtils.isBlank(path)) {
            return "";
        }
        String trimmed = StringUtils.trim(path);
        if (trimmed.startsWith(File.separator)) { // absolute
            return trimmed;
        } else {
            URI workspaceURI = build.getWorkspace().toURI();
            return workspaceURI.getPath() + trimmed;
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    private Mode projectTypeToConfigMode(Type type) {
        return APITestRunConfig.Mode
                .valueOf(type.toString().replace(Type.ANDROID.toString(), Mode.FULL_RUN.toString()));
    }

    private void updateUserEmailNotifications(APIUser user, APIProject project) {
        try {
            //set emails per user
            APINotificationEmail.Type neType = APINotificationEmail.Type.valueOf(TestdroidCloudSettings.descriptor()
                    .getNotificationEmailType());
            List<String> emailAddressesToSet = EmailHelper.getEmailAddresses(TestdroidCloudSettings.descriptor()
                    .getNotificationEmail());
            List<APINotificationEmail> currentEmails = user.getNotificationEmails().getEntity().getData();
            //remove exceeded emails and update type of existed ones
            for (APINotificationEmail email : currentEmails) {
                if (!emailAddressesToSet.contains(email.getEmail())) {
                    email.delete();
                } else if (!email.getType().equals(neType)) {
                    email.setType(neType);
                    email.refresh();
                }
            }
            //add missed emails
            for (String email : emailAddressesToSet) {
                if (!isNotificationEmailContained(currentEmails, email)) {
                    user.createNotificationEmail(email, neType);
                }
            }

            //set emails per project
            neType = APINotificationEmail.Type.valueOf(notificationEmailType);
            emailAddressesToSet = EmailHelper.getEmailAddresses(notificationEmail);
            currentEmails = project.getNotificationEmails().getEntity().getData();
            //remove exceeded emails and update type of existed ones
            for (APINotificationEmail email : currentEmails) {
                if (!emailAddressesToSet.contains(email.getEmail())) {
                    email.delete();
                } else if (!email.getType().equals(neType)) {
                    email.setType(neType);
                    email.refresh();
                }
            }
            //add missed emails
            for (String email : emailAddressesToSet) {
                if (!isNotificationEmailContained(currentEmails, email)) {
                    project.createNotificationEmail(email, neType);
                }
            }
        } catch (APIException e) {
            LOGGER.log(Level.WARNING, Messages.ERROR_API());
        }
    }

    private boolean isNotificationEmailContained(List<APINotificationEmail> notificationEmails, String searchEmail) {
        for (APINotificationEmail notificationEmail : notificationEmails) {
            if (notificationEmail.getEmail().equals(searchEmail)) {
                return true;
            }
        }
        return false;
    }

    public static class WaitForResultsBlock {

        private boolean downloadScreenshots;

        private boolean forceFinishAfterBreak;

        private String hookURL = "";

        private String resultsPath = "";

        private Integer waitForResultsTimeout;

        @DataBoundConstructor
        public WaitForResultsBlock(
                String hookURL, String waitForResultsTimeout, String resultsPath, boolean downloadScreenshots,
                boolean forceFinishAfterBreak) {
            this.hookURL = hookURL;
            this.resultsPath = resultsPath;
            this.downloadScreenshots = downloadScreenshots;
            this.forceFinishAfterBreak = forceFinishAfterBreak;
            try {
                this.waitForResultsTimeout = Integer.parseInt(waitForResultsTimeout);
            } catch (NumberFormatException ignore) {
            }
        }

        public String getHookURL() {
            return hookURL;
        }

        public void setHookURL(String hookURL) {
            this.hookURL = hookURL;
        }

        public Integer getWaitForResultsTimeout() {
            return waitForResultsTimeout;
        }

        public void setWaitForResultsTimeout(Integer waitForResultsTimeout) {
            this.waitForResultsTimeout = waitForResultsTimeout;
        }

        public String getResultsPath() {
            return resultsPath;
        }

        public void setResultsPath(String resultsPath) {
            this.resultsPath = resultsPath;
        }

        public boolean isDownloadScreenshots() {
            return downloadScreenshots;
        }

        public void setDownloadScreenshots(boolean downloadScreenshots) {
            this.downloadScreenshots = downloadScreenshots;
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> implements Serializable {

        private static final long serialVersionUID = 1L;

        public DescriptorImpl() {
            super(RunInCloudBuilder.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.TESTDROID_RUN_TESTS_IN_CLOUD();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> arg0) {
            return true;
        }

        public boolean isAuthenticated() {
            try {
                return TestdroidCloudSettings.descriptor().getUser() != null;
            } catch (APIException e) {
                LOGGER.log(Level.WARNING, Messages.ERROR_API());
                return false;
            }
        }

        public boolean isAdmin() {
            if (isAuthenticated()) {
                try {
                    APIUser user = TestdroidCloudSettings.descriptor().getUser();
                    for (APIRole role : user.getRoles()) {
                        if ("ADMIN".equals(role.getName())) {
                            return !role.isLimited() || role.getUseLimit() > 0;
                        }
                    }
                } catch (APIException e) {
                    LOGGER.log(Level.WARNING, Messages.ERROR_API());
                }

            }
            return false;
        }

        public ListBoxModel doFillProjectIdItems() {
            ListBoxModel projects = new ListBoxModel();
            try {
                APIUser user = TestdroidCloudSettings.descriptor().getUser();
                List<APIProject> list = user.getProjectsResource(new APIQueryBuilder().limit(Integer.MAX_VALUE))
                        .getEntity().getData();
                for (APIProject project : list) {
                    projects.add(project.getName(), project.getId().toString());
                }
            } catch (APIException e) {
                LOGGER.log(Level.WARNING, Messages.ERROR_API());
            }
            return projects;
        }

        public ListBoxModel doFillSchedulerItems() {
            ListBoxModel schedulers = new ListBoxModel();
            schedulers.add(Messages.SCHEDULER_PARALLEL(), Scheduler.PARALLEL.toString());
            schedulers.add(Messages.SCHEDULER_SERIAL(), Scheduler.SERIAL.toString());
            schedulers.add(Messages.SCHEDULER_SINGLE(), Scheduler.SINGLE.toString());
            return schedulers;
        }

        public ListBoxModel doFillClusterIdItems() {
            ListBoxModel deviceGroups = new ListBoxModel();
            try {
                APIUser user = TestdroidCloudSettings.descriptor().getUser();
                List<APIDeviceGroup> list = user.getDeviceGroupsResource(new APIQueryBuilder()
                        .limit(Integer.MAX_VALUE)).getEntity().getData();
                for (APIDeviceGroup deviceGroup : list) {
                    deviceGroups.add(String.format("%s (%d device(s))", deviceGroup.getDisplayName(),
                            deviceGroup.getDeviceCount()), deviceGroup.getId().toString());
                }
            } catch (APIException e) {
                LOGGER.log(Level.WARNING, Messages.ERROR_API());
            }

            return deviceGroups;
        }

        public ListBoxModel doFillLanguageItems() {
            ListBoxModel language = new ListBoxModel();
            for (Locale locale : AndroidLocale.LOCALES) {
                String langDisplay = String.format("%s (%s)", locale.getDisplayLanguage(),
                        locale.getDisplayCountry());
                String langCode = String.format("%s-%s", locale.getLanguage(), locale.getCountry());
                language.add(langDisplay, langCode);
            }
            return language;
        }

        public ListBoxModel doFillNotificationEmailTypeItems() {
            return TestdroidCloudSettings.descriptor().doFillNotificationEmailTypeItems();
        }

        public ListBoxModel doFillTestCasesSelectItems() {
            ListBoxModel testCases = new ListBoxModel();
            String value;
            for (APITestRunConfig.LimitationType limitationType : APITestRunConfig.LimitationType.values()) {
                value = limitationType.name();
                testCases.add(value.toLowerCase(), value);
            }
            return testCases;
        }
    }
}