package uk.co.reecenicholls.spawnticks.runtime;

/**
 * Exposes {@code ServerLevel#emptyTime} so unattended chunk ticking can detect
 * when an empty dimension's entity ticking has paused at the 300-tick vanilla limit.
 */
public interface EmptyTimeAccess {
    int spawnticks$getEmptyTime();
}
