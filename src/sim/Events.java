package sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Events {
	private static final Random RNG = new Random();

	// ---- define your events here ----
	private final static List<Event> events = new ArrayList<>();

	// the event currently in effect (or null if none)
	private static Event currentEvent = null;
	private int ticksRemaining = 0;

	public Events() {
		// name, birthMod, deathMod, durationSteps, triggerProb
		events.add(new Event("Harvest Season", 1.6, 0.8, 5, 0.05));
		events.add(new Event("Drought", 0.8, 1.2, 5, 0.05));
		events.add(new Event("Migration Boom", 2.0, 1.0, 4, 0.03));
		events.add(new Event("Predator Invasion", 0.75, 3.0, 3, 0.02));
		// a very rare, short but severe plague
		events.add(new Event("Plague", 0.5, 6.0, 6, 0.005));
	}

	/**
	 * Call this once per timestep. It may start a new event (if none active), or
	 * decrement the remaining duration of the active one.
	 */
	public void update() {
		if (currentEvent != null) {
			ticksRemaining--;
			if (ticksRemaining <= 0) {
				System.out.println("Event ended: " + currentEvent.name);
				currentEvent = null;
			}
		} else {
			// try to trigger each event in turn
			for (Event e : events) {
				if (RNG.nextDouble() < e.triggerProb) {
					startEvent(e);
					break;
				}
			}
		}
	}

	private void startEvent(Event e) {
		currentEvent = e;
		ticksRemaining = e.duration;
		// compute how much above/below 100% this modifier is
		double birthDelta = (e.birthMod - 1.0) * 100.0;
		double deathDelta = (e.deathMod - 1.0) * 100.0;

		// decide sign and absolute value
		String birthSign = birthDelta >= 0 ? "+" : "-";
		String deathSign = deathDelta >= 0 ? "+" : "-";
		birthDelta = Math.abs(birthDelta);
		deathDelta = Math.abs(deathDelta);

		System.out.println(String.format("Event started: %s (%s%.0f%% birth, %s%.0f%% death) for %d steps.", e.name,
				birthSign, birthDelta, deathSign, deathDelta, e.duration));
	}

	/** Multiplicative modifier to apply to your base birthProbPerPair */
	public double getBirthModifier() {
		return currentEvent == null ? 1.0 : currentEvent.birthMod;
	}

	/** Multiplicative modifier to apply to your base deathProbPerStep */
	public double getDeathModifier() {
		return currentEvent == null ? 1.0 : currentEvent.deathMod;
	}

	// ---- inner class to hold event data ----
	private static class Event {
		final String name;
		final double birthMod;
		final double deathMod;
		final int duration;
		final double triggerProb;

		Event(String name, double birthMod, double deathMod, int duration, double triggerProb) {
			this.name = name;
			this.birthMod = birthMod;
			this.deathMod = deathMod;
			this.duration = duration;
			this.triggerProb = triggerProb;
		}


		
	}

	/** If I ever want the full list of all possible events: */
	public List<Event> getAllEvents() {
		return Collections.unmodifiableList(events);
	}
/** Returns the name of the current event, or "None" if there isn’t one. */

	public String getCurrentEventName() {
		return currentEvent != null ? currentEvent.name : "None";
	}
}
