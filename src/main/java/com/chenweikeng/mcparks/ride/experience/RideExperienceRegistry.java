package com.chenweikeng.mcparks.ride.experience;

import com.chenweikeng.mcparks.ride.experience.rides.DisneylandRailroad;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Central list of concrete {@link RideExperience} instances. Add new rides
 * here as dedicated classes under
 * {@code com.chenweikeng.mcparks.ride.experience.rides}.
 */
public final class RideExperienceRegistry {

    private static final RideExperienceRegistry INSTANCE = new RideExperienceRegistry();

    private final List<RideExperience> experiences = new ArrayList<>();

    public static RideExperienceRegistry getInstance() {
        return INSTANCE;
    }

    private RideExperienceRegistry() {
        register(new DisneylandRailroad());
    }

    public void register(RideExperience experience) {
        experiences.add(experience);
    }

    /** Registered experiences, in registration order. */
    public List<RideExperience> all() {
        return List.copyOf(experiences);
    }

    /** First experience whose {@link RideExperience#isActive} returns true, or empty. */
    public Optional<RideExperience> findActive(ExperienceContext ctx) {
        if (ctx == null) return Optional.empty();
        for (RideExperience exp : experiences) {
            if (exp.isActive(ctx)) return Optional.of(exp);
        }
        return Optional.empty();
    }
}
