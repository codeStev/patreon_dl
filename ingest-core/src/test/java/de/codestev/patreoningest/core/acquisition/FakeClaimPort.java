package de.codestev.patreoningest.core.acquisition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

// Test double for ClaimQueueJobTest - stands in for GumroadClaimAdapter
// (a real browser). Scripted with the outcome of each successive call;
// joins ClaimQueueJob's List<ClaimPort> injection point as a plain bean.
class FakeClaimPort implements ClaimPort {

    private final Deque<Supplier<ClaimOutcome>> scripted = new ArrayDeque<>();
    private final List<String> claimedUrls = new ArrayList<>();

    @Override
    public SourceType supports() {
        return SourceType.GUMROAD;
    }

    @Override
    public ClaimOutcome claim(DownloadSource source) {
        claimedUrls.add(source.getSourceUrl());
        Supplier<ClaimOutcome> next = scripted.poll();
        return next != null ? next.get() : new ClaimOutcome.Claimed(null);
    }

    void willReturn(ClaimOutcome outcome) {
        scripted.add(() -> outcome);
    }

    void willThrow(RuntimeException e) {
        scripted.add(() -> {
            throw e;
        });
    }

    List<String> claimedUrls() {
        return claimedUrls;
    }

    void reset() {
        scripted.clear();
        claimedUrls.clear();
    }
}
