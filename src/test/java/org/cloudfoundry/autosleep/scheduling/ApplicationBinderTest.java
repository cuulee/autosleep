package org.cloudfoundry.autosleep.scheduling;

import lombok.AllArgsConstructor;
import org.cloudfoundry.autosleep.dao.model.ApplicationInfo;
import org.cloudfoundry.autosleep.dao.model.AutosleepServiceInstance;
import org.cloudfoundry.autosleep.dao.repositories.ApplicationRepository;
import org.cloudfoundry.autosleep.dao.repositories.ServiceRepository;
import org.cloudfoundry.autosleep.remote.CloudFoundryApiService;
import org.cloudfoundry.autosleep.remote.ApplicationIdentity;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ApplicationBinderTest {
    private static final Duration INTERVAL = Duration.ofMillis(300);

    private static final String SERVICE_ID = "serviceId";

    private static final UUID SPACE_ID = UUID.randomUUID();

    @AllArgsConstructor
    private static class ListOfSizeMatcher<T> extends ArgumentMatcher<List<T>> {
        private int expectedSize;

        public boolean matches(Object list) {
            return List.class.isInstance(list) && List.class.cast(list).size() == expectedSize;
        }
    }


    @Mock
    private Clock clock;

    @Mock
    private CloudFoundryApiService cloudFoundryApi;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private AutosleepServiceInstance autosleepServiceInstance;


    private ApplicationBinder applicationBinder;

    private List<UUID> remoteApplicationIds = Arrays.asList(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());


    @Before
    public void buildMocks() {
        //default
        when(autosleepServiceInstance.getSpaceGuid()).thenReturn(SPACE_ID.toString());
        when(autosleepServiceInstance.getServiceInstanceId()).thenReturn(SERVICE_ID);
        when(serviceRepository.findOne(eq(SERVICE_ID))).thenReturn(autosleepServiceInstance);
        when(cloudFoundryApi.listApplications(eq(SPACE_ID), any(Pattern.class)))
                .thenReturn(remoteApplicationIds.stream()
                        .map(applicationId -> new ApplicationIdentity(applicationId, applicationId.toString()))
                        .collect(Collectors.toList()));


        applicationBinder = spy(ApplicationBinder.builder()
                .clock(clock)
                .period(INTERVAL)
                .serviceInstanceId(SERVICE_ID)
                .cloudFoundryApi(cloudFoundryApi)
                .serviceRepository(serviceRepository)
                .applicationRepository(applicationRepository)
                .build());

    }


    @Test
    public void testNewAppeared() throws Exception {
        when(serviceRepository.findOne(eq(SERVICE_ID))).thenReturn(autosleepServiceInstance);
        when(autosleepServiceInstance.getExcludeNames()).thenReturn(null);
        //it will return every ids except final one
        when(applicationRepository.findAll()).thenReturn(remoteApplicationIds.stream()
                .filter(applicationId ->
                        !applicationId.equals(remoteApplicationIds.get(remoteApplicationIds.size() - 1)))
                .map(remoteApplicationId -> new ApplicationInfo(remoteApplicationId, "testNewSid"))
                .collect(Collectors.toList()));
        applicationBinder.run();

        verify(applicationBinder, times(1)).rescheduleWithDefaultPeriod();
        verify(cloudFoundryApi, times(1)).listApplications(eq(SPACE_ID), eq(null));
        verify(cloudFoundryApi, times(1))
                .bindServiceInstance(argThat(anyListOfSize(1, ApplicationIdentity.class)), anyString());

        Pattern pattern = Pattern.compile(".*");
        when(autosleepServiceInstance.getExcludeNames()).thenReturn(pattern);
        applicationBinder.run();
        verify(cloudFoundryApi, times(1)).listApplications(eq(SPACE_ID), eq(pattern));

    }

    @Test
    public void testNoNew() throws Exception {
        when(serviceRepository.findOne(eq(SERVICE_ID))).thenReturn(autosleepServiceInstance);
        //it will return every ids except final one
        when(applicationRepository.findAll()).thenReturn(remoteApplicationIds.stream()
                .map(remoteApplicationId -> new ApplicationInfo(remoteApplicationId,"testNoNewSid"))
                .collect(Collectors.toList()));
        applicationBinder.run();

        verify(applicationBinder, times(1)).rescheduleWithDefaultPeriod();
        verify(cloudFoundryApi, never())
                .bindServiceInstance(anyListOf(ApplicationIdentity.class), anyString());

    }

    @Test
    public void testRunServiceDeleted() {
        when(serviceRepository.findOne(eq(SERVICE_ID))).thenReturn(null);
        applicationBinder.run();
        verify(clock, times(1)).removeTask(eq(SERVICE_ID));
        verify(applicationBinder, never()).rescheduleWithDefaultPeriod();
    }


    private <T> ArgumentMatcher<List<T>> anyListOfSize(final int expectedSize, Class<T> objectClass) {
        return new ArgumentMatcher<List<T>>() {
            @Override
            public boolean matches(Object object) {
                return List.class.isInstance(object) && List.class.cast(object).size() == expectedSize;
            }
        };
    }


}