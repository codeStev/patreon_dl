package de.codestev.patreoningest.core.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProcessedEmailRepository extends JpaRepository<ProcessedEmail, UUID> {

    Optional<ProcessedEmail> findByMailboxAndUid(String mailbox, long uid);

    @Query("select max(pe.uid) from ProcessedEmail pe where pe.mailbox = :mailbox")
    Optional<Long> findMaxUidByMailbox(@Param("mailbox") String mailbox);
}
