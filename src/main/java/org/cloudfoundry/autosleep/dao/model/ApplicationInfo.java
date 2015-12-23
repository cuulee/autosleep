package org.cloudfoundry.autosleep.dao.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.autosleep.util.serializer.InstantDeserializer;
import org.cloudfoundry.autosleep.util.serializer.InstantSerializer;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.CloudApplication.AppState;

import javax.persistence.*;
import java.time.Instant;
import java.util.HashMap;

@Getter
@Slf4j
@Entity
@EqualsAndHashCode
public class ApplicationInfo {

    @Getter
    @Slf4j
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @Embeddable
    @EqualsAndHashCode
    public static class DiagnosticInfo {
        @JsonSerialize(using = InstantSerializer.class)
        @JsonDeserialize(using = InstantDeserializer.class)
        private Instant lastEvent;

        @JsonSerialize(using = InstantSerializer.class)
        @JsonDeserialize(using = InstantDeserializer.class)
        private Instant lastLog;

        @JsonSerialize
        private CloudApplication.AppState appState;

        @JsonSerialize(using = InstantSerializer.class)
        @JsonDeserialize(using = InstantDeserializer.class)
        private Instant nextCheck;

        @JsonSerialize(using = InstantSerializer.class)
        @JsonDeserialize(using = InstantDeserializer.class)
        private Instant lastCheck;

        //see https://issues.jboss.org/browse/HIBERNATE-50
        private int hibernateWorkaround = 1;
    }

    @Getter
    @Slf4j
    @Embeddable
    @EqualsAndHashCode
    public static class EnrollmentState {

        public enum State {
            /**
             * service instance is bound to the application.
             */
            ENROLLED,
            /**
             * service (with AUTO_ENROLLMENT set to false) was manually unbound,
             * it won't be automatically bound again.
             */
            BLACKLISTED
        }

        private HashMap<String /**serviceId.**/, EnrollmentState.State> states;

        private EnrollmentState() {
            states = new HashMap<>();
        }

        public void addEnrollmentState(String serviceId) {
            states.put(serviceId, EnrollmentState.State.ENROLLED);
        }

        public void updateEnrollment(String serviceId, boolean addToBlackList) {
            if (addToBlackList) {
                states.put(serviceId, EnrollmentState.State.BLACKLISTED);
            } else {
                states.remove(serviceId);
            }
        }

        public boolean isWatched() {
            return states.values().stream().filter(
                    serviceInstanceState -> serviceInstanceState == EnrollmentState.State.ENROLLED
            ).findAny().isPresent();
        }

        public boolean isCandidate(String serviceInstanceId) {
            return !states.containsKey(serviceInstanceId);
        }

        public boolean isEnrolledByService(String serviceInstanceId) {
            return states.get(serviceInstanceId) == EnrollmentState.State.ENROLLED;
        }

    }

    @Id
    @Column(length = 40)
    private String uuid;

    private String name;

    @Embedded
    @JsonUnwrapped
    private DiagnosticInfo diagnosticInfo;


    @Embedded
    @JsonUnwrapped
    private EnrollmentState enrollmentState;


    private ApplicationInfo() {
        diagnosticInfo = new DiagnosticInfo();
        enrollmentState = new EnrollmentState();
    }

    public ApplicationInfo(String uuid) {
        this();
        this.uuid = uuid;
    }

    public void updateDiagnosticInfo(AppState state, Instant lastLog, Instant lastEvent, String name) {
        this.diagnosticInfo.appState = state;
        this.diagnosticInfo.lastLog = lastLog;
        this.diagnosticInfo.lastEvent = lastEvent;
        this.name = name;
    }

    public void markAsChecked(Instant next) {
        this.diagnosticInfo.lastCheck = Instant.now();
        this.diagnosticInfo.nextCheck = next;
    }

    public void clearCheckInformation() {
        this.diagnosticInfo.lastCheck = Instant.now();
        this.diagnosticInfo.nextCheck = null;
        this.diagnosticInfo.appState = null;
    }

    public void markAsPutToSleep() {
        this.diagnosticInfo.appState = AppState.STOPPED;
        this.diagnosticInfo.lastEvent = Instant.now();
    }


    @Override
    public String toString() {
        return "[ApplicationInfo:" + name + "/" + uuid + " lastEvent:"
                + diagnosticInfo.lastEvent + " lastLog:" + diagnosticInfo.lastLog + "]";
    }


}
