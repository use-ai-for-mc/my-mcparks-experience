package com.chenweikeng.mcparks.ride.experience;

import com.chenweikeng.mcparks.ride.experience.rides.DisneylandRailroad;
import com.chenweikeng.mcparks.ride.experience.rides.GreatMomentsWithMrLincoln;
import com.chenweikeng.mcparks.ride.experience.rides.HauntedMansion;
import com.chenweikeng.mcparks.ride.experience.rides.JourneyIntoImagination;
import com.chenweikeng.mcparks.ride.experience.rides.LivingWithTheLand;
import com.chenweikeng.mcparks.ride.experience.rides.PeopleMover;
import com.chenweikeng.mcparks.ride.experience.rides.SpaceshipEarth;
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
        register(new GreatMomentsWithMrLincoln());
        register(new HauntedMansion());
        register(new JourneyIntoImagination());
        register(new LivingWithTheLand());
        register(new PeopleMover());
        register(new SpaceshipEarth());
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
