package com.dantech.skimbrowser.testing;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.IdlingPolicies;
import androidx.test.espresso.IdlingResource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.reflector.ForType;
import org.robolectric.util.reflector.Reflector;
import org.robolectric.util.reflector.Static;

/**
 * Drops Compose's Espresso idling resource.
 *
 * <p>The unit-test thread is the paused main looper. {@code Espresso.onIdle} pumps that looper by
 * calling {@code MessageQueue.next}. When Compose's link is busy and the next frame is not due
 * yet, Robolectric's {@code nativePollOnce} waits until some other thread enqueues a message.
 * Nothing will: the test thread is the one blocked in the wait, and the paused clock does not
 * advance, so the Espresso timeout never fires. {@code performClick} then sits until CI kills the
 * job. Which test loses the race changes from run to run.
 *
 * <p>Compose still waits for itself. {@code RobolectricIdlingStrategy} checks its own idling
 * resource after {@code onIdle} and calls {@code requestLayout} until the composition is idle.
 * That path uses the wall clock, so a composition that never settles fails the test instead of
 * parking the looper.
 */
@Implements(Espresso.class)
public class ShadowSkimEspresso {

    /** Name of the resource Compose installs from {@code EspressoLink}. */
    static final String COMPOSE_LINK = "Compose-Espresso link";

    static {
        // A composition that never settles fails here, on the wall clock, instead of parking.
        IdlingPolicies.setMasterPolicyTimeout(15, TimeUnit.SECONDS);
        IdlingPolicies.setIdlingResourceTimeout(15, TimeUnit.SECONDS);
        HungTestWatch.install();
    }

    @Implementation
    public static boolean registerIdlingResources(IdlingResource... resources) {
        List<IdlingResource> kept = new ArrayList<>();
        for (IdlingResource resource : resources) {
            if (!COMPOSE_LINK.equals(resource.getName())) {
                kept.add(resource);
            }
        }
        if (kept.isEmpty()) {
            return false;
        }
        return Reflector.reflector(RealEspresso.class)
                .registerIdlingResources(kept.toArray(IdlingResource[]::new));
    }

    /** The real {@link Espresso#registerIdlingResources}, not this shadow. */
    @ForType(value = Espresso.class, direct = true)
    interface RealEspresso {
        @Static
        boolean registerIdlingResources(IdlingResource... resources);
    }
}
