package LLD.f1lastlaphero;

/**
 * Observer pattern: implementers are notified whenever the Last Lap Hero
 * changes (not on every lap update - only on actual hero changes, so a
 * dashboard/commentary feed doesn't get spammed on every no-op update).
 */
public interface HeroChangeListener {
    /**
     * @param previousHero driverId of the prior hero, or null if there was none yet
     * @param newHero      driverId of the new hero
     */
    void onHeroChanged(String previousHero, String newHero);
}
