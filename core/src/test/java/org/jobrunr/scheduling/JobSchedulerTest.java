package org.jobrunr.scheduling;

import org.jobrunr.jobs.AbstractJob;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.RecurringJob;
import org.jobrunr.jobs.filters.JobClientFilter;
import org.jobrunr.scheduling.cron.Cron;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.stubs.TestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSchedulerTest {

    @Mock
    private StorageProvider storageProvider;

    private TestService testService;
    private JobScheduler jobScheduler;
    private JobClientLogFilter jobClientLogFilter;

    @BeforeEach
    public void setupTestService() {
        testService = new TestService();

        jobClientLogFilter = new JobClientLogFilter();
        jobScheduler = new JobScheduler(storageProvider, Arrays.asList(jobClientLogFilter));
    }

    @Test
    void onJobCreatingAndCreatedAreCalled() {
        when(storageProvider.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        jobScheduler.enqueue(() -> testService.doWork());

        assertThat(jobClientLogFilter.onCreating).isTrue();
        assertThat(jobClientLogFilter.onCreated).isTrue();
    }

    @Test
    void onStreamOfJobsCreatingAndCreatedAreCalled() {
        when(storageProvider.save(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        final Stream<Integer> range = IntStream.range(0, 1).boxed();
        jobScheduler.enqueue(range, (i) -> testService.doWork(i));

        assertThat(jobClientLogFilter.onCreating).isTrue();
        assertThat(jobClientLogFilter.onCreated).isTrue();
    }

    @Test
    void onRecurringJobCreatingAndCreatedAreCalled() {
        when(storageProvider.saveRecurringJob(any(RecurringJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        jobScheduler.scheduleRecurrently(null, () -> testService.doWork(), Cron.daily(), ZoneId.systemDefault());

        assertThat(jobClientLogFilter.onCreating).isTrue();
        assertThat(jobClientLogFilter.onCreated).isTrue();
    }

    public static class JobClientLogFilter implements JobClientFilter {

        boolean onCreating;
        boolean onCreated;

        @Override
        public void onCreating(AbstractJob job) {
            onCreating = true;
        }

        @Override
        public void onCreated(AbstractJob job) {
            onCreated = true;
        }
    }

}