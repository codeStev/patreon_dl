package de.codestev.patreoningest.core.acquisition;

// One per source type that needs an external action to secure ownership.
// DRIVE and MMF have no implementation at all - ClaimSourceUseCase treats
// "no ClaimPort registered for this SourceType" as an implicit, immediate
// claim. Port-backed claims never run inline during ingestion - ClaimQueueJob
// drives them in the background, since they're slow (a real browser for
// Gumroad) and must not be able to break a mailbox poll.
public interface ClaimPort {

    SourceType supports();

    ClaimOutcome claim(DownloadSource source);
}
