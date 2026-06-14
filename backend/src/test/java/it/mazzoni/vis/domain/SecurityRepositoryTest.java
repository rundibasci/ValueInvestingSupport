package it.mazzoni.vis.domain;

import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SecurityRepositoryTest {

    @Autowired
    private SecurityRepository repository;

    @Test
    void savesAndFindsSecurityBySymbol() {
        Security s = new Security();
        s.setSymbol("AAPL");
        s.setCompanyName("Apple Inc.");
        s.setSector("Technology");
        repository.save(s);

        Optional<Security> found = repository.findBySymbol("AAPL");
        assertThat(found).isPresent();
        assertThat(found.get().getCompanyName()).isEqualTo("Apple Inc.");
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void returnsEmptyForUnknownSymbol() {
        assertThat(repository.findBySymbol("UNKNOWN")).isEmpty();
    }

    @Test
    void rejectsDuplicateSymbol() {
        Security a = new Security();
        a.setSymbol("MSFT");
        a.setCompanyName("Microsoft Corporation");
        repository.saveAndFlush(a);

        Security b = new Security();
        b.setSymbol("MSFT");
        b.setCompanyName("Duplicate");

        assertThatThrownBy(() -> repository.saveAndFlush(b))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsBySymbolWorks() {
        Security s = new Security();
        s.setSymbol("GOOGL");
        s.setCompanyName("Alphabet Inc.");
        repository.save(s);

        assertThat(repository.existsBySymbol("GOOGL")).isTrue();
        assertThat(repository.existsBySymbol("TSLA")).isFalse();
    }
}
