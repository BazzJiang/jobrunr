package org.jobrunr.server;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.RecurringJob;
import org.jobrunr.jobs.filters.JobDefaultFilters;
import org.jobrunr.jobs.filters.JobFilter;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.server.jmx.BackgroundJobServerMBean;
import org.jobrunr.server.runner.BackgroundJobRunner;
import org.jobrunr.server.runner.BackgroundJobWithIocRunner;
import org.jobrunr.server.runner.BackgroundJobWithoutIocRunner;
import org.jobrunr.server.runner.BackgroundStaticJobWithoutIocRunner;
import org.jobrunr.server.threadpool.JobRunrExecutor;
import org.jobrunr.server.threadpool.ScheduledThreadPoolJobRunrExecutor;
import org.jobrunr.storage.BackgroundJobServerStatus;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.ThreadSafeStorageProvider;
import org.jobrunr.utils.diagnostics.Diagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.Integer.compare;
import static java.util.Arrays.asList;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.StreamSupport.stream;
import static org.jobrunr.JobRunrException.problematicConfigurationException;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;
import static org.jobrunr.utils.JobUtils.jobExists;

public class BackgroundJobServer implements BackgroundJobServerMBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundJobServer.class);

    private final BackgroundJobServerStatus serverStatus;
    private final StorageProvider storageProvider;
    private final List<BackgroundJobRunner> backgroundJobRunners;
    private final ServerZooKeeper serverZooKeeper;
    private final JobZooKeeper jobZooKeeper;

    private java.util.concurrent.ScheduledThreadPoolExecutor zookeeperThreadPool;
    private JobRunrExecutor jobExecutor;
    private JobDefaultFilters jobDefaultFilters;
    private BackgroundJobServerConfiguration configuration;

    public BackgroundJobServer(StorageProvider storageProvider) {
        this(storageProvider, null);
    }

    public BackgroundJobServer(StorageProvider storageProvider, JobActivator jobActivator) {
        this(storageProvider, jobActivator, usingStandardBackgroundJobServerConfiguration());
    }

    public BackgroundJobServer(StorageProvider storageProvider, JobActivator jobActivator, BackgroundJobServerConfiguration configuration) {
        if (storageProvider == null) throw new IllegalArgumentException("A JobStorageProvider is required to use the JobScheduler. Please see the documentation on how to setup a JobStorageProvider");

        this.configuration = configuration;
        this.serverStatus = new BackgroundJobServerStatus(configuration.pollIntervalInSeconds, configuration.backgroundJobServerWorkerPolicy.getWorkerCount());
        this.storageProvider = new ThreadSafeStorageProvider(storageProvider);
        this.backgroundJobRunners = initializeBackgroundJobRunners(jobActivator);
        this.jobDefaultFilters = new JobDefaultFilters();
        this.serverZooKeeper = createServerZooKeeper();
        this.jobZooKeeper = createJobZooKeeper();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "extShutdownHook"));
        Diagnostics.registerBackgroundJobServer(this);
    }

    public void setJobFilters(List<JobFilter> jobFilters) {
        this.jobDefaultFilters = new JobDefaultFilters(jobFilters);
    }

    JobDefaultFilters getJobFilters() {
        return jobDefaultFilters;
    }

    public boolean isRunning() {
        return serverStatus.isRunning();
    }

    public void start() {
        if (isStarted()) return;
        serverStatus.start();
        startZooKeepers();
        startWorkers();
        checkForPotentialJobNotFoundExceptions();
        LOGGER.info("BackgroundJobServer ({}) and BackgroundJobPerformers started successfully", getId());
    }

    public void pauseProcessing() {
        if (isStopped()) throw new IllegalStateException("First start the BackgroundJobServer before pausing");
        if (isPaused()) return;
        serverStatus.pause();
        stopWorkers();
        LOGGER.info("Paused job processing");
    }

    public void resumeProcessing() {
        if (isStopped()) throw new IllegalStateException("First start the BackgroundJobServer before resuming");
        if (isProcessing()) return;
        startWorkers();
        serverStatus.resume();
        LOGGER.info("Resumed job processing");
    }

    public void stop() {
        if (isStopped()) return;
        stopWorkers();
        stopZooKeepers();
        serverStatus.stop();
        LOGGER.info("BackgroundJobServer and BackgroundJobPerformers stopped");
    }

    public BackgroundJobServerStatus getServerStatus() {
        return serverStatus;
    }

    public JobZooKeeper getJobZooKeeper() {
        return jobZooKeeper;
    }

    public UUID getId() {
        return serverStatus.getId();
    }

    public StorageProvider getStorageProvider() {
        return storageProvider;
    }

    BackgroundJobRunner getBackgroundJobRunner(Job job) {
        return backgroundJobRunners.stream()
                .filter(jobRunner -> jobRunner.supports(job))
                .findFirst()
                .orElseThrow(() -> problematicConfigurationException("Could not find a BackgroundJobRunner: either no JobActivator is registered, your Background Job Class is not registered within the IoC container or your Job does not have a default no-arg constructor."));
    }

    void processJob(Job job) {
        BackgroundJobPerformer backgroundJobPerformer = new BackgroundJobPerformer(this, job);
        jobExecutor.execute(backgroundJobPerformer);
        LOGGER.debug("Submitted BackgroundJobPerformer for job {} to executor service", job.getId());
    }

    void scheduleJob(RecurringJob recurringJob) {
        Job job = recurringJob.toScheduledJob();
        this.storageProvider.save(job);
    }

    boolean isStarted() {
        return !isStopped();
    }

    boolean isStopped() {
        return zookeeperThreadPool == null;
    }

    boolean isPaused() {
        return !isProcessing();
    }

    boolean isProcessing() {
        return serverStatus.isRunning();
    }

    public BackgroundJobServerConfiguration getConfiguration() {
        return configuration;
    }

    private void startZooKeepers() {
        zookeeperThreadPool = new ScheduledThreadPoolJobRunrExecutor(2, "backgroundjob-zookeeper-pool");
        zookeeperThreadPool.scheduleAtFixedRate(serverZooKeeper, 0, serverStatus.getPollIntervalInSeconds(), TimeUnit.SECONDS);
        zookeeperThreadPool.scheduleAtFixedRate(jobZooKeeper, 1, serverStatus.getPollIntervalInSeconds(), TimeUnit.SECONDS);
    }

    private void startWorkers() {
        jobExecutor = loadJobRunrExecutor();
        jobExecutor.start();
    }

    private void checkForPotentialJobNotFoundExceptions() {
        jobExecutor.execute(() -> {
            Set<String> distinctJobSignatures = storageProvider.getDistinctJobSignatures(StateName.SCHEDULED);
            Set<String> jobsThatCannotBeFound = distinctJobSignatures.stream().filter(job -> !jobExists(job)).collect(toSet());
            if (!jobsThatCannotBeFound.isEmpty()) {
                LOGGER.warn("JobRunr found SCHEDULED jobs that do not exist anymore in your code. These jobs will fail with a JobNotFoundException (due to a ClassNotFoundException or a MethodNotFoundException)." +
                        "\n\tBelow you can find the method signatures of the jobs that cannot be found anymore: " +
                        jobsThatCannotBeFound.stream().map(sign -> "\n\t" + sign + ",").collect(Collectors.joining())
                );
            }
        });
    }

    private void stopZooKeepers() {
        jobZooKeeper.stop();
        serverZooKeeper.stop();
        stop(zookeeperThreadPool);
        this.zookeeperThreadPool = null;
    }

    private void stopWorkers() {
        if (jobExecutor == null) return;
        jobExecutor.stop();
        this.jobExecutor = null;
    }

    private List<BackgroundJobRunner> initializeBackgroundJobRunners(JobActivator jobActivator) {
        return asList(
                new BackgroundStaticJobWithoutIocRunner(),
                new BackgroundJobWithIocRunner(jobActivator),
                new BackgroundJobWithoutIocRunner()
        );
    }

    private void stop(ScheduledExecutorService executorService) {
        if (executorService == null) return;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private JobZooKeeper createJobZooKeeper() {
        return new JobZooKeeper(this);
    }

    private ServerZooKeeper createServerZooKeeper() {
        return new ServerZooKeeper(this);
    }

    private JobRunrExecutor loadJobRunrExecutor() {
        ServiceLoader<JobRunrExecutor> serviceLoader = ServiceLoader.load(JobRunrExecutor.class);
        return stream(spliteratorUnknownSize(serviceLoader.iterator(), Spliterator.ORDERED), false)
                .sorted((a, b) -> compare(b.getPriority(), a.getPriority()))
                .findFirst()
                .orElse(new ScheduledThreadPoolJobRunrExecutor(serverStatus.getWorkerPoolSize(), "backgroundjob-worker-pool"));
    }

}
