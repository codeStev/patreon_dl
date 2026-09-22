package de.codestev.patreoningest.core.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProcessedEmailRepository extends JpaRepository<ProcessedEmail, UUID> {

    Optional<ProcessedEmail> findByMailboxAndUid(String mailbox, long uid);
}
