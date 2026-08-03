package cn.lcxqy.starfree.economy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EconomyOperationJournalTest {
    @Test
    void explicitRequestIdProducesStableOperationKey() {
        EconomyOperationJournal journal = new EconomyOperationJournal(new ObjectMapper());

        String first = journal.requestKey("reward", 7L, "request-123", "11:10");
        String retry = journal.requestKey("reward", 7L, "request-123", "11:10");
        String different = journal.requestKey("reward", 7L, "request-124", "11:10");

        assertThat(retry).isEqualTo(first);
        assertThat(different).isNotEqualTo(first);
        assertThat(first).startsWith("reward:");
    }

    @Test
    void fixedKeySeparatesOperationKinds() {
        EconomyOperationJournal journal = new EconomyOperationJournal(new ObjectMapper());

        assertThat(journal.fixedKey("withdraw-review-1", 42L))
                .isNotEqualTo(journal.fixedKey("withdraw-review-0", 42L));
    }

    @Test
    void naturalKeysAreStableAndDoNotExposeTheIdentifier() {
        EconomyOperationJournal journal = new EconomyOperationJournal(new ObjectMapper());

        String first = journal.fixedKey("user-register", "alice\nalice@example.com");
        String replay = journal.fixedKey("user-register", "alice\nalice@example.com");
        String other = journal.fixedKey("user-register", "bob\nbob@example.com");

        assertThat(first).isEqualTo(replay);
        assertThat(first).isNotEqualTo(other);
        assertThat(first).startsWith("user-register:");
        assertThat(first).doesNotContain("alice");
    }
}
