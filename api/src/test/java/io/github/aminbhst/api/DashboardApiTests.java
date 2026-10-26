package io.github.aminbhst.api;

import io.github.aminbhst.api.service.UserService;
import io.github.aminbhst.common.core.task.TaskStatus;
import io.github.aminbhst.common.core.task.TaskType;
import io.github.aminbhst.common.persistence.entity.AppUser;
import io.github.aminbhst.common.persistence.entity.Task;
import io.github.aminbhst.common.persistence.repository.TaskRepository;
import io.github.aminbhst.common.persistence.repository.UserRepository;
import io.github.aminbhst.common.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.io.ByteArrayInputStream;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dashboard;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "spring.grpc.server.enabled=false",
        "krite.admin.username=bootstrap-admin", "krite.admin.password=bootstrap-secret",
        "spring.servlet.multipart.max-file-size=4KB",
        "krite.transcription.models=tiny,base", "krite.transcription.subtitles-enabled=false"
})
@AutoConfigureMockMvc
class DashboardApiTests {
    @Autowired io.github.aminbhst.common.core.task.TranscriptionProperties transcription;
    @Autowired MockMvc mvc;
    @Autowired TaskRepository tasks;
    @Autowired UserRepository users;
    @Autowired UserService userService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired @Qualifier("initDefaultUser") CommandLineRunner adminInitializer;
    @MockitoBean StorageService storage;
    private Task aliceTask;
    private Task bobTask;

    @Test
    void catalogPublishesMetadataAndTheEnforcedUploadLimit() throws Exception {
        mvc.perform(get("/api/task/types").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(TaskType.values().length))
                .andExpect(jsonPath("$[?(@.id == 'GENERATE_THUMBNAIL')].label").value("Generate thumbnail"))
                .andExpect(jsonPath("$[?(@.id == 'GENERATE_THUMBNAIL')].output").value("300 px · PNG"))
                .andExpect(jsonPath("$[?(@.id == 'GENERATE_THUMBNAIL')].acceptedMimeTypes[0]").value("image/jpeg"))
                .andExpect(jsonPath("$[0].maxFileSizeBytes").value(4096));
        mvc.perform(multipart("/api/task/COMPRESS")
                        .file(new MockMultipartFile("file", "too-big.txt", "text/plain", new byte[4097]))
                        .with(user("alice").roles("USER")))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/task/GENERATE_THUMBNAIL")
                        .file(new MockMultipartFile("file", "wrong.txt", "text/plain", "hello".getBytes()))
                        .with(user("alice").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a file matching: JPEG, PNG, GIF, or BMP."));
        verifyNoInteractions(storage);
    }

    @Test
    void transcriptionModelAllowlistIsPublishedAndEnforcedBeforeStorage() throws Exception {
        mvc.perform(get("/api/task/types").with(user("alice")))
                .andExpect(jsonPath("$[?(@.id == 'TRANSCRIBE')].options[0].choices[0]").value("tiny"))
                .andExpect(jsonPath("$[?(@.id == 'TRANSCRIBE')].options[0].choices[1]").value("base"))
                .andExpect(jsonPath("$[?(@.id == 'TRANSCRIBE')].options[1]").doesNotExist());
        for (String options : List.of("{}", "{\"model\":\"large\"}",
                "{\"model\":\"tiny\",\"burnSubtitles\":\"true\"}",
                "{\"model\":\"tiny\",\"burnSubtitles\":\"yes\"}", "null", "[]")) {
            mvc.perform(multipart("/api/task/TRANSCRIBE")
                    .file(new MockMultipartFile("file", "speech.wav", "audio/wav", new byte[]{1}))
                    .param("options", options).with(user("alice")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(storage);
        when(storage.upload(any(), anyLong(), any())).thenReturn("speech-source");
        mvc.perform(multipart("/api/task/TRANSCRIBE")
                .file(new MockMultipartFile("file", "speech.wav", "audio/wav", new byte[]{1}))
                .param("options", "{\"model\":\"tiny\"}").with(user("alice")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.options.model").value("tiny"));
        Task task = tasks.findAll().stream().filter(t -> t.getType() == TaskType.TRANSCRIBE).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("tiny", task.toTaskAssignment().getModel());
        org.junit.jupiter.api.Assertions.assertFalse(task.toTaskAssignment().getBurnSubtitles());
    }

    @Test
    void enabledSubtitlesArePublishedAndPersistedAndEmptyModelsHideTranscription() throws Exception {
        try {
            transcription.setSubtitlesEnabled(true);
            mvc.perform(get("/api/task/types").with(user("alice")))
                    .andExpect(jsonPath("$[?(@.id == 'TRANSCRIBE')].options[1].name").value("burnSubtitles"));
            when(storage.upload(any(), anyLong(), any())).thenReturn("source");
            mvc.perform(multipart("/api/task/TRANSCRIBE")
                    .file(new MockMultipartFile("file", "meeting.mp4", "video/mp4", new byte[]{1}))
                    .param("options", "{\"model\":\"base\",\"burnSubtitles\":\"true\"}").with(user("alice")))
                    .andExpect(status().isCreated());
            Task saved = tasks.findAll().stream().filter(t -> t.getType() == TaskType.TRANSCRIBE).findFirst().orElseThrow();
            org.junit.jupiter.api.Assertions.assertTrue(saved.toTaskAssignment().getBurnSubtitles());
            transcription.setModels(List.of());
            mvc.perform(get("/api/task/types").with(user("alice")))
                    .andExpect(jsonPath("$[?(@.id == 'TRANSCRIBE')]").isEmpty());
        } finally {
            transcription.setSubtitlesEnabled(false);
            transcription.setModels(List.of("tiny", "base"));
        }
    }

    @Test
    void transcriptAndVideoAreAvailableOnlyToOwnerOrAdminAfterCompletion() throws Exception {
        aliceTask.setType(TaskType.TRANSCRIBE);
        aliceTask.setOriginalFilename("meeting.mp4");
        aliceTask.setVideoFileObjectKey("video");
        tasks.save(aliceTask);
        String path = "/api/task/" + aliceTask.getId();
        for (String suffix : List.of("/preview", "/video", "/result")) {
            mvc.perform(get(path + suffix).with(user("bob"))).andExpect(status().isNotFound());
            mvc.perform(get(path + suffix)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(storage);
        when(storage.download("output")).thenAnswer(i -> new ByteArrayInputStream("Hello world".getBytes()));
        when(storage.download("video")).thenAnswer(i -> new ByteArrayInputStream(new byte[]{1,2,3}));
        mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$.textPreviewAvailable").value(true))
                .andExpect(jsonPath("$.artifacts.length()").value(2));
        mvc.perform(get(path + "/preview").with(user("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Hello world"))
                .andExpect(jsonPath("$.truncated").value(false));
        mvc.perform(get(path + "/result").with(user("alice")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("meeting-transcript.txt")))
                .andExpect(content().string("Hello world"));
        mvc.perform(get(path + "/video").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().contentType("video/mp4"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("meeting-subtitled.mp4")));
        aliceTask.setStatus(TaskStatus.IN_PROGRESS);
        tasks.save(aliceTask);
        mvc.perform(get(path + "/preview").with(user("alice"))).andExpect(status().isConflict());
        mvc.perform(get(path + "/video").with(user("alice"))).andExpect(status().isConflict());
    }

    @Test
    void configuredAdminIsCreatedWithoutOverwritingExistingCredentialsOrRole() throws Exception {
        AppUser admin = users.findByUsername("bootstrap-admin").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("ADMIN", admin.getRole());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("bootstrap-secret", admin.getPassword()));
        String changedPassword = passwordEncoder.encode("changed-password");
        admin.setPassword(changedPassword);
        admin.setRole("USER");
        users.save(admin);

        adminInitializer.run();

        AppUser existing = users.findByUsername("bootstrap-admin").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(changedPassword, existing.getPassword());
        org.junit.jupiter.api.Assertions.assertEquals("USER", existing.getRole());
    }

    @BeforeEach
    void setup() {
        tasks.deleteAll();
        for (String name : List.of("alice", "bob")) {
            if (users.findByUsername(name).isEmpty()) userService.createUser(name, "password123", "USER");
        }
        aliceTask = create(users.findByUsername("alice").orElseThrow(), "alice.png");
        bobTask = create(users.findByUsername("bob").orElseThrow(), "bob.png");
    }

    private Task create(AppUser owner, String name) {
        Task task = new Task();
        task.setType(TaskType.GENERATE_THUMBNAIL);
        task.setStatus(TaskStatus.COMPLETE);
        task.setCreatedBy(owner);
        task.setOriginalFilename(name);
        task.setSourceFileObjectKey("source");
        task.setOutputFileObjectKey("output");
        return tasks.save(task);
    }

    @Test
    void anonymousAndRegularUsersCannotReadExecutorMetrics() throws Exception {
        mvc.perform(get("/admin/executors")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/executors").with(user("alice").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/executors").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void taskListsAndSummaryAreScopedToTheCurrentUser() throws Exception {
        mvc.perform(get("/api/task").with(user("alice").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].filename").value("alice.png"))
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
        mvc.perform(get("/api/task").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/dashboard").with(user("alice").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.counts.COMPLETE").value(1))
                .andExpect(jsonPath("$.submissions.length()").value(24));
        mvc.perform(get("/api/task").param("search", "bob").with(user("alice").roles("USER")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void usersCannotReadOrDownloadAnotherUsersTask() throws Exception {
        mvc.perform(get("/api/task/" + bobTask.getId()).with(user("alice").roles("USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/task/" + bobTask.getId() + "/result").with(user("alice").roles("USER")))
                .andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }

    @Test
    void ownerCanDownloadCompletedOutput() throws Exception {
        when(storage.download("output")).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        mvc.perform(get("/api/task/" + aliceTask.getId() + "/result").with(user("alice").roles("USER")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("alice-thumbnail.png")))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void uploadsReturnTrackableTaskAndRejectInvalidFiles() throws Exception {
        when(storage.upload(any(), anyLong(), any())).thenReturn("new-source");
        mvc.perform(multipart("/api/task/COMPRESS")
                        .file(new MockMultipartFile("file", "report.txt", "text/plain", "hello".getBytes()))
                        .with(user("alice").roles("USER")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.filename").value("report.txt"))
                .andExpect(jsonPath("$.owner").value("alice"))
                .andExpect(jsonPath("$.status").value("INITIAL"));
        mvc.perform(multipart("/api/task/GENERATE_THUMBNAIL")
                        .file(new MockMultipartFile("file", "report.txt", "text/plain", "hello".getBytes()))
                        .with(user("alice").roles("USER"))).andExpect(status().isBadRequest());
    }

    @Test
    void sessionErrorsReturnUnauthorizedAndRegistrationCannotChooseAdmin() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer malformed")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/register").contentType("application/json")
                        .content("{\"username\":\"new-member\",\"password\":\"password123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals("USER", users.findByUsername("new-member").orElseThrow().getRole());
        mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"username\":\"alice\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }
}
