package it.mazzoni.vis.admin;

import it.mazzoni.vis.domain.entity.SeedRun;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.SeedRunOutcomeRepository;
import it.mazzoni.vis.domain.repository.SeedRunRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeedRunServiceTest {
    @Mock SeedService seedService;
    @Mock SeedRunRepository runs;
    @Mock SeedRunOutcomeRepository outcomes;
    @Mock UserRepository users;

    @Test
    void smallSubmissionStaysSynchronousAndNormalizesSymbols() {
        SeedRunService service = service(new SeedRunProperties(10, 500, 1, 5, 30, 1500), Runnable::run);
        when(seedService.seedTickers(List.of("AAPL", "MSFT"))).thenReturn(List.of());

        SeedSubmissionResult result = service.submit(auth(), List.of(" aapl ", "AAPL", "msft"), "CSV");

        assertFalse(result.asynchronous());
        verify(seedService).seedTickers(List.of("AAPL", "MSFT"));
        verifyNoInteractions(runs);
    }

    @Test
    void longSubmissionPersistsQueuedRunAndSchedulesOnlyAfterCommit() {
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        Executor executor = scheduled::set;
        SeedRunService service = service(new SeedRunProperties(1, 500, 1, 5, 30, 1500), executor);
        User user = user();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(runs.findFirstByUserAndScopeAndRequestFingerprintAndStatusInOrderByCreatedAtDesc(
                eq(user), eq("CSV"), any(), any())).thenReturn(Optional.empty());
        when(runs.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SeedSubmissionResult result = service.submit(auth(), List.of("AAPL", "MSFT"), "CSV");

        assertTrue(result.asynchronous());
        assertEquals(2, result.accepted().normalizedTickerCount());
        assertNotNull(result.accepted().seedRunId());
        assertNotNull(scheduled.get());
        verify(runs).saveAndFlush(argThat(run -> "QUEUED".equals(run.getStatus()) && run.getTotalCount() == 2));
    }

    private SeedRunService service(SeedRunProperties properties, Executor executor) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:seed-run-" + System.nanoTime(), "sa", "");
        return new SeedRunService(seedService, runs, outcomes, users, properties, executor,
                new DataSourceTransactionManager(dataSource));
    }
    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken("investor@example.com", "n/a");
    }
    private User user() {
        User user = new User(); user.setEmail("investor@example.com"); user.setRole(UserRole.INVESTOR); return user;
    }
}
