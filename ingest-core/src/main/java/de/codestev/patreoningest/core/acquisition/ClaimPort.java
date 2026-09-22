package de.codestev.patreoningest.core.acquisition;

// One per source type that needs an external action to secure ownership.
// DRIVE and MMF have no implementation at all - ClaimSourceUseCase treats
// "no ClaimPort registered for this SourceType" as an implicit, immediate
// claim. GUMROAD's real implementation (Playwright checkout automation) is
// deferred to a later pass.
public interface ClaimPort {

    SourceType supports();

    void claim(DownloadSource source);
}
